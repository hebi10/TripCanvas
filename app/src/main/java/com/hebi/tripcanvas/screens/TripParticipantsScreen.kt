package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hebi.tripcanvas.data.TripFinanceRepository
import com.hebi.tripcanvas.data.local.TripParticipantEntity
import com.hebi.tripcanvas.designsystem.components.TripConfirmDialog
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.components.TripTextField
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.domain.collaboration.TripSharingBoundary
import com.hebi.tripcanvas.ui.TripPageFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun TripParticipantsScreen(
    tripId: String,
    repository: TripFinanceRepository,
    onBack: () -> Unit
) {
    val palette = TripDesign.palette
    val scope = rememberCoroutineScope()
    val participants by remember(tripId, repository) {
        repository.participants(tripId)
    }.collectAsState(initial = emptyList())
    var editor by remember { mutableStateOf<TripParticipantEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var editorError by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<TripParticipantEntity?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSharingReview by remember(tripId) { mutableStateOf(false) }

    TripPageFrame(title = "동행자 관리", onBack = onBack, contentSpacing = TripSpacing.md) {
        TripSectionHeading(
            title = "로컬 동행자 " + participants.size + "명",
            supportingText = "정산에 사용하는 기기 내 참가자입니다. 공동 여행 계정과 별개이며 동명이인도 각각 등록할 수 있습니다."
        )
        if (participants.isEmpty()) {
            TripEmptyState(
                title = "등록된 동행자가 없습니다",
                description = "지출 결제자와 분담 대상을 등록해 주세요."
            )
        }
        participants.forEach { person ->
            TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(TripSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                ) {
                    Text(person.displayName, style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                    ) {
                        TripSecondaryButton(
                            label = "이름 변경", enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = "이름 변경: ${person.displayName}" },
                            onClick = {
                                editor = person; draft = person.displayName
                                error = null; editorError = null; showEditor = true
                            }
                        )
                        TripSecondaryButton(
                            label = "참가자 삭제", enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = "참가자 삭제: ${person.displayName}" },
                            onClick = { target = person; error = null }
                        )
                    }
                }
            }
        }
        if (error != null) {
            TripErrorState(
                title = "동행자 작업 오류",
                description = requireNotNull(error),
                onRetry = { error = null }
            )
        }
        TripPrimaryButton(
            label = "동행자 추가",
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                editor = null; draft = ""; error = null; editorError = null; showEditor = true
            }
        )
        TripSectionHeading(
            title = "공동 여행",
            supportingText = "서버 미연결 · 현재 여행은 이 기기에만 저장됩니다."
        )
        TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(TripSpacing.md),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
            ) {
                Text(
                    "계정 없이 기존 여행과 정산을 계속 이용할 수 있습니다. 공동 여행의 로그인·초대·동기화는 운영 서버가 연결된 후 제공됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
                TripSecondaryButton(
                    label = "공유 범위 미리보기",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = { showSharingReview = true }
                )
            }
        }
    }
    if (showSharingReview) {
        TripSharingReviewDialog(onDismiss = { showSharingReview = false })
    }
    if (showEditor) {
        AlertDialog(
            onDismissRequest = { if (!busy) showEditor = false },
            title = { Text(if (editor == null) "동행자 추가" else "동행자 이름 변경") },
            text = {
                TripTextField(
                    value = draft,
                    onValueChange = { draft = it; editorError = null },
                    label = "동행자 이름 *",
                    supportingText = editorError ?: "1~80자 · 동명이인 구분은 내부 ID로 처리",
                    isError = editorError != null
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    if (draft.trim().length !in 1..80) {
                        editorError = "이름은 1~80자로 입력해 주세요."
                    } else {
                        busy = true
                        scope.launch {
                            try {
                                val current = editor
                                if (current == null) repository.addParticipant(tripId, draft)
                                else repository.renameParticipant(tripId, current.id, draft)
                                showEditor = false
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failed: Exception) {
                                editorError = "동행자 정보를 저장하지 못했습니다."
                            } finally {
                                busy = false
                            }
                        }
                    }
                }) { Text("저장", color = palette.primaryAction) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { showEditor = false }) {
                    Text("취소", color = palette.textSecondary)
                }
            },
            shape = RectangleShape,
            containerColor = palette.surface
        )
    }
    target?.let { person ->
        TripConfirmDialog(
            title = "동행자 삭제",
            message = person.displayName + "님을 삭제하시겠습니까? " +
                "기존 지출의 결제자 또는 분담 대상이면 삭제할 수 없습니다.",
            confirmLabel = "삭제",
            destructive = true,
            onDismiss = { if (!busy) target = null },
            onConfirm = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            repository.deleteParticipant(tripId, person.id)
                            target = null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failed: Exception) {
                            error = "참가자를 삭제할 수 없습니다. 기존 지출의 결제자 또는 분담 대상인지 확인해 주세요."
                            target = null
                        } finally { busy = false }
                    }
                }
            }
        )
    }
}


@Composable
private fun TripSharingReviewDialog(onDismiss: () -> Unit) {
    val palette = TripDesign.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("공동 여행 공유 범위 미리보기") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
            ) {
                Text("현재 서버는 연결되지 않았습니다. 아래는 공동 여행 연결 전 검토할 공유 항목입니다. 이 화면에서는 데이터가 전송되지 않습니다.")
                TripSharingBoundary.reviewSections.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                        Text(section.label, style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                        Text(section.description, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                    }
                }
                Text(TripSharingBoundary.excludedDescription, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                Text("예정된 권한", style = MaterialTheme.typography.titleSmall)
                Text("소유자: 초대·역할 관리 및 여행 편집", style = MaterialTheme.typography.bodyMedium)
                Text("편집자: 허용된 공동 데이터 편집", style = MaterialTheme.typography.bodyMedium)
                Text("열람자: 허용된 공동 데이터 조회", style = MaterialTheme.typography.bodyMedium)
                Text("실제 공유 전 별도의 범위 확인과 사용자 승인이 필요합니다. 로컬 동행자와 기존 정산 기록은 유지됩니다.",
                    style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기", color = palette.primaryAction) } },
        shape = RectangleShape,
        containerColor = palette.surface
    )
}
