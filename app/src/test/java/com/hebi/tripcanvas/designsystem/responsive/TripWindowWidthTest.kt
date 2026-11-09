package com.hebi.tripcanvas.designsystem.responsive

import org.junit.Assert.assertEquals
import org.junit.Test

class TripWindowWidthTest {
    @Test
    fun compactRangeEndsBefore600() {
        assertEquals(TripWindowWidth.Compact, classifyWindowWidth(0))
        assertEquals(TripWindowWidth.Compact, classifyWindowWidth(599))
    }

    @Test
    fun mediumRangeIs600Through839() {
        assertEquals(TripWindowWidth.Medium, classifyWindowWidth(600))
        assertEquals(TripWindowWidth.Medium, classifyWindowWidth(839))
    }

    @Test
    fun expandedRangeBeginsAt840() {
        assertEquals(TripWindowWidth.Expanded, classifyWindowWidth(840))
        assertEquals(TripWindowWidth.Expanded, classifyWindowWidth(1200))
    }
}
