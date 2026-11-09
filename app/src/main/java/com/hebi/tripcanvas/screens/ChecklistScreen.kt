package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.hebi.tripcanvas.data.ChecklistRepository
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.local.ChecklistItemEntity
import com.hebi.tripcanvas.designsystem.components.TripChoiceChip
import com.hebi.tripcanvas.designsystem.components.TripConfirmDialog
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.components.TripTextField
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.ui.TripPageFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private sealed interface ChecklistState {
    data object Loading : ChecklistState
    data object Failed : ChecklistState
    data class Ready(val items: List<ChecklistItemEntity>) : ChecklistState
}

@Composable
fun ChecklistScreen(
    tripId: String,
    repository: ChecklistRepository,
    tripRepository: TripRepository,
    onBack: () -> Unit
) {
    val p = TripDesign.palette
    val scope = rememberCoroutineScope()
    var incompleteOnly by rememberSaveable(tripId) { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ChecklistItemEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ChecklistItemEntity?>(null) }
    var lastDeleted by remember { mutableStateOf<ChecklistItemEntity?>(null) }
    var saving by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    val flow = remember(repository, tripId, refresh) {
        repository.observe(tripId)
            .map<List<ChecklistItemEntity>, ChecklistState> { ChecklistState.Ready(it) }
            .catch { emit(ChecklistState.Failed) }
    }
    val state by flow.collectAsState(initial = ChecklistState.Loading)
    val trip by remember(tripRepository, tripId) {
        tripRepository.observeTrip(tripId)
    }.collectAsState(initial = null)

    fun edit(item: ChecklistItemEntity?) {
        editing = item
        draft = item?.title.orEmpty()
        formError = null
        showEditor = true
    }

    fun applyOperation(block: suspend () -> Unit) {
        if (saving) return
        saving = true
        actionError = null
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                actionError = "변경 내용을 저장하지 못했습니다. 다시 시도해 주세요."
            } finally {
                saving = false
            }
        }
    }

    TripPageFrame(title = "준비물", onBack = onBack) {
        TripSectionHeading(
            title = trip?.title ?: "여행 준비물",
            supportingText = "체크 상태와 항목 순서는 기기에 저장됩니다."
        )
        when (val result = state) {
            ChecklistState.Loading -> TripLoadingState("준비물을 불러오는 중")
            ChecklistState.Failed -> TripErrorState(
                title = "준비물을 불러오지 못했습니다",
                description = "기기 저장소를 확인해 주세요.",
                onRetry = { refresh++ }
            )
            is ChecklistState.Ready -> {
                val total = result.items.size
                val completed = result.items.count { it.checked }
                TripSectionHeading(
                    title = "준비 완료 " + completed + " / " + total,
                    supportingText = if (total == 0) "여행에 필요한 준비물을 추가해 보세요."
                        else "체크를 해제해도 항목은 삭제되지 않습니다."
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                    TripChoiceChip(
                        label = "전체",
                        selected = !incompleteOnly,
                        onClick = { incompleteOnly = false }
                    )
                    TripChoiceChip(
                        label = "미완료",
                        selected = incompleteOnly,
                        onClick = { incompleteOnly = true }
                    )
                }
                val filtered = if (incompleteOnly) result.items.filterNot { it.checked } else result.items
                if (filtered.isEmpty()) {
                    TripEmptyState(
                        title = if (total == 0) "준비물이 없습니다" else "남은 준비물이 없습니다",
                        description = if (total == 0) "새 준비물을 추가해 주세요."
                            else "필터를 변경하거나 새 준비물을 추가할 수 있습니다."
                    )
                } else {
                    filtered.forEach { item ->
                        TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(TripSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                                ) {
                                    Checkbox(
                                        checked = item.checked,
                                        onCheckedChange = { checked ->
                                            applyOperation {
                                                repository.setChecked(tripId, item.id, checked)
                                            }
                                        },
                                        enabled = !saving
                                    )
                                    Text(
                                        item.title,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (item.checked) p.textSecondary else p.textPrimary
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
                                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                                ) {
                                    TripSecondaryButton("수정", onClick = { edit(item) }, enabled = !saving)
                                    TripSecondaryButton("위로", onClick = {
                                        applyOperation { repository.move(tripId, item.id, -1) }
                                    }, enabled = !saving && !incompleteOnly)
                                    TripSecondaryButton("아래로", onClick = {
                                        applyOperation { repository.move(tripId, item.id, 1) }
                                    }, enabled = !saving && !incompleteOnly)
                                    TripSecondaryButton("삭제", onClick = { deleteTarget = item }, enabled = !saving)
                                }
                            }
                        }
                    }
                }
            }
        }
        lastDeleted?.let { deleted ->
            TripSecondaryButton(
                label = "방금 삭제한 준비물 복구",
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    applyOperation {
                        repository.undoDelete(deleted)
                        lastDeleted = null
                    }
                }
            )
        }
        if (actionError != null) {
            TripErrorState(
                title = "준비물 저장 오류",
                description = requireNotNull(actionError),
                onRetry = { actionError = null }
            )
        }
        TripPrimaryButton(
            label = "준비물 추가",
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
            onClick = { edit(null) }
        )
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { if (!saving) showEditor = false },
            title = { Text(if (editing == null) "준비물 추가" else "준비물 수정") },
            text = {
                TripTextField(
                    value = draft,
                    onValueChange = { draft = it; formError = null },
                    label = "준비물 이름 *",
                    supportingText = formError ?: "1~100자",
                    isError = formError != null
                )
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    if (draft.trim().length !in 1..100) {
                        formError = "이름은 1~100자로 입력해 주세요."
                    } else {
                        applyOperation {
                            val current = editing
                            if (current == null) repository.create(tripId, draft)
                            else repository.rename(tripId, current.id, draft)
                            showEditor = false
                        }
                    }
                }) { Text(if (saving) "저장 중" else "저장", color = p.primaryAction) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { showEditor = false }) {
                    Text("취소", color = p.textSecondary)
                }
            },
            shape = RectangleShape,
            containerColor = p.surface
        )
    }

    deleteTarget?.let { item ->
        TripConfirmDialog(
            title = "준비물 삭제",
            message = item.title + " 항목을 삭제하시겠습니까? 삭제 직후 이 화면에서 복구할 수 있습니다.",
            confirmLabel = "삭제",
            destructive = true,
            onConfirm = {
                applyOperation {
                    lastDeleted = repository.delete(tripId, item.id)
                    deleteTarget = null
                }
            },
            onDismiss = { if (!saving) deleteTarget = null }
        )
    }
}
