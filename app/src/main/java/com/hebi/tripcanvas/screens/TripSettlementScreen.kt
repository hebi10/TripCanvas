package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.domain.calculateSettlement
import com.hebi.tripcanvas.domain.displayMinor
import com.hebi.tripcanvas.ui.TripPageFrame
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun TripSettlementScreen(
    tripId: String,
    trips: TripRepository,
    expenses: ExpenseRepository,
    finance: TripFinanceRepository,
    onBack: () -> Unit,
    onParticipants: () -> Unit,
    onExpenseSplit: (String) -> Unit
) {
    val palette = TripDesign.palette
    val scope = rememberCoroutineScope()
    val trip by remember(trips, tripId) { trips.observeTrip(tripId) }
        .collectAsState(initial = null)
    val people by remember(finance, tripId) { finance.participants(tripId) }
        .collectAsState(initial = emptyList())
    val entries by remember(expenses, tripId) { expenses.observe(tripId) }
        .collectAsState(initial = emptyList())
    val shares by remember(finance, tripId) { finance.shares(tripId) }
        .collectAsState(initial = emptyList())
    val confirmation by remember(finance, tripId) { finance.confirmation(tripId) }
        .collectAsState(initial = null)
    val summary = trip?.let { current ->
        calculateSettlement(current.currencyCode, people, entries, shares)
    }
    val confirmed = summary != null && confirmation?.fingerprint == summary.fingerprint
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    TripPageFrame(title = "동행자 정산", onBack = onBack) {
        if (trip == null || summary == null) {
            TripEmptyState("여행 정보 확인 중", "여행과 경비 정보를 불러옵니다.")
        } else {
            val report = requireNotNull(summary)
            TripSectionHeading(
                title = "정산 · " + trip!!.currencyCode,
                supportingText = "참가자별 지불액과 부담액으로 계산합니다. 실제 송금하지 않습니다."
            )
            TripSectionHeading(
                title = when {
                    confirmed -> "정산 완료 확인"
                    confirmation != null -> "내역 변경 · 재확인 필요"
                    else -> "정산 검토 중"
                },
                supportingText = if (confirmed) {
                    val date = confirmation!!.confirmedAtMillis
                    "확인 시각 " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        .withZone(ZoneId.of("Asia/Seoul")).format(Instant.ofEpochMilli(date))
                } else "현재 지출 내역을 기준으로 다시 계산했습니다."
            )
            if (report.unconverted > 0 || report.unallocated > 0) {
                TripErrorState(
                    title = "정산 보류",
                    description = "환산 필요 " + report.unconverted +
                        "건 · 결제자/분담 미완료 " + report.unallocated + "건. " +
                        "미확정 지출은 정산 합계에 포함되지 않습니다."
                )
            }
            if (people.isEmpty()) {
                TripEmptyState("등록한 동행자가 없습니다", "로컬 동행자를 먼저 등록해 주세요.")
            }
            TripSecondaryButton(
                label = "동행자 관리",
                modifier = Modifier.fillMaxWidth(),
                onClick = onParticipants
            )
            TripSectionHeading(
                title = "참가자별 지불·부담",
                supportingText = "분담이 확정된 " + report.includedExpenses + "건에 대한 금액"
            )
            report.balances.forEach { balance ->
                TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(TripSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                    ) {
                        Text(
                            balance.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary
                        )
                        Text(
                            "지불 " + displayMinor(balance.paidMinor, report.baseCurrencyCode) +
                                " · 부담 " + displayMinor(balance.owedMinor, report.baseCurrencyCode),
                            color = palette.textSecondary
                        )
                        Text(
                            if (balance.netMinor >= 0) {
                                "받을 금액 " + displayMinor(balance.netMinor, report.baseCurrencyCode)
                            } else {
                                "낼 금액 " + displayMinor(-balance.netMinor, report.baseCurrencyCode)
                            },
                            color = palette.textPrimary
                        )
                    }
                }
            }
            TripSectionHeading("정산 제안 " + report.transfers.size + "건")
            val peopleById = people.associateBy { it.id }
            if (report.transfers.isEmpty()) {
                Text("현재 정산할 금액이 없습니다.", color = palette.textSecondary)
            }
            report.transfers.forEach { transfer ->
                TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        peopleById[transfer.fromParticipantId]?.displayName.orEmpty() +
                            " → " + peopleById[transfer.toParticipantId]?.displayName.orEmpty() +
                            " · " + displayMinor(transfer.amountMinor, report.baseCurrencyCode),
                        modifier = Modifier.padding(TripSpacing.md),
                        color = palette.textPrimary
                    )
                }
            }
            TripSectionHeading("지출별 환율·분담 검토")
            entries.forEach { expense ->
                TripSecondaryButton(
                    label = expense.amountDecimal + " " + expense.currencyCode + " · 환율·분담 설정",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onExpenseSplit(expense.id) }
                )
            }
            if (error != null) {
                TripErrorState(
                    title = "정산 확인 오류",
                    description = requireNotNull(error),
                    onRetry = { error = null }
                )
            }
            if (confirmation != null) {
                TripSecondaryButton(
                    label = "정산 완료 표시 철회",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = {
                        busy = true; error = null
                        scope.launch {
                            try {
                                finance.revokeConfirmation(tripId)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failed: Exception) {
                                error = "정산 상태 변경에 실패했습니다."
                            } finally { busy = false }
                        }
                    }
                )
            }
            TripPrimaryButton(
                label = if (confirmed) "확인 완료" else "현재 정산 내역 확인",
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && report.readyToConfirm && !confirmed,
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            finance.confirm(tripId)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failed: Exception) {
                            error = failed.message ?: "정산을 완료할 수 없습니다."
                        } finally { busy = false }
                    }
                }
            )
        }
    }
}
