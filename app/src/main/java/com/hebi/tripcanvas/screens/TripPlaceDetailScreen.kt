package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hebi.tripcanvas.data.ItineraryRepository
import com.hebi.tripcanvas.data.PlaceRepository
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.ui.TripPageFrame
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TripPlaceDetailScreen(
    tripId: String,
    placeId: String,
    places: PlaceRepository,
    itinerary: ItineraryRepository,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMap: () -> Unit,
    onSchedule: (String) -> Unit
) {
    val palette = TripDesign.palette
    val place by remember(places, tripId, placeId) {
        places.observePlace(tripId, placeId)
    }.collectAsState(initial = null)
    val schedules by remember(itinerary, tripId, placeId) {
        itinerary.observeLinkedPlace(tripId, placeId)
    }.collectAsState(initial = emptyList())

    TripPageFrame(title = "장소 상세", onBack = onBack) {
        if (place == null) {
            TripEmptyState("장소를 찾을 수 없습니다", "삭제됐거나 다른 여행의 장소입니다.")
        } else {
            val current = requireNotNull(place)
            TripSectionHeading(
                title = current.name,
                supportingText = current.address.ifBlank { "주소 미입력" }
            )
            if (current.note.isNotBlank()) Text(current.note, color = palette.textPrimary)
            TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(TripSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
                ) {
                    val coords = if (current.latitude == null || current.longitude == null) {
                        "좌표 없음 · 지도 핀 미표시"
                    } else "위도 " + current.latitude + " · 경도 " + current.longitude
                    Text(coords, color = palette.textSecondary)
                    val source = current.provider
                    if (source != null) {
                        Text(
                            "공급자: " + source,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                        val time = current.sourceFetchedAtMillis?.let {
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                .withZone(ZoneId.of("Asia/Seoul"))
                                .format(Instant.ofEpochMilli(it))
                        }
                        if (time != null) Text("조회: $time KST", color = palette.textSecondary)
                    } else {
                        Text("직접 등록한 장소", color = palette.textSecondary)
                    }
                    TripPrimaryButton(
                        label = "지도에서 보기",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onMap,
                        enabled = current.latitude != null && current.longitude != null
                    )
                    TripSecondaryButton(
                        label = "장소 편집",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onEdit
                    )
                }
            }
            TripSectionHeading("연결된 일정 " + schedules.size + "개")
            if (schedules.isEmpty()) Text("연결된 일정이 없습니다.", color = palette.textSecondary)
            schedules.forEach { item ->
                TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(TripSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
                    ) {
                        Text(
                            item.date + " · " + item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary
                        )
                        Text(if (item.confirmed) "확정" else "후보", color = palette.textSecondary)
                        TripSecondaryButton("일정 편집", onClick = { onSchedule(item.id) })
                    }
                }
            }
        }
    }
}
