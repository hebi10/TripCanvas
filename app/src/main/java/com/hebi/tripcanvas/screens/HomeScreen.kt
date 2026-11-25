package com.hebi.tripcanvas.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebi.tripcanvas.monetization.TripHomeBanner
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.ItineraryRepository
import com.hebi.tripcanvas.data.ChecklistRepository
import com.hebi.tripcanvas.data.ExpenseRepository
import com.hebi.tripcanvas.data.local.TripBudgetEntity
import com.hebi.tripcanvas.designsystem.components.TripProgressIndicator
import com.hebi.tripcanvas.designsystem.components.TripDivider
import com.hebi.tripcanvas.designsystem.components.TripTextField
import com.hebi.tripcanvas.domain.calculateExpenseTotals
import com.hebi.tripcanvas.domain.displayMoney
import com.hebi.tripcanvas.domain.budgetUsageProgress
import com.hebi.tripcanvas.domain.checklistCompletionFraction
import java.math.BigDecimal
import kotlinx.coroutines.flow.combine
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.designsystem.components.TripChoiceChip
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripStatusTag
import com.hebi.tripcanvas.designsystem.icons.TripIcons
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSizes
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.domain.TripPhase
import com.hebi.tripcanvas.domain.daysUntil
import com.hebi.tripcanvas.domain.phase
import com.hebi.tripcanvas.ui.TripPageFrame
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private enum class TripListFilter(val label: String, val emptyLabel: String) {
    All("전체", "등록된 여행이 없습니다"),
    Upcoming("예정", "예정된 여행이 없습니다"),
    Active("여행 중", "진행 중인 여행이 없습니다"),
    Completed("완료", "완료된 여행이 없습니다");

    fun includes(phase: TripPhase): Boolean =
        this == All || name == phase.name
}

private sealed interface TripListState {
    data object Loading : TripListState
    data object Failed : TripListState
    data class Ready(val trips: List<TripEntity>) : TripListState
}

