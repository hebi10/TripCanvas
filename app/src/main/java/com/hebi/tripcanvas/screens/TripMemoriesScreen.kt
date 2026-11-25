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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripStatusTag
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.components.TripTextField
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.ui.TripPageFrame
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
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
    val palette = TripDesign.palette
    val scope = rememberCoroutineScope()
    val visits by remember(tripId) { journal.visits(tripId) }.collectAsState(initial = emptyList())
    val moments by remember(tripId) { journal.moments(tripId) }.collectAsState(initial = emptyList())
    var caption by remember(tripId) { mutableStateOf("") }
    var saving by remember(tripId) { mutableStateOf(false) }
    var selectedVisit by remember(tripId) { mutableStateOf<String?>(null) }
    var photoUri by remember(tripId) { mutableStateOf<String?>(null) }
    var error by remember(tripId) { mutableStateOf<String?>(null) }
    val mediaExport = remember(context) { TravelMediaExportService(context.applicationContext) }
    var exporting by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null && !exporting) {
            exporting = true
            exportStatus = null
            error = null
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
    TripPageFrame(title = "방문·사진 기록", onBack = onBack, contentSpacing = TripSpacing.md) {
        TripSectionHeading(title = "방문 기록", supportingText = "${visits.size}건의 실제 방문")
        if (visits.isEmpty()) {
            TripEmptyState("방문 완료한 일정이 없습니다.", "사진은 방문과 연결하지 않고도 기록할 수 있습니다.")
        }
        visits.forEach { visit ->
            TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(TripSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                ) {
                    Text(visit.titleSnapshot + " · " + formatMomentTime(visit.visitAtUtcMillis),
                        style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    if (visit.note.isNotBlank()) {
                        Text(visit.note, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                    }
                    TripSecondaryButton(
                        label = if (selectedVisit == visit.id) "연결 선택됨" else "이 방문에 사진 연결",
                        enabled = !saving,
                        onClick = { selectedVisit = visit.id; error = null }
                    )
                }
            }
        }
        TripSectionHeading(title = "새 기록", supportingText = "사진 또는 메모를 저장할 수 있습니다.")
        TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(TripSpacing.md),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
            ) {
                val linked = visits.firstOrNull { it.id == selectedVisit }
                Text(if (selectedVisit == null) "방문 연결 없음" else "선택한 방문에 연결",
                    color = palette.textSecondary)
                if (selectedVisit != null) {
                    Text(linked?.titleSnapshot ?: "연결 해제된 방문", style = MaterialTheme.typography.titleMedium)
                    TripSecondaryButton(label = "방문 연결 해제", enabled = !saving,
                        onClick = { selectedVisit = null })
                }
                TripSecondaryButton(
                    label = "사진 선택",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    onClick = {
                        try {
                            picker.launch(arrayOf("image/*"))
                        } catch (_: android.content.ActivityNotFoundException) {
                            error = "기기의 파일 선택기를 열 수 없습니다."
                        }
                    }
                )
                photoUri?.let {
                    Text("사진이 선택되었습니다. 파일은 원본 위치에 보관됩니다.", color = palette.textSecondary)
                    TravelPhotoThumbnail(it)
                }
                TripTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 2000) caption = it },
                    label = "사진 설명·메모",
                    supportingText = "최대 2,000자",
                    singleLine = false
                )
                TripPrimaryButton(
                    label = if (saving) "저장 중" else "기록 저장",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving && (photoUri != null || caption.isNotBlank()),
                    onClick = {
                        if (!saving) {
                            saving = true
                            error = null
                            val note = caption
                            val visitId = selectedVisit
                            val photo = photoUri
                            scope.launch {
                                try {
                                    journal.addMoment(tripId, visitId, photo, note)
                                    caption = ""
                                    photoUri = null
                                    selectedVisit = null
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    error = "기록 저장에 실패했습니다."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    }
                )
            }
        }
        error?.let { TripErrorState(title = "여행 기록 오류", description = it) }
        TripSectionHeading(title = "저장된 기록", supportingText = "${moments.size}건의 사진·메모")
        if (moments.isEmpty()) {
            TripEmptyState("저장된 기록이 없습니다.", "새 사진이나 메모를 추가해 주세요.")
        }
        moments.forEach { moment ->
            TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(TripSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                ) {
                    Text(formatMomentTime(moment.createdAtUtcMillis), color = palette.textSecondary)
                    Text(moment.caption.ifBlank { "사진 기록" }, style = MaterialTheme.typography.titleMedium)
                    Text(if (moment.visitId == null) "연결하지 않은 기록" else
                        visits.firstOrNull { it.id == moment.visitId }?.titleSnapshot ?: "연결 해제된 방문",
                        color = palette.textSecondary)
                    if (moment.photoUri != null) {
                        TravelPhotoThumbnail(moment.photoUri)
                        TripSecondaryButton(label = "사진 열기", onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(moment.photoUri), "image/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                                error = null
                            } catch (_: Exception) {
                                error = "사진 파일에 접근할 수 없습니다. 원본 파일 위치를 확인해 주세요."
                            }
                        })
                    }
                }
            }
        }
        TripSectionHeading(title = "사진 원본 ZIP 내보내기")
        TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(TripSpacing.md),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
            ) {
                Text("현재 여행에서 선택해 저장한 사진만 ZIP으로 복사합니다. 메모가 포함되고 암호화되지 않으므로 안전하게 보관해 주세요. 다른 기기에서 ZIP을 앱으로 복원하는 기능은 아직 지원하지 않습니다.",
                    style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                TripSecondaryButton(
                    label = if (exporting) "사진 내보내는 중" else "사진 원본 ZIP 저장",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = moments.any { it.photoUri != null } && !exporting && !saving,
                    onClick = {
                        try {
                            exportPicker.launch("TripCanvas_photos_" + tripId.take(8) + ".zip")
                        } catch (_: android.content.ActivityNotFoundException) {
                            error = "기기의 파일 선택기를 열 수 없습니다."
                        }
                    }
                )
                exportStatus?.let { TripStatusTag(it) }
            }
        }
        Text("사진 원본은 복사하지 않습니다. 원본을 이동·삭제하거나 접근 권한이 만료되면 열리지 않을 수 있습니다.",
            style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
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
