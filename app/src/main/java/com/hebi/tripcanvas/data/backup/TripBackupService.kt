package com.hebi.tripcanvas.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.hebi.tripcanvas.data.local.BookingAttachmentEntity
import com.hebi.tripcanvas.data.local.TripBackupSnapshot
import com.hebi.tripcanvas.data.local.TripDatabase
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BackupPasswordRequired : IllegalArgumentException("암호화 백업 비밀번호가 필요합니다.")

class TripBackupService(
    private val context: Context,
    private val database: TripDatabase,
    private val checkpointStore: RestoreCheckpointStore = DeviceRestoreCheckpointStore(context.applicationContext)
) {
    suspend fun export(uri: Uri): TripBackupSnapshot = withContext(Dispatchers.IO) {
        val snapshot = database.backupDao().snapshot()
        val bytes = encode(snapshot)
        try {
            write(uri, bytes)
            snapshot
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun exportEncrypted(uri: Uri, password: CharArray): TripBackupSnapshot = withContext(Dispatchers.IO) {
        require(EncryptedTripBackup.validPassword(password))
        val snapshot = database.backupDao().snapshot()
        val plaintext = encode(snapshot)
        try {
            write(uri, EncryptedTripBackup.encrypt(plaintext, password))
            snapshot
        } finally {
            plaintext.fill(0)
        }
    }

    private fun encode(snapshot: TripBackupSnapshot): ByteArray {
        val bytes = TripBackupCodec.encode(snapshot).toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > TripBackupCodec.MAX_BYTES) {
            bytes.fill(0)
            throw IllegalArgumentException("백업 용량이 제한을 초과합니다.")
        }
        return bytes
    }

    private fun write(uri: Uri, bytes: ByteArray) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("파일을 저장할 수 없습니다.")
        stream.use { it.write(bytes) }
    }

    suspend fun preview(uri: Uri, password: CharArray? = null): TripBackupSnapshot = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("파일을 열 수 없습니다.")
        val bytes = ByteArrayOutputStream()
        stream.use { input ->
            val buffer = ByteArray(8192)
            try {
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    total += count
                    require(total <= EncryptedTripBackup.MAX_BYTES) {
                        "백업 파일 크기가 허용 범위를 초과했습니다."
                    }
                    bytes.write(buffer, 0, count)
                }
            } finally {
                buffer.fill(0)
            }
        }
        val raw = bytes.toByteArray()
        var plaintext: ByteArray? = null
        try {
            val decoded = if (EncryptedTripBackup.isEncrypted(raw)) {
                val supplied = password ?: throw BackupPasswordRequired()
                EncryptedTripBackup.decrypt(raw, supplied).also { plaintext = it }
            } else {
                require(password == null) { "선택한 암호화 백업 파일이 변경되었습니다." }
                require(raw.size <= TripBackupCodec.MAX_BYTES) { "백업 파일 크기가 허용 범위를 초과했습니다." }
                raw
            }
            val text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(decoded)).toString()
            TripBackupCodec.decode(text)
        } catch (required: BackupPasswordRequired) {
            throw required
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw IllegalArgumentException("비밀번호가 올바르지 않거나 백업 파일이 손상되었거나 지원하지 않는 버전입니다.")
        } finally {
            plaintext?.fill(0)
            raw.fill(0)
        }
    }

    suspend fun hasRestoreCheckpoint(): Boolean = withContext(Dispatchers.IO) {
        restoreMutex.withLock { checkpointStore.exists() }
    }

    suspend fun previewRestoreCheckpoint(): RestoreCheckpoint = withContext(Dispatchers.IO) {
        restoreMutex.withLock {
            requireNotNull(checkpointStore.load()) { "복원 전 안전 백업이 없습니다." }
                .also(RestoreCheckpointCodec::validate)
        }
    }

    suspend fun deleteRestoreCheckpoint() = withContext(Dispatchers.IO) {
        restoreMutex.withLock { checkpointStore.delete() }
    }

    suspend fun restore(snapshot: TripBackupSnapshot) = withContext(Dispatchers.IO) {
        TripBackupCodec.validate(snapshot)
        restoreMutex.withLock {
            database.withTransaction {
                saveCurrentCheckpoint()
                database.backupDao().replaceAll(snapshot)
            }
        }
    }

    internal suspend fun cleanupUnusedRestoredMedia(): Int = withContext(Dispatchers.IO) {
        restoreMutex.withLock {
            database.withTransaction {
                val dao = database.backupDao()
                val current = dao.snapshot()
                val checkpoint = if (checkpointStore.exists()) {
                    requireNotNull(checkpointStore.load()).also(RestoreCheckpointCodec::validate)
                } else null
                val references = current.moments.mapNotNull { it.photoUri } +
                    dao.allBookingAttachments().map { it.uriString } +
                    checkpoint?.snapshot?.moments.orEmpty().mapNotNull { it.photoUri } +
                    checkpoint?.attachments.orEmpty().map { it.uriString }
                val authority = context.packageName + ".restoredmedia"
                val batchName = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                val mediaName = Regex("[0-9]{3}\\.(jpg|png|webp|gif|heic|heif|avif|pdf)")
                val protected = references.mapNotNull { value ->
                    val uri = Uri.parse(value)
                    if (uri.authority != authority) null else {
                        val segments = uri.pathSegments
                        require(uri.scheme == "content" && segments.size == 4 &&
                            segments[0] == "restored_media" && segments[1].matches(batchName) &&
                            segments[2] == "media" && segments[3].matches(mediaName)) {
                            "복원 파일 참조를 확인하지 못해 정리를 중단했습니다."
                        }
                        segments[1]
                    }
                }.toSet()
                val root = File(context.filesDir, "restored_media")
                var deleted = 0
                if (root.exists()) {
                    require(root.canonicalFile.parentFile == context.filesDir.canonicalFile)
                    requireNotNull(root.listFiles()).forEach { batch ->
                        if (batch.name in protected || !batch.name.matches(batchName) ||
                            !batch.isDirectory || batch.canonicalFile.parentFile != root.canonicalFile
                        ) return@forEach
                        val media = File(batch, "media")
                        if (!media.isDirectory || media.canonicalFile.parentFile != batch.canonicalFile ||
                            batch.listFiles()?.map { it.name } != listOf("media")
                        ) return@forEach
                        val files = media.listFiles() ?: return@forEach
                        if (files.any { !it.isFile || !it.name.matches(mediaName) ||
                                it.canonicalFile.parentFile != media.canonicalFile
                            }) return@forEach
                        files.forEach { file ->
                            if (file.delete()) deleted++
                        }
                        if (media.listFiles()?.isEmpty() == true) media.delete()
                        if (batch.listFiles()?.isEmpty() == true) batch.delete()
                    }
                }
                deleted
            }
        }
    }

    internal suspend fun restorePortable(
        snapshot: TripBackupSnapshot,
        attachments: List<BookingAttachmentEntity>
    ) = withContext(Dispatchers.IO) {
        RestoreCheckpointCodec.validate(
            RestoreCheckpoint("portable", System.currentTimeMillis(), snapshot, attachments)
        )
        restoreMutex.withLock {
            database.withTransaction {
                saveCurrentCheckpoint()
                val dao = database.backupDao()
                dao.replaceAll(snapshot)
                dao.insertBookingAttachments(attachments)
            }
        }
    }

    suspend fun restoreCheckpoint(checkpoint: RestoreCheckpoint) = withContext(Dispatchers.IO) {
        RestoreCheckpointCodec.validate(checkpoint)
        restoreMutex.withLock {
            require(checkpointStore.load() == checkpoint) {
                "안전 백업이 변경되었습니다. 다시 확인해 주세요."
            }
            database.withTransaction {
                saveCurrentCheckpoint()
                val dao = database.backupDao()
                dao.replaceAll(checkpoint.snapshot)
                dao.insertBookingAttachments(checkpoint.attachments)
            }
        }
    }

    private suspend fun saveCurrentCheckpoint() {
        try {
            val dao = database.backupDao()
            val checkpoint = RestoreCheckpoint(
                id = UUID.randomUUID().toString(),
                createdAtMillis = System.currentTimeMillis(),
                snapshot = dao.snapshot(),
                attachments = dao.allBookingAttachments()
            )
            RestoreCheckpointCodec.validate(checkpoint)
            checkpointStore.save(checkpoint)
            require(checkpointStore.load() == checkpoint)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Fail closed before replacing any Room rows.
            throw RestoreSafetyBackupFailed()
        }
    }

    private companion object {
        val restoreMutex = Mutex()
    }
}
