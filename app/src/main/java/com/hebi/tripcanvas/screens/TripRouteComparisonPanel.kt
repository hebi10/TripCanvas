package com.hebi.tripcanvas.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.PlaceEntity
import com.hebi.tripcanvas.data.route.ConfiguredTripRouteProvider
import com.hebi.tripcanvas.data.route.isFreshRouteEstimate
import com.hebi.tripcanvas.designsystem.components.TripChoiceChip
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.domain.TripRouteAvailability
import com.hebi.tripcanvas.domain.TripRouteEstimate
import com.hebi.tripcanvas.domain.TripRouteLeg
import com.hebi.tripcanvas.domain.TripRouteMode
import com.hebi.tripcanvas.domain.tripRouteLegsForDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun TripRouteComparisonPanel(
    selectedDate: String,
    schedules: List<ItineraryItemEntity>,
    savedPlaces: List<PlaceEntity>
) {
    val palette = TripDesign.palette
    val legs = tripRouteLegsForDate(schedules, savedPlaces, selectedDate)
    val places = remember(savedPlaces) { savedPlaces.associateBy { it.id } }
    val provider = remember { ConfiguredTripRouteProvider.fromBuildConfig() }
    val scope = rememberCoroutineScope()
    var selectedMode by rememberSaveable { mutableStateOf(TripRouteMode.DRIVING.name) }
    val mode = TripRouteMode.valueOf(selectedMode)
    val results = remember(selectedDate, mode, legs, savedPlaces) {
        mutableStateMapOf<Pair<String, String>, TripRouteEstimate>()
    }
    var selectedLeg by remember(selectedDate, mode, legs, savedPlaces) { mutableStateOf<TripRouteLeg?>(null) }
    var requesting by remember(selectedDate, mode, legs, savedPlaces) { mutableStateOf<Pair<String, String>?>(null) }
    var failed by remember(selectedDate, mode, legs, savedPlaces) { mutableStateOf<Pair<String, String>?>(null) }

    // Expiry only updates local state. Never send a network request on a timer.
    LaunchedEffect(results, results.isNotEmpty()) {
        while (results.isNotEmpty()) {
            val now = System.currentTimeMillis()
            results.entries.filterNot { isFreshRouteEstimate(it.value, now) }
                .map { it.key }.forEach { results.remove(it) }
            if (results.isNotEmpty()) delay(15_000L)
        }
    }

    fun legKey(leg: TripRouteLeg) = leg.fromPlaceId to leg.toPlaceId

    selectedLeg?.let { leg ->
        AlertDialog(
            onDismissRequest = { selectedLeg = null },
            title = { Text("경로 조회 동의") },
            text = {
                Text(
                    "선택한 두 장소의 위경도와 이동수단을 경로 서버에 전송합니다. " +
                        "일정·예약·개인정보는 전송하지 않으며 여행 순서를 변경하지 않습니다. " +
                        "이 경로를 조회하시겠습니까?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedLeg = null
                    val key = legKey(leg)
                    val from = places[leg.fromPlaceId]
                    val to = places[leg.toPlaceId]
                    if (provider != null && from != null && to != null) {
                        requesting = key
                        failed = null
                        results.remove(key)
                        scope.launch {
                            try {
                                val result = provider.estimate(from, to, mode)
                                if (result == null || !isFreshRouteEstimate(result, System.currentTimeMillis())) failed = key
                                else results[key] = result
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                failed = key
                            } finally {
                                requesting = null
                            }
                        }
                    }
                }) { Text("동의하고 조회") }
            },
            dismissButton = {
                TextButton(onClick = { selectedLeg = null }) { Text("취소") }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)) {
        TripSectionHeading(
            title = "이동 경로 비교",
            supportingText = "저장된 일정 순서대로 구간을 확인합니다. 경로 조회는 선택한 구간에서만 수행합니다."
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
                val key = legKey(leg)
                val estimate = results[key]
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
                            when {
                                estimate != null -> formatRouteEstimate(estimate)
                                leg.availability == TripRouteAvailability.COORDINATES_MISSING ->
                                    "좌표 없는 장소 포함 · 거리·예상 시간 조회 불가"
                                provider == null ->
                                    mode.label + " 경로 제공자 연결 전 · 거리·예상 시간 조회 불가"
                                requesting == key -> "실제 이동 경로를 확인하는 중입니다."
                                failed == key -> "경로 조회 불가 · 네트워크·지원 교통수단·요청 한도를 확인해 주세요."
                                else -> "조회 전 · 실제 공급자가 제공한 값만 표시합니다."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                        if (provider != null &&
                            leg.availability != TripRouteAvailability.COORDINATES_MISSING
                        ) {
                            TripPrimaryButton(
                                label = if (requesting == key) "조회 중" else "경로 조회",
                                modifier = Modifier.fillMaxWidth(),
                                enabled = requesting == null,
                                onClick = { if (requesting == null) selectedLeg = leg }
                            )
                        }
                    }
                }
            }
        }
        Text(
            "실제 경로 공급자가 확인되지 않으면 거리·시간을 추정해서 표시하지 않습니다. " +
                "조회 결과는 일시적으로만 보관하며 기존 일정의 순서는 바뀌지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary
        )
    }
}

private fun formatRouteEstimate(estimate: TripRouteEstimate): String {
    val distance = if (estimate.distanceMeters >= 1000L) {
        String.format(Locale.KOREA, "%.1f km", estimate.distanceMeters / 1000.0)
    } else {
        estimate.distanceMeters.toString() + " m"
    }
    val minutes = estimate.durationSeconds / 60L + if (estimate.durationSeconds % 60L == 0L) 0L else 1L
    val fetched = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(estimate.queriedAtMillis))
    return distance + " · 약 " + minutes + "분 · " + estimate.provider +
        " · 조회 " + fetched
}
