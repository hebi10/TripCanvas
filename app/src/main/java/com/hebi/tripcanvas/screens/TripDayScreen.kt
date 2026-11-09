package com.hebi.tripcanvas.screens

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hebi.tripcanvas.data.ItineraryRepository
import com.hebi.tripcanvas.data.PlaceRepository
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.designsystem.components.TripChoiceChip
import com.hebi.tripcanvas.designsystem.components.TripConfirmDialog
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripStatusTag
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.ui.TripPageFrame
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private sealed interface DayState {
    data object Loading : DayState
    data object Failed : DayState
    data class Ready(val items: List<ItineraryItemEntity>) : DayState
}

@Composable
fun TripDayScreen(
    tripRepository: TripRepository,
    itineraryRepository: ItineraryRepository,
    placeRepository: PlaceRepository,
    tripId: String,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onEdit: (String) -> Unit,
    onPlaces: () -> Unit
) {
    val p = TripDesign.palette
    val scope = rememberCoroutineScope()
    var selectedDate by rememberSaveable(tripId) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var failedAction by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ItineraryItemEntity?>(null) }

    val tripFlow = remember(tripRepository, tripId) {
        tripRepository.observeTrip(tripId)
    }
    val trip by tripFlow.collectAsState(initial = null)
    LaunchedEffect(trip?.startDate, trip?.endDate, trip?.timeZoneId) {
        val current = trip ?: return@LaunchedEffect
        val start = LocalDate.parse(current.startDate)
        val end = LocalDate.parse(current.endDate)
        val oldDate = runCatching { LocalDate.parse(selectedDate) }.getOrNull()
        if (oldDate == null || oldDate.isBefore(start) || oldDate.isAfter(end)) {
            val today = Instant.now().atZone(ZoneId.of(current.timeZoneId)).toLocalDate()
            selectedDate = today.coerceIn(start, end).toString()
        }
    }
    val itemsFlow = remember(itineraryRepository, tripId, selectedDate, refresh) {
        itineraryRepository.observeDay(tripId, selectedDate)
            .map<List<ItineraryItemEntity>, DayState> { DayState.Ready(it) }
            .catch { emit(DayState.Failed) }
    }
    val state by itemsFlow.collectAsState(initial = DayState.Loading)
    val places by remember(placeRepository, tripId) {
        placeRepository.observePlaces(tripId)
    }.collectAsState(initial = emptyList())
    val placeNames = places.associate { it.id to it.name }

    TripPageFrame(title = "여행 일정", onBack = onBack) {
        if (trip == null) {
            TripLoadingState("여행 정보를 불러오는 중")
        } else {
            val current = requireNotNull(trip)
            val start = LocalDate.parse(current.startDate)
            val end = LocalDate.parse(current.endDate)
            val selected = runCatching { LocalDate.parse(selectedDate) }.getOrNull()
            TripSectionHeading(
                title = current.title,
                supportingText = current.startDate + " ~ " + current.endDate +
                    " · " + current.timeZoneId
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                TripSecondaryButton(
                    label = "이전 날짜",
                    enabled = selected != null && selected.isAfter(start),
                    onClick = {
                        selectedDate = selected!!.minusDays(1).toString()
                    }
                )
                TripSecondaryButton(
                    label = "다음 날짜",
                    enabled = selected != null && selected.isBefore(end),
                    onClick = {
                        selectedDate = selected!!.plusDays(1).toString()
                    }
                )
            }
            if (selected != null) {
                val dates = (-2L..2L).map { selected.plusDays(it) }
                    .filter { !it.isBefore(start) && !it.isAfter(end) }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                ) {
                    dates.forEach { date ->
                        TripChoiceChip(
                            label = date.monthValue.toString() + "/" + date.dayOfMonth,
                            selected = date == selected,
                            onClick = { selectedDate = date.toString() }
                        )
                    }
                }
                TripDateField(
                    label = "날짜 바로 이동",
                    date = selectedDate,
                    error = null,
                    onDateSelected = { chosen ->
                        val date = LocalDate.parse(chosen)
                        if (!date.isBefore(start) && !date.isAfter(end)) {
                            selectedDate = chosen
                        } else {
                            failedAction = true
                        }
                    }
                )
            }
            if (failedAction) {
                TripErrorState(
                    title = "일정 작업을 처리하지 못했습니다",
                    description = "여행 날짜 범위를 확인하거나 다시 시도해 주세요.",
                    onRetry = { failedAction = false }
                )
            }
            when (val result = state) {
                DayState.Loading -> TripLoadingState("선택 날짜의 일정을 불러오는 중")
                DayState.Failed -> TripErrorState(
                    title = "일정을 불러오지 못했습니다",
                    description = "다시 시도해 주세요.",
                    onRetry = { refresh++ }
                )
                is DayState.Ready -> {
                    val timed = result.items.filter { it.startTime != null }
                    val untimed = result.items.filter { it.startTime == null }
                    if (result.items.isEmpty()) {
                        TripEmptyState(
                            title = "이 날짜에는 일정이 없습니다",
                            description = "일정을 추가하거나 다른 날짜를 선택해 주세요.",
                            actionLabel = "일정 추가",
                            onAction = { onAdd(selectedDate) }
                        )
                    } else {
                        TripSectionHeading(
                            title = "시간 지정 일정",
                            supportingText = "길게 누른 이동 핸들을 끌거나 위/아래 버튼으로 순서를 저장할 수 있습니다."
                        )
                        if (timed.isEmpty()) {
                            Text("시간을 지정한 일정이 없습니다.", color = p.textSecondary)
                        }
                        timed.forEach { item ->
                            ScheduleRow(
                                item = item,
                                placeName = item.placeId?.let(placeNames::get),
                                disabled = busy,
                                onEdit = { onEdit(item.id) },
                                onDuplicate = {
                                    if (!busy) {
                                        busy = true
                                        scope.launch {
                                            try {
                                                itineraryRepository.duplicate(tripId, item.id)
                                            } catch (error: CancellationException) {
                                                throw error
                                            } catch (error: Exception) {
                                                failedAction = true
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    }
                                },
                                onDelete = { deleteTarget = item },
                                onMove = { offset ->
                                    if (!busy && offset != 0) {
                                        busy = true
                                        scope.launch {
                                            try {
                                                itineraryRepository.moveWithinDay(
                                                    tripId, selectedDate, item.id, offset
                                                )
                                            } catch (error: CancellationException) {
                                                throw error
                                            } catch (error: Exception) {
                                                failedAction = true
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        TripSectionHeading(
                            title = "시간 미정",
                            supportingText = "시간 없이 추가한 일정은 별도로 표시됩니다."
                        )
                        if (untimed.isEmpty()) {
                            Text("시간 미정 일정이 없습니다.", color = p.textSecondary)
                        }
                        untimed.forEach { item ->
                            ScheduleRow(
                                item = item,
                                placeName = item.placeId?.let(placeNames::get),
                                disabled = busy,
                                onEdit = { onEdit(item.id) },
                                onDuplicate = {
                                    if (!busy) {
                                        busy = true
                                        scope.launch {
                                            try {
                                                itineraryRepository.duplicate(tripId, item.id)
                                            } catch (error: CancellationException) {
                                                throw error
                                            } catch (error: Exception) {
                                                failedAction = true
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    }
                                },
                                onDelete = { deleteTarget = item },
                                onMove = { offset ->
                                    if (!busy && offset != 0) {
                                        busy = true
                                        scope.launch {
                                            try {
                                                itineraryRepository.moveWithinDay(
                                                    tripId, selectedDate, item.id, offset
                                                )
                                            } catch (error: CancellationException) {
                                                throw error
                                            } catch (error: Exception) {
                                                failedAction = true
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            TripPrimaryButton(
                label = "일정 추가",
                onClick = { onAdd(selectedDate) },
                enabled = selectedDate.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            TripSecondaryButton(
                label = "저장 장소 관리",
                onClick = onPlaces,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    deleteTarget?.let { target ->
        TripConfirmDialog(
            title = "일정 삭제",
            message = target.title + " 일정을 영구 삭제하시겠습니까?",
            confirmLabel = "일정 삭제",
            destructive = true,
            onDismiss = { if (!busy) deleteTarget = null },
            onConfirm = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            itineraryRepository.delete(tripId, target.id)
                            deleteTarget = null
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            failedAction = true
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ScheduleRow(
    item: ItineraryItemEntity,
    placeName: String?,
    disabled: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit
) {
    val p = TripDesign.palette
    var dragY by remember(item.id) { mutableFloatStateOf(0f) }
    var heightPx by remember(item.id) { mutableIntStateOf(1) }
    val dragHandle = Modifier
        .width(48.dp)
        .heightIn(min = 48.dp)
        .pointerInput(item.id, disabled) {
            if (!disabled) {
                detectDragGesturesAfterLongPress(
                    onDrag = { change, amount ->
                        change.consume()
                        dragY += amount.y
                    },
                    onDragCancel = { dragY = 0f },
                    onDragEnd = {
                        val steps = (dragY / heightPx.coerceAtLeast(1)).roundToInt()
                        dragY = 0f
                        if (steps != 0) onMove(steps)
                    }
                )
            }
        }
    TripSurfacePanel(
        modifier = Modifier.fillMaxWidth()
            .onSizeChanged { heightPx = it.height }
            .offset { IntOffset(0, dragY.roundToInt()) }
            .zIndex(if (dragY == 0f) 0f else 1f)
    ) {
        Column(
            modifier = Modifier.padding(TripSpacing.md),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                Text("≡", modifier = dragHandle, style = MaterialTheme.typography.headlineSmall, color = p.textSecondary)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                    Text(
                        item.startTime?.let { it + " ~ " + item.endTime } ?: "시간 미정",
                        style = MaterialTheme.typography.labelMedium,
                        color = p.textSecondary
                    )
                    Text(item.title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                    if (placeName != null) {
                        Text(placeName, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
                    }
                    if (item.note.isNotBlank()) {
                        Text(item.note.take(120), style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
            ) {
                if (item.important) TripStatusTag("중요")
                if (item.confirmed) TripStatusTag("확정")
                TripSecondaryButton(label = "수정", onClick = onEdit, enabled = !disabled)
                TripSecondaryButton(label = "복제", onClick = onDuplicate, enabled = !disabled)
                TripSecondaryButton(label = "위로", onClick = { onMove(-1) }, enabled = !disabled)
                TripSecondaryButton(label = "아래로", onClick = { onMove(1) }, enabled = !disabled)
                TripSecondaryButton(label = "삭제", onClick = onDelete, enabled = !disabled)
            }
        }
    }
}
