package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.hebi.tripcanvas.data.ExpenseRepository
import com.hebi.tripcanvas.data.ItineraryRepository
import com.hebi.tripcanvas.data.TravelJournalRepository
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.domain.calculateExpenseTotals
import com.hebi.tripcanvas.domain.displayMoney
import com.hebi.tripcanvas.ui.TripPageFrame
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
fun TripReportScreen(
    tripId: String,
    trips: TripRepository,
    itineraries: ItineraryRepository,
    expenses: ExpenseRepository,
    journal: TravelJournalRepository,
    onBack: () -> Unit
) {
    val trip by remember(tripId) { trips.observeTrip(tripId) }.collectAsState(initial = null)
    val schedules by remember(tripId) { itineraries.observeTrip(tripId) }.collectAsState(initial = emptyList())
    val costs by remember(tripId) { expenses.observe(tripId) }.collectAsState(initial = emptyList())
    val visits by remember(tripId) { journal.visits(tripId) }.collectAsState(initial = emptyList())
    val moments by remember(tripId) { journal.moments(tripId) }.collectAsState(initial = emptyList())
    val palette = TripDesign.palette
    TripPageFrame(title = "여행 리포트", onBack = onBack, contentSpacing = TripSpacing.md) {
        val data = trip
        if (data == null) {
            TripLoadingState("여행 정보를 불러오는 중입니다.")
        } else {
            val start = LocalDate.parse(data.startDate)
            val end = LocalDate.parse(data.endDate)
            val days = ChronoUnit.DAYS.between(start, end) + 1
            val today = LocalDate.now(ZoneId.of(data.timeZoneId))
            val destination = listOf(data.country, data.city).filter { it.isNotBlank() }.distinct().joinToString(" · ")
            TripSectionHeading(title = data.title, supportingText =
                listOf(destination, data.startDate + " ~ " + data.endDate)
                    .filter { it.isNotBlank() }.joinToString("\n"))
            Text(
                when {
                    today.isBefore(start) -> "여행 전 계획 및 현재 기록"
                    today.isAfter(end) -> "여행 결과 요약"
                    else -> "현재까지의 여행 기록"
                }, color = palette.textSecondary
            )
            TripSectionHeading(title = "여행 요약")
            TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(TripSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
                ) {
                    Text("여행 기간: ${days}일", style = MaterialTheme.typography.titleLarge)
                    Text("계획 일정: ${schedules.size}개", style = MaterialTheme.typography.titleMedium)
                    Text("실제 방문: ${visits.size}건", style = MaterialTheme.typography.titleMedium)
                    Text("사진·메모 기록: ${moments.size}건", style = MaterialTheme.typography.titleMedium)
                }
            }
            TripSectionHeading(title = "대표 기록 사진")
            val cover = moments.firstOrNull { it.photoUri != null }?.photoUri
            TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(TripSpacing.md)) {
                    if (cover != null) {
                        TravelPhotoThumbnail(cover)
                    } else {
                        Text("등록된 기록 사진이 없습니다.", color = palette.textSecondary)
                    }
                }
            }
            val totals = calculateExpenseTotals(costs, data.currencyCode)
            TripSectionHeading(title = "경비 요약")
            TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(TripSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                ) {
                    Text("기록된 경비: " + displayMoney(totals.baseSpent.toPlainString(), data.currencyCode),
                        style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                    if (totals.unconvertedByCurrency.isNotEmpty()) {
                        Text("미환산 외화 지출은 합계에서 제외했습니다.", color = palette.textSecondary)
                    }
                }
            }
            Text("여행 리포트는 기기 내부의 실제 기록을 요약합니다. 예약 확인번호와 동행자 개인정보는 표시하지 않습니다.",
                style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
        }
    }
}
