package com.hebi.tripcanvas.domain

import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRulesTest {
    private val trip = TripEntity(
        id = "trip-a", title = "도쿄", startDate = "2026-11-10", endDate = "2026-11-13",
        country = "일본", city = "도쿄", timeZoneId = "Asia/Tokyo",
        currencyCode = "JPY", purpose = "", note = "",
        createdAtMillis = 1L, updatedAtMillis = 1L
    )
    private val valid = ScheduleInput(
        title = " 관광 ", date = "2026-11-11", startTime = "09:00", endTime = "10:30"
    )

    @Test fun normalizesTitleAndTimes() {
        val result = validateSchedule(valid, trip)
        assertTrue(result.errors.isEmpty())
        assertEquals("관광", result.normalized?.title)
        assertEquals("09:00", result.normalized?.startTime)
    }

    @Test fun allowsBothUnspecifiedTimes() {
        val result = validateSchedule(valid.copy(startTime = "", endTime = ""), trip)
        assertTrue(result.errors.isEmpty())
        assertEquals(null, result.normalized?.startTime)
        assertEquals(null, result.normalized?.endTime)
    }

    @Test fun blocksInvalidDateAndEndBeforeStart() {
        val result = validateSchedule(
            valid.copy(date = "2026-11-15", endTime = "08:59"), trip
        )
        assertTrue(result.errors.containsKey(ScheduleField.Date))
        assertTrue(result.errors.containsKey(ScheduleField.EndTime))
    }

    @Test fun blocksPartialOrMalformedTimes() {
        val result = validateSchedule(valid.copy(endTime = ""), trip)
        assertTrue(result.errors.containsKey(ScheduleField.EndTime))
        assertFalse(validateSchedule(valid.copy(startTime = "25:00"), trip).errors.isEmpty())
    }

    @Test fun overlappingTimesWarnButAdjacentTimesDoNot() {
        val candidate = requireNotNull(validateSchedule(valid, trip).normalized)
        val existing = listOf(
            item("one", "2026-11-11", "09:30", "10:00"),
            item("two", "2026-11-11", "10:30", "11:30"),
            item("three", "2026-11-12", "09:30", "10:00")
        )
        assertEquals(listOf("one"), overlappingItems(candidate, existing).map { it.id })
        assertTrue(overlappingItems(candidate, existing, exceptId = "one").isEmpty())
        assertFalse(scheduleOverlaps("09:00", "10:00", "10:00", "11:00"))
    }

    private fun item(id: String, date: String, start: String, end: String) =
        ItineraryItemEntity(
            id = id, tripId = trip.id, date = date, title = id, startTime = start,
            endTime = end, placeId = null, note = "", important = false,
            confirmed = false, sortOrder = 0, createdAtMillis = 1L, updatedAtMillis = 1L
        )
}
