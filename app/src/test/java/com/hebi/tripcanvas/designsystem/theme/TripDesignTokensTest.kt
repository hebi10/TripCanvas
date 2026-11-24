package com.hebi.tripcanvas.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hebi.tripcanvas.designsystem.tokens.TripSizes
import com.hebi.tripcanvas.designsystem.tokens.TripSpacing
import org.junit.Assert.assertEquals
import org.junit.Test

class TripDesignTokensTest {
    @Test
    fun lightAndDarkPalettesUseApprovedNeutralOutlines() {
        assertEquals(Color(0xFFD2D2D2), LightTripPalette.outline)
        assertEquals(Color(0xFF555555), DarkTripPalette.outline)
        assertEquals(Color(0xFFFFFFFF), LightTripPalette.background)
        assertEquals(Color(0xFF111111), DarkTripPalette.background)
        assertEquals(Color(0xFF1F1F1F), LightTripPalette.primaryAction)
        assertEquals(Color(0xFFEFEFEF), DarkTripPalette.primaryAction)
    }

    @Test
    fun spacingAndTouchTargetsRemainStable() {
        assertEquals(2.dp, TripSpacing.xxxs)
        assertEquals(8.dp, TripSpacing.xs)
        assertEquals(16.dp, TripSpacing.md)
        assertEquals(24.dp, TripSpacing.lg)
        assertEquals(48.dp, TripSizes.minimumTouchTarget)
        assertEquals(16.dp, TripSizes.compactGutter)
        assertEquals(32.dp, TripSizes.expandedGutter)
    }
}
