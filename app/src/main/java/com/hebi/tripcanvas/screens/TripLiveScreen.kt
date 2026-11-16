package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hebi.tripcanvas.data.BookingRepository
import com.hebi.tripcanvas.data.ItineraryRepository
import com.hebi.tripcanvas.data.TravelJournalRepository
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.ui.TripPageFrame
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun TripLiveScreen(
    tripId: String,
    trips: TripRepository,
    schedules: ItineraryRepository,
    journal: TravelJournalRepository,
    bookings: BookingRepository,
    onBack: () -> Unit,
    onExpenses: () -> Unit,
    onMemories: () -> Unit
) {
    val trip by remember(tripId) { trips.observeTrip(tripId) }.collectAsState(initial = null)
    val items by remember(tripId) { schedules.observeTrip(tripId) }.collectAsState(initial = emptyList())
    val visits by remember(tripId) { journal.visits(tripId) }.collectAsState(initial = emptyList())
    val reservations by remember(tripId) { bookings.observe(tripId) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val today = trip?.let { localToday(it) }
    val todays = items.filter { it.date == today }.sortedWith(
        compareBy({ it.startTime ?: "99:99" }, { it.sortOrder })
    )
    val visitedIds = visits.mapNotNull { it.itineraryItemId }.toSet()
    val currentTime = trip?.let { LocalTime.now(ZoneId.of(it.timeZoneId)).toString().take(5) }
    val next = todays.firstOrNull { it.id !in visitedIds && (it.startTime == null || it.startTime >= currentTime.orEmpty()) }
    val todayReservations = reservations.filter { it.startAtLocal?.startsWith(today.orEmpty()) == true }
    TripPageFrame(title = "오늘의 여행", onBack = onBack) {
        Text(today ?: "여행 정보 없음", style = MaterialTheme.typography.titleLarge)
        next?.let { Text("다음 일정: " + it.title, style = MaterialTheme.typography.titleMedium) }
        if (todayReservations.isNotEmpty()) {
            Text("오늘의 예약", style = MaterialTheme.typography.titleMedium)
            todayReservations.forEach { Text(it.title + " · " + (it.startAtLocal?.substringAfter("T") ?: "시간 미정")) }
        }
        if (todays.isEmpty()) {
            Text("오늘 등록된 일정이 없습니다.")
        }
        todays.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(com.hebi.tripcanvas.designsystem.tokens.TripSpacing.xs)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(item.startTime ?: "시간 미정")
                if (item.id in visitedIds) {
                    Text("방문 완료")
                } else {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                error = if (journal.checkIn(tripId, item.id)) null else "이미 방문한 일정입니다."
                            } catch (_: Exception) {
                                error = "방문 기록을 저장하지 못했습니다."
                            }
                        }
                    }) { Text("방문 완료") }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(modifier = androidx.compose.ui.Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onExpenses) { Text("빠른 지출 등록") }
            TextButton(onClick = onMemories) { Text("방문·사진 기록") }
        }
        Text("방문 기록은 버튼을 눌렀을 때만 저장합니다. 위치를 자동 수집하지 않습니다.")
    }
}

internal fun localToday(trip: TripEntity, today: LocalDate? = null): String =
    (today ?: LocalDate.now(ZoneId.of(trip.timeZoneId))).toString()
