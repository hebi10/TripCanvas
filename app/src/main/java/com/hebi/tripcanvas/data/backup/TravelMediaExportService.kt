package com.hebi.tripcanvas.data.backup

import android.content.Context
import android.net.Uri
import com.hebi.tripcanvas.data.local.TravelMomentEntity
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Portable photo copies only. This intentionally does not mutate source URIs,
 * existing Room records or user-owned original media.
 */
object TravelMediaArchivePolicy {
    const val MAX_PHOTOS = 100
    const val MAX_PHOTO_BYTES = 25L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 200L * 1024 * 1024

    fun photoPath(index: Int, mimeType: String): String {
        require(index in 0 until MAX_PHOTOS)
        val extension = when (mimeType.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            else -> throw IllegalArgumentException("지원하지 않는 이미지 형식입니다.")
        }
        return "photos/" + index.toString().padStart(3, '0') + "." + extension
    }

    fun acceptedPhotoCount(moments: List<TravelMomentEntity>, tripId: String): Int {
        require(moments.all { it.tripId == tripId }) { "다른 여행 기록을 포함할 수 없습니다." }
        val count = moments.count { it.photoUri != null }
        require(count in 1..MAX_PHOTOS) { "사진은 1~100개까지 내보낼 수 있습니다." }
        return count
    }
}

class TravelMediaExportService(private val context: Context) {
    suspend fun export(
        tripId: String,
        destination: Uri,
        moments: List<TravelMomentEntity>
    ): Int = withContext(Dispatchers.IO) {
        val count = TravelMediaArchivePolicy.acceptedPhotoCount(moments, tripId)
        val resolver = context.contentResolver
        val sourceUris = moments.mapNotNull { it.photoUri }
        require(destination.scheme == "content") { "선택한 저장 파일에만 기록할 수 있습니다." }
        require(sourceUris.none { it == destination.toString() }) {
            "원본 사진을 내보내기 대상 파일로 지정할 수 없습니다."
        }
        val output = resolver.openOutputStream(destination, "w")
            ?: throw IOException("내보내기 파일을 열 수 없습니다.")
        var totalCopied = 0L
        var photoIndex = 0
        ZipOutputStream(output.buffered()).use { zip ->
            val manifest = JSONArray()
            moments.forEach { moment ->
                val meta = JSONObject()
                    .put("id", moment.id)
                    .put("visitId", moment.visitId ?: JSONObject.NULL)
                    .put("caption", moment.caption)
                    .put("createdAtUtcMillis", moment.createdAtUtcMillis)
                moment.photoUri?.let { source ->
                    val uri = Uri.parse(source)
                    require(uri.scheme == "content" && uri.authority != null) {
                        "사진 원본 접근 경로를 확인해 주세요."
                    }
                    val type = resolver.getType(uri).orEmpty()
                    val entryName = TravelMediaArchivePolicy.photoPath(photoIndex++, type)
                    val input = resolver.openInputStream(uri)
                        ?: throw IOException("일부 사진 원본을 열 수 없습니다.")
                    var photoCopied = 0L
                    zip.putNextEntry(ZipEntry(entryName))
                    input.use { stream ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = stream.read(buffer)
                            if (read == -1) break
                            photoCopied += read
                            totalCopied += read
                            if (photoCopied > TravelMediaArchivePolicy.MAX_PHOTO_BYTES ||
                                totalCopied > TravelMediaArchivePolicy.MAX_TOTAL_BYTES
                            ) throw IOException("사진 백업 용량 제한을 초과했습니다.")
                            zip.write(buffer, 0, read)
                        }
                    }
                    zip.closeEntry()
                    meta.put("mediaPath", entryName).put("mimeType", type)
                }
                manifest.put(meta)
            }
            val manifestBytes = JSONObject()
                .put("format", "tripcanvas-media-export")
                .put("version", 1)
                .put("tripId", tripId)
                .put("moments", manifest)
                .toString().toByteArray(StandardCharsets.UTF_8)
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestBytes)
            zip.closeEntry()
        }
        count
    }
}
