package com.hebi.tripcanvas.domain

import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.PlaceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripRouteComparisonTest {
    private val airport = place("airport", "공항", 33.5104, 126.4914)
    private val cityHall = place("cityhall", "제주도청", 33.4888, 126.4975)
    private val withoutCoordinates = place("museum", "좌표 없는 전시관", null, null)
    private val places = listOf(airport, cityHall, withoutCoordinates)

    @Test fun onlyTheExplicitDateIsUsedAndOrderComesFromSavedSortOrder() {
        val items = listOf(
            itinerary("late", "2026-11-11", "cityhall", 20),
            itinerary("early", "2026-11-11", "airport", 0),
            itinerary("tomorrow", "2026-11-12", "museum", 5)
        )
        val result = tripRouteLegsForDate(items, places, "2026-11-11")
        assertEquals(1, result.size)
        assertEquals("airport", result.single().fromPlaceId)
        assertEquals("cityhall", result.single().toPlaceId)
        assertEquals(TripRouteAvailability.PROVIDER_NOT_CONFIGURED, result.single().availability)
    }

    @Test fun samePlaceInConsecutiveSchedulesIsNotAnExtraLeg() {
        val items = listOf(
            itinerary("a", "2026-11-11", "airport", 0),
            itinerary("b", "2026-11-11", "airport", 1),
            itinerary("c", "2026-11-11", "cityhall", 2)
        )
        assertEquals(1, tripRouteLegsForDate(items, places, "2026-11-11").size)
    }

    @Test fun blankDateAllDateMissingLinksAndSinglePlaceDoNotFabricateRoutes() {
        val items = listOf(
            itinerary("one", "2026-11-11", "airport", 0),
            itinerary("missing", "2026-11-11", "nonexistent", 1)
        )
        assertEquals(emptyList<TripRouteLeg>(), tripRouteLegsForDate(items, places, "all"))
        assertEquals(emptyList<TripRouteLeg>(), tripRouteLegsForDate(items, places, ""))
        assertEquals(emptyList<TripRouteLeg>(), tripRouteLegsForDate(items, places, "2026-11-12"))
        assertEquals(emptyList<TripRouteLeg>(), tripRouteLegsForDate(items, places, "2026-11-11"))
    }

    @Test fun missingCoordinatesRemainUnavailableAcrossValidRouteLegs() {
        val items = listOf(
            itinerary("a", "2026-11-11", "airport", 0),
            itinerary("b", "2026-11-11", "museum", 1)
        )
        val leg = tripRouteLegsForDate(items, places, "2026-11-11").single()
        assertEquals(TripRouteAvailability.COORDINATES_MISSING, leg.availability)
    }

    @Test fun estimatesRequireRealNonNegativeProviderResponseAndTime() {
        assertNull(validatedRouteEstimate(null))
        assertNull(validatedRouteEstimate(TripRouteEstimate(-1L, 20L, "provider", 1L)))
        assertNull(validatedRouteEstimate(TripRouteEstimate(50L, -1L, "provider", 1L)))
        assertNull(validatedRouteEstimate(TripRouteEstimate(50L, 20L, " ", 1L)))
        assertNull(validatedRouteEstimate(TripRouteEstimate(50L, 20L, "provider", 0L)))
        val valid = TripRouteEstimate(2000L, 600L, "verified provider", 10L)
        assertEquals(valid, validatedRouteEstimate(valid))
    }

    private fun place(id: String, name: String, lat: Double?, lon: Double?) =
        PlaceEntity(
            id = id, tripId = "trip", name = name, address = "", note = "",
            latitude = lat, longitude = lon,
            createdAtMillis = 1L, updatedAtMillis = 1L
        )

    private fun itinerary(id: String, date: String, placeId: String?, order: Int) =
        ItineraryItemEntity(
            id = id, tripId = "trip", date = date, title = id,
            startTime = "09:00", endTime = null, placeId = placeId,
            note = "", important = false, confirmed = false,
            sortOrder = order, createdAtMillis = 1L, updatedAtMillis = 1L
        )
}
