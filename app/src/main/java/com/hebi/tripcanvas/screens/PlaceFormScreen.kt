package com.hebi.tripcanvas.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.hebi.tripcanvas.data.PlaceRepository
import com.hebi.tripcanvas.data.local.PlaceEntity
import com.hebi.tripcanvas.designsystem.components.TripConfirmDialog
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripTextField
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.domain.PlaceField
import com.hebi.tripcanvas.domain.PlaceInput
import com.hebi.tripcanvas.domain.toInput
import com.hebi.tripcanvas.domain.validatePlace
import com.hebi.tripcanvas.ui.TripPageFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val PlaceInputSaver = listSaver<PlaceInput, String>(
    save = { listOf(it.name, it.address, it.note, it.latitude, it.longitude) },
    restore = { PlaceInput(it[0], it[1], it[2], it[3], it[4]) }
)

private sealed interface PlaceFormState {
    data object Loading : PlaceFormState
    data object Failed : PlaceFormState
    data class Ready(val place: PlaceEntity?) : PlaceFormState
}

@Composable
fun PlaceFormScreen(
    repository: PlaceRepository,
    tripId: String,
    placeId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var input by rememberSaveable(tripId, placeId, stateSaver = PlaceInputSaver) {
        mutableStateOf(PlaceInput())
    }
    var hydrated by rememberSaveable(tripId, placeId) { mutableStateOf(placeId == null) }
    var errors by remember { mutableStateOf<Map<PlaceField, String>>(emptyMap()) }
    var saveFailed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val flow = remember(repository, tripId, placeId, refresh) {
        if (placeId == null) flowOf<PlaceFormState>(PlaceFormState.Ready(null))
        else repository.observePlace(tripId, placeId)
            .map<PlaceEntity?, PlaceFormState> { PlaceFormState.Ready(it) }
            .catch { emit(PlaceFormState.Failed) }
    }
    val state by flow.collectAsState(initial = PlaceFormState.Loading)
    val loaded = (state as? PlaceFormState.Ready)?.place
    LaunchedEffect(tripId, placeId, loaded?.id) {
        if (placeId != null && loaded != null && !hydrated) {
            input = loaded.toInput()
            hydrated = true
        }
    }
    val dirty = hydrated && (if (placeId == null) input != PlaceInput()
        else loaded != null && input != loaded.toInput())
    val requestBack = {
        if (!saving) {
            if (dirty) showDiscard = true else onBack()
        }
    }
    BackHandler(enabled = dirty || saving) { requestBack() }

    TripPageFrame(
        title = if (placeId == null) "새 장소" else "장소 편집",
        onBack = requestBack
    ) {
        when {
            state == PlaceFormState.Loading || (placeId != null && loaded != null && !hydrated) ->
                TripLoadingState("장소 정보를 불러오는 중")
            state == PlaceFormState.Failed -> TripErrorState(
                title = "장소를 불러오지 못했습니다",
                description = "다시 시도해 주세요.",
                onRetry = { refresh++ }
            )
            placeId != null && loaded == null -> TripErrorState(
                title = "장소를 찾을 수 없습니다",
                description = "삭제된 장소일 수 있습니다."
            )
            else -> {
                TripSectionHeading(
                    title = "장소 정보",
                    supportingText = "좌표는 선택 사항입니다. 장소 이름만으로 일정에 연결할 수 있습니다."
                )
                TripTextField(
                    value = input.name,
                    onValueChange = { input = input.copy(name = it); errors = emptyMap() },
                    label = "장소명 *",
                    supportingText = errors[PlaceField.Name] ?: "1~100자",
                    isError = errors.containsKey(PlaceField.Name)
                )
                TripTextField(
                    value = input.address,
                    onValueChange = { input = input.copy(address = it); errors = emptyMap() },
                    label = "주소",
                    supportingText = errors[PlaceField.Address],
                    isError = errors.containsKey(PlaceField.Address)
                )
                TripTextField(
                    value = input.note,
                    onValueChange = { input = input.copy(note = it); errors = emptyMap() },
                    label = "메모",
                    supportingText = errors[PlaceField.Note],
                    isError = errors.containsKey(PlaceField.Note),
                    singleLine = false
                )
                Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                    Text("좌표 · 선택 사항")
                    TripTextField(
                        value = input.latitude,
                        onValueChange = { input = input.copy(latitude = it); errors = emptyMap() },
                        label = "위도",
                        placeholder = "예: 37.5547",
                        supportingText = errors[PlaceField.Latitude],
                        isError = errors.containsKey(PlaceField.Latitude)
                    )
                    TripTextField(
                        value = input.longitude,
                        onValueChange = { input = input.copy(longitude = it); errors = emptyMap() },
                        label = "경도",
                        placeholder = "예: 126.9706",
                        supportingText = errors[PlaceField.Longitude],
                        isError = errors.containsKey(PlaceField.Longitude)
                    )
                }
                if (saveFailed) {
                    TripErrorState(
                        title = "장소를 저장하지 못했습니다",
                        description = "입력한 내용은 유지됩니다. 다시 시도해 주세요."
                    )
                }
                TripPrimaryButton(
                    label = if (saving) "저장 중" else if (placeId == null) "장소 만들기" else "변경 사항 저장",
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val validation = validatePlace(input)
                        if (!saving) {
                            if (validation.errors.isNotEmpty()) {
                                errors = validation.errors
                            } else {
                                saving = true
                                saveFailed = false
                                scope.launch {
                                    try {
                                        val id = if (placeId == null) repository.create(tripId, input)
                                            else {
                                                repository.update(tripId, placeId, input)
                                                placeId
                                            }
                                        onSaved(id)
                                    } catch (error: CancellationException) {
                                        throw error
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
            title = "변경 사항 폐기",
            message = "저장하지 않은 장소 변경 내용을 버리시겠습니까?",
            confirmLabel = "변경 폐기",
            destructive = true,
            onConfirm = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false }
        )
    }
}
