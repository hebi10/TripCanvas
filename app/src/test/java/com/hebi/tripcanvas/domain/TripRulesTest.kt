package com.hebi.tripcanvas.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRulesTest {
    private val valid = TripInput(
        title = " 서울 여행 ",
        startDate = "2026-11-10",
        endDate = "2026-11-15",
        timeZoneId = "Asia/Seoul",
        currencyCode = "krw"
    )

    @Test fun trimsTitleAndNormalizesCurrency() {
        val result = validateTrip(valid)
        assertTrue(result.isValid)
        assertEquals("서울 여행", result.normalized?.title)
        assertEquals("KRW", result.normalized?.currencyCode)
    }

    @Test fun blocksTitleAndDateErrors() {
        val result = validateTrip(valid.copy(title = " ", endDate = "2026-11-01"))
        assertFalse(result.isValid)
        assertTrue(result.errors.containsKey(TripField.Title))
        assertTrue(result.errors.containsKey(TripField.EndDate))
    }

    @Test fun rejectsInvalidIanaZoneAndIsoCurrency() {
        val result = validateTrip(valid.copy(timeZoneId = "Moon/Sea", currencyCode = "AAAA"))
        assertTrue(result.errors.containsKey(TripField.TimeZone))
        assertTrue(result.errors.containsKey(TripField.Currency))
    }

    @Test fun validatesOptionalFieldsWithoutRequiringThem() {
        assertTrue(validateTrip(valid.copy(country = "", city = "", purpose = "", note = "")).isValid)
        assertTrue(validateTrip(valid.copy(note = "x".repeat(2001))).errors.containsKey(TripField.Note))
    }

    @Test fun datesAreClassifiedInLocalTripTimezone() {
        val instant = Instant.parse("2026-11-09T16:00:00Z")
        assertEquals(TripPhase.Active, tripPhase("2026-11-10", "2026-11-11", "Asia/Seoul", instant))
        assertEquals(TripPhase.Upcoming, tripPhase("2026-11-10", "2026-11-11", "America/Los_Angeles", instant))
        assertEquals(TripPhase.Completed, tripPhase("2026-11-01", "2026-11-02", "Asia/Seoul", instant))
    }
}
