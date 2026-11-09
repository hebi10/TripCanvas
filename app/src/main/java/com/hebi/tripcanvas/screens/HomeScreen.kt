package com.hebi.tripcanvas.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.designsystem.components.TripChoiceChip
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripStatusTag
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.domain.TripPhase
import com.hebi.tripcanvas.domain.daysUntil
import com.hebi.tripcanvas.domain.phase
import com.hebi.tripcanvas.ui.TripPageFrame
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private enum class TripListFilter(val label: String, val emptyLabel: String) {
    All("전체", "등록된 여행이 없습니다"),
    Upcoming("예정", "예정된 여행이 없습니다"),
    Active("여행 중", "진행 중인 여행이 없습니다"),
    Completed("완료", "완료된 여행이 없습니다");

    fun includes(phase: TripPhase): Boolean =
        this == All || name == phase.name
}

private sealed interface TripListState {
    data object Loading : TripListState
    data object Failed : TripListState
    data class Ready(val trips: List<TripEntity>) : TripListState
}

@Composable
fun HomeScreen(
    repository: TripRepository,
    onOpenSettings: () -> Unit,
    onCreateTrip: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onEditTrip: (String) -> Unit
) {
    var selectedFilter by rememberSaveable { mutableStateOf(TripListFilter.All) }
    var deleteTarget by remember { mutableStateOf<TripEntity?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val flow = remember(repository, refresh) {
        repository.trips.map<List<TripEntity>, TripListState> { TripListState.Ready(it) }
            .catch { emit(TripListState.Failed) }
    }
    val state by flow.collectAsState(initial = TripListState.Loading)
    val p = TripDesign.palette

    TripPageFrame(
        title = "TripCanvas",
        trailingActions = {
            TextButton(onClick = onOpenSettings) {
                Text("설정", color = p.textPrimary)
            }
        }
    ) {
        TripSectionHeading(
            title = "내 여행",
            supportingText = "여행을 만들고 일정과 경비를 한곳에서 관리하세요."
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
        ) {
            TripListFilter.entries.forEach { filter ->
                TripChoiceChip(
                    label = filter.label,
                    selected = filter == selectedFilter,
                    onClick = { selectedFilter = filter }
                )
            }
        }
        when (val result = state) {
            TripListState.Loading -> TripLoadingState("여행 목록을 불러오는 중")
            TripListState.Failed -> TripErrorState(
                title = "여행 목록을 불러오지 못했습니다",
                description = "기기 저장소를 확인한 뒤 다시 시도해 주세요.",
                onRetry = { refresh++ }
            )
            is TripListState.Ready -> {
                val filtered = result.trips.filter { selectedFilter.includes(it.phase()) }
                if (filtered.isEmpty()) {
                    TripEmptyState(
                        title = selectedFilter.emptyLabel,
                        description = if (selectedFilter == TripListFilter.All) {
                            "첫 여행을 만들어보세요. 여행 정보는 기기에 저장됩니다."
                        } else {
                            "다른 여행 상태를 선택하거나 새 여행을 추가해 보세요."
                        },
                        actionLabel = if (selectedFilter == TripListFilter.All) "새 여행" else null,
                        onAction = onCreateTrip
                    )
                } else {
                    Text(
                        "여행 " + filtered.size + "개",
                        style = MaterialTheme.typography.labelMedium,
                        color = p.textSecondary
                    )
                    filtered.forEach { trip ->
                        TripListCard(
                            trip = trip,
                            onOpen = { onOpenTrip(trip.id) },
                            onEdit = { onEditTrip(trip.id) },
                            onDelete = { deleteTarget = trip }
                        )
                    }
                }
            }
        }
        TripPrimaryButton(
            label = "새 여행 만들기",
            onClick = onCreateTrip,
            modifier = Modifier.fillMaxWidth()
        )
    }
    deleteTarget?.let { trip ->
        TripDeleteDialog(
            trip = trip,
            onDelete = repository::delete,
            onDeleted = { deleteTarget = null },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun TripListCard(
    trip: TripEntity,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val p = TripDesign.palette
    val phase = trip.phase()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        color = p.surface,
        shape = RectangleShape,
        border = BorderStroke(1.dp, p.outline)
    ) {
        Column(
            modifier = Modifier.padding(TripSpacing.md),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(TripSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
            ) {
                Text(trip.title, style = MaterialTheme.typography.titleLarge, color = p.textPrimary)
                TripStatusTag(label = phase.label)
            }
            Text(
                text = trip.startDate + " ~ " + trip.endDate + "  ·  " + trip.timeZoneId,
                style = MaterialTheme.typography.bodyMedium,
                color = p.textSecondary
            )
            val location = listOf(trip.country, trip.city).filter { it.isNotBlank() }.joinToString(" · ")
            if (location.isNotEmpty()) {
                Text(location, style = MaterialTheme.typography.bodyMedium, color = p.textPrimary)
            }
            val timing = when (phase) {
                TripPhase.Upcoming -> "출발 D-" + trip.daysUntil()
                TripPhase.Active -> "여행 진행 중"
                TripPhase.Completed -> "지난 여행"
            }
            Text(timing, style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                TextButton(onClick = onEdit) { Text("편집", color = p.textPrimary) }
                TextButton(onClick = onDelete) { Text("삭제", color = p.error) }
            }
        }
    }
}
