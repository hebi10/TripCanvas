package com.hebi.tripcanvas.domain

import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.PlaceEntity

enum class TripRouteMode(val label: String) {
    WALKING("도보"),
    DRIVING("차량"),
    CYCLING("자전거"),
    TRANSIT("대중교통")
}

enum class TripRouteAvailability {
    COORDINATES_MISSING,
    PROVIDER_NOT_CONFIGURED
}

data class TripRouteLeg(
    val fromPlaceId: String,
    val fromName: String,
    val toPlaceId: String,
    val toName: String,
    val availability: TripRouteAvailability
)

data class TripRouteEstimate(
    val distanceMeters: Long,
    val durationSeconds: Long,
    val provider: String,
    val queriedAtMillis: Long
)

/**
 * ROUTE-02 will implement this through an approved routing backend only.
 * No demo-server requests and no estimates from straight-line distances.
 */
interface TripRouteProvider {
    suspend fun estimate(
        from: PlaceEntity,
        to: PlaceEntity,
        mode: TripRouteMode
    ): TripRouteEstimate?
}

fun validatedRouteEstimate(estimate: TripRouteEstimate?): TripRouteEstimate? =
    estimate?.takeIf {
        it.distanceMeters >= 0L &&
            it.durationSeconds >= 0L &&
            it.provider.isNotBlank() &&
            it.queriedAtMillis > 0L
    }

private fun PlaceEntity.hasValidCoordinates(): Boolean =
    latitude != null && longitude != null &&
        latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0

fun tripRouteLegsForDate(
    schedules: List<ItineraryItemEntity>,
    savedPlaces: List<PlaceEntity>,
    selectedDate: String
): List<TripRouteLeg> {
    if (selectedDate == "all" || selectedDate.isBlank()) return emptyList()

    val placesById = savedPlaces.associateBy { it.id }
    val stops = schedules.asSequence()
        .filter { it.date == selectedDate }
        .sortedWith(compareBy<ItineraryItemEntity> { it.sortOrder }.thenBy { it.id })
        .mapNotNull { item -> item.placeId?.let(placesById::get) }
        .fold(mutableListOf<PlaceEntity>()) { collected, place ->
            if (collected.lastOrNull()?.id != place.id) collected.add(place)
            collected
        }

    return stops.zipWithNext { from, to ->
        TripRouteLeg(
            fromPlaceId = from.id,
            fromName = from.name,
            toPlaceId = to.id,
            toName = to.name,
            availability = if (!from.hasValidCoordinates() || !to.hasValidCoordinates()) {
                TripRouteAvailability.COORDINATES_MISSING
            } else {
                TripRouteAvailability.PROVIDER_NOT_CONFIGURED
            }
        )
    }
}
