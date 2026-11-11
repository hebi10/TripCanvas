package com.hebi.tripcanvas.screens

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDayNavigationTest {
    @Test fun dateStripClipsToStartAndEndOfTrip() {
        val start = LocalDate.parse("2026-11-20")
        val end = LocalDate.parse("2026-11-22")
        assertEquals(
            listOf(start, start.plusDays(1), end),
            visibleScheduleDates(start, end, start)
        )
        assertEquals(
            listOf(start, start.plusDays(1), end),
            visibleScheduleDates(start, end, end)
        )
    }

    @Test fun dateStripKeepsFiveDaysAroundSelectionForLongTrip() {
        val start = LocalDate.parse("2026-11-01")
        val end = LocalDate.parse("2026-11-30")
        val selected = LocalDate.parse("2026-11-15")
        assertEquals(
            listOf(-2L, -1L, 0L, 1L, 2L).map(selected::plusDays),
            visibleScheduleDates(start, end, selected)
        )
    }

    @Test fun invalidDateRangeOrSelectionDoesNotEscapeTrip() {
        val start = LocalDate.parse("2026-11-20")
        val end = LocalDate.parse("2026-11-22")
        assertTrue(visibleScheduleDates(end, start, start).isEmpty())
        assertTrue(visibleScheduleDates(start, end, start.minusDays(1)).isEmpty())
        assertTrue(visibleScheduleDates(start, end, end.plusDays(1)).isEmpty())
    }
}
