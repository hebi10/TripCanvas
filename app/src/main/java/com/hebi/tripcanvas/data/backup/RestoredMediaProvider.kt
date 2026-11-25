package com.hebi.tripcanvas.data.backup

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.FileNotFoundException
import java.util.Locale

/** Only the app's restored-media directory is exposed; readers cannot change original copies. */
class RestoredMediaProvider : FileProvider() {
    override fun getType(uri: Uri): String {
        return when (uri.lastPathSegment?.substringAfterLast('.')?.lowercase(Locale.ROOT)) {
            "jpg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "avif" -> "image/avif"
            "pdf" -> "application/pdf"
            else -> super.getType(uri) ?: "application/octet-stream"
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r") { "복원 첨부 파일은 읽기 전용입니다." }
        return super.openFile(uri, mode)
            ?: throw FileNotFoundException("복원 첨부 파일을 열 수 없습니다.")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw SecurityException("복원 첨부 파일은 앱 내부에서만 관리합니다.")
    }
}
