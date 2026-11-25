package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hebi.tripcanvas.data.BookingRepository
import com.hebi.tripcanvas.data.ItineraryRepository
import com.hebi.tripcanvas.data.TravelJournalRepository
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripStatusTag
import com.hebi.tripcanvas.designsystem.components.TripStatusTone
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.components.TripTextField
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.ui.TripPageFrame
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
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
    val palette = TripDesign.palette
    val trip by remember(tripId) { trips.observeTrip(tripId) }.collectAsState(initial = null)
    val items by remember(tripId) { schedules.observeTrip(tripId) }.collectAsState(initial = emptyList())
    val visits by remember(tripId) { journal.visits(tripId) }.collectAsState(initial = emptyList())
    val reservations by remember(tripId) { bookings.observe(tripId) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var error by remember(tripId) { mutableStateOf<String?>(null) }
    var visitNote by remember(tripId) { mutableStateOf("") }
    var savingId by remember(tripId) { mutableStateOf<String?>(null) }
    val today = trip?.let { localToday(it) }
    val todays = items.filter { it.date == today }.sortedWith(
        compareBy({ it.startTime ?: "99:99" }, { it.sortOrder })
    )
    val visitedIds = visits.mapNotNull { it.itineraryItemId }.toSet()
    val visitedToday = todays.count { it.id in visitedIds }
    val currentTime = trip?.let { LocalTime.now(ZoneId.of(it.timeZoneId)).toString().take(5) }
    val next = todays.firstOrNull { it.id !in visitedIds && (it.startTime == null || it.startTime >= currentTime.orEmpty()) }
    val todayReservations = if (today == null) emptyList() else reservations.filter {
        it.startAtLocal?.startsWith(today) == true
    }

    TripPageFrame(
        title = "오늘의 여행",
        onBack = onBack,
        contentSpacing = TripSpacing.md,
        bottomAction = {
            if (trip != null) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                ) {
                    TripPrimaryButton(label = "빠른 지출 등록", onClick = onExpenses, enabled = savingId == null)
                    TripSecondaryButton(label = "방문·사진 기록", onClick = onMemories, enabled = savingId == null)
                }
            }
        }
    ) {
        val data = trip
        if (data == null || today == null) {
            TripLoadingState("여행 정보를 불러오는 중")
        } else {
            TripSectionHeading(title = data.title, supportingText = "여행 현지 날짜 · " + data.timeZoneId)
            Text(today, style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
            val day = LocalDate.parse(today)
            val start = LocalDate.parse(data.startDate)
            val end = LocalDate.parse(data.endDate)
            if (day.isBefore(start) || day.isAfter(end)) {
                Text("오늘은 여행 기간 밖입니다.", color = palette.textSecondary)
            } else {
                val totalDays = ChronoUnit.DAYS.between(start, end) + 1
                val dayNumber = ChronoUnit.DAYS.between(start, day) + 1
                Text("Day $dayNumber / $totalDays", color = palette.textSecondary)
            }

            TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(TripSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                ) {
                    TripSectionHeading(title = "다음 일정")
                    if (next != null) {
                        Text(next.startTime ?: "시간 미정", color = palette.textSecondary)
                        Text(next.title, style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                    } else {
                        Text("예정된 다음 일정이 없습니다.", color = palette.textSecondary)
                    }
                    Text("방문 완료 $visitedToday / ${todays.size}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            TripSectionHeading(title = "오늘의 예약")
            if (todayReservations.isEmpty()) {
                Text("오늘 예정된 예약이 없습니다.", color = palette.textSecondary)
            } else {
                todayReservations.forEach {
                    TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(TripSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(TripSpacing.xxs)
                        ) {
                            Text(it.title, style = MaterialTheme.typography.titleMedium)
                            Text(it.startAtLocal?.substringAfter("T") ?: "시간 미정", color = palette.textSecondary)
                        }
                    }
                }
            }
            TripSectionHeading(title = "오늘의 일정")
            if (todays.isEmpty()) {
                TripEmptyState("오늘 등록된 일정이 없습니다.", "저장한 여행 일정은 작업 공간에서 확인할 수 있습니다.")
            }
            if (todays.any { it.id !in visitedIds }) {
                TripTextField(
                    value = visitNote,
                    onValueChange = { if (it.length <= 2000) visitNote = it },
                    label = "방문 메모 (선택)",
                    supportingText = "다음 방문 완료 기록에 함께 저장됩니다. 최대 2,000자",
                    singleLine = false
                )
            }
            todays.forEach { item ->
                TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(TripSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                        Text(item.startTime ?: "시간 미정", color = palette.textSecondary)
                        if (item.id in visitedIds) {
                            TripStatusTag("방문 완료", tone = TripStatusTone.Success)
                        } else {
                            TripPrimaryButton(
                                label = if (savingId == item.id) "저장 중" else "방문 완료",
                                modifier = Modifier.fillMaxWidth(),
                                enabled = savingId == null,
                                onClick = {
                                    if (savingId == null) {
                                        savingId = item.id
                                        val note = visitNote
                                        scope.launch {
                                            try {
                                                error = if (journal.checkIn(tripId, item.id, note)) {
                                                    visitNote = ""
                                                    null
                                                } else "이미 방문한 일정입니다."
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                error = "방문 기록을 저장하지 못했습니다."
                                            } finally {
                                                savingId = null
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            error?.let { TripErrorState(title = "방문 기록 오류", description = it) }
            Text(
                "방문 기록은 버튼을 눌렀을 때만 저장합니다. 위치를 자동 수집하지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
    }
}

internal fun localToday(trip: TripEntity, today: LocalDate? = null): String =
    (today ?: LocalDate.now(ZoneId.of(trip.timeZoneId))).toString()
