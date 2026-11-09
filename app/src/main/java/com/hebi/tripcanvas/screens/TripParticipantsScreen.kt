package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    var target by remember { mutableStateOf<TripParticipantEntity?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    TripPageFrame(title = "동행자 관리", onBack = onBack) {
        TripSectionHeading(
            title = "로컬 동행자 " + participants.size + "명",
            supportingText = "계정이나 초대 없이 이 여행에서만 사용합니다. 동명이인도 각각 등록할 수 있습니다."
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
                    TripSecondaryButton(
                        label = "이름 변경", enabled = !busy,
                        onClick = {
                            editor = person; draft = person.displayName
                            error = null; showEditor = true
                        }
                    )
                    TripSecondaryButton(
                        label = "참가자 삭제", enabled = !busy,
                        onClick = { target = person; error = null }
                    )
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
                editor = null; draft = ""; error = null; showEditor = true
            }
        )
    }
    if (showEditor) {
        AlertDialog(
            onDismissRequest = { if (!busy) showEditor = false },
            title = { Text(if (editor == null) "동행자 추가" else "동행자 이름 변경") },
            text = {
                TripTextField(
                    value = draft,
                    onValueChange = { draft = it; error = null },
                    label = "동행자 이름 *",
                    supportingText = "1~80자 · 동명이인 구분은 내부 ID로 처리"
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    if (draft.trim().length !in 1..80) {
                        error = "이름은 1~80자로 입력해 주세요."
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
                                error = "동행자 정보를 저장하지 못했습니다."
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
                            error = failed.message ?: "정산에 연결된 참가자는 삭제할 수 없습니다."
                            target = null
                        } finally { busy = false }
                    }
                }
            }
        )
    }
}
