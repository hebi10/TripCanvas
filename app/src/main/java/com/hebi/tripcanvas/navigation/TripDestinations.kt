package com.hebi.tripcanvas.navigation

object TripDestinations {
    const val Welcome = "welcome"
    const val Home = "home"
    const val Settings = "settings"
    const val WorkspacePreview = "workspace-preview"
    const val DesignSystemPreview = "design-system-preview"
    const val WorkspaceRoute = "trip/{tripId}"

    fun startDestination(onboardingCompleted: Boolean): String =
        if (onboardingCompleted) Home else Welcome

    val all = listOf(
        Welcome, Home, Settings, WorkspacePreview, DesignSystemPreview, WorkspaceRoute
    )
}
