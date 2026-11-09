package com.hebi.tripcanvas.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.hebi.tripcanvas.data.TripPeriodConflictException
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.local.TripEntity
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
import com.hebi.tripcanvas.domain.TripField
import com.hebi.tripcanvas.domain.TripInput
import com.hebi.tripcanvas.domain.defaultTripInput
import com.hebi.tripcanvas.domain.toInput
import com.hebi.tripcanvas.domain.validateTrip
import com.hebi.tripcanvas.ui.TripPageFrame
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val TripInputSaver = listSaver<TripInput, String>(
    save = {
        listOf(it.title, it.startDate, it.endDate, it.country, it.city,
            it.timeZoneId, it.currencyCode, it.purpose, it.note)
    },
    restore = {
        TripInput(
            title = it[0], startDate = it[1], endDate = it[2], country = it[3],
            city = it[4], timeZoneId = it[5], currencyCode = it[6],
            purpose = it[7], note = it[8]
        )
    }
)

private sealed interface TripFormState {
    data object Loading : TripFormState
    data object Failed : TripFormState
    data class Ready(val trip: TripEntity?) : TripFormState
}

@Composable
fun TripFormScreen(
    repository: TripRepository,
    tripId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit
) {
    val defaults = remember(tripId) { defaultTripInput() }
    var input by rememberSaveable(tripId, stateSaver = TripInputSaver) { mutableStateOf(defaults) }
    var hydrated by rememberSaveable(tripId) { mutableStateOf(tripId == null) }
    var errors by remember { mutableStateOf<Map<TripField, String>>(emptyMap()) }
    var saveFailed by remember { mutableStateOf(false) }
    var periodWarning by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val flow = remember(repository, tripId, retry) {
        if (tripId == null) {
            flowOf<TripFormState>(TripFormState.Ready(null))
        } else {
            repository.observeTrip(tripId)
                .map<TripEntity?, TripFormState> { TripFormState.Ready(it) }
                .catch { emit(TripFormState.Failed) }
        }
    }
    val state by flow.collectAsState(initial = TripFormState.Loading)
    val currentTrip = (state as? TripFormState.Ready)?.trip

    LaunchedEffect(tripId, currentTrip?.id) {
        if (tripId != null && currentTrip != null && !hydrated) {
            input = currentTrip.toInput()
            hydrated = true
        }
    }
    val dirty = hydrated && (
        if (tripId == null) input != defaults
        else currentTrip != null && input != currentTrip.toInput()
    )
    val requestBack = {
        if (!saving) {
            if (dirty) showDiscard = true else onBack()
        }
    }
    BackHandler(enabled = dirty || saving) { requestBack() }

    TripPageFrame(
        title = if (tripId == null) "새 여행" else "여행 편집",
        onBack = requestBack
    ) {
        when {
            state == TripFormState.Loading || (tripId != null && !hydrated && currentTrip != null) ->
                TripLoadingState("여행 정보를 불러오는 중")
            state == TripFormState.Failed -> TripErrorState(
                title = "여행 정보를 읽지 못했습니다",
                description = "다시 시도해 주세요.",
                onRetry = { retry++ }
            )
            tripId != null && currentTrip == null -> TripErrorState(
                title = "여행을 찾을 수 없습니다",
                description = "다른 기기 또는 화면에서 삭제된 여행일 수 있습니다."
            )
            else -> {
                TripSectionHeading(
                    title = "여행 기본 정보",
                    supportingText = "여행별 제목·기간·목적지와 현지 시간대, 통화를 저장합니다."
                )
                TripFormFields(
                    input = input,
                    errors = errors,
                    onChange = {
                        input = it
                        errors = emptyMap()
                        saveFailed = false
                        periodWarning = null
                    }
                )
                if (periodWarning != null) {
                    TripErrorState(
                        title = "여행 기간을 변경할 수 없습니다",
                        description = requireNotNull(periodWarning)
                    )
                }
                if (saveFailed) {
                    TripErrorState(
                        title = "저장하지 못했습니다",
                        description = "입력한 내용은 유지됩니다. 저장소를 확인하고 다시 시도해 주세요."
                    )
                }
                TripPrimaryButton(
                    label = if (saving) "저장 중" else if (tripId == null) "여행 만들기" else "변경 사항 저장",
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (!saving) {
                            val validated = validateTrip(input)
                            if (!validated.isValid) {
                                errors = validated.errors
                            } else {
                                saving = true
                                saveFailed = false
                                scope.launch {
                                    try {
                                        val savedId = if (tripId == null) {
                                            repository.create(input)
                                        } else {
                                            repository.update(tripId, input)
                                            tripId
                                        }
                                        onSaved(savedId)
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: TripPeriodConflictException) {
                                        val dates = error.affectedDates.distinct()
                                        periodWarning = "기간 밖 일정 " + error.affectedDates.size +
                                            "개 (" + dates.take(8).joinToString(", ") +
                                            if (dates.size > 8) " 외 " + (dates.size - 8) + "개 날짜). " +
                                                "일정을 기간 안으로 이동하거나 삭제한 뒤 다시 저장해 주세요."
                                            else "). 일정을 기간 안으로 이동하거나 삭제한 뒤 다시 저장해 주세요."
                                    } catch (error: Exception) {
                                        saveFailed = true
                                    } finally {
                                        saving = false
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }
    if (showDiscard) {
        TripConfirmDialog(
            title = "변경 사항을 버리시겠습니까?",
            message = "저장하지 않은 변경 내용은 사라집니다.",
            confirmLabel = "변경 폐기",
            destructive = true,
            onConfirm = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false }
        )
    }
}

@Composable
private fun TripFormFields(
    input: TripInput,
    errors: Map<TripField, String>,
    onChange: (TripInput) -> Unit
) {
    val p = TripDesign.palette
    TripTextField(
        value = input.title,
        onValueChange = { onChange(input.copy(title = it)) },
        label = "여행 제목 *",
        placeholder = "예: 교토 3박 4일",
        supportingText = errors[TripField.Title] ?: "1~80자",
        isError = errors.containsKey(TripField.Title)
    )
    TripDateField(
        label = "시작일 *",
        date = input.startDate,
        error = errors[TripField.StartDate],
        onDateSelected = { onChange(input.copy(startDate = it)) }
    )
    TripDateField(
        label = "종료일 *",
        date = input.endDate,
        error = errors[TripField.EndDate],
        onDateSelected = { onChange(input.copy(endDate = it)) }
    )
    TripTextField(
        value = input.country,
        onValueChange = { onChange(input.copy(country = it)) },
        label = "국가",
        placeholder = "예: 일본",
        supportingText = errors[TripField.Country],
        isError = errors.containsKey(TripField.Country)
    )
    TripTextField(
        value = input.city,
        onValueChange = { onChange(input.copy(city = it)) },
        label = "도시",
        placeholder = "예: 교토",
        supportingText = errors[TripField.City],
        isError = errors.containsKey(TripField.City)
    )
    TripTextField(
        value = input.timeZoneId,
        onValueChange = { onChange(input.copy(timeZoneId = it)) },
        label = "여행 현지 시간대 *",
        supportingText = errors[TripField.TimeZone] ?: "IANA 시간대. 예: Asia/Seoul",
        isError = errors.containsKey(TripField.TimeZone)
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
    ) {
        listOf("Asia/Seoul", "Asia/Tokyo", "Europe/Paris", "America/Los_Angeles").forEach { zone ->
            TripChoiceChip(
                label = zone,
                selected = input.timeZoneId == zone,
                onClick = { onChange(input.copy(timeZoneId = zone)) }
            )
        }
    }
    TripTextField(
        value = input.currencyCode,
        onValueChange = { onChange(input.copy(currencyCode = it)) },
        label = "기본 통화 *",
        supportingText = errors[TripField.Currency] ?: "ISO 4217 코드. 예: KRW",
        isError = errors.containsKey(TripField.Currency)
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
    ) {
        listOf("KRW", "JPY", "USD", "EUR").forEach { currency ->
            TripChoiceChip(
                label = currency,
                selected = input.currencyCode.equals(currency, ignoreCase = true),
                onClick = { onChange(input.copy(currencyCode = currency)) }
            )
        }
    }
    TripTextField(
        value = input.purpose,
        onValueChange = { onChange(input.copy(purpose = it)) },
        label = "여행 목적",
        supportingText = errors[TripField.Purpose],
        isError = errors.containsKey(TripField.Purpose)
    )
    TripTextField(
        value = input.note,
        onValueChange = { onChange(input.copy(note = it)) },
        label = "메모",
        supportingText = errors[TripField.Note] ?: "최대 2,000자",
        isError = errors.containsKey(TripField.Note),
        singleLine = false
    )
    Text(
        "대표 이미지와 일정·경비·준비물 등록은 후속 단계에서 제공됩니다.",
        style = MaterialTheme.typography.bodyMedium,
        color = p.textSecondary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDateField(
    label: String,
    date: String,
    error: String?,
    onDateSelected: (String) -> Unit
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val p = TripDesign.palette
    Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
        TripSecondaryButton(
            label = label + "  " + date,
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodyMedium, color = p.error)
        }
    }
    if (showPicker) {
        val initialMillis = runCatching {
            LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initialMillis
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selectedMillis ->
                        val value = Instant.ofEpochMilli(selectedMillis)
                            .atZone(ZoneOffset.UTC).toLocalDate().toString()
                        onDateSelected(value)
                    }
                    showPicker = false
                }) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("취소") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
