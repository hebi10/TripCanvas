package com.hebi.tripcanvas.domain

import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceSummaryRulesTest {
    private fun schedule(
        id: String,
        date: String,
        startTime: String?,
        sortOrder: Int = 0
    ) = ItineraryItemEntity(
        id = id, tripId = "qa-trip", date = date, title = id,
        startTime = startTime, endTime = null, placeId = null, note = "",
        important = false, confirmed = true, sortOrder = sortOrder,
        createdAtMillis = 0L, updatedAtMillis = 0L
    )

    @Test fun upcomingScheduleUsesTripTimezone() {
        val instant = Instant.parse("2026-11-09T16:00:00Z")
        val items = listOf(
            schedule("previous-day", "2026-11-09", "23:30"),
            schedule("today", "2026-11-10", "10:00")
        )
        assertEquals("today", findNextItineraryItem(items, "Asia/Seoul", instant)?.id)
        assertEquals("previous-day", findNextItineraryItem(items, "America/Los_Angeles", instant)?.id)
    }

    @Test fun pastTimeIsSkippedButTodayUntimedScheduleIsRetained() {
        val now = Instant.parse("2026-11-10T03:00:00Z")
        val items = listOf(
            schedule("past", "2026-11-10", "11:59"),
            schedule("tomorrow", "2026-11-11", "09:00"),
            schedule("untimed-today", "2026-11-10", null)
        )
        assertEquals("untimed-today", findNextItineraryItem(items, "Asia/Seoul", now)?.id)
        assertEquals(
            "tomorrow",
            findNextItineraryItem(items.filterNot { it.id == "untimed-today" }, "Asia/Seoul", now)?.id
        )
    }

    @Test fun sortsByDateTimeAndSavedOrder() {
        val now = Instant.parse("2026-11-09T15:00:00Z")
        val items = listOf(
            schedule("late", "2026-11-11", "09:00"),
            schedule("second", "2026-11-10", "09:00", 2),
            schedule("first", "2026-11-10", "09:00", 1)
        )
        assertEquals("first", findNextItineraryItem(items, "Asia/Seoul", now)?.id)
    }

    @Test fun returnsNullWhenNoFutureSchedule() {
        assertNull(findNextItineraryItem(
            listOf(schedule("past", "2026-11-09", "12:00")),
            "Asia/Seoul",
            Instant.parse("2026-11-10T01:00:00Z")
        ))
        assertNull(findNextItineraryItem(emptyList(), "Asia/Seoul"))
    }
}
