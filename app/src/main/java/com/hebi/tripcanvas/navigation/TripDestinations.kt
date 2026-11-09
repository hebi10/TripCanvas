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

    const val DayRoute = "trip/{tripId}/day"
    const val NewScheduleRoute = "trip/{tripId}/day/{date}/new"
    const val EditScheduleRoute = "trip/{tripId}/schedule/{itemId}/edit"
    const val PlacesRoute = "trip/{tripId}/places"
    const val PickPlaceRoute = "trip/{tripId}/places/pick"
    const val NewPlaceRoute = "trip/{tripId}/places/new"
    const val EditPlaceRoute = "trip/{tripId}/places/{placeId}/edit"

    fun workspace(tripId: String): String = "trip/" + tripId
    fun editTrip(tripId: String): String = "trip/" + tripId + "/edit"
    fun day(tripId: String): String = "trip/" + tripId + "/day"
    fun newSchedule(tripId: String, date: String): String =
        "trip/" + tripId + "/day/" + date + "/new"
    fun editSchedule(tripId: String, itemId: String): String =
        "trip/" + tripId + "/schedule/" + itemId + "/edit"
    fun places(tripId: String): String = "trip/" + tripId + "/places"
    fun pickPlace(tripId: String): String = "trip/" + tripId + "/places/pick"
    fun newPlace(tripId: String): String = "trip/" + tripId + "/places/new"
    fun editPlace(tripId: String, placeId: String): String =
        "trip/" + tripId + "/places/" + placeId + "/edit"

    fun startDestination(onboardingCompleted: Boolean): String =
        if (onboardingCompleted) Home else Welcome

    val all = listOf(
        Welcome, Home, Settings, WorkspacePreview, DesignSystemPreview,
        CreateTrip, WorkspaceRoute, EditTripRoute, DayRoute,
        NewScheduleRoute, EditScheduleRoute, PlacesRoute, PickPlaceRoute,
        NewPlaceRoute, EditPlaceRoute
    )
}
