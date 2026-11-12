package com.hebi.tripcanvas.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebi.tripcanvas.data.ChecklistRepository
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.local.ChecklistItemEntity
import com.hebi.tripcanvas.designsystem.components.TripChoiceChip
import com.hebi.tripcanvas.designsystem.components.TripConfirmDialog
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripProgressTrack
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.components.TripTextField
import com.hebi.tripcanvas.designsystem.icons.TripIcons
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSizes
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
                        else "남은 준비물 ${total - completed}개"
                )
                if (total > 0) {
                    val percent = completed * 100 / total
                    Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "준비 진행률",
                                style = MaterialTheme.typography.labelLarge,
                                color = p.textSecondary
                            )
                            Text(
                                "$" + "{percent}% 완료",
                                style = MaterialTheme.typography.labelLarge,
                                color = p.textPrimary
                            )
                        }
                        TripProgressTrack(
                            fraction = checklistCompletionFraction(completed, total),
                            accessibilityLabel = "준비물 완료율 $" + "{percent}%"
                        )
                    }
                }
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
                TripPrimaryButton(
                    label = "준비물 추가",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    onClick = { edit(null) }
                )
                val filtered = if (incompleteOnly) result.items.filterNot { it.checked } else result.items
                if (filtered.isEmpty()) {
                    TripEmptyState(
                        title = if (total == 0) "준비물이 없습니다" else "남은 준비물이 없습니다",
                        description = if (total == 0) "새 준비물을 추가해 주세요."
                            else "필터를 변경하거나 새 준비물을 추가할 수 있습니다."
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                    ) {
                        filtered.forEachIndexed { index, item ->
                            ChecklistItemRow(
                                item = item,
                                disabled = saving,
                                canMoveUp = !incompleteOnly && index > 0,
                                canMoveDown = !incompleteOnly && index < filtered.lastIndex,
                                onCheckedChange = { checked ->
                                    applyOperation { repository.setChecked(tripId, item.id, checked) }
                                },
                                onEdit = { edit(item) },
                                onMove = { offset ->
                                    applyOperation { repository.move(tripId, item.id, offset) }
                                },
                                onDelete = { deleteTarget = item }
                            )
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

@Composable
private fun ChecklistItemRow(
    item: ChecklistItemEntity,
    disabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val p = TripDesign.palette
    var showMenu by remember(item.id) { mutableStateOf(false) }

    TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TripSpacing.sm, vertical = TripSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)
        ) {
            Checkbox(
                checked = item.checked,
                onCheckedChange = onCheckedChange,
                enabled = !disabled,
                modifier = Modifier
                    .size(TripSizes.minimumTouchTarget)
                    .semantics { contentDescription = "${item.title} 준비 여부" },
                colors = CheckboxDefaults.colors(
                    checkedColor = p.primaryAction,
                    uncheckedColor = p.outline,
                    checkmarkColor = p.onPrimaryAction
                )
            )
            Text(
                text = item.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.checked) p.textSecondary else p.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    enabled = !disabled,
                    modifier = Modifier
                        .size(TripSizes.minimumTouchTarget)
                        .semantics { contentDescription = "${item.title} 준비물 메뉴" }
                ) {
                    Icon(
                        imageVector = TripIcons.MoreVert,
                        contentDescription = null,
                        tint = p.textPrimary
                    )
                }
                DropdownMenu(
                    expanded = showMenu && !disabled,
                    onDismissRequest = { showMenu = false },
                    containerColor = p.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, p.outline)
                ) {
                    DropdownMenuItem(
                        text = { Text("수정") },
                        onClick = { showMenu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("위로") },
                        enabled = canMoveUp,
                        onClick = { showMenu = false; onMove(-1) }
                    )
                    DropdownMenuItem(
                        text = { Text("아래로") },
                        enabled = canMoveDown,
                        onClick = { showMenu = false; onMove(1) }
                    )
                    DropdownMenuItem(
                        text = { Text("삭제", color = p.error) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}
