package com.hebi.tripcanvas.navigation

object TripDestinations {
    const val Welcome = "welcome"
    const val Home = "home"
    const val Settings = "settings"
    const val WorkspacePreview = "workspace-preview"
    const val DesignSystemPreview = "design-system-preview"
    const val CreateTrip = "trip/new"
    const val WorkspaceRoute = "trip/{tripId}"
    const val EditTripRoute = "trip/{tripId}/edit"

    fun workspace(tripId: String): String = "trip/" + tripId
    fun editTrip(tripId: String): String = "trip/" + tripId + "/edit"

    fun startDestination(onboardingCompleted: Boolean): String =
        if (onboardingCompleted) Home else Welcome

    val all = listOf(
        Welcome, Home, Settings, WorkspacePreview, DesignSystemPreview,
        CreateTrip, WorkspaceRoute, EditTripRoute
    )
}
