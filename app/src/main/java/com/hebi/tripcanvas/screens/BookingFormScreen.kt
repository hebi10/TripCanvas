package com.hebi.tripcanvas.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.hebi.tripcanvas.data.BookingInput
import com.hebi.tripcanvas.data.BookingRepository
import com.hebi.tripcanvas.data.BookingType
import com.hebi.tripcanvas.data.toInput
import com.hebi.tripcanvas.designsystem.components.TripChoiceChip
import com.hebi.tripcanvas.designsystem.components.TripConfirmDialog
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripTextField
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.ui.TripPageFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun BookingFormScreen(
    tripId: String,
    bookingId: String?,
    repository: BookingRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val palette = TripDesign.palette
    val scope = rememberCoroutineScope()
    val stored = if (bookingId == null) null else {
        val selected by remember(repository, tripId, bookingId) {
            repository.observeItem(tripId, bookingId)
        }.collectAsState(initial = null)
        selected
    }
    val documents = if (bookingId == null) emptyList() else {
        val attached by remember(repository, bookingId) {
            repository.attachments(bookingId)
        }.collectAsState(initial = emptyList())
        attached
    }
    var input by remember(bookingId) { mutableStateOf(BookingInput()) }
    var hydrated by remember(bookingId) { mutableStateOf(bookingId == null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    var removingId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(stored?.id) {
        if (!hydrated && stored != null) {
            input = stored.toInput()
            hydrated = true
        }
    }
    val dirty = hydrated && if (bookingId == null) {
        input != BookingInput()
    } else {
        stored != null && input != stored.toInput()
    }
    val requestBack = {
        if (!saving) {
            if (dirty) showDiscard = true else onBack()
        }
    }
    BackHandler(enabled = dirty || saving) { requestBack() }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && bookingId != null) {
            error = null
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val mime = context.contentResolver.getType(uri).orEmpty()
                val display = context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                } ?: "예약 문서"
                saving = true
                scope.launch {
                    try {
                        repository.attachDocument(tripId, bookingId, uri.toString(), display, mime)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failed: Exception) {
                        error = "파일 참조 저장에 실패했습니다. 다시 선택해 주세요."
                    } finally { saving = false }
                }
            } catch (failed: Exception) {
                error = "파일 접근 권한을 유지할 수 없습니다. 다른 파일을 선택해 주세요."
            }
        }
    }

    TripPageFrame(
        title = if (bookingId == null) "예약 추가" else "예약 편집",
        onBack = requestBack
    ) {
        if (!hydrated) {
            TripLoadingState("예약 정보를 불러오는 중")
        } else {
            TripSectionHeading(
                title = "예약 정보",
                supportingText = "예약·결제를 대행하지 않고 기기에만 저장합니다."
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                BookingType.entries.forEach { type ->
                    TripChoiceChip(
                        label = type.label,
                        selected = input.type == type,
                        onClick = { input = input.copy(type = type) }
                    )
                }
            }
            TripTextField(
                value = input.title,
                onValueChange = { input = input.copy(title = it); error = null },
                label = "예약명 *",
                supportingText = "1~100자"
            )
            TripTextField(
                value = input.startAtLocal,
                onValueChange = { input = input.copy(startAtLocal = it); error = null },
                label = "예약 날짜·현지 시각",
                placeholder = "2026-11-20T14:30",
                supportingText = "선택 사항 · yyyy-MM-ddTHH:mm"
            )
            TripTextField(
                value = input.referenceNumber,
                onValueChange = { input = input.copy(referenceNumber = it); error = null },
                label = "예약 확인번호",
                supportingText = "선택 사항 · 목록과 로그에는 표시하지 않습니다."
            )
            TripTextField(
                value = input.note,
                onValueChange = { input = input.copy(note = it); error = null },
                label = "메모",
                singleLine = false,
                supportingText = "최대 2,000자"
            )
            TripSectionHeading(
                title = "예약 첨부",
                supportingText = "PDF·이미지 연결 · 시스템 파일 선택기로 읽기 권한을 유지합니다."
            )
            Text(
                "문서 원본과 접근 URI는 JSON 백업에 포함되지 않습니다. 파일 이동·삭제 또는 " +
                    "접근 권한 만료 시 다시 연결해야 합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
            if (bookingId != null) {
                documents.forEach { document ->
                    Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                        Text(document.displayName, color = palette.textPrimary)
                        TripSecondaryButton(
                            label = "파일 열기",
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(document.uriString), document.mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (failed: Exception) {
                                    error = "파일에 접근할 수 없습니다. 연결을 제거하고 다시 선택해 주세요."
                                }
                            }
                        )
                        TripSecondaryButton(
                            label = "파일 연결 제거",
                            enabled = !saving,
                            onClick = { removingId = document.id }
                        )
                    }
                }
                TripSecondaryButton(
                    label = "PDF·이미지 첨부",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    onClick = { documentPicker.launch(arrayOf("application/pdf", "image/*")) }
                )
            } else {
                Text("예약 정보를 저장한 뒤 파일을 첨부할 수 있습니다.", color = palette.textSecondary)
            }
            if (error != null) {
                TripErrorState(
                    title = "예약 작업 오류", description = requireNotNull(error),
                    onRetry = { error = null }
                )
            }
            TripPrimaryButton(
                label = if (saving) "저장 중" else "예약 정보 저장",
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                onClick = {
                    if (!saving) {
                        saving = true; error = null
                        scope.launch {
                            try {
                                repository.save(tripId, input, bookingId)
                                onSaved()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failed: Exception) {
                                error = failed.message ?: "예약 정보를 저장할 수 없습니다."
                            } finally { saving = false }
                        }
                    }
                }
            )
        }
    }
    if (showDiscard) {
        TripConfirmDialog(
            title = "예약 변경 사항 폐기",
            message = "저장하지 않은 예약 정보 변경을 버리시겠습니까?",
            confirmLabel = "폐기", destructive = true,
            onDismiss = { showDiscard = false },
            onConfirm = { showDiscard = false; onBack() }
        )
    }
    if (bookingId != null && removingId != null) {
        val id = requireNotNull(removingId)
        TripConfirmDialog(
            title = "문서 연결 제거",
            message = "첨부 연결만 제거합니다. 원본 파일은 삭제되지 않습니다.",
            confirmLabel = "연결 제거", destructive = true,
            onDismiss = { if (!saving) removingId = null },
            onConfirm = {
                if (!saving) {
                    saving = true
                    scope.launch {
                        try {
                            repository.detachDocument(tripId, bookingId, id)
                            removingId = null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failed: Exception) {
                            error = "파일 연결을 제거하지 못했습니다."
                        } finally { saving = false }
                    }
                }
            }
        )
    }
}
