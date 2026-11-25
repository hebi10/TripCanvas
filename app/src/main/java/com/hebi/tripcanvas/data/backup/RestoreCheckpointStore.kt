package com.hebi.tripcanvas.data.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.hebi.tripcanvas.data.local.BookingAttachmentEntity
import com.hebi.tripcanvas.data.local.TripBackupSnapshot
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

data class RestoreCheckpoint(
    val id: String,
    val createdAtMillis: Long,
    val snapshot: TripBackupSnapshot,
    val attachments: List<BookingAttachmentEntity>
)

interface RestoreCheckpointStore {
    fun exists(): Boolean
    fun save(checkpoint: RestoreCheckpoint)
    fun load(): RestoreCheckpoint?
    fun delete()
}

class RestoreSafetyBackupFailed : IOException("복원 전 안전 백업을 저장하고 검증하지 못했습니다.")

internal object RestoreCheckpointCodec {
    const val MAX_BYTES = 16 * 1024 * 1024

    fun validate(value: RestoreCheckpoint) {
        require(value.id.isNotBlank() && value.id.length <= 128 && value.createdAtMillis > 0)
        TripBackupCodec.validate(value.snapshot)
        require(value.attachments.size <= 100_000)
        val bookings = value.snapshot.bookings.map { it.id }.toHashSet()
        require(value.attachments.map { it.id }.toSet().size == value.attachments.size)
        value.attachments.forEach {
            require(it.id.isNotBlank() && it.id.length <= 128 && it.bookingId in bookings)
            require(it.uriString.startsWith("content://") && it.uriString.length <= 16_384)
            require(it.displayName.length in 1..250 && it.mimeType.length in 1..120)
            require(it.mimeType == "application/pdf" || it.mimeType.startsWith("image/"))
        }
    }

    fun encode(value: RestoreCheckpoint): ByteArray {
        validate(value)
        val structured = TripBackupCodec.encode(value.snapshot)
        require(structured.toByteArray(Charsets.UTF_8).size <= TripBackupCodec.MAX_BYTES)
        val links = JSONArray()
        value.attachments.forEach {
            links.put(JSONObject().put("id", it.id).put("bookingId", it.bookingId)
                .put("uri", it.uriString).put("name", it.displayName).put("mime", it.mimeType)
                .put("addedAtMillis", it.addedAtMillis))
        }
        return JSONObject().put("version", 1).put("id", value.id)
            .put("createdAtMillis", value.createdAtMillis)
            .put("snapshot", JSONObject(structured)).put("attachments", links)
            .toString().toByteArray(Charsets.UTF_8).also { bytes ->
                if (bytes.size > MAX_BYTES) {
                    bytes.fill(0)
                    throw IllegalArgumentException("안전 백업의 허용 용량을 초과했습니다.")
                }
            }
    }

    fun decode(bytes: ByteArray): RestoreCheckpoint {
        require(bytes.size in 1..MAX_BYTES)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        val root = JSONObject(text)
        require(root.getInt("version") == 1)
        val structured = root.getJSONObject("snapshot").toString()
        require(structured.toByteArray(Charsets.UTF_8).size <= TripBackupCodec.MAX_BYTES)
        val links = root.getJSONArray("attachments")
        require(links.length() <= 100_000)
        val attachments = (0 until links.length()).map { index ->
            val row = links.getJSONObject(index)
            BookingAttachmentEntity(row.getString("id"), row.getString("bookingId"),
                row.getString("uri"), row.getString("name"), row.getString("mime"),
                row.getLong("addedAtMillis"))
        }
        return RestoreCheckpoint(root.getString("id"), root.getLong("createdAtMillis"),
            TripBackupCodec.decode(structured), attachments).also(::validate)
    }
}

/** App-private, device-bound recovery point; not a portable backup file. */
class DeviceRestoreCheckpointStore(
    context: Context,
    private val file: File = File(context.noBackupFilesDir, "tripcanvas-restore-checkpoint.v1"),
    private val keyAlias: String = "tripcanvas.restore-checkpoint.v1"
) : RestoreCheckpointStore {
    private val atomic = AtomicFile(file)

    override fun exists(): Boolean = file.exists() || File(file.path + ".bak").exists()

    override fun save(checkpoint: RestoreCheckpoint) {
        val plaintext = RestoreCheckpointCodec.encode(checkpoint)
        try {
            require(RestoreCheckpointCodec.decode(plaintext) == checkpoint)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
            val nonce = cipher.iv
            require(nonce.size == NONCE_BYTES)
            val header = MAGIC + byteArrayOf(VERSION) + nonce
            cipher.updateAAD(header)
            val encrypted = header + cipher.doFinal(plaintext)
            val output = atomic.startWrite()
            try {
                output.write(encrypted)
                output.fd.sync()
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
            // AtomicFile may report rename failures via logs; require actual readback.
            require(load() == checkpoint) { "안전 백업을 다시 읽어 확인하지 못했습니다." }
        } finally {
            plaintext.fill(0)
        }
    }

    override fun load(): RestoreCheckpoint? {
        if (!exists()) return null
        val encrypted = atomic.openRead().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                require(count <= MAX_ENVELOPE_BYTES - output.size())
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(encrypted.size in (HEADER_BYTES + TAG_BYTES + 1)..MAX_ENVELOPE_BYTES)
        require(MAGIC.indices.all { encrypted[it] == MAGIC[it] } && encrypted[MAGIC.size] == VERSION)
        val nonce = encrypted.copyOfRange(MAGIC.size + 1, HEADER_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(TAG_BYTES * 8, nonce))
        cipher.updateAAD(encrypted, 0, HEADER_BYTES)
        val plaintext = cipher.doFinal(encrypted, HEADER_BYTES, encrypted.size - HEADER_BYTES)
        try {
            return RestoreCheckpointCodec.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    override fun delete() {
        atomic.delete()
        check(!exists() && !File(file.path + ".new").exists()) { "안전 백업을 삭제하지 못했습니다." }
    }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(keyAlias, null)
        if (existing != null) return existing as SecretKey
        check(create) { "안전 백업 암호화 키를 찾을 수 없습니다." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
            generateKey()
        }
    }

    private companion object {
        val MAGIC = "TCRESTOR".toByteArray(Charsets.US_ASCII)
        const val VERSION: Byte = 1
        const val NONCE_BYTES = 12
        const val TAG_BYTES = 16
        const val HEADER_BYTES = 8 + 1 + NONCE_BYTES
        const val MAX_ENVELOPE_BYTES = RestoreCheckpointCodec.MAX_BYTES + HEADER_BYTES + TAG_BYTES
    }
}
