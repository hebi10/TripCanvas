package com.hebi.tripcanvas.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import com.hebi.tripcanvas.data.ChecklistRepository
import com.hebi.tripcanvas.data.ExpenseRepository
import com.hebi.tripcanvas.data.ItineraryRepository
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.local.ChecklistItemEntity
import com.hebi.tripcanvas.data.local.ExpenseEntity
import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.TripBudgetEntity
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.components.TripStatusTag
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.components.TripProgressIndicator
import com.hebi.tripcanvas.designsystem.components.TripDivider
import com.hebi.tripcanvas.domain.budgetUsageProgress
import com.hebi.tripcanvas.domain.checklistCompletionFraction
import androidx.compose.ui.platform.LocalDensity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import com.hebi.tripcanvas.designsystem.responsive.TripAdaptiveContainer
import com.hebi.tripcanvas.designsystem.responsive.TripWindowWidth
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSizes
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.domain.calculateExpenseTotals
import com.hebi.tripcanvas.domain.displayMoney
import com.hebi.tripcanvas.domain.findNextItineraryItem
import com.hebi.tripcanvas.domain.phase
import java.math.BigDecimal
import java.math.RoundingMode
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
    onMap: () -> Unit = {},
    checklistRepository: ChecklistRepository? = null,
    expenseRepository: ExpenseRepository? = null,
    onChecklist: () -> Unit = {},
    onExpenses: () -> Unit = {},
    onSettlement: () -> Unit = {},
    onParticipants: () -> Unit = {},
    onBookings: () -> Unit = {},
    onLive: () -> Unit = {},
    onMemories: () -> Unit = {},
    onReport: () -> Unit = {}
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
    val itineraryFlow = remember(itineraryRepository, tripId) {
        if (itineraryRepository != null && tripId != null) {
            itineraryRepository.observeTrip(tripId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList<ItineraryItemEntity>())
        }
    }
    val schedules by itineraryFlow.collectAsState(initial = emptyList())
    val scheduleCount = schedules.size
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
    val budgetFlow = remember(expenseRepository, tripId) {
        if (expenseRepository != null && tripId != null) {
            expenseRepository.observeBudget(tripId)
        } else {
            kotlinx.coroutines.flow.flowOf<TripBudgetEntity?>(null)
        }
    }
    val budget by budgetFlow.collectAsState(initial = null)

    TripPageFrame(
        title = "여행 작업 공간",
        contentSpacing = TripSpacing.sm,
        onBack = onBack,
        trailingActions = {
            if (loaded != null) {
                TextButton(onClick = onEdit) { Text("편집") }
                TextButton(onClick = { showDelete = true }) { Text("삭제") }
            }
        }
    ) {
        when {
            isPreview -> WorkspaceContent(trip = null, isPreview = true, scheduleCount = 0, onSchedule = onSchedule, onPlaces = onPlaces, onMap = onMap,
                schedules = emptyList(), budget = null, checklist = emptyList(), expenses = emptyList(),
                onChecklist = onChecklist, onExpenses = onExpenses,
                onSettlement = onSettlement, onParticipants = onParticipants, onBookings = onBookings,
                onLive = onLive, onMemories = onMemories, onReport = onReport)
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
            else -> WorkspaceContent(trip = loaded, isPreview = false, scheduleCount = scheduleCount, onSchedule = onSchedule, onPlaces = onPlaces, onMap = onMap,
                schedules = schedules, budget = budget, checklist = checklist, expenses = expenses,
                onChecklist = onChecklist, onExpenses = onExpenses,
                onSettlement = onSettlement, onParticipants = onParticipants, onBookings = onBookings,
                onLive = onLive, onMemories = onMemories, onReport = onReport)
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
    schedules: List<ItineraryItemEntity>,
    budget: TripBudgetEntity?,
    onSchedule: () -> Unit,
    onPlaces: () -> Unit,
    onMap: () -> Unit,
    checklist: List<ChecklistItemEntity>,
    expenses: List<ExpenseEntity>,
    onChecklist: () -> Unit,
    onExpenses: () -> Unit,
    onSettlement: () -> Unit,
    onParticipants: () -> Unit,
    onBookings: () -> Unit,
    onLive: () -> Unit,
    onMemories: () -> Unit,
    onReport: () -> Unit
) {
    val p = TripDesign.palette
    WorkspaceHeader(trip = trip, isPreview = isPreview)
    WorkspaceOverview(
        trip = trip,
        isPreview = isPreview,
        schedules = schedules,
        budget = budget,
        checklist = checklist,
        expenses = expenses
    )
    WorkspaceToolGrid(
        trip = trip,
        isPreview = isPreview,
        scheduleCount = scheduleCount,
        checklist = checklist,
        expenses = expenses,
        onSchedule = onSchedule,
        onPlaces = onPlaces,
        onMap = onMap,
        onChecklist = onChecklist,
        onExpenses = onExpenses,
        onSettlement = onSettlement,
        onParticipants = onParticipants,
        onBookings = onBookings,
        onLive = onLive,
        onMemories = onMemories,
        onReport = onReport
    )
    Text(
        "일정·경비·준비물은 이 여행의 로컬 데이터로 관리됩니다.",
        style = MaterialTheme.typography.bodyMedium,
        color = p.textSecondary
    )
}

@Composable
private fun WorkspaceHeader(trip: TripEntity?, isPreview: Boolean) {
    val p = TripDesign.palette
    var showTravelDetails by rememberSaveable(trip?.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(TripSpacing.sm)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.xxs)
            ) {
                Text(
                    text = trip?.title ?: "여행의 모든 정보를 한곳에",
                    style = MaterialTheme.typography.headlineSmall,
                    color = p.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isPreview) {
                        "실제 여행 데이터 없이 정보 구조만 확인하는 화면입니다."
                    } else {
                        listOfNotNull(
                            trip?.country?.takeIf { it.isNotBlank() },
                            trip?.city?.takeIf { it.isNotBlank() }
                        ).joinToString(" · ").ifBlank { "목적지 미설정" }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textSecondary
                )
            }
            TripStatusTag(label = if (isPreview) "화면 미리보기" else requireNotNull(trip).phase().label)
        }
        if (trip != null) {
            Text(
                text = trip.startDate.replace('-', '.') + " ~ " + trip.endDate.replace('-', '.'),
                style = MaterialTheme.typography.bodyMedium,
                color = p.textPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)
            ) {
                Text(
                    text = trip.timeZoneId + " · " + trip.currencyCode,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary
                )
                if (trip.purpose.isNotBlank() || trip.note.isNotBlank()) {
                    TextButton(
                        onClick = { showTravelDetails = !showTravelDetails },
                        modifier = Modifier.heightIn(min = TripSizes.minimumTouchTarget)
                    ) {
                        Text(
                            text = if (showTravelDetails) "여행 설명 접기" else "여행 설명 보기",
                            color = p.textPrimary
                        )
                    }
                }
            }
            if (showTravelDetails && (trip.purpose.isNotBlank() || trip.note.isNotBlank())) {
                if (trip.purpose.isNotBlank()) {
                    Text(trip.purpose, style = MaterialTheme.typography.bodyLarge, color = p.textPrimary)
                }
                if (trip.note.isNotBlank()) {
                    Text(trip.note, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceOverview(
    trip: TripEntity?,
    isPreview: Boolean,
    schedules: List<ItineraryItemEntity>,
    budget: TripBudgetEntity?,
    checklist: List<ChecklistItemEntity>,
    expenses: List<ExpenseEntity>
) {
    val p = TripDesign.palette
    val next = trip?.let { findNextItineraryItem(schedules, it.timeZoneId) }
    val nextTitle = when {
        isPreview -> "데이터 없음"
        schedules.isEmpty() -> "일정 미등록"
        next == null -> "예정된 일정 없음"
        else -> next.title
    }
    val nextDetail = when {
        isPreview -> "실제 저장 데이터에서 표시됩니다."
        next != null -> next.date.replace('-', '.') + " · " + (next.startTime ?: "시간 미정")
        else -> "여행 일정에서 새 일정을 등록할 수 있습니다."
    }
    val totals = trip?.let { calculateExpenseTotals(expenses, it.currencyCode) }
    val spent = if (trip != null && totals != null) {
        displayMoney(totals.baseSpent.toPlainString(), trip.currencyCode)
    } else {
        "데이터 없음"
    }
    val budgetDetail = when {
        trip == null -> "실제 여행 예산을 표시합니다."
        budget == null -> "예산 미설정"
        budget.currencyCode != trip.currencyCode -> {
            "예산 " + displayMoney(budget.amountDecimal, budget.currencyCode) + " · 통화가 달라 직접 비교 불가"
        }
        else -> {
            val amount = BigDecimal(budget.amountDecimal)
            val ratio = if (amount.signum() > 0 && totals != null) {
                totals.baseSpent.multiply(BigDecimal(100))
                    .divide(amount, 0, RoundingMode.HALF_UP).toPlainString() + "% 사용"
            } else {
                "비교 불가"
            }
            "예산 " + displayMoney(budget.amountDecimal, budget.currencyCode) + " · " + ratio
        }
    }
    val unconvertedCount = totals?.unconvertedByCurrency?.size ?: 0
    val expenseDetail = if (unconvertedCount > 0) {
        budgetDetail + " · 미환산 외화 " + unconvertedCount + "종 제외"
    } else {
        budgetDetail
    }
    val completed = checklist.count { it.checked }
    val usage = if (trip != null && totals != null && budget?.currencyCode == trip.currencyCode) {
        budgetUsageProgress(totals.baseSpent, BigDecimal(requireNotNull(budget).amountDecimal))
    } else null
    val dayLabel = trip?.let {
        val start = LocalDate.parse(it.startDate)
        val end = LocalDate.parse(it.endDate)
        val days = ChronoUnit.DAYS.between(start, end) + 1
        val today = Instant.now().atZone(ZoneId.of(it.timeZoneId)).toLocalDate()
        when {
            today.isBefore(start) -> "출발 D-" + ChronoUnit.DAYS.between(today, start)
            today.isAfter(end) -> "여행 완료 · " + days + "일"
            else -> "Day " + (ChronoUnit.DAYS.between(start, today) + 1) + " / " + days
        }
    } ?: "데이터 없음"
    val checklistValue = if (isPreview) "데이터 없음" else "$completed / ${checklist.size}"
    val checklistDetail = when {
        isPreview -> "실제 준비물 상태를 표시합니다."
        checklist.isEmpty() -> "준비물 미등록"
        else -> (completed * 100 / checklist.size).toString() + "% 준비 완료"
    }
    Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
        Text(
            text = "여행 한눈에",
            style = MaterialTheme.typography.titleMedium,
            color = p.textPrimary
        )
        TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(TripSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TripSpacing.md)) {
                    WorkspaceMetric("여행 일정", if (isPreview) "데이터 없음" else schedules.size.toString() + "개", "등록된 일정", Modifier.weight(1f))
                    WorkspaceMetric("현재 여행일", dayLabel, "", Modifier.weight(1f))
                }
                TripDivider()
                val moneyBlock: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(TripSpacing.xxs)) {
                        WorkspaceMetric("사용 경비", spent, expenseDetail)
                        usage?.let { TripProgressIndicator(it.fraction, "예산 사용률 " + it.percentage, warning = it.overBudget) }
                    }
                }
                val packingBlock: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(TripSpacing.xxs)) {
                        WorkspaceMetric("준비물 완료", checklistValue, checklistDetail)
                        if (!isPreview && checklist.isNotEmpty()) {
                            TripProgressIndicator(checklistCompletionFraction(completed, checklist.size), "준비 완료 " + checklistValue)
                        }
                    }
                }
                if (LocalDensity.current.fontScale < 1.4f) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TripSpacing.sm)) {
                        moneyBlock(Modifier.weight(1.4f))
                        packingBlock(Modifier.weight(1f))
                    }
                } else {
                    moneyBlock(Modifier.fillMaxWidth())
                    TripDivider()
                    packingBlock(Modifier.fillMaxWidth())
                }
                TripDivider()
                WorkspaceMetric("다음 일정", nextTitle, nextDetail, compact = LocalDensity.current.fontScale < 1.4f)
            }
        }
    }
}

