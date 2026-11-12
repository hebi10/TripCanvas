package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.PlaceEntity
import com.hebi.tripcanvas.designsystem.components.TripChoiceChip
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.domain.TripRouteAvailability
import com.hebi.tripcanvas.domain.TripRouteMode
import com.hebi.tripcanvas.domain.tripRouteLegsForDate

@Composable
internal fun TripRouteComparisonPanel(
    selectedDate: String,
    schedules: List<ItineraryItemEntity>,
    savedPlaces: List<PlaceEntity>
) {
    val palette = TripDesign.palette
    val legs = tripRouteLegsForDate(schedules, savedPlaces, selectedDate)
    var selectedMode by rememberSaveable { mutableStateOf(TripRouteMode.DRIVING.name) }
    val mode = TripRouteMode.valueOf(selectedMode)

    Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)) {
        TripSectionHeading(
            title = "이동 경로 비교",
            supportingText = "선택한 날짜의 저장된 일정 순서를 기준으로 이동 구간을 확인합니다."
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
        ) {
            TripRouteMode.entries.forEach { option ->
                TripChoiceChip(
                    label = option.label,
                    selected = mode == option,
                    onClick = { selectedMode = option.name }
                )
            }
        }
        Text(
            text = "선택 교통수단: " + mode.label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.textSecondary
        )
        when {
            selectedDate == "all" -> Text(
                "이동을 비교할 여행 날짜를 선택해 주세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
            legs.isEmpty() -> Text(
                "비교할 연결 장소가 2곳 이상 필요합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
            else -> legs.forEachIndexed { index, leg ->
                TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(TripSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(TripSpacing.xs)
                    ) {
                        Text(
                            "이동 구간 " + (index + 1),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textSecondary
                        )
                        Text(
                            leg.fromName + " → " + leg.toName,
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary
                        )
                        Text(
                            when (leg.availability) {
                                TripRouteAvailability.COORDINATES_MISSING ->
                                    "좌표 없는 장소 포함 · 거리·예상 시간 조회 불가"
                                TripRouteAvailability.PROVIDER_NOT_CONFIGURED ->
                                    mode.label + " 경로 제공자 연결 전 · 거리·예상 시간 조회 불가"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                }
            }
        }
        Text(
            "경로 제공자가 검증되기 전에는 이동 거리·시간을 추정해서 표시하지 않습니다. " +
                "표시 순서는 원본 일정에 영향을 주지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary
        )
    }
}
