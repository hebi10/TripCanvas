package com.hebi.tripcanvas.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import com.hebi.tripcanvas.data.ExpenseRepository
import com.hebi.tripcanvas.data.PlaceRepository
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.local.ExpenseEntity
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
import com.hebi.tripcanvas.domain.ExpenseCategory
import com.hebi.tripcanvas.domain.ExpenseField
import com.hebi.tripcanvas.domain.ExpenseInput
import com.hebi.tripcanvas.domain.toInput
import com.hebi.tripcanvas.domain.validateExpense
import com.hebi.tripcanvas.ui.TripPageFrame
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val ExpenseInputSaver = listSaver<ExpenseInput, String>(
    save = {
        listOf(it.amount, it.currencyCode, it.date, it.time,
            it.category.name, it.note, it.placeId)
    },
    restore = {
        ExpenseInput(
            amount = it[0], currencyCode = it[1],
            date = it[2], time = it[3],
            category = ExpenseCategory.valueOf(it[4]),
            note = it[5], placeId = it[6]
        )
    }
)

private sealed interface ExpenseFormState {
    data object Loading : ExpenseFormState
    data object Failed : ExpenseFormState
    data class Ready(val expense: ExpenseEntity?) : ExpenseFormState
}

@Composable
fun ExpenseFormScreen(
    tripId: String,
    itemId: String?,
    trips: TripRepository,
    repository: ExpenseRepository,
    places: PlaceRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val p = TripDesign.palette
    val scope = rememberCoroutineScope()
    val trip by remember(trips, tripId) { trips.observeTrip(tripId) }
        .collectAsState(initial = null)
    val flow = remember(repository, tripId, itemId) {
        if (itemId == null) flowOf<ExpenseFormState>(ExpenseFormState.Ready(null))
        else repository.observeItem(tripId, itemId)
            .map<ExpenseEntity?, ExpenseFormState> { ExpenseFormState.Ready(it) }
            .catch { emit(ExpenseFormState.Failed) }
    }
    val state by flow.collectAsState(initial = ExpenseFormState.Loading)
    val original = (state as? ExpenseFormState.Ready)?.expense
    val placeList by remember(places, tripId) { places.observePlaces(tripId) }
        .collectAsState(initial = emptyList())

    var input by rememberSaveable(tripId, itemId, stateSaver = ExpenseInputSaver) {
        mutableStateOf(ExpenseInput())
    }
    var hydrated by rememberSaveable(tripId, itemId) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf<Map<ExpenseField, String>>(emptyMap()) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }

    val defaults = remember(trip?.id, trip?.timeZoneId, trip?.currencyCode) {
        trip?.let {
            val local = LocalDateTime.now(ZoneId.of(it.timeZoneId))
            ExpenseInput(
                currencyCode = it.currencyCode,
                date = local.toLocalDate().toString(),
                time = local.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            )
        }
    }
    LaunchedEffect(trip?.id, original?.id, itemId) {
        if (!hydrated && trip != null && (itemId == null || original != null)) {
            input = if (itemId == null) requireNotNull(defaults) else requireNotNull(original).toInput()
            hydrated = true
        }
    }
    val dirty = hydrated && (
        if (itemId == null) input != defaults
        else original != null && input != original.toInput()
    )
    val requestBack = {
        if (!saving) {
            if (dirty) showDiscard = true else onBack()
        }
    }
    BackHandler(enabled = dirty || saving) { requestBack() }

    TripPageFrame(
        title = if (itemId == null) "지출 추가" else "지출 편집",
        onBack = requestBack
    ) {
        when {
            trip == null || state == ExpenseFormState.Loading ->
                TripLoadingState("지출 정보를 불러오는 중")
            state == ExpenseFormState.Failed -> TripErrorState(
                title = "지출 정보를 불러오지 못했습니다",
                description = "다시 열어 주세요."
            )
            itemId != null && original == null -> TripErrorState(
                title = "지출을 찾을 수 없습니다",
                description = "삭제된 항목이거나 다른 여행의 지출입니다."
            )
            !hydrated -> TripLoadingState("지출 정보를 불러오는 중")
            else -> {
                TripSectionHeading(
                    title = "지출 정보",
                    supportingText = "* 표시 항목은 필수입니다. 금액은 통화별 소수 자릿수로 저장됩니다."
                )
                TripTextField(
                    value = input.amount,
                    onValueChange = {
                        input = input.copy(amount = it)
                        errors = errors - ExpenseField.Amount; failed = false
                    },
                    label = "금액 *",
                    placeholder = "예: 12000 또는 12.50",
                    supportingText = errors[ExpenseField.Amount]
                        ?: "0보다 큰 금액 · 쉼표 없이 입력",
                    isError = errors.containsKey(ExpenseField.Amount),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                TripTextField(
                    value = input.currencyCode,
                    onValueChange = {
                        input = input.copy(currencyCode = it)
                        errors = errors - ExpenseField.Currency; failed = false
                    },
                    label = "통화 *",
                    supportingText = errors[ExpenseField.Currency]
                        ?: "ISO 4217 코드 · 기본 통화 " + trip!!.currencyCode,
                    isError = errors.containsKey(ExpenseField.Currency),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                ) {
                    listOf(trip!!.currencyCode, "KRW", "JPY", "USD", "EUR")
                        .distinct().forEach { code ->
                            TripChoiceChip(
                                label = code,
                                selected = input.currencyCode.equals(code, true),
                                onClick = { input = input.copy(currencyCode = code); errors = errors - ExpenseField.Currency }
                            )
                        }
                }
                TripSectionHeading(
                    title = "지출 일시",
                    supportingText = "여행 현지 시각을 그대로 기록합니다. 선결제도 저장할 수 있습니다."
                )
                TripDateField(
                    label = "지출 날짜 *",
                    date = input.date,
                    error = errors[ExpenseField.Date],
                    onDateSelected = { input = input.copy(date = it); errors = errors - ExpenseField.Date }
                )
                TripTextField(
                    value = input.time,
                    onValueChange = { input = input.copy(time = it); errors = errors - ExpenseField.Time },
                    label = "현지 시각 *",
                    placeholder = "14:30",
                    supportingText = errors[ExpenseField.Time] ?: "HH:mm · 여행 전 선결제 날짜도 저장 가능",
                    isError = errors.containsKey(ExpenseField.Time),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )
                TripSectionHeading(
                    title = "분류·장소·메모",
                    supportingText = "분류는 필수, 장소와 메모는 선택 사항입니다."
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                ) {
                    ExpenseCategory.entries.forEach { category ->
                        TripChoiceChip(
                            label = category.label,
                            selected = input.category == category,
                            onClick = { input = input.copy(category = category) }
                        )
                    }
                }
                Text(
                    "장소 연결 · 이 여행에 등록된 장소만 선택할 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                ) {
                    TripChoiceChip(
                        label = "연결 안 함",
                        selected = input.placeId.isBlank(),
                        onClick = { input = input.copy(placeId = "") }
                    )
                    placeList.forEach { place ->
                        TripChoiceChip(
                            label = place.name,
                            selected = input.placeId == place.id,
                            onClick = { input = input.copy(placeId = place.id) }
                        )
                    }
                }
                TripTextField(
                    value = input.note,
                    onValueChange = { input = input.copy(note = it); errors = errors - ExpenseField.Note },
                    label = "메모",
                    supportingText = errors[ExpenseField.Note] ?: "최대 2,000자",
                    isError = errors.containsKey(ExpenseField.Note),
                    singleLine = false
                )
                if (failed) {
                    TripErrorState(
                        title = "지출을 저장하지 못했습니다",
                        description = "입력은 유지됩니다. 저장소 또는 장소 연결을 확인해 주세요."
                    )
                }
                TripPrimaryButton(
                    label = if (saving) "저장 중" else "지출 저장",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    onClick = {
                        if (!saving) {
                            val validated = validateExpense(input)
                            if (validated.errors.isNotEmpty()) {
                                errors = validated.errors
                            } else {
                                saving = true
                                failed = false
                                scope.launch {
                                    try {
                                        repository.save(tripId, input, itemId)
                                        onSaved()
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        failed = true
                                    } finally {
                                        saving = false
                                    }
                                }
                            }
                        }
                    }
                )
                if (itemId != null) {
                    TripSecondaryButton(
                        label = "지출 삭제",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showDelete = true },
                        enabled = !saving
                    )
                }
            }
        }
    }
    if (showDiscard) {
        TripConfirmDialog(
            title = "변경 사항 폐기",
            message = "저장하지 않은 지출 변경 내용을 버리시겠습니까?",
            confirmLabel = "변경 폐기",
            destructive = true,
            onConfirm = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false }
        )
    }
    if (showDelete && itemId != null) {
        TripConfirmDialog(
            title = "지출 삭제",
            message = "지출을 삭제하시겠습니까? 백업이 없다면 복구할 수 없습니다." +
                if (deleteFailed) " 이전 삭제에 실패했습니다." else "",
            confirmLabel = "삭제",
            destructive = true,
            onConfirm = {
                if (!saving) {
                    saving = true
                    scope.launch {
                        try {
                            repository.delete(tripId, itemId)
                            onSaved()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            deleteFailed = true
                        } finally {
                            saving = false
                        }
                    }
                }
            },
            onDismiss = { if (!saving) showDelete = false }
        )
    }
}
