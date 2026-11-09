package com.hebi.tripcanvas.data

import com.hebi.tripcanvas.designsystem.theme.TripThemeMode

data class TripUserPreferences(
    val onboardingCompleted: Boolean = false,
    val themeMode: TripThemeMode = TripThemeMode.System
)
