package com.hebi.tripcanvas.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceRulesTest {
    @Test fun storesManualPlaceWithoutCoordinates() {
        val result = validatePlace(PlaceInput(name = " 서울역 "))
        assertTrue(result.errors.isEmpty())
        assertEquals("서울역", result.normalized?.name)
        assertNull(result.normalized?.latitude)
    }

    @Test fun rejectsUnpairedAndOutOfRangeCoordinates() {
        val unpaired = validatePlace(PlaceInput(name = "역", latitude = "37.5"))
        assertTrue(unpaired.errors.containsKey(PlaceField.Longitude))
        val outOfRange = validatePlace(PlaceInput(name = "역", latitude = "91", longitude = "180.1"))
        assertTrue(outOfRange.errors.containsKey(PlaceField.Latitude))
        assertTrue(outOfRange.errors.containsKey(PlaceField.Longitude))
    }

    @Test fun acceptsValidCoordinatesAndRejectsEmptyName() {
        val valid = validatePlace(PlaceInput(name = "역", latitude = "37.5547", longitude = "126.9706"))
        assertEquals(37.5547, valid.normalized?.latitude!!, 0.0)
        assertFalse(validatePlace(PlaceInput(name = " ")).errors.isEmpty())
    }
}
