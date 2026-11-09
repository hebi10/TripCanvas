package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.hebi.tripcanvas.data.ExpenseRepository
import com.hebi.tripcanvas.data.TripFinanceRepository
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.designsystem.components.TripChoiceChip
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripTextField
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.domain.SplitMode
import com.hebi.tripcanvas.domain.convertedMinor
import com.hebi.tripcanvas.domain.displayMinor
import com.hebi.tripcanvas.domain.validCurrency
import com.hebi.tripcanvas.ui.TripPageFrame
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ExpenseSplitScreen(
    tripId: String,
    expenseId: String,
    trips: TripRepository,
    expenses: ExpenseRepository,
    finance: TripFinanceRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onParticipants: () -> Unit
) {
    val palette = TripDesign.palette
    val scope = rememberCoroutineScope()
    val trip by remember(trips, tripId) { trips.observeTrip(tripId) }
        .collectAsState(initial = null)
    val expense by remember(expenses, tripId, expenseId) {
        expenses.observeItem(tripId, expenseId)
    }.collectAsState(initial = null)
    val people by remember(finance, tripId) {
        finance.participants(tripId)
    }.collectAsState(initial = emptyList())
    val shares by remember(finance, expenseId) {
        finance.expenseShares(expenseId)
    }.collectAsState(initial = null)
    var hydrated by remember(expenseId) { mutableStateOf(false) }
    var rate by remember(expenseId) { mutableStateOf("") }
    var rateDate by remember(expenseId) { mutableStateOf("") }
    var payerId by remember(expenseId) { mutableStateOf("") }
    var chosen by remember(expenseId) { mutableStateOf<List<String>>(emptyList()) }
    var mode by remember(expenseId) { mutableStateOf(SplitMode.EQUAL) }
    var values by remember(expenseId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(expense?.id, shares) {
        val current = expense
        val prior = shares
        if (!hydrated && current != null && prior != null) {
            rate = current.rateToBaseDecimal.orEmpty()
            rateDate = current.rateDate ?: current.occurredAtLocal.substringBefore("T")
            payerId = current.payerParticipantId.orEmpty()
            chosen = prior.map { it.participantId }
            if (prior.isNotEmpty()) {
                mode = SplitMode.AMOUNT
                val digits = validCurrency(prior.first().baseCurrencyCode)?.defaultFractionDigits ?: 0
                values = prior.associate {
                    it.participantId to BigDecimal.valueOf(it.amountMinor, digits).toPlainString()
                }
            }
            hydrated = true
        }
    }

    TripPageFrame(title = "환율·분담 설정", onBack = { if (!saving) onBack() }) {
        val current = expense
        val tripInfo = trip
        if (current == null || tripInfo == null || !hydrated) {
            TripLoadingState("지출 정보를 확인하는 중")
        } else {
            val foreign = current.currencyCode != tripInfo.currencyCode
            val estimate = convertedMinor(
                current.copy(
                    baseCurrencyCode = tripInfo.currencyCode,
                    rateToBaseDecimal = rate.ifBlank { null },
                    rateDate = if (foreign) rateDate.ifBlank { null } else null,
                    rateSource = if (foreign && rate.isNotBlank()) "사용자 입력" else null
                ),
                tripInfo.currencyCode,
                rate.ifBlank { null }
            )
            TripSectionHeading(
                title = current.amountDecimal + " " + current.currencyCode,
                supportingText = "기본 통화: " + tripInfo.currencyCode +
                    " · 환산 비율은 이 지출에만 저장됩니다."
            )
            if (foreign) {
                TripSectionHeading(
                    title = "환율 스냅샷",
                    supportingText = "1 " + current.currencyCode + "당 " + tripInfo.currencyCode +
                        " 금액을 직접 입력합니다. 자동 시세 조회값이 아닙니다."
                )
                TripTextField(
                    value = rate,
                    onValueChange = { rate = it; error = null },
                    label = "환율 *",
                    placeholder = "예: 1350.25",
                    supportingText = "양수 · 소수점 최대 10자리"
                )
                TripTextField(
                    value = rateDate,
                    onValueChange = { rateDate = it; error = null },
                    label = "환율 기준일 *",
                    placeholder = "yyyy-MM-dd",
                    supportingText = "출처: 사용자 입력"
                )
                Text(
                    estimate?.let { "환산 예정: " + displayMinor(it, tripInfo.currencyCode) }
                        ?: "환산 필요 · 환율과 기준일을 입력해 주세요.",
                    color = palette.textSecondary
                )
                TripSecondaryButton(
                    label = "환율만 저장 (기존 분담 초기화)",
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        saving = true; error = null
                        scope.launch {
                            try {
                                finance.saveRate(tripId, expenseId, rate, rateDate)
                                onSaved()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failed: Exception) {
                                error = failed.message ?: "환율을 저장할 수 없습니다."
                            } finally { saving = false }
                        }
                    }
                )
            } else {
                Text(
                    "기본 통화 지출로 별도 환율이 필요하지 않습니다.",
                    color = palette.textSecondary
                )
            }
            TripSectionHeading(
                title = "결제자",
                supportingText = "실제 결제한 로컬 동행자 1명을 선택해 주세요."
            )
            if (people.isEmpty()) Text("먼저 동행자를 등록해 주세요.", color = palette.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                people.forEach { person ->
                    TripChoiceChip(
                        label = person.displayName,
                        selected = payerId == person.id,
                        onClick = { payerId = person.id }
                    )
                }
            }
            TripSecondaryButton(
                label = "동행자 관리",
                modifier = Modifier.fillMaxWidth(),
                onClick = onParticipants
            )
            TripSectionHeading(
                title = "부담 참가자",
                supportingText = "선택한 순서로 균등 분담의 잔여 최소 단위를 배정합니다."
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                people.forEach { person ->
                    TripChoiceChip(
                        label = person.displayName,
                        selected = person.id in chosen,
                        onClick = {
                            chosen = if (person.id in chosen) chosen - person.id else chosen + person.id
                        }
                    )
                }
            }
            TripSectionHeading(title = "분담 방식")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                SplitMode.entries.forEach { option ->
                    TripChoiceChip(
                        label = option.label,
                        selected = option == mode,
                        onClick = { mode = option; error = null }
                    )
                }
            }
            if (mode != SplitMode.EQUAL) {
                chosen.forEach { id ->
                    val person = people.firstOrNull { it.id == id } ?: return@forEach
                    TripTextField(
                        value = values[id].orEmpty(),
                        onValueChange = { values = values + (id to it); error = null },
                        label = person.displayName + if (mode == SplitMode.PERCENT) " 부담 비율 (%)" else
                            " 부담액 (" + tripInfo.currencyCode + ")",
                        placeholder = if (mode == SplitMode.PERCENT) "예: 50" else "예: 12000"
                    )
                }
            }
            Text(
                "지정 금액은 기본 통화의 최소 단위까지 일치해야 합니다. " +
                    "균등·비율 분담 잔여 단위는 참가자 선택 순서로 배정합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
            if (error != null) {
                TripErrorState(
                    title = "환율·분담 저장 오류",
                    description = requireNotNull(error),
                    onRetry = { error = null }
                )
            }
            TripPrimaryButton(
                label = if (saving) "저장 중" else "결제자·분담 확정",
                enabled = !saving && payerId.isNotBlank() && chosen.isNotEmpty() &&
                    (!foreign || estimate != null),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    saving = true; error = null
                    scope.launch {
                        try {
                            finance.saveAllocation(
                                tripId, expenseId, payerId, rate, rateDate, mode,
                                chosen.map { id -> id to values[id].orEmpty() }
                            )
                            onSaved()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failed: Exception) {
                            error = failed.message ?: "금액 합계를 확인해 주세요."
                        } finally { saving = false }
                    }
                }
            )
        }
    }
}