@Composable
fun HomeScreen(
    repository: TripRepository,
    onOpenSettings: () -> Unit,
    onCreateTrip: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onEditTrip: (String) -> Unit,
    canRequestAds: Boolean = false,
    billingResolved: Boolean = false,
    adFree: Boolean = true,
    itineraryRepository: ItineraryRepository? = null,
    checklistRepository: ChecklistRepository? = null,
    expenseRepository: ExpenseRepository? = null
) {
    var selectedFilter by rememberSaveable { mutableStateOf(TripListFilter.All) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<TripEntity?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val flow = remember(repository, refresh) {
        repository.trips.map<List<TripEntity>, TripListState> { TripListState.Ready(it) }
            .catch { emit(TripListState.Failed) }
    }
    val state by flow.collectAsState(initial = TripListState.Loading)
    val p = TripDesign.palette

    TripPageFrame(
        title = "TripCanvas",
        trailingActions = {
            IconButton(
                onClick = { searchVisible = !searchVisible; if (!searchVisible) search = "" },
                modifier = Modifier.size(TripSizes.minimumTouchTarget)
            ) {
                Icon(if (searchVisible) TripIcons.Close else TripIcons.Search,
                    contentDescription = if (searchVisible) "검색 닫기" else "여행 검색",
                    tint = p.textPrimary)
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(TripSizes.minimumTouchTarget)
            ) {
                Icon(TripIcons.Settings, contentDescription = "설정", tint = p.textPrimary)
            }
        }
    ) {
        TripSectionHeading(title = "내 여행")
        if (searchVisible) {
            TripTextField(
                value = search,
                onValueChange = { search = it },
                label = "여행 검색",
                placeholder = "여행명 또는 목적지"
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
        ) {
            TripListFilter.entries.forEach { filter ->
                TripChoiceChip(
                    label = filter.label,
                    selected = filter == selectedFilter,
                    onClick = { selectedFilter = filter }
                )
            }
        }
        TripPrimaryButton(
            label = "새 여행 만들기",
            onClick = onCreateTrip,
            modifier = Modifier.fillMaxWidth()
        )
        when (val result = state) {
            TripListState.Loading -> TripLoadingState("여행 목록을 불러오는 중")
            TripListState.Failed -> TripErrorState(
                title = "여행 목록을 불러오지 못했습니다",
                description = "기기 저장소를 확인한 뒤 다시 시도해 주세요.",
                onRetry = { refresh++ }
            )
            is TripListState.Ready -> {
                val term = search.trim()
                val filtered = result.trips.filter { trip ->
                    selectedFilter.includes(trip.phase()) &&
                        (term.isEmpty() || listOf(trip.title, trip.country, trip.city).any { it.contains(term, ignoreCase = true) })
                }
                if (filtered.isEmpty()) {
                    TripEmptyState(
                        title = if (term.isNotEmpty()) "검색 결과가 없습니다" else selectedFilter.emptyLabel,
                        description = if (term.isNotEmpty()) "검색어를 변경해 주세요." else if (selectedFilter == TripListFilter.All) {
                            "첫 여행을 만들어보세요. 여행 정보는 기기에 저장됩니다."
                        } else {
                            "다른 여행 상태를 선택하거나 새 여행을 추가해 보세요."
                        },
                        actionLabel = null
                    )
                } else {
                    Text(
                        "여행 " + filtered.size + "개",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.textSecondary
                    )
                    filtered.forEach { trip ->
                        key(trip.id) {
                            TripListCard(
                                trip = trip,
                                onOpen = { onOpenTrip(trip.id) },
                                onEdit = { onEditTrip(trip.id) },
                                onDelete = { deleteTarget = trip },
                                itineraryRepository = itineraryRepository,
                                checklistRepository = checklistRepository,
                                expenseRepository = expenseRepository
                            )
                        }
                    }
                }
            }
        }
        TripHomeBanner(canRequestAds = canRequestAds, billingResolved = billingResolved, adFree = adFree)
    }
    deleteTarget?.let { trip ->
        TripDeleteDialog(
            trip = trip,
            onDelete = repository::delete,
            onDeleted = { deleteTarget = null },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun TripListCard(
    trip: TripEntity,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    itineraryRepository: ItineraryRepository?,
    checklistRepository: ChecklistRepository?,
    expenseRepository: ExpenseRepository?
) {
    val p = TripDesign.palette
    val phase = trip.phase()
    val destination = listOf(trip.country, trip.city)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    val timing = when (phase) {
        TripPhase.Upcoming -> "출발 D-" + trip.daysUntil()
        TripPhase.Active -> "여행 진행 중"
        TripPhase.Completed -> "지난 여행"
    }
    var showMenu by remember(trip.id) { mutableStateOf(false) }
    val summaryFlow = remember(trip.id, trip.currencyCode, itineraryRepository, checklistRepository, expenseRepository) {
        if (itineraryRepository == null || checklistRepository == null || expenseRepository == null) {
            kotlinx.coroutines.flow.flowOf<HomeTripSummary?>(null)
        } else {
            combine(
                itineraryRepository.observeTripCount(trip.id),
                checklistRepository.observe(trip.id),
                expenseRepository.observe(trip.id),
                expenseRepository.observeBudget(trip.id)
            ) { schedules, checklist, expenses, budget ->
                val totals = calculateExpenseTotals(expenses, trip.currencyCode)
                HomeTripSummary(schedules, checklist.count { it.checked }, checklist.size,
                    totals.baseSpent, budget, totals.unconvertedByCurrency.isNotEmpty())
            }.map<HomeTripSummary, HomeTripSummary?> { it }.catch { emit(null) }
        }
    }
    val summary by summaryFlow.collectAsState(initial = null)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        color = p.surface,
        shape = RectangleShape,
        border = BorderStroke(1.dp, p.outline)
    ) {
        Column(
            modifier = Modifier.padding(TripSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.xxs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xxs)
                ) {
                    Text(
                        text = trip.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = p.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (destination.isNotEmpty()) {
                        Text(
                            text = destination,
                            style = MaterialTheme.typography.bodyMedium,
                            color = p.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = trip.startDate.replace('-', '.') + " ~ " + trip.endDate.replace('-', '.'),
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.textPrimary
                    )
                }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(TripSizes.minimumTouchTarget)
                            .semantics { contentDescription = "${trip.title} 메뉴" }
                    ) {
                        Icon(
                            imageVector = TripIcons.MoreVert,
                            contentDescription = null,
                            tint = p.textPrimary
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = p.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = BorderStroke(1.dp, p.outline)
                    ) {
                        DropdownMenuItem(
                            text = { Text("여행 편집") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("여행 삭제", color = p.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TripStatusTag(label = phase.label)
                Text(
                    text = timing,
                    style = MaterialTheme.typography.labelLarge,
                    color = p.textPrimary
                )
            }
            summary?.let { current ->
                TripDivider(modifier = Modifier.padding(vertical = TripSpacing.xs))
                HomeTripMetrics(current, trip.currencyCode)
            }
        }
    }
}

private data class HomeTripSummary(
    val schedules: Int, val completed: Int, val total: Int,
    val spent: BigDecimal, val budget: TripBudgetEntity?, val unconverted: Boolean
)

@Composable
private fun HomeTripMetrics(summary: HomeTripSummary, currency: String) {
    val p = TripDesign.palette
    val budget = summary.budget?.takeIf { it.currencyCode == currency }
    val usage = budget?.let { budgetUsageProgress(summary.spent, BigDecimal(it.amountDecimal)) }
    Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TripSpacing.sm)) {
            Column(Modifier.weight(1f)) {
                Text("일정", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                Text(summary.schedules.toString() + "개", style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1.65f)) {
                Text("경비", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                Text(displayMoney(summary.spent.toPlainString(), currency), style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f)) {
                Text("준비물", style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                Text(summary.completed.toString() + " / " + summary.total, style = MaterialTheme.typography.titleMedium)
            }
        }
        usage?.let {
            TripProgressIndicator(it.fraction, "예산 사용률 " + it.percentage, warning = it.overBudget)
            Text("예산 " + displayMoney(requireNotNull(budget).amountDecimal, currency) + " · " + it.percentage,
                style = MaterialTheme.typography.bodySmall, color = if (it.overBudget) p.warning else p.textSecondary)
        }
        if (summary.unconverted) Text("미환산 외화 제외", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
        if (summary.total > 0) TripProgressIndicator(
            checklistCompletionFraction(summary.completed, summary.total),
            "준비 완료 " + summary.completed + " / " + summary.total
        )
    }
}
