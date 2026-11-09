package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hebi.tripcanvas.data.PlaceRepository
import com.hebi.tripcanvas.data.local.PlaceEntity
import com.hebi.tripcanvas.designsystem.components.TripConfirmDialog
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.components.TripTextField
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.ui.TripPageFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private sealed interface PlaceListState {
    data object Loading : PlaceListState
    data object Failed : PlaceListState
    data class Ready(val places: List<PlaceEntity>) : PlaceListState
}

@Composable
fun PlaceListScreen(
    repository: PlaceRepository,
    tripId: String,
    selectionMode: Boolean,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    newlyCreatedPlaceId: String? = null
) {
    val p = TripDesign.palette
    val scope = rememberCoroutineScope()
    var search by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PlaceEntity?>(null) }
    var linked by remember(deleteTarget?.id) { mutableStateOf<Int?>(null) }
    val flow = remember(repository, tripId, refresh) {
        repository.observePlaces(tripId)
            .map<List<PlaceEntity>, PlaceListState> { PlaceListState.Ready(it) }
            .catch { emit(PlaceListState.Failed) }
    }
    val state by flow.collectAsState(initial = PlaceListState.Loading)

    LaunchedEffect(newlyCreatedPlaceId) {
        if (selectionMode && !newlyCreatedPlaceId.isNullOrBlank()) onSelect(newlyCreatedPlaceId)
    }
    LaunchedEffect(deleteTarget?.id) {
        linked = null
        val target = deleteTarget
        if (target != null) {
            linked = try {
                repository.linkedCount(tripId, target.id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                deleteFailed = true
                null
            }
        }
    }

    TripPageFrame(
        title = if (selectionMode) "일정 장소 선택" else "저장 장소",
        onBack = onBack
    ) {
        TripSectionHeading(
            title = if (selectionMode) "장소 연결" else "여행 장소",
            supportingText = "이 여행에 저장된 장소만 표시됩니다. 지도 검색 없이도 직접 추가할 수 있습니다."
        )
        TripTextField(
            value = search,
            onValueChange = { search = it },
            label = "장소 검색",
            placeholder = "이름·주소·메모"
        )
        when (val result = state) {
            PlaceListState.Loading -> TripLoadingState("장소를 불러오는 중")
            PlaceListState.Failed -> TripErrorState(
                title = "장소 목록을 불러오지 못했습니다",
                description = "다시 시도해 주세요.",
                onRetry = { refresh++ }
            )
            is PlaceListState.Ready -> {
                val filtered = result.places.filter {
                    search.isBlank() ||
                        it.name.contains(search, ignoreCase = true) ||
                        it.address.contains(search, ignoreCase = true) ||
                        it.note.contains(search, ignoreCase = true)
                }
                if (filtered.isEmpty()) {
                    TripEmptyState(
                        title = if (search.isBlank()) "저장한 장소가 없습니다" else "검색 결과가 없습니다",
                        description = "새 장소를 직접 등록할 수 있습니다."
                    )
                }
                filtered.forEach { place ->
                    TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(TripSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
                        ) {
                            Text(place.name, style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                            if (place.address.isNotBlank()) {
                                Text(place.address, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
                            }
                            if (place.note.isNotBlank()) {
                                Text(place.note, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                                if (selectionMode) {
                                    TripSecondaryButton(label = "선택", onClick = { onSelect(place.id) })
                                }
                                TripSecondaryButton(label = "편집", onClick = { onEdit(place.id) })
                                TripSecondaryButton(label = "삭제", onClick = {
                                    deleteFailed = false
                                    deleteTarget = place
                                })
                            }
                        }
                    }
                }
            }
        }
        TripPrimaryButton(
            label = "새 장소 만들기",
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth()
        )
    }
    deleteTarget?.let { target ->
        TripConfirmDialog(
            title = "장소 삭제",
            message = if (deleteFailed) {
                "연결 정보를 확인하거나 장소를 삭제하지 못했습니다. 취소 후 다시 시도해 주세요."
            } else if (linked == null) {
                "연결된 일정을 확인하는 중입니다."
            } else {
                target.name + " 장소를 삭제하시겠습니까? " +
                    "연결된 일정 " + linked + "개는 유지되고 장소 연결만 해제됩니다."
            },
            confirmLabel = if (deleting) "삭제 중" else "장소 삭제",
            destructive = true,
            onDismiss = {
                if (!deleting) {
                    deleteTarget = null
                    deleteFailed = false
                }
            },
            onConfirm = {
                if (!deleting && linked != null) {
                    deleting = true
                    scope.launch {
                        try {
                            repository.delete(tripId, target.id)
                            deleteTarget = null
                            deleteFailed = false
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            deleteFailed = true
                        } finally {
                            deleting = false
                        }
                    }
                }
            }
        )
    }
}
