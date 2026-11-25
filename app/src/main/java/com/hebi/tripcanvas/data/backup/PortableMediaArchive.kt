package com.hebi.tripcanvas.data.backup

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Bounded, password-protected original-media archive. No plaintext temporary archive. */
object PortableMediaArchive {
    const val MAX_FILES = 100
    const val MAX_FILE_BYTES = 10 * 1024 * 1024
    const val MAX_MEDIA_BYTES = 20 * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 1024 * 1024
    const val MAX_PAYLOAD_BYTES = 32 * 1024 * 1024
    private val MAGIC = "TCMEDIA1".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val HEADER_BYTES = 8 + 1 + SALT_BYTES + NONCE_BYTES
    const val MAX_ENVELOPE_BYTES = MAX_PAYLOAD_BYTES + HEADER_BYTES + TAG_BYTES

    fun extension(mimeType: String, photo: Boolean): String {
        return when (mimeType.lowercase(Locale.ROOT)) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "image/avif" -> "avif"
            "application/pdf" -> {
                require(!photo) { "사진 기록에 PDF를 연결할 수 없습니다." }
                "pdf"
            }
            else -> throw IllegalArgumentException("지원하지 않는 첨부 파일 형식입니다.")
        }
    }

    fun mediaPath(index: Int, mimeType: String, photo: Boolean): String {
        require(index in 0 until MAX_FILES)
        return "media/" + index.toString().padStart(3, '0') + "." + extension(mimeType, photo)
    }

    fun photoPlaceholder(index: Int): String {
        require(index in 0 until MAX_FILES)
        return "content://com.hebi.tripcanvas.archive/photos/" + index.toString().padStart(3, '0')
    }

    data class CopiedMedia(val bytes: Int, val sha256: String)

    fun copyMedia(input: InputStream, output: OutputStream, remainingBytes: Int): CopiedMedia {
        require(remainingBytes in 0..MAX_MEDIA_BYTES)
        val digest = MessageDigest.getInstance("SHA-256")
        val limit = minOf(MAX_FILE_BYTES, remainingBytes)
        val buffer = ByteArray(8192)
        var total = 0
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(count > 0 && count <= limit - total) {
                    "첨부 파일의 개별 또는 전체 허용 용량을 초과했습니다."
                }
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                total += count
            }
            require(total > 0) { "빈 첨부 파일은 백업할 수 없습니다." }
            return CopiedMedia(total, digest.digest().toHex())
        } finally {
            buffer.fill(0)
        }
    }

    fun writeEncryptedZip(output: OutputStream, password: CharArray, writeEntries: (ZipOutputStream) -> Unit) {
        require(EncryptedTripBackup.validPassword(password))
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = MAGIC + byteArrayOf(VERSION) + salt + nonce
        val key = derive(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
            cipher.updateAAD(header)
            output.write(header)
            val payload = object : OutputStream() {
                private var total = 0
                override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    require(length >= 0 && length <= MAX_PAYLOAD_BYTES - total) {
                        "암호화 첨부 백업의 허용 용량을 초과했습니다."
                    }
                    if (length == 0) return
                    total += length
                    cipher.update(bytes, offset, length)?.let { output.write(it) }
                }
                override fun flush() = output.flush()
            }
            // Original bytes are retained; no recompression of already compressed media.
            ZipOutputStream(payload).use { zip ->
                zip.setLevel(Deflater.NO_COMPRESSION)
                writeEntries(zip)
            }
            // Explicit finalization: do not report success if writing the GCM tag fails.
            output.write(cipher.doFinal())
            output.flush()
        } finally {
            key.fill(0)
        }
    }

    /** Authenticate the complete bounded envelope before exposing any ZIP plaintext. */
    fun decryptVerified(envelope: ByteArray, password: CharArray): ByteArray {
        require(envelope.size in (HEADER_BYTES + TAG_BYTES + 1)..MAX_ENVELOPE_BYTES)
        require(MAGIC.indices.all { envelope[it] == MAGIC[it] } && envelope[MAGIC.size] == VERSION)
        require(EncryptedTripBackup.validPassword(password))
        val salt = envelope.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + SALT_BYTES)
        val nonce = envelope.copyOfRange(HEADER_BYTES - NONCE_BYTES, HEADER_BYTES)
        val key = derive(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
            cipher.updateAAD(envelope, 0, HEADER_BYTES)
            return cipher.doFinal(envelope, HEADER_BYTES, envelope.size - HEADER_BYTES)
        } finally {
            key.fill(0)
        }
    }

    fun digest(input: InputStream, limit: Long = MAX_ENVELOPE_BYTES.toLong()): Pair<Long, String> {
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var count = 0L
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                require(read > 0 && read <= limit - count)
                count += read
                hash.update(buffer, 0, read)
            }
            return count to hash.digest().toHex()
        } finally {
            buffer.fill(0)
        }
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, 600_000, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
