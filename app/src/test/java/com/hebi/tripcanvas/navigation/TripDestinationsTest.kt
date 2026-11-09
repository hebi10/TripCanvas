package com.hebi.tripcanvas.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TripDestinationsTest {
    @Test
    fun appStartsWithWelcomeBeforeFirstCompletion() {
        assertEquals(
            TripDestinations.Welcome,
            TripDestinations.startDestination(onboardingCompleted = false)
        )
    }

    @Test
    fun completedOnboardingStartsAtHome() {
        assertEquals(
            TripDestinations.Home,
            TripDestinations.startDestination(onboardingCompleted = true)
        )
    }

    @Test
    fun allRoutesAreUnique() {
        assertEquals(TripDestinations.all.size, TripDestinations.all.distinct().size)
    }
}
