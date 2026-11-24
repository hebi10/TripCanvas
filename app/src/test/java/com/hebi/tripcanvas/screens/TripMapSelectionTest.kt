package com.hebi.tripcanvas.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class TripMapSelectionTest {
    @Test fun selectedPlaceIsPreservedWithinCurrentDateFilter() {
        assertEquals(
            "place-airport",
            retainedMapPlaceSelection("place-airport", listOf("place-airport", "place-hotel"))
        )
    }

    @Test fun selectionClearsWhenDateFilterHidesPlace() {
        assertEquals(
            "",
            retainedMapPlaceSelection("place-airport", listOf("place-hotel"))
        )
        assertEquals("", retainedMapPlaceSelection("place-airport", emptyList()))
    }

    @Test fun unselectedStateDoesNotPickAnUnrequestedPlace() {
        assertEquals("", retainedMapPlaceSelection("", listOf("place-airport")))
        assertEquals("", retainedMapPlaceSelection("place-missing", listOf("place-airport")))
    }

    @Test fun selectionSwitchesBetweenVisiblePlacesWithoutChangingTheirIdentity() {
        val visible = listOf("place-airport", "place-hotel")
        assertEquals("place-hotel", retainedMapPlaceSelection("place-hotel", visible))
        assertEquals("place-airport", retainedMapPlaceSelection("place-airport", visible))
    }
}
