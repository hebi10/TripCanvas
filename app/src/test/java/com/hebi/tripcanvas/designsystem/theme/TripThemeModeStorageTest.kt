package com.hebi.tripcanvas.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class TripThemeModeStorageTest {
    @Test
    fun storedEnumNamesRoundTrip() {
        TripThemeMode.entries.forEach { themeMode ->
            assertEquals(themeMode, TripThemeMode.fromStoredValue(themeMode.name))
        }
    }

    @Test
    fun missingOrInvalidModeFallsBackToSystem() {
        assertEquals(TripThemeMode.System, TripThemeMode.fromStoredValue(null))
        assertEquals(TripThemeMode.System, TripThemeMode.fromStoredValue("LIGHT"))
    }
}
