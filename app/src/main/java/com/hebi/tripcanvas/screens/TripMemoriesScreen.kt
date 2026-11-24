package com.hebi.tripcanvas.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import com.hebi.tripcanvas.data.backup.TravelMediaExportService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.hebi.tripcanvas.data.TravelJournalRepository
import com.hebi.tripcanvas.ui.TripPageFrame
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TripMemoriesScreen(
    tripId: String,
    journal: TravelJournalRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val visits by remember(tripId) { journal.visits(tripId) }.collectAsState(initial = emptyList())
    val moments by remember(tripId) { journal.moments(tripId) }.collectAsState(initial = emptyList())
    var caption by remember { mutableStateOf("") }
    var selectedVisit by remember { mutableStateOf<String?>(null) }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val mediaExport = remember(context) { TravelMediaExportService(context.applicationContext) }
    var exporting by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null && !exporting) {
            exporting = true
            exportStatus = null
            scope.launch {
                try {
                    val copied = mediaExport.export(tripId, uri, moments)
                    exportStatus = "사진 원본 " + copied + "개 ZIP 내보내기 완료"
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error = "사진 내보내기에 실패했습니다. 원본 접근 권한·파일 형식·저장 공간을 확인해 주세요."
                } finally {
                    exporting = false
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                photoUri = uri.toString()
                error = null
            } catch (_: SecurityException) {
                photoUri = null
                error = "사진 접근 권한을 보관하지 못했습니다. 다시 선택해 주세요."
            }
        }
    }
    TripPageFrame(title = "방문·사진 기록", onBack = onBack) {
        Text("방문 기록", style = MaterialTheme.typography.titleMedium)
        if (visits.isEmpty()) Text("방문 완료한 일정이 없습니다. 사진은 방문과 연결하지 않고도 기록할 수 있습니다.")
        visits.forEach { visit ->
            Text(visit.titleSnapshot + " · " + formatMomentTime(visit.visitAtUtcMillis))
            TextButton(onClick = { selectedVisit = visit.id }) {
                Text(if (selectedVisit == visit.id) "연결 선택됨" else "이 방문에 사진 연결")
            }
        }
        Text("새 기록", style = MaterialTheme.typography.titleMedium)
        Text(if (selectedVisit == null) "방문 연결 없음" else "선택한 방문에 연결")
        if (selectedVisit != null) {
            TextButton(onClick = { selectedVisit = null }) { Text("방문 연결 해제") }
        }
        OutlinedTextField(
            value = caption, onValueChange = { if (it.length <= 2000) caption = it },
            label = { Text("사진 설명·메모") }, modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = { picker.launch(arrayOf("image/*")) }) { Text("사진 선택") }
        photoUri?.let { Text("사진이 선택되었습니다. 파일은 원본 위치에 보관됩니다.") }
        TextButton(
            enabled = photoUri != null || caption.isNotBlank(),
            onClick = {
                scope.launch {
                    try {
                        journal.addMoment(tripId, selectedVisit, photoUri, caption)
                        caption = ""
                        photoUri = null
                        selectedVisit = null
                        error = null
                    } catch (_: Exception) {
                        error = "기록 저장에 실패했습니다."
                    }
                }
            }
        ) { Text("기록 저장") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("저장된 기록", style = MaterialTheme.typography.titleMedium)
        if (moments.isEmpty()) Text("저장된 기록이 없습니다.")
        moments.forEach { moment ->
            Column(verticalArrangement = Arrangement.spacedBy(com.hebi.tripcanvas.designsystem.tokens.TripSpacing.xs)) {
                Text(formatMomentTime(moment.createdAtUtcMillis))
                Text(moment.caption.ifBlank { "사진 기록" })
                Text(if (moment.visitId == null) "연결하지 않은 기록" else
                    visits.firstOrNull { it.id == moment.visitId }?.titleSnapshot ?: "연결 해제된 방문")
                if (moment.photoUri != null) {
                    TravelPhotoThumbnail(moment.photoUri)
                    TextButton(onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(android.net.Uri.parse(moment.photoUri), "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                            error = null
                        } catch (_: Exception) {
                            error = "사진 파일에 접근할 수 없습니다. 원본 파일 위치를 확인해 주세요."
                        }
                    }) { Text("사진 열기") }
                }
            }
        }
        Text("사진 원본 ZIP 내보내기", style = MaterialTheme.typography.titleMedium)
        Text("현재 여행에서 선택해 저장한 사진만 ZIP으로 복사합니다. 메모가 포함되고 암호화되지 않으므로 안전하게 보관해 주세요. 다른 기기에서 ZIP을 앱으로 복원하는 기능은 아직 지원하지 않습니다.")
        TextButton(
            enabled = moments.any { it.photoUri != null } && !exporting,
            onClick = { exportPicker.launch("TripCanvas_photos_" + tripId.take(8) + ".zip") }
        ) { Text(if (exporting) "사진 내보내는 중" else "사진 원본 ZIP 저장") }
        exportStatus?.let { Text(it) }
        Text("사진 원본은 복사하지 않습니다. 원본을 이동·삭제하거나 접근 권한이 만료되면 열리지 않을 수 있습니다.")
    }
}

internal fun formatMomentTime(timestamp: Long): String =
    DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))

@Composable
internal fun TravelPhotoThumbnail(uri: String) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                        inSampleSize = 8
                    })?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    if (image != null) {
        Image(
            bitmap = requireNotNull(image), contentDescription = "여행 기록 사진",
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Text("사진 미리보기를 열 수 없거나 불러오는 중입니다.")
    }
}
