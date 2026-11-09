package com.hebi.tripcanvas.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hebi.tripcanvas.data.ItineraryRepository
import com.hebi.tripcanvas.data.PlaceRepository
import com.hebi.tripcanvas.data.ScheduleOverlapException
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.designsystem.components.TripChoiceChip
import com.hebi.tripcanvas.designsystem.components.TripConfirmDialog
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripTextField
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.domain.ScheduleField
import com.hebi.tripcanvas.domain.ScheduleInput
import com.hebi.tripcanvas.domain.toInput
import com.hebi.tripcanvas.domain.validateSchedule
import com.hebi.tripcanvas.ui.TripPageFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val ScheduleInputSaver = listSaver<ScheduleInput, String>(
    save = {
        listOf(it.title, it.date, it.startTime, it.endTime, it.placeId,
            it.note, it.important.toString(), it.confirmed.toString())
    },
    restore = {
        ScheduleInput(
            title = it[0], date = it[1], startTime = it[2], endTime = it[3],
            placeId = it[4], note = it[5],
            important = it[6].toBoolean(), confirmed = it[7].toBoolean()
        )
    }
)

private sealed interface ScheduleFormState {
    data object Loading : ScheduleFormState
    data object Failed : ScheduleFormState
    data class Ready(val item: ItineraryItemEntity?) : ScheduleFormState
}

