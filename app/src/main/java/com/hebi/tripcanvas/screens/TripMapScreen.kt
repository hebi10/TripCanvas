package com.hebi.tripcanvas.screens

import android.os.Bundle
import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hebi.tripcanvas.data.ItineraryRepository
import com.hebi.tripcanvas.data.PlaceRepository
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.PlaceEntity
import com.hebi.tripcanvas.designsystem.components.TripChoiceChip
import com.hebi.tripcanvas.designsystem.components.TripEmptyState
import com.hebi.tripcanvas.designsystem.components.TripPrimaryButton
import com.hebi.tripcanvas.designsystem.components.TripSecondaryButton
import com.hebi.tripcanvas.designsystem.components.TripSectionHeading
import com.hebi.tripcanvas.designsystem.components.TripSurfacePanel
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import com.hebi.tripcanvas.ui.TripPageFrame
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import androidx.compose.runtime.rememberUpdatedState
import com.google.gson.JsonObject
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private data class PlacePin(val place: PlaceEntity, val label: String)

private const val PIN_SOURCE = "tripcanvas-place-source"
private const val PIN_CONFIRMED = "tripcanvas-place-confirmed"
private const val PIN_CANDIDATE = "tripcanvas-place-candidate"
private const val PIN_SAVED = "tripcanvas-place-saved"
private val PIN_LAYERS = arrayOf(PIN_CONFIRMED, PIN_CANDIDATE, PIN_SAVED)

@Composable
fun TripMapScreen(
    tripId: String,
    initialDate: String,
    initialPlaceId: String,
    trips: TripRepository,
    places: PlaceRepository,
    itinerary: ItineraryRepository,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onPlace: (String) -> Unit,
    onSchedule: (String) -> Unit
) {
    val p = TripDesign.palette
    val trip by remember(trips, tripId) { trips.observeTrip(tripId) }
        .collectAsState(initial = null)
    val savedPlaces by remember(places, tripId) { places.observePlaces(tripId) }
        .collectAsState(initial = emptyList())
    val schedules by remember(itinerary, tripId) { itinerary.observeTrip(tripId) }
        .collectAsState(initial = emptyList())
    var selectedDate by rememberSaveable(tripId) { mutableStateOf(initialDate) }
    var selectedId by rememberSaveable(tripId) {
        mutableStateOf(initialPlaceId.takeUnless { it == "all" }.orEmpty())
    }
    val dates = schedules.map { it.date }.distinct().sorted()
    val dateItems = schedules.filter { selectedDate == "all" || it.date == selectedDate }
    val selectedPlaces = dateItems.mapNotNull { it.placeId }.toSet()
    val visiblePlaces = if (selectedDate == "all") savedPlaces
        else savedPlaces.filter { it.id in selectedPlaces }
    val pins = visiblePlaces.filter {
        it.latitude != null && it.longitude != null
    }.map { place ->
        val linked = dateItems.filter { it.placeId == place.id }
        val label = when {
            linked.any { it.confirmed } -> "확정"
            linked.isNotEmpty() -> "후보"
            else -> "저장"
        }
        PlacePin(place, label)
    }
    val focus = visiblePlaces.firstOrNull { it.id == selectedId }
        ?: visiblePlaces.firstOrNull()
    LaunchedEffect(visiblePlaces) {
        if (focus == null) selectedId = ""
    }

    TripPageFrame(title = "여행 지도", onBack = onBack) {
        if (trip == null) {
            TripEmptyState("여행 정보를 확인하는 중", "잠시 후 다시 확인해 주세요.")
        } else {
            TripSectionHeading(
                title = requireNotNull(trip).title,
                supportingText = "여행에 저장된 장소와 일정의 연결 상태를 지도에서 확인합니다."
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TripSpacing.xs)) {
                TripChoiceChip("전체", selectedDate == "all", onClick = { selectedDate = "all" })
                dates.forEach { date ->
                    TripChoiceChip(
                        label = date,
                        selected = selectedDate == date,
                        onClick = { selectedDate = date }
                    )
                }
            }
            Text(
                "핀 구분: 확정 일정 / 후보 일정 / 저장 장소. 좌표가 없는 장소는 목록만 표시됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary
            )
            if (pins.isEmpty()) {
                TripEmptyState(
                    title = "표시할 지도 핀이 없습니다",
                    description = "좌표가 있는 장소를 추가하거나 날짜를 바꿔 주세요."
                )
            } else {
                TripMapCanvas(pins, selectedId, onSelected = onPlace)
                Text(
                    "지도: OpenFreeMap · © OpenStreetMap contributors. " +
                        "지도 타일은 인터넷 연결이 필요합니다. 오프라인에서는 아래 장소 목록을 확인하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary
                )
            }
            TripPrimaryButton(
                label = "외부 장소 검색",
                modifier = Modifier.fillMaxWidth(),
                onClick = onSearch
            )
            TripSectionHeading("장소 " + visiblePlaces.size + "개", "해당 날짜의 연결 장소만 표시합니다.")
            if (visiblePlaces.isEmpty()) {
                TripEmptyState("연결된 장소가 없습니다", "날짜를 변경하거나 장소를 새로 등록해 주세요.")
            }
            visiblePlaces.forEach { place ->
                val linked = dateItems.filter { it.placeId == place.id }
                TripSurfacePanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(TripSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(TripSpacing.sm)
                    ) {
                        Text(
                            place.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = p.textPrimary
                        )
                        Text(
                            place.address.ifBlank { "주소 미등록" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = p.textSecondary
                        )
                        if (place.latitude == null || place.longitude == null) {
                            Text("좌표 없음 · 지도 핀 제외", color = p.textSecondary)
                        }
                        if (linked.isNotEmpty()) {
                            Text(
                                linked.joinToString(" · ") { if (it.confirmed) "확정" else "후보" } +
                                    " · 일정 " + linked.size + "개",
                                color = p.textSecondary
                            )
                        }
                        TripSecondaryButton(
                            label = "장소 상세",
                            onClick = { onPlace(place.id) }
                        )
                        linked.forEach { item ->
                            TripSecondaryButton(
                                label = "일정에서 보기: " + item.title,
                                onClick = { onSchedule(item.id) }
                            )
                        }
                    }
                }
            }
            Text(
                "이동 시간·경로: 조회 불가. 제공자 응답이 없는 값을 임의로 계산하지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = p.textSecondary
            )
        }
    }
}

