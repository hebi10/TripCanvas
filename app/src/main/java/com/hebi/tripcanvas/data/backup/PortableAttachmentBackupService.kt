package com.hebi.tripcanvas.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.hebi.tripcanvas.data.local.BookingAttachmentEntity
import com.hebi.tripcanvas.data.local.TripDatabase
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class PortableAttachmentExportResult(val trips: Int, val files: Int, val originalBytes: Int)

/** Full portable archive creation. The archive never replaces or deletes its source files. */
class PortableAttachmentBackupService(
    private val context: Context,
    private val database: TripDatabase
) {
    private data class Source(
        val uri: String,
        val id: String,
        val bookingId: String? = null,
        val displayName: String? = null,
        val mimeType: String? = null,
        val addedAtMillis: Long? = null
    ) {
        val photo: Boolean get() = bookingId == null
    }

    suspend fun export(destination: Uri, password: CharArray): PortableAttachmentExportResult =
        withContext(Dispatchers.IO) {
            require(EncryptedTripBackup.validPassword(password))
            require(destination.scheme == "content" && destination.authority != null)
            val coroutine = currentCoroutineContext()
            val (snapshot, attachments) = database.withTransaction {
                val dao = database.backupDao()
                dao.snapshot() to dao.allBookingAttachments()
            }
            RestoreCheckpointCodec.validate(RestoreCheckpoint("portable", System.currentTimeMillis(), snapshot, attachments))
            val sources = snapshot.moments.mapNotNull { moment ->
                moment.photoUri?.let { Source(uri = it, id = moment.id) }
            } + attachments.map { attachment ->
                Source(attachment.uriString, attachment.id, attachment.bookingId,
                    attachment.displayName, attachment.mimeType, attachment.addedAtMillis)
            }
            require(sources.size in 1..PortableMediaArchive.MAX_FILES) {
                "첨부 파일은 1~100개까지 백업할 수 있습니다."
            }
            require(sources.none { Uri.parse(it.uri) == destination }) {
                "원본 파일을 백업 저장 대상으로 지정할 수 없습니다."
            }

            // Portable snapshots never persist a device-specific photo URI.
            val photoIndexes = sources.mapIndexedNotNull { index, source ->
                if (source.photo) source.id to index else null
            }.toMap()
            val portable = snapshot.copy(moments = snapshot.moments.map { moment ->
                val index = photoIndexes[moment.id]
                if (index == null) moment else moment.copy(photoUri = PortableMediaArchive.photoPlaceholder(index))
            })
            val snapshotBytes = TripBackupCodec.encode(portable).toByteArray(Charsets.UTF_8)
            val resolver = context.contentResolver
            var stage: File? = null
            try {
                require(snapshotBytes.size <= TripBackupCodec.MAX_BYTES)
                val staged = File.createTempFile("tc-media-", ".encrypted", context.cacheDir)
                stage = staged
                var total = 0
                FileOutputStream(staged).use { output ->
                    PortableMediaArchive.writeEncryptedZip(output, password) { zip ->
                        zip.putNextEntry(ZipEntry("snapshot.json"))
                        zip.write(snapshotBytes)
                        zip.closeEntry()
                        val media = JSONArray()
                        sources.forEachIndexed { index, source ->
                            coroutine.ensureActive()
                            val uri = Uri.parse(source.uri)
                            require(uri.scheme == "content" && uri.authority != null)
                            val mime = resolver.getType(uri)?.lowercase(Locale.ROOT)
                                ?: throw IOException("첨부 파일 형식을 확인할 수 없습니다.")
                            if (!source.photo) {
                                require(source.mimeType?.lowercase(Locale.ROOT) == mime) {
                                    "예약 첨부 파일 형식이 변경되었습니다."
                                }
                            }
                            val entry = PortableMediaArchive.mediaPath(index, mime, source.photo)
                            val row = JSONObject()
                                .put("kind", if (source.photo) "photo" else "booking")
                                .put("id", source.id)
                                .put("path", entry)
                                .put("mimeType", mime)
                            if (!source.photo) {
                                row.put("bookingId", source.bookingId)
                                    .put("displayName", source.displayName)
                                    .put("addedAtMillis", source.addedAtMillis)
                            }
                            val input = resolver.openInputStream(uri)
                                ?: throw IOException("첨부 원본을 열 수 없습니다.")
                            val copied = input.use {
                                zip.putNextEntry(ZipEntry(entry))
                                PortableMediaArchive.copyMedia(it, zip, PortableMediaArchive.MAX_MEDIA_BYTES - total)
                                    .also { zip.closeEntry() }
                            }
                            total += copied.bytes
                            media.put(row.put("bytes", copied.bytes).put("sha256", copied.sha256))
                        }
                        val manifest = JSONObject().put("format", "tripcanvas-portable-media")
                            .put("version", 1).put("media", media).put("originalBytes", total)
                            .toString().toByteArray(Charsets.UTF_8)
                        try {
                            require(manifest.size <= PortableMediaArchive.MAX_MANIFEST_BYTES)
                            zip.putNextEntry(ZipEntry("manifest.json"))
                            zip.write(manifest)
                            zip.closeEntry()
                        } finally {
                            manifest.fill(0)
                        }
                    }
                    output.fd.sync()
                }
                coroutine.ensureActive()
                val expected = staged.inputStream().use { PortableMediaArchive.digest(it) }
                require(expected.first > 0 && expected.first <= PortableMediaArchive.MAX_ENVELOPE_BYTES.toLong())

                // SAF CreateDocument is used by the UI. Refuse any nonempty destination,
                // including a distinct URI pointing at the same nonempty source file.
                val empty = resolver.openInputStream(destination)?.use { it.read() == -1 }
                    ?: throw IOException("새 백업 저장 파일을 확인할 수 없습니다.")
                require(empty) { "이미 자료가 있는 파일은 덮어쓰지 않습니다." }
                coroutine.ensureActive()
                val output = resolver.openOutputStream(destination, "wt")
                    ?: throw IOException("백업 저장 파일을 열 수 없습니다.")
                output.use { out -> staged.inputStream().use { it.copyTo(out) } }
                val actual = resolver.openInputStream(destination)?.use { PortableMediaArchive.digest(it) }
                    ?: throw IOException("저장된 백업을 다시 열 수 없습니다.")
                require(actual == expected) { "저장된 백업 파일 검증에 실패했습니다." }
                PortableAttachmentExportResult(snapshot.trips.size, sources.size, total)
            } finally {
                snapshotBytes.fill(0)
                stage?.delete()
            }
        }

    suspend fun preview(source: Uri, password: CharArray): PortableAttachmentPreview =
        withContext(Dispatchers.IO) {
            readVerified(source, password)
        }

    suspend fun restore(
        source: Uri,
        password: CharArray,
        approved: PortableAttachmentPreview,
        backupService: TripBackupService = TripBackupService(context, database)
    ) = withContext(Dispatchers.IO) {
        mediaMutex.withLock {
            require(source.scheme == "content" && source.authority != null)
            val coroutine = currentCoroutineContext()
            val root = File(context.filesDir, "restored_media")
            val directory = File(root, UUID.randomUUID().toString())
            val media = File(directory, "media")
            val authority = context.packageName + ".restoredmedia"
            var committed = false
            try {
                // Limit copies retained for current data and previous recovery points.
                require(ownedBytes(root) + approved.originalBytes <= MAX_MANAGED_BYTES) {
                    "복원한 첨부 저장 공간이 부족합니다. 사용하지 않는 복원 파일을 정리해 주세요."
                }
                check(media.mkdirs()) { "첨부 복원 저장 공간을 만들 수 없습니다." }
                val verified = readVerified(source, password, approved.archiveSha256) { path ->
                    coroutine.ensureActive()
                    // The reader accepts only canonical sequential media/NNN.ext paths.
                    val file = File(media, path.substringAfter('/'))
                    require(file.canonicalFile.parentFile == media.canonicalFile && !file.exists())
                    object : FileOutputStream(file) {
                        override fun close() {
                            try { fd.sync() } finally { super.close() }
                        }
                    }
                }
                require(verified == approved) { "미리보기 이후 첨부 백업이 변경되었습니다." }
                val photoLinks = mutableMapOf<String, String>()
                val attachments = mutableListOf<BookingAttachmentEntity>()
                verified.media.forEach { record ->
                    coroutine.ensureActive()
                    val file = File(media, record.path.substringAfter('/'))
                    val uri = FileProvider.getUriForFile(context, authority, file)
                    val actual = context.contentResolver.openInputStream(uri)?.use {
                        PortableMediaArchive.digest(it, PortableMediaArchive.MAX_FILE_BYTES.toLong())
                    } ?: throw IOException("복원 파일을 다시 열 수 없습니다.")
                    require(actual == (record.bytes.toLong() to record.sha256))
                    if (record.kind == "photo") {
                        photoLinks[record.id] = uri.toString()
                    } else {
                        attachments.add(BookingAttachmentEntity(
                            record.id, requireNotNull(record.bookingId), uri.toString(),
                            requireNotNull(record.displayName), record.mimeType, requireNotNull(record.addedAtMillis)
                        ))
                    }
                }
                val restored = verified.snapshot.copy(moments = verified.snapshot.moments.map { moment ->
                    if (moment.photoUri == null) moment
                    else moment.copy(photoUri = requireNotNull(photoLinks[moment.id]))
                })
                coroutine.ensureActive()
                // The DB transaction saves a verified checkpoint first. Once it commits,
                // cancellation must not remove the files now referenced by Room.
                withContext(NonCancellable) {
                    backupService.restorePortable(restored, attachments)
                    committed = true
                }
            } finally {
                if (!committed) directory.deleteRecursively()
            }
        }
    }

    suspend fun cleanupUnused(backupService: TripBackupService = TripBackupService(context, database)): Int =
        withContext(Dispatchers.IO) { mediaMutex.withLock { backupService.cleanupUnusedRestoredMedia() } }

    private fun readVerified(
        source: Uri,
        password: CharArray,
        expectedSha: String? = null,
        openMediaOutput: ((String) -> java.io.OutputStream)? = null
    ): PortableAttachmentPreview {
        require(source.scheme == "content" && source.authority != null)
        val envelope = context.contentResolver.openInputStream(source)?.use {
            PortableMediaReader.readBounded(it, PortableMediaArchive.MAX_ENVELOPE_BYTES)
        } ?: throw IOException("첨부 백업 파일을 열 수 없습니다.")
        try {
            val fingerprint = PortableMediaArchive.digest(ByteArrayInputStream(envelope)).second
            require(expectedSha == null || expectedSha == fingerprint) {
                "미리보기 이후 첨부 백업이 변경되었습니다."
            }
            val plaintext = PortableMediaArchive.decryptVerified(envelope, password)
            try {
                return PortableMediaReader.inspect(plaintext, fingerprint, openMediaOutput)
            } finally {
                plaintext.fill(0)
            }
        } finally {
            envelope.fill(0)
        }
    }

    private fun ownedBytes(root: File): Long =
        if (!root.exists()) 0L else root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private companion object {
        val mediaMutex = Mutex()
        const val MAX_MANAGED_BYTES = 200L * 1024 * 1024
    }

}