@Composable
fun ScheduleFormScreen(
    tripRepository: TripRepository,
    itineraryRepository: ItineraryRepository,
    placeRepository: PlaceRepository,
    tripId: String,
    itemId: String?,
    initialDate: String,
    selectedPlaceId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onPickPlace: () -> Unit
) {
    val p = TripDesign.palette
    val scope = rememberCoroutineScope()
    val defaults = remember(tripId, initialDate) { ScheduleInput(date = initialDate) }
    var input by rememberSaveable(tripId, itemId, stateSaver = ScheduleInputSaver) {
        mutableStateOf(defaults)
    }
    var hydrated by rememberSaveable(tripId, itemId) { mutableStateOf(itemId == null) }
    var errors by remember { mutableStateOf<Map<ScheduleField, String>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var overlapCount by remember { mutableIntStateOf(0) }
    var showOverlap by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }

    val trip by remember(tripRepository, tripId) {
        tripRepository.observeTrip(tripId)
    }.collectAsState(initial = null)
    val flow = remember(itineraryRepository, tripId, itemId, retry) {
        if (itemId == null) flowOf<ScheduleFormState>(ScheduleFormState.Ready(null))
        else itineraryRepository.observeItem(tripId, itemId)
            .map<ItineraryItemEntity?, ScheduleFormState> { ScheduleFormState.Ready(it) }
            .catch { emit(ScheduleFormState.Failed) }
    }
    val state by flow.collectAsState(initial = ScheduleFormState.Loading)
    val original = (state as? ScheduleFormState.Ready)?.item
    val places by remember(placeRepository, tripId) {
        placeRepository.observePlaces(tripId)
    }.collectAsState(initial = emptyList())

    LaunchedEffect(itemId, original?.id) {
        if (itemId != null && original != null && !hydrated) {
            input = original.toInput()
            hydrated = true
        }
    }
    LaunchedEffect(selectedPlaceId) {
        if (!selectedPlaceId.isNullOrBlank()) {
            input = input.copy(placeId = selectedPlaceId)
        }
    }

    val dirty = hydrated && (if (itemId == null) input != defaults
        else original != null && input != original.toInput())
    val requestBack = {
        if (!saving) {
            if (dirty) showDiscard = true else onBack()
        }
    }
    BackHandler(enabled = dirty || saving) { requestBack() }

    fun performSave(allowOverlap: Boolean) {
        val current = trip ?: return
        val validation = validateSchedule(input, current)
        if (validation.errors.isNotEmpty()) {
            errors = validation.errors
            return
        }
        if (saving) return
        saving = true
        saveFailed = false
        scope.launch {
            try {
                itineraryRepository.save(
                    tripId = tripId, input = input, itemId = itemId, allowOverlap = allowOverlap
                )
                showOverlap = false
                onSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (error: ScheduleOverlapException) {
                overlapCount = error.conflicts
                showOverlap = true
            } catch (error: Exception) {
                saveFailed = true
            } finally {
                saving = false
            }
        }
    }

    TripPageFrame(
        title = if (itemId == null) "일정 추가" else "일정 편집",
        onBack = requestBack
    ) {
        when {
            trip == null || state == ScheduleFormState.Loading ||
                (itemId != null && original != null && !hydrated) ->
                TripLoadingState("일정 정보를 불러오는 중")
            state == ScheduleFormState.Failed -> TripErrorState(
                title = "일정을 불러오지 못했습니다",
                description = "다시 시도해 주세요.",
                onRetry = { retry++ }
            )
            itemId != null && original == null -> TripErrorState(
                title = "일정을 찾을 수 없습니다",
                description = "삭제되었거나 다른 여행의 일정입니다."
            )
            else -> {
                TripSectionHeading(
                    title = "일정 정보",
                    supportingText = "현지 날짜와 시각을 그대로 저장하므로 기기 시간대를 바꿔도 일정이 이동하지 않습니다."
                )
                TripTextField(
                    value = input.title,
                    onValueChange = { input = input.copy(title = it); errors = emptyMap() },
                    label = "일정 제목 *",
                    supportingText = errors[ScheduleField.Title] ?: "1~100자",
                    isError = errors.containsKey(ScheduleField.Title)
                )
                TripDateField(
                    label = "일정 날짜 *",
                    date = input.date,
                    error = errors[ScheduleField.Date],
                    onDateSelected = { input = input.copy(date = it); errors = emptyMap() }
                )
                Text(
                    "여행 기간: " + trip!!.startDate + " ~ " + trip!!.endDate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textSecondary
                )
                TripSectionHeading(
                    title = "시간",
                    supportingText = "24시간제 HH:mm (예: 09:30). 시작과 종료를 모두 비우면 시간 미정입니다."
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                    TripChoiceChip(
                        label = "시간 미정",
                        selected = input.startTime.isEmpty() && input.endTime.isEmpty(),
                        onClick = { input = input.copy(startTime = "", endTime = ""); errors = emptyMap() }
                    )
                }
                TripTextField(
                    value = input.startTime,
                    onValueChange = { input = input.copy(startTime = it); errors = emptyMap() },
                    label = "시작 시각",
                    placeholder = "09:30",
                    supportingText = errors[ScheduleField.StartTime],
                    isError = errors.containsKey(ScheduleField.StartTime)
                )
                TripTextField(
                    value = input.endTime,
                    onValueChange = { input = input.copy(endTime = it); errors = emptyMap() },
                    label = "종료 시각",
                    placeholder = "10:30",
                    supportingText = errors[ScheduleField.EndTime],
                    isError = errors.containsKey(ScheduleField.EndTime)
                )
                TripSectionHeading(title = "장소", supportingText = "기존 장소 연결 또는 수동 등록")
                val selectedPlace = places.firstOrNull { it.id == input.placeId }
                Text(
                    selectedPlace?.name ?: if (input.placeId.isNotBlank()) "선택한 장소를 찾을 수 없습니다" else "장소 미선택",
                    color = if (selectedPlace == null) p.textSecondary else p.textPrimary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                ) {
                    TripSecondaryButton(label = "장소 선택·추가", onClick = onPickPlace)
                    if (input.placeId.isNotBlank()) {
                        TripSecondaryButton(
                            label = "장소 연결 해제",
                            onClick = { input = input.copy(placeId = "") }
                        )
                    }
                }
                TripTextField(
                    value = input.note,
                    onValueChange = { input = input.copy(note = it); errors = emptyMap() },
                    label = "메모",
                    supportingText = errors[ScheduleField.Note] ?: "최대 2,000자",
                    isError = errors.containsKey(ScheduleField.Note),
                    singleLine = false
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                ) {
                    TripChoiceChip(
                        label = "중요 일정",
                        selected = input.important,
                        onClick = { input = input.copy(important = !input.important) }
                    )
                    TripChoiceChip(
                        label = "확정",
                        selected = input.confirmed,
                        onClick = { input = input.copy(confirmed = !input.confirmed) }
                    )
                }
                if (saveFailed) {
                    TripErrorState(
                        title = "일정을 저장하지 못했습니다",
                        description = "연결한 장소가 삭제됐거나 저장소에 오류가 발생했습니다. 입력은 보존됩니다."
                    )
                }
                TripPrimaryButton(
                    label = if (saving) "저장 중" else "일정 저장",
                    onClick = { performSave(allowOverlap = false) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving
                )
            }
        }
    }
    if (showOverlap) {
        TripConfirmDialog(
            title = "겹치는 일정",
            message = "같은 날짜와 시간에 " + overlapCount + "개 일정이 겹칩니다. 그래도 저장하시겠습니까?",
            confirmLabel = "그래도 저장",
            onConfirm = { showOverlap = false; performSave(allowOverlap = true) },
            onDismiss = { showOverlap = false }
        )
    }
    if (showDiscard) {
        TripConfirmDialog(
            title = "변경 사항 폐기",
            message = "저장하지 않은 일정 변경 내용을 버리시겠습니까?",
            confirmLabel = "변경 폐기",
            destructive = true,
            onConfirm = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false }
        )
    }
}
