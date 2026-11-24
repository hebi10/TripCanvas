package com.hebi.tripcanvas.screens

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
import com.hebi.tripcanvas.domain.calculateExpenseTotals
import com.hebi.tripcanvas.domain.displayMoney
import com.hebi.tripcanvas.ui.TripPageFrame
import java.time.LocalDate
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
    TripPageFrame(title = "여행 리포트", onBack = onBack) {
        val data = trip
        if (data == null) {
            Text("여행 정보를 불러오는 중입니다.")
        } else {
            Text(data.title, style = MaterialTheme.typography.titleLarge)
            val days = ChronoUnit.DAYS.between(
                LocalDate.parse(data.startDate), LocalDate.parse(data.endDate)
            ) + 1
            Text("여행 기간: ${days}일")
            Text("계획 일정: ${schedules.size}개")
            Text("실제 방문: ${visits.size}건")
            Text("사진·메모 기록: ${moments.size}건")
            moments.firstOrNull { it.photoUri != null }?.photoUri?.let { uri ->
                Text("대표 기록 사진", style = MaterialTheme.typography.titleMedium)
                TravelPhotoThumbnail(uri)
            }
            val totals = calculateExpenseTotals(costs, data.currencyCode)
            Text("기록된 경비: " + displayMoney(totals.baseSpent.toPlainString(), data.currencyCode))
            if (totals.unconvertedByCurrency.isNotEmpty()) {
                Text("미환산 외화 지출은 합계에서 제외했습니다.")
            }
            Text("여행 리포트는 기기 내부의 실제 기록을 요약합니다. 예약 확인번호와 동행자 개인정보는 표시하지 않습니다.")
        }
    }
}
