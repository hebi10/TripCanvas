package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.hebi.tripcanvas.data.ExpenseRepository
import com.hebi.tripcanvas.data.PlaceRepository
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.local.ExpenseEntity
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
import com.hebi.tripcanvas.designsystem.responsive.TripAdaptiveContainer
import com.hebi.tripcanvas.designsystem.responsive.TripWindowWidth
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.domain.ExpenseCategory
import com.hebi.tripcanvas.domain.calculateExpenseTotals
import com.hebi.tripcanvas.domain.displayMoney
import com.hebi.tripcanvas.domain.validateMoney
import com.hebi.tripcanvas.ui.TripPageFrame
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private sealed interface ExpenseListState {
    data object Loading : ExpenseListState
    data object Failed : ExpenseListState
    data class Ready(val expenses: List<ExpenseEntity>) : ExpenseListState
}

@Composable
fun ExpenseListScreen(
    tripId: String,
    trips: TripRepository,
    repository: ExpenseRepository,
    places: PlaceRepository,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onSplit: (String) -> Unit = {},
    onSettlement: () -> Unit = {},
    onParticipants: () -> Unit = {}
) {
    val p = TripDesign.palette
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ExpenseEntity?>(null) }
    var showBudgetEditor by remember { mutableStateOf(false) }
    var budgetInput by remember { mutableStateOf("") }
    var budgetError by remember { mutableStateOf<String?>(null) }

    val trip by remember(trips, tripId) { trips.observeTrip(tripId) }
        .collectAsState(initial = null)
    val flow = remember(repository, tripId, refresh) {
        repository.observe(tripId)
            .map<List<ExpenseEntity>, ExpenseListState> { ExpenseListState.Ready(it) }
            .catch { emit(ExpenseListState.Failed) }
    }
    val state by flow.collectAsState(initial = ExpenseListState.Loading)
    val budget by remember(repository, tripId) { repository.observeBudget(tripId) }
        .collectAsState(initial = null)
    val savedPlaces by remember(places, tripId) { places.observePlaces(tripId) }
        .collectAsState(initial = emptyList())
    val placeNames = savedPlaces.associate { it.id to it.name }

    fun submit(action: suspend () -> Unit) {
        if (working) return
        working = true
        actionError = null
        scope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                actionError = "변경 내용을 저장하지 못했습니다. 다시 시도해 주세요."
            } finally {
                working = false
            }
        }
    }

    TripPageFrame(title = "예산·경비", onBack = onBack) {
        val current = trip
        if (current == null) {
            TripLoadingState("여행 정보를 불러오는 중")
        } else {
            TripSectionHeading(
                title = current.title,
                supportingText = "기본 통화 " + current.currencyCode + " · 여행별 지출 내역"
            )
            when (val result = state) {
                ExpenseListState.Loading -> TripLoadingState("지출 내역을 불러오는 중")
                ExpenseListState.Failed -> TripErrorState(
                    title = "지출 내역을 불러오지 못했습니다",
                    description = "기기 저장소를 확인해 주세요.",
                    onRetry = { refresh++ }
                )
                is ExpenseListState.Ready -> {
                    val totals = calculateExpenseTotals(result.expenses, current.currencyCode)
                    val activeBudget = budget?.takeIf { it.currencyCode == current.currencyCode }
                    val remaining = activeBudget?.let {
                        BigDecimal(it.amountDecimal).subtract(totals.baseSpent)
                    }
                    val budgetValue = activeBudget?.let {
                        displayMoney(it.amountDecimal, current.currencyCode)
                    } ?: if (budget == null) "미설정" else "통화 불일치"
                    val spentValue = displayMoney(
                        totals.baseSpent.toPlainString(), current.currencyCode
                    )
                    val budgetUsage = budgetUsageVisual(
                        totals.baseSpent, activeBudget?.let { BigDecimal(it.amountDecimal) }
                    )
                    val remainingValue = when {
                        remaining == null -> "비교 불가"
                        remaining.signum() < 0 -> displayMoney(
                            remaining.abs().toPlainString(), current.currencyCode
                        ) + " 초과"
                        else -> displayMoney(remaining.toPlainString(), current.currencyCode) + " 남음"
                    }
                    TripSectionHeading(
                        title = "예산 현황",
                        supportingText = "기본 통화 ${current.currencyCode} · 환산 가능한 지출만 합산합니다."
                    )
                    TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(TripSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
                        ) {
                            TripAdaptiveContainer(modifier = Modifier.fillMaxWidth()) { width ->
                                if (width == TripWindowWidth.Compact) {
                                    Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)) {
                                        ExpenseSummaryMetric("총예산", budgetValue)
                                        ExpenseSummaryMetric("지출 합계", spentValue)
                                        ExpenseSummaryMetric(
                                            "잔액", remainingValue, warning = remaining?.signum() == -1
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(TripSpacing.md)
                                    ) {
                                        ExpenseSummaryMetric("총예산", budgetValue, Modifier.weight(1f))
                                        ExpenseSummaryMetric("지출 합계", spentValue, Modifier.weight(1f))
                                        ExpenseSummaryMetric(
                                            "잔액", remainingValue, Modifier.weight(1f),
                                            warning = remaining?.signum() == -1
                                        )
                                    }
                                }
                            }
                            if (budgetUsage != null) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "예산 사용률",
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = p.textSecondary
                                        )
                                        Text(
                                            budgetUsage.percentageLabel,
                                            modifier = Modifier.weight(1f),
                                            textAlign = TextAlign.End,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (budgetUsage.overBudget) p.warning else p.textPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TripProgressTrack(
                                        fraction = budgetUsage.fraction,
                                        accessibilityLabel = "예산 사용률 " + budgetUsage.percentageLabel,
                                        warning = budgetUsage.overBudget
                                    )
                                    Text(
                                        text = if (budgetUsage.overBudget) {
                                            "예산 초과 · 환산 가능한 지출 기준"
                                        } else if (totals.unconvertedByCurrency.isNotEmpty()) {
                                            "환산 가능한 지출 기준 · 미환산 외화 제외"
                                        } else {
                                            "환산 가능한 지출 기준"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (budgetUsage.overBudget) p.warning else p.textSecondary
                                    )
                                }
                            } else {
                                Text(
                                    text = when {
                                        budget == null -> "예산을 설정하면 사용률을 확인할 수 있습니다."
                                        activeBudget == null -> "예산 통화가 달라 사용률을 표시하지 않습니다."
                                        else -> "0 이하의 예산은 사용률을 계산할 수 없습니다."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = p.textSecondary
                                )
                            }
                            if (budget != null && activeBudget == null) {
                                Text(
                                    "기존 예산 " + displayMoney(
                                        budget!!.amountDecimal, budget!!.currencyCode
                                    ) + " · 현재 기본 통화와 달라 비교하지 않습니다.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = p.textSecondary
                                )
                            }
                            TripSecondaryButton(
                                label = "예산 설정·변경",
                                onClick = {
                                    budgetInput = budget?.amountDecimal.orEmpty()
                                    budgetError = null
                                    showBudgetEditor = true
                                }
                            )
                        }
                    }
                    if (totals.unconvertedByCurrency.isNotEmpty()) {
                        TripSectionHeading(
                            title = "환산 필요",
                            supportingText = "환율이 없는 다른 통화는 기본 통화 합계·예산에서 제외합니다."
                        )
                        totals.unconvertedByCurrency.forEach { (code, amount) ->
                            Text(
                                displayMoney(amount.toPlainString(), code),
                                style = MaterialTheme.typography.bodyMedium,
                                color = p.textSecondary
                            )
                        }
                    }
                    TripPrimaryButton(
                        label = "지출 추가",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onNew
                    )
                    TripSectionHeading(
                        title = "지출 " + result.expenses.size + "건",
                        supportingText = "날짜별 내역 · 환율 스냅샷과 분담 상태는 지출별로 관리합니다."
                    )
                    if (result.expenses.isEmpty()) {
                        TripEmptyState(
                            title = "기록한 지출이 없습니다",
                            description = "첫 지출을 추가해 보세요.",
                            actionLabel = null
                        )
                    } else {
                        result.expenses.groupBy { it.occurredAtLocal.substringBefore("T") }
                            .forEach { (date, rows) ->
                                Text(
                                    date,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = p.textPrimary
                                )
                                rows.forEach { item ->
                                    TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier.padding(TripSpacing.md),
                                            verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                                        ) {
                                            Text(
                                                displayMoney(item.amountDecimal, item.currencyCode),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = p.textPrimary
                                            )
                                            Text(
                                                ExpenseCategory.valueOf(item.category).label +
                                                    " · " + item.occurredAtLocal.substringAfter("T").take(5),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = p.textSecondary
                                            )
                                            item.placeId?.let(placeNames::get)?.let { place ->
                                                Text(place, color = p.textSecondary)
                                            }
                                            if (item.note.isNotEmpty()) {
                                                Text(item.note, color = p.textSecondary)
                                            }
                                            if (item.rateToBaseDecimal != null) {
                                                Text(
                                                    "환율 " + item.rateToBaseDecimal +
                                                        " · " + item.rateDate.orEmpty() +
                                                        " · " + item.rateSource.orEmpty(),
                                                    color = p.textSecondary
                                                )
                                            } else if (item.currencyCode != current.currencyCode) {
                                                Text("환산 필요", color = p.textSecondary)
                                            }
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                                                TripSecondaryButton(
                                                    label = "환율·분담",
                                                    onClick = { onSplit(item.id) }
                                                )
                                                TripSecondaryButton(
                                                    label = "편집",
                                                    onClick = { onEdit(item.id) }
                                                )
                                                TripSecondaryButton(
                                                    label = "삭제",
                                                    onClick = { deleteTarget = item }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                    }
                }
            }
            if (actionError != null) {
                TripErrorState(
                    title = "경비 저장 오류",
                    description = requireNotNull(actionError),
                    onRetry = { actionError = null }
                )
            }
            TripSectionHeading(
                title = "동행자·정산",
                supportingText = "참가자 관리와 비용 정산은 별도로 확인할 수 있습니다."
            )
            TripSecondaryButton(
                label = "동행자 정산",
                onClick = onSettlement,
                modifier = Modifier.fillMaxWidth()
            )
            TripSecondaryButton(
                label = "동행자 관리",
                onClick = onParticipants,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showBudgetEditor && trip != null) {
        val current = requireNotNull(trip)
        AlertDialog(
            onDismissRequest = { if (!working) showBudgetEditor = false },
            title = { Text("총예산 · " + current.currencyCode) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                    TripTextField(
                        value = budgetInput,
                        onValueChange = { budgetInput = it; budgetError = null },
                        label = "총예산",
                        supportingText = budgetError ?: "기본 통화의 양수 금액",
                        isError = budgetError != null
                    )
                    Text(
                        "예산이 다른 통화로 저장되어 있어도 기존 값을 자동 환산하지 않습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.textSecondary
                    )
                    if (budget != null) {
                        TripSecondaryButton(
                            label = "예산 해제",
                            enabled = !working,
                            onClick = {
                                submit {
                                    repository.clearBudget(tripId)
                                    showBudgetEditor = false
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !working, onClick = {
                    if (validateMoney(budgetInput, current.currencyCode) == null) {
                        budgetError = "0보다 큰 금액을 정확한 소수 자릿수로 입력해 주세요."
                    } else {
                        submit {
                            repository.setBudget(tripId, budgetInput, current.currencyCode)
                            showBudgetEditor = false
                        }
                    }
                }) { Text(if (working) "저장 중" else "저장", color = p.primaryAction) }
            },
            dismissButton = {
                TextButton(enabled = !working, onClick = { showBudgetEditor = false }) {
                    Text("취소", color = p.textSecondary)
                }
            },
            shape = RectangleShape,
            containerColor = p.surface
        )
    }

    deleteTarget?.let { item ->
        TripConfirmDialog(
            title = "지출 삭제",
            message = displayMoney(item.amountDecimal, item.currencyCode) +
                " 지출을 삭제하시겠습니까? 삭제 후에는 복원할 백업이 없다면 되돌릴 수 없습니다.",
            confirmLabel = "지출 삭제",
            destructive = true,
            onConfirm = {
                submit {
                    repository.delete(tripId, item.id)
                    deleteTarget = null
                }
            },
            onDismiss = { if (!working) deleteTarget = null }
        )
    }
}

@Composable
private fun ExpenseSummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false
) {
    val p = TripDesign.palette
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(TripSpacing.xxs)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = p.textSecondary
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (warning) p.warning else p.textPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