@Composable
private fun WorkspaceMetric(
    label: String,
    value: String,
    supportingText: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val p = TripDesign.palette
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(TripSpacing.xxs)
    ) {
        if (compact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TripSpacing.sm)
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
                Text(
                    value,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = p.textPrimary,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(label, style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = p.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (supportingText.isNotBlank()) Text(
            supportingText,
            style = MaterialTheme.typography.bodySmall,
            color = p.textSecondary
        )
    }
}

private data class WorkspaceTool(
    val title: String,
    val description: String,
    val actionLabel: String,
    val onOpen: (() -> Unit)?
)

@Composable
private fun WorkspaceToolGrid(
    trip: TripEntity?,
    isPreview: Boolean,
    scheduleCount: Int,
    checklist: List<ChecklistItemEntity>,
    expenses: List<ExpenseEntity>,
    onSchedule: () -> Unit,
    onPlaces: () -> Unit,
    onMap: () -> Unit,
    onChecklist: () -> Unit,
    onExpenses: () -> Unit,
    onSettlement: () -> Unit,
    onParticipants: () -> Unit,
    onBookings: () -> Unit,
    onLive: () -> Unit,
    onMemories: () -> Unit,
    onReport: () -> Unit
) {
    val p = TripDesign.palette
    val tools = listOf(
        WorkspaceTool(
            "일정", if (isPreview) "날짜별 일정 관리" else "등록된 일정 ${scheduleCount}개",
            "일정 열기", if (isPreview) null else onSchedule
        ),
        WorkspaceTool(
            "경비", if (isPreview) "지출·예산 관리" else "지출 ${expenses.size}건 · 예산 관리",
            "경비 열기", if (isPreview) null else onExpenses
        ),
        WorkspaceTool(
            "준비물", if (isPreview) "체크리스트 관리" else
                "준비 완료 ${checklist.count { it.checked }} / ${checklist.size}",
            "준비물 열기", if (isPreview) null else onChecklist
        ),
        WorkspaceTool(
            "지도", "저장한 장소와 날짜별 일정 핀",
            "지도 열기", if (isPreview) null else onMap
        ),
        WorkspaceTool("오늘의 여행", "오늘 일정과 방문 체크인", "오늘의 여행 열기", if (isPreview) null else onLive),
        WorkspaceTool("여행 기록", "사진·방문 메모", "여행 기록 열기", if (isPreview) null else onMemories),
        WorkspaceTool("여행 리포트", "실제 방문·지출 요약", "리포트 열기", if (isPreview) null else onReport),
        WorkspaceTool(
            "예약 자료", "예약 확인 정보와 문서",
            "예약 열기", if (isPreview) null else onBookings
        ),
        WorkspaceTool(
            "동행자 정산", "환율과 참가자별 비용",
            "정산 열기", if (isPreview) null else onSettlement
        ),
        WorkspaceTool(
            "동행자", "로컬 참가자 관리",
            "동행자 열기", if (isPreview) null else onParticipants
        ),
        WorkspaceTool(
            "저장 장소", "저장한 장소 목록 관리",
            "저장 장소 관리", if (isPreview) null else onPlaces
        ),

    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
    ) {
        Text(
            "작업 도구",
            style = MaterialTheme.typography.titleMedium,
            color = p.textPrimary
        )
        TripAdaptiveContainer(modifier = Modifier.fillMaxWidth()) { width ->
            Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)) {
                WorkspaceToolRows(tools.take(4), width, compactPrimary = true)
                Text(
                    "추가 관리",
                    style = MaterialTheme.typography.labelLarge,
                    color = p.textSecondary
                )
                WorkspaceToolRows(tools.drop(4), width)
            }
        }
    }
}

