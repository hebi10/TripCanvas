package com.hebi.tripcanvas.data.backup

import android.content.Context
import android.net.Uri
import com.hebi.tripcanvas.data.local.TripBackupSnapshot
import com.hebi.tripcanvas.data.local.TripDatabase
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TripBackupService(
    private val context: Context,
    private val database: TripDatabase
) {
    suspend fun export(uri: Uri): TripBackupSnapshot = withContext(Dispatchers.IO) {
        val snapshot = database.backupDao().snapshot()
        val bytes = TripBackupCodec.encode(snapshot).toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= TripBackupCodec.MAX_BYTES) {
            "백업 용량이 제한을 초과합니다."
        }
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("파일을 저장할 수 없습니다.")
        stream.use { it.write(bytes) }
        snapshot
    }

    suspend fun preview(uri: Uri): TripBackupSnapshot = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("파일을 열 수 없습니다.")
        val bytes = ByteArrayOutputStream()
        stream.use { input ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                total += count
                require(total <= TripBackupCodec.MAX_BYTES) {
                    "백업 파일 크기가 허용 범위를 초과합니다."
                }
                bytes.write(buffer, 0, count)
            }
        }
        try {
            val text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
            TripBackupCodec.decode(text)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw IllegalArgumentException("파일이 손상되었거나 지원하지 않는 백업 버전입니다.")
        }
    }

    suspend fun restore(snapshot: TripBackupSnapshot) = withContext(Dispatchers.IO) {
        TripBackupCodec.validate(snapshot)
        database.backupDao().replaceAll(snapshot)
    }
}
