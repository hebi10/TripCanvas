package com.hebi.tripcanvas.data.backup

import com.hebi.tripcanvas.data.local.TripBackupSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import org.json.JSONObject

data class PortableMediaRecord(
    val kind: String,
    val id: String,
    val path: String,
    val mimeType: String,
    val bytes: Int,
    val sha256: String,
    val bookingId: String? = null,
    val displayName: String? = null,
    val addedAtMillis: Long? = null
)

data class PortableAttachmentPreview(
    val snapshot: TripBackupSnapshot,
    val media: List<PortableMediaRecord>,
    val originalBytes: Int,
    val archiveSha256: String
)

internal object PortableMediaReader {
    private val mediaName = Regex("media/[0-9]{3}\\.(jpg|png|webp|gif|heic|heif|avif|pdf)")
    private val shaPattern = Regex("[0-9a-f]{64}")
    private val discard = object : OutputStream() {
        override fun write(value: Int) {}
        override fun write(bytes: ByteArray, offset: Int, length: Int) {}
    }

    fun readBounded(input: InputStream, limit: Int): ByteArray {
        val result = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(count > 0 && count <= limit - result.size()) {
                    "첨부 백업 허용 용량을 초과했습니다."
                }
                result.write(buffer, 0, count)
            }
            return result.toByteArray()
        } finally {
            buffer.fill(0)
        }
    }

    private fun text(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } finally {
        bytes.fill(0)
    }

    /**
     * Caller authenticates the entire envelope first. Entries are consumed in the only
     * supported order; paths are never taken directly from a user-supplied file name.
     */
    fun inspect(
        plaintext: ByteArray,
        archiveSha256: String,
        openMediaOutput: ((String) -> OutputStream)? = null
    ): PortableAttachmentPreview {
        require(plaintext.size in 1..PortableMediaArchive.MAX_PAYLOAD_BYTES)
        require(archiveSha256.matches(shaPattern))
        val copied = linkedMapOf<String, PortableMediaArchive.CopiedMedia>()
        var total = 0
        val (snapshot, manifest) = ZipInputStream(ByteArrayInputStream(plaintext)).use { zip ->
            val first = requireNotNull(zip.nextEntry)
            require(!first.isDirectory && first.name == "snapshot.json")
            val snapshot = TripBackupCodec.decode(text(readBounded(zip, TripBackupCodec.MAX_BYTES)))
            zip.closeEntry()

            var manifest: JSONObject? = null
            while (manifest == null) {
                val entry = requireNotNull(zip.nextEntry) { "첨부 백업 manifest가 없습니다." }
                require(!entry.isDirectory)
                if (entry.name == "manifest.json") {
                    manifest = JSONObject(text(readBounded(zip, PortableMediaArchive.MAX_MANIFEST_BYTES)))
                    zip.closeEntry()
                    require(zip.nextEntry == null) { "정의되지 않은 백업 항목입니다." }
                    break
                }
                require(copied.size < PortableMediaArchive.MAX_FILES)
                require(entry.name.matches(mediaName) &&
                    entry.name.substring(6, 9) == copied.size.toString().padStart(3, '0'))
                require(entry.name !in copied)
                val output = openMediaOutput?.invoke(entry.name) ?: discard
                val media = output.use {
                    PortableMediaArchive.copyMedia(zip, it, PortableMediaArchive.MAX_MEDIA_BYTES - total)
                }
                zip.closeEntry()
                copied[entry.name] = media
                total += media.bytes
            }
            snapshot to requireNotNull(manifest)
        }

        require(copied.isNotEmpty())
        require(manifest.getString("format") == "tripcanvas-portable-media" && manifest.getInt("version") == 1)
        require(manifest.getInt("originalBytes") == total)
        val rows = manifest.getJSONArray("media")
        require(rows.length() == copied.size)
        val photos = snapshot.moments.filter { it.photoUri != null }.associateBy { it.id }
        val bookings = snapshot.bookings.map { it.id }.toHashSet()
        val linkedPhotos = hashSetOf<String>()
        val linkedAttachments = hashSetOf<String>()
        val records = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val kind = row.getString("kind")
            require(kind == "photo" || kind == "booking")
            val id = row.getString("id")
            require(id.isNotBlank() && id.length <= 128)
            val mime = row.getString("mimeType")
            val path = row.getString("path")
            require(path == PortableMediaArchive.mediaPath(index, mime, kind == "photo"))
            val original = requireNotNull(copied[path])
            val bytes = row.getInt("bytes")
            val sha = row.getString("sha256")
            require(bytes == original.bytes && sha.matches(shaPattern) && sha == original.sha256)
            if (kind == "photo") {
                require(linkedPhotos.add(id))
                require(photos[id]?.photoUri == PortableMediaArchive.photoPlaceholder(index))
                PortableMediaRecord(kind, id, path, mime, bytes, sha)
            } else {
                val owner = row.getString("bookingId")
                val name = row.getString("displayName")
                val addedAt = row.getLong("addedAtMillis")
                require(linkedAttachments.add(id) && owner in bookings)
                require(name.length in 1..250 && addedAt >= 0)
                PortableMediaRecord(kind, id, path, mime, bytes, sha, owner, name, addedAt)
            }
        }
        require(linkedPhotos == photos.keys)
        require(records.map { it.path }.toSet() == copied.keys)
        return PortableAttachmentPreview(snapshot, records, total, archiveSha256)
    }
}
