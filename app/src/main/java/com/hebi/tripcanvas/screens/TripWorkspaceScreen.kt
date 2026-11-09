package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hebi.tripcanvas.data.ChecklistRepository
import com.hebi.tripcanvas.data.ExpenseRepository
import com.hebi.tripcanvas.data.ItineraryRepository
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripStatusTag
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.responsive.TripAdaptiveContainer
import com.hebi.tripcanvas.designsystem.responsive.TripWindowWidth
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.domain.calculateExpenseTotals
import com.hebi.tripcanvas.domain.displayMoney
import com.hebi.tripcanvas.domain.phase
import com.hebi.tripcanvas.ui.TripPageFrame
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private sealed interface WorkspaceState {
    data object Loading : WorkspaceState
    data object Failed : WorkspaceState
    data class Ready(val trip: TripEntity?) : WorkspaceState
}

@Composable
fun TripWorkspaceScreen(
    onBack: () -> Unit,
    isPreview: Boolean,
    repository: TripRepository? = null,
    tripId: String? = null,
    onEdit: () -> Unit = {},
    itineraryRepository: ItineraryRepository? = null,
    onSchedule: () -> Unit = {},
    onPlaces: () -> Unit = {},
    checklistRepository: ChecklistRepository? = null,
    expenseRepository: ExpenseRepository? = null,
    onChecklist: () -> Unit = {},
    onExpenses: () -> Unit = {}
) {
    var refresh by remember { mutableIntStateOf(0) }
    val flow = remember(repository, tripId, refresh) {
        repository?.takeIf { tripId != null }?.observeTrip(requireNotNull(tripId))
            ?.map<TripEntity?, WorkspaceState> { WorkspaceState.Ready(it) }
            ?.catch { emit(WorkspaceState.Failed) }
    }
    val state: WorkspaceState = if (isPreview) {
        WorkspaceState.Ready(null)
    } else if (flow != null) {
        val collected by flow.collectAsState(initial = WorkspaceState.Loading)
        collected
    } else {
        WorkspaceState.Failed
    }
    val loaded = (state as? WorkspaceState.Ready)?.trip
    var showDelete by remember { mutableStateOf(false) }
    val countFlow = remember(itineraryRepository, tripId) {
        if (itineraryRepository != null && tripId != null) {
            itineraryRepository.observeTripCount(tripId)
        } else {
            kotlinx.coroutines.flow.flowOf(0)
        }
    }
    val scheduleCount by countFlow.collectAsState(initial = 0)
    val checklistFlow = remember(checklistRepository, tripId) {
        if (checklistRepository != null && tripId != null) {
            checklistRepository.observe(tripId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }
    val checklist by checklistFlow.collectAsState(initial = emptyList())
    val expenseFlow = remember(expenseRepository, tripId) {
        if (expenseRepository != null && tripId != null) {
            expenseRepository.observe(tripId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }
    val expenses by expenseFlow.collectAsState(initial = emptyList())

    TripPageFrame(
        title = loaded?.title ?: "여행 작업 공간",
        onBack = onBack,
        trailingActions = {
            if (loaded != null) {
                TextButton(onClick = onEdit) { Text("편집") }
                TextButton(onClick = { showDelete = true }) { Text("삭제") }
            }
        }
    ) {
        when {
            isPreview -> WorkspaceContent(trip = null, isPreview = true, scheduleCount = 0, onSchedule = onSchedule, onPlaces = onPlaces,
                checklist = emptyList(), expenses = emptyList(),
                onChecklist = onChecklist, onExpenses = onExpenses)
            state == WorkspaceState.Loading -> TripLoadingState("여행 정보를 불러오는 중")
            state == WorkspaceState.Failed -> TripErrorState(
                title = "여행 정보를 불러오지 못했습니다",
                description = "저장소를 확인한 뒤 다시 시도해 주세요.",
                onRetry = { refresh++ }
            )
            loaded == null -> TripEmptyState(
                title = "여행을 찾을 수 없습니다",
                description = "삭제되었거나 존재하지 않는 여행입니다."
            )
            else -> WorkspaceContent(trip = loaded, isPreview = false, scheduleCount = scheduleCount, onSchedule = onSchedule, onPlaces = onPlaces,
                checklist = checklist, expenses = expenses,
                onChecklist = onChecklist, onExpenses = onExpenses)
        }
    }
    if (loaded != null && showDelete && repository != null) {
        TripDeleteDialog(
            trip = loaded,
            onDelete = repository::delete,
            onDeleted = { showDelete = false; onBack() },
            onDismiss = { showDelete = false }
        )
    }
}

@Composable
private fun WorkspaceContent(
    trip: TripEntity?,
    isPreview: Boolean,
    scheduleCount: Int,
    onSchedule: () -> Unit,
    onPlaces: () -> Unit,
    checklist: List<com.hebi.tripcanvas.data.local.ChecklistItemEntity>,
    expenses: List<com.hebi.tripcanvas.data.local.ExpenseEntity>,
    onChecklist: () -> Unit,
    onExpenses: () -> Unit
) {
    val p = TripDesign.palette
    TripStatusTag(label = if (isPreview) "화면 미리보기" else requireNotNull(trip).phase().label)
    TripSectionHeading(
        title = trip?.title ?: "여행의 모든 정보를 한곳에",
        supportingText = if (isPreview) {
            "실제 여행 데이터 없이 정보 구조만 확인하는 화면입니다."
        } else {
            listOfNotNull(trip?.country?.takeIf { it.isNotBlank() }, trip?.city?.takeIf { it.isNotBlank() })
                .joinToString(" · ").ifBlank { "목적지 미설정" }
        }
    )
    if (trip != null) {
        Text(trip.startDate + " ~ " + trip.endDate, style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
        Text(
            trip.timeZoneId + " · " + trip.currencyCode,
            style = MaterialTheme.typography.bodyMedium,
            color = p.textSecondary
        )
        if (trip.purpose.isNotBlank()) {
            Text(trip.purpose, style = MaterialTheme.typography.bodyLarge, color = p.textPrimary)
        }
        if (trip.note.isNotBlank()) {
            Text(trip.note, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
        }
    }
    TripAdaptiveContainer(modifier = Modifier.fillMaxWidth()) { width ->
        if (width == TripWindowWidth.Compact) {
            Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.md)) {
                WorkspacePanel("일정", if (isPreview) "날짜별 일정 미리보기" else "등록된 일정 " + scheduleCount + "개", if (isPreview) null else onSchedule, "일정 열기")
                WorkspacePanel(
                    "경비",
                    if (trip == null) "경비 미리보기" else
                        "기본 통화 지출 " + displayMoney(
                            calculateExpenseTotals(expenses, trip.currencyCode).baseSpent.toPlainString(),
                            trip.currencyCode
                        ) + " · " + expenses.size + "건",
                    if (isPreview) null else onExpenses,
                    "경비 열기"
                )
                WorkspacePanel(
                    "준비물",
                    if (trip == null) "준비물 미리보기" else
                        "준비 완료 " + checklist.count { it.checked } + " / " + checklist.size,
                    if (isPreview) null else onChecklist,
                    "준비물 열기"
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TripSpacing.md)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TripSpacing.md)) {
                    WorkspacePanel("일정", if (isPreview) "날짜별 일정 미리보기" else "등록된 일정 " + scheduleCount + "개", if (isPreview) null else onSchedule, "일정 열기")
                    WorkspacePanel(
                    "준비물",
                    if (trip == null) "준비물 미리보기" else
                        "준비 완료 " + checklist.count { it.checked } + " / " + checklist.size,
                    if (isPreview) null else onChecklist,
                    "준비물 열기"
                )
                }
                Column(modifier = Modifier.weight(1f)) {
                    WorkspacePanel(
                    "경비",
                    if (trip == null) "경비 미리보기" else
                        "기본 통화 지출 " + displayMoney(
                            calculateExpenseTotals(expenses, trip.currencyCode).baseSpent.toPlainString(),
                            trip.currencyCode
                        ) + " · " + expenses.size + "건",
                    if (isPreview) null else onExpenses,
                    "경비 열기"
                )
                }
            }
        }
    }
    if (!isPreview) {
        TripSecondaryButton(
            label = "저장 장소 관리",
            onClick = onPlaces,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Text(
        "일정·경비·준비물은 이 여행의 로컬 데이터로 관리됩니다.",
        style = MaterialTheme.typography.bodyMedium,
        color = p.textSecondary
    )
}

@Composable
private fun WorkspacePanel(
    title: String,
    description: String,
    onClick: (() -> Unit)? = null,
    actionLabel: String = ""
) {
    val p = TripDesign.palette
    TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TripSpacing.md),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
            if (onClick != null) {
                TripSecondaryButton(
                    label = actionLabel,
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
