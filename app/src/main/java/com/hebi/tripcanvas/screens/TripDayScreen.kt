package com.hebi.tripcanvas.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import com.hebi.tripcanvas.designsystem.components.TripStatusTone
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.icons.TripIcons
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSizes
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.domain.scheduleOverlaps
import com.hebi.tripcanvas.ui.TripPageFrame
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
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

private sealed interface DayTripState {
    data object Loading : DayTripState
    data object Failed : DayTripState
    data class Ready(val trip: TripEntity?) : DayTripState
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
    onPlaces: () -> Unit,
    onMap: (String) -> Unit = {}
) {
    val p = TripDesign.palette
    val scope = rememberCoroutineScope()
    var selectedDate by rememberSaveable(tripId) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var failedAction by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ItineraryItemEntity?>(null) }

    var tripRefresh by remember { mutableIntStateOf(0) }
    val tripFlow = remember(tripRepository, tripId, tripRefresh) {
        tripRepository.observeTrip(tripId)
            .map<TripEntity?, DayTripState> { DayTripState.Ready(it) }
            .catch { emit(DayTripState.Failed) }
    }
    val tripState by tripFlow.collectAsState(initial = DayTripState.Loading)
    val trip = (tripState as? DayTripState.Ready)?.trip
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
        if (tripState == DayTripState.Loading) {
            TripLoadingState("여행 정보를 불러오는 중")
        } else if (tripState == DayTripState.Failed) {
            TripErrorState(
                title = "여행 정보를 읽지 못했습니다",
                description = "기기 저장소를 확인하고 다시 시도해 주세요.",
                onRetry = { tripRefresh++ }
            )
        } else if (trip == null) {
            TripEmptyState(
                title = "여행을 찾을 수 없습니다",
                description = "삭제되었거나 존재하지 않는 여행입니다."
            )
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
            if (selected != null) {
                Text("날짜 선택", style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                ) {
                    visibleScheduleDates(start, end, selected).forEach { date ->
                        TripChoiceChip(
                            label = "${date.monthValue}/${date.dayOfMonth} " +
                                date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.KOREAN),
                            selected = date == selected,
                            onClick = { selectedDate = date.toString() }
                        )
                    }
                }
                if (scheduleUsesDatePicker(start, end)) TripDateField(
                    label = "다른 날짜 선택",
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
                Text(
                    text = "${selected.monthValue}월 ${selected.dayOfMonth}일 · " +
                        selected.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN),
                    style = MaterialTheme.typography.titleLarge,
                    color = p.textPrimary
                )
            }
            if (failedAction) {
                TripErrorState(
                    title = "일정 작업을 처리하지 못했습니다",
                    description = "여행 날짜 범위를 확인하거나 다시 시도해 주세요.",
                    onRetry = { failedAction = false }
                )
            }
            TripPrimaryButton(
                label = "일정 추가",
                onClick = { onAdd(selectedDate) },
                enabled = selectedDate.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
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
                            actionLabel = null
                        )
                    } else {
                        if (timed.isNotEmpty()) {
                            TripSectionHeading(
                                title = "시간 지정 일정",
                                supportingText = "손잡이를 길게 끌거나 더보기 메뉴에서 순서를 변경하세요."
                            )
                        }
                        timed.forEachIndexed { index, item ->
                            ScheduleTimelineRow(
                                isFirst = index == 0,
                                isLast = index == timed.lastIndex,
                                item = item,
                                placeName = item.placeId?.let(placeNames::get),
                                disabled = busy,
                                canMoveUp = index > 0,
                                canMoveDown = index < timed.lastIndex,
                                hasConflict = result.items.any { other ->
                                    other.id != item.id && scheduleOverlaps(
                                        item.startTime, item.endTime,
                                        other.startTime, other.endTime
                                    )
                                },
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
                        if (untimed.isNotEmpty()) {
                            TripSectionHeading(title = "시간 미정")
                        }
                        untimed.forEachIndexed { index, item ->
                            ScheduleRow(
                                item = item,
                                placeName = item.placeId?.let(placeNames::get),
                                disabled = busy,
                                canMoveUp = index > 0,
                                canMoveDown = index < untimed.lastIndex,
                                hasConflict = result.items.any { other ->
                                    other.id != item.id && scheduleOverlaps(
                                        item.startTime, item.endTime,
                                        other.startTime, other.endTime
                                    )
                                },
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
            TripSecondaryButton(
                label = "지도에서 보기",
                onClick = { onMap(selectedDate.ifBlank { "all" }) },
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
private fun ScheduleTimelineRow(
    isFirst: Boolean,
    isLast: Boolean,
    item: ItineraryItemEntity,
    placeName: String?,
    disabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    hasConflict: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit
) {
    val p = TripDesign.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val axisX = 76.dp.toPx()
                val dotY = 24.dp.toPx()
                val lineWidth = 1.dp.toPx()
                if (!isFirst) {
                    drawLine(p.outline, Offset(axisX, 0f), Offset(axisX, dotY), lineWidth)
                }
                if (!isLast) {
                    drawLine(p.outline, Offset(axisX, dotY), Offset(axisX, size.height), lineWidth)
                }
                drawCircle(p.textPrimary, radius = 4.dp.toPx(), center = Offset(axisX, dotY))
            }
            .padding(bottom = if (isLast) 0.dp else TripSpacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.width(64.dp).padding(top = TripSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.xxs)
        ) {
            Text(
                text = requireNotNull(item.startTime),
                style = MaterialTheme.typography.labelLarge,
                color = p.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            item.endTime?.let { end ->
                Text(
                    text = "~ " + end,
                    style = MaterialTheme.typography.labelSmall,
                    color = p.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(24.dp))
        ScheduleRow(
            item = item,
            placeName = placeName,
            disabled = disabled,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            hasConflict = hasConflict,
            onEdit = onEdit,
            onDuplicate = onDuplicate,
            onDelete = onDelete,
            onMove = onMove,
            modifier = Modifier.weight(1f),
            showTimeInCard = false
        )
    }
}

@Composable
private fun ScheduleRow(
    item: ItineraryItemEntity,
    placeName: String?,
    disabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    hasConflict: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showTimeInCard: Boolean = true
) {
    val p = TripDesign.palette
    var dragY by remember(item.id) { mutableFloatStateOf(0f) }
    var heightPx by remember(item.id) { mutableIntStateOf(1) }
    var showMenu by remember(item.id) { mutableStateOf(false) }
    val dragHandle = Modifier
        .width(TripSizes.minimumTouchTarget)
        .heightIn(min = TripSizes.minimumTouchTarget)
        .semantics { contentDescription = "${item.title} 순서 이동 핸들" }
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
        modifier = modifier.fillMaxWidth()
            .onSizeChanged { heightPx = it.height }
            .offset { IntOffset(0, dragY.roundToInt()) }
            .zIndex(if (dragY == 0f) 0f else 1f)
    ) {
        Column(
            modifier = Modifier.padding(TripSpacing.md),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)
            ) {
                Text("≡", modifier = dragHandle, style = MaterialTheme.typography.headlineSmall, color = p.textSecondary)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xxs)
                ) {
                    if (showTimeInCard) {
                        Text(
                            item.startTime?.let { it + " ~ " + item.endTime } ?: "시간 미정",
                            style = MaterialTheme.typography.labelLarge,
                            color = p.textPrimary
                        )
                    }
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = p.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (placeName != null) {
                        Text(
                            placeName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = p.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (item.note.isNotBlank()) {
                        Text(
                            item.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        enabled = !disabled,
                        modifier = Modifier
                            .size(TripSizes.minimumTouchTarget)
                            .semantics { contentDescription = "${item.title} 일정 메뉴" }
                    ) {
                        Icon(imageVector = TripIcons.MoreVert, contentDescription = null, tint = p.textPrimary)
                    }
                    DropdownMenu(
                        expanded = showMenu && !disabled,
                        onDismissRequest = { showMenu = false },
                        containerColor = p.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = BorderStroke(1.dp, p.outline)
                    ) {
                        DropdownMenuItem(
                            text = { Text("수정") },
                            onClick = { showMenu = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("복제") },
                            onClick = { showMenu = false; onDuplicate() }
                        )
                        DropdownMenuItem(
                            text = { Text("위로") },
                            enabled = canMoveUp,
                            onClick = { showMenu = false; onMove(-1) }
                        )
                        DropdownMenuItem(
                            text = { Text("아래로") },
                            enabled = canMoveDown,
                            onClick = { showMenu = false; onMove(1) }
                        )
                        DropdownMenuItem(
                            text = { Text("삭제", color = p.error) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
            ) {
                if (hasConflict) TripStatusTag("시간 겹침", TripStatusTone.Warning)
                if (item.important) TripStatusTag("중요")
                if (item.confirmed) TripStatusTag("확정")
            }
        }
    }
}

internal fun visibleScheduleDates(
    start: LocalDate,
    end: LocalDate,
    selected: LocalDate
): List<LocalDate> {
    if (start.isAfter(end) || selected.isBefore(start) || selected.isAfter(end)) {
        return emptyList()
    }
    if (!scheduleUsesDatePicker(start, end)) {
        return (0L..(end.toEpochDay() - start.toEpochDay()))
            .map(start::plusDays)
    }
    return (-2L..2L)
        .map { selected.plusDays(it) }
        .filter { !it.isBefore(start) && !it.isAfter(end) }
}

internal fun scheduleUsesDatePicker(start: LocalDate, end: LocalDate): Boolean =
    !start.isAfter(end) && start.plusDays(4).isBefore(end)