@Composable
private fun WorkspaceToolRows(
    tools: List<WorkspaceTool>,
    width: TripWindowWidth,
    compactPrimary: Boolean = false
) {
    if (width == TripWindowWidth.Compact && LocalDensity.current.fontScale >= 1.4f) {
        Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)) {
            tools.forEach { tool ->
                WorkspaceToolCard(tool, modifier = Modifier.fillMaxWidth())
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)) {
            tools.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TripSpacing.sm)
                ) {
                    WorkspaceToolCard(
                        pair[0],
                        modifier = Modifier.weight(1f),
                        compactPrimary = width == TripWindowWidth.Compact
                    )
                    if (pair.size == 2) {
                        WorkspaceToolCard(
                            pair[1],
                            modifier = Modifier.weight(1f),
                            compactPrimary = width == TripWindowWidth.Compact
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceToolCard(
    tool: WorkspaceTool,
    modifier: Modifier = Modifier,
    compactPrimary: Boolean = false
) {
    val p = TripDesign.palette
    Surface(
        onClick = { tool.onOpen?.invoke() },
        enabled = tool.onOpen != null,
        modifier = modifier
            .heightIn(min = TripSizes.minimumTouchTarget)
            .semantics { contentDescription = tool.actionLabel },
        shape = RectangleShape,
        color = p.surface,
        border = BorderStroke(1.dp, p.outline),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(TripSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (tool.actionLabel) {
                "일정 열기" -> Icons.Outlined.DateRange
                "경비 열기" -> Icons.Outlined.ShoppingCart
                "준비물 열기" -> Icons.Outlined.CheckCircle
                "지도 열기", "저장 장소 관리" -> Icons.Outlined.Place
                "오늘의 여행 열기" -> Icons.Outlined.DateRange
                "여행 기록 열기" -> Icons.Outlined.FavoriteBorder
                "예약 열기" -> Icons.Outlined.Email
                "동행자 열기" -> Icons.Outlined.Person
                "정산 열기", "리포트 열기" -> Icons.Outlined.List
                else -> Icons.Outlined.Info
            }
            Icon(icon, contentDescription = null, tint = p.textPrimary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TripSpacing.xxs)) {
                Text(tool.title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                Text(tool.description, style = MaterialTheme.typography.bodySmall, color = p.textSecondary,
                    maxLines = if (compactPrimary) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