@Composable
private fun TripMapCanvas(
    pins: List<PlacePin>,
    selectedId: String,
    onSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null as Bundle?) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { ready ->
                    ready.setStyle(
                        Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
                    ) { map = ready }
                }
            }
        },
        modifier = Modifier.fillMaxWidth().height(340.dp)
    )
    val currentOnSelected by rememberUpdatedState(onSelected)
    DisposableEffect(map) {
        val ready = map
        if (ready == null) {
            onDispose { }
        } else {
            val listener = MapLibreMap.OnMapClickListener { coordinate ->
                val features = ready.queryRenderedFeatures(
                    ready.projection.toScreenLocation(coordinate),
                    PIN_LAYERS
                )
                val placeId = features.firstOrNull()?.getStringProperty("placeId")
                if (placeId.isNullOrBlank()) false
                else {
                    currentOnSelected(placeId)
                    true
                }
            }
            ready.addOnMapClickListener(listener)
            onDispose { ready.removeOnMapClickListener(listener) }
        }
    }
    LaunchedEffect(map, pins, selectedId) {
        val ready = map ?: return@LaunchedEffect
        val style = ready.style ?: return@LaunchedEffect
        val features = pins.map { item ->
            val attributes = JsonObject().apply {
                addProperty("placeId", item.place.id)
                addProperty("status", item.label)
            }
            Feature.fromGeometry(
                Point.fromLngLat(
                    requireNotNull(item.place.longitude),
                    requireNotNull(item.place.latitude)
                ),
                attributes
            )
        }.toTypedArray()
        val collection = FeatureCollection.fromFeatures(features)
        val source = style.getSource(PIN_SOURCE) as? GeoJsonSource
        if (source == null) {
            style.addSource(GeoJsonSource(PIN_SOURCE, collection))
        } else {
            source.setGeoJson(collection)
        }
        listOf(
            Triple(PIN_CONFIRMED, "확정", Color.rgb(44, 112, 73)),
            Triple(PIN_CANDIDATE, "후보", Color.rgb(153, 107, 36)),
            Triple(PIN_SAVED, "저장", Color.rgb(45, 45, 45))
        ).forEach { (layerId, status, color) ->
            if (style.getLayer(layerId) == null) {
                style.addLayer(
                    CircleLayer(layerId, PIN_SOURCE)
                        .withFilter(
                            Expression.eq(
                                Expression.get("status"),
                                Expression.literal(status)
                            )
                        )
                        .withProperties(
                            PropertyFactory.circleColor(color),
                            PropertyFactory.circleRadius(11f),
                            PropertyFactory.circleStrokeWidth(2.5f),
                            PropertyFactory.circleStrokeColor(Color.WHITE)
                        )
                )
            }
        }
        val target = pins.firstOrNull { it.place.id == selectedId }
            ?: pins.firstOrNull()
        if (target != null) {
            ready.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(
                        requireNotNull(target.place.latitude),
                        requireNotNull(target.place.longitude)
                    ),
                    12.0
                )
            )
        }
    }
}
