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
    const val MapRoute = "trip/{tripId}/map/{date}/{focusId}"
    const val ExternalPlaceSearchRoute = "trip/{tripId}/places/search"
    const val PlaceDetailRoute = "trip/{tripId}/places/{placeId}/detail"
    const val ChecklistRoute = "trip/{tripId}/checklist"
    const val ExpensesRoute = "trip/{tripId}/expenses"
    const val NewExpenseRoute = "trip/{tripId}/expenses/new"
    const val EditExpenseRoute = "trip/{tripId}/expenses/{expenseId}/edit"
    const val BackupRoute = "settings/backup"

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

    fun map(tripId: String, date: String = "all", focusId: String = "all"): String =
        "trip/" + tripId + "/map/" + date + "/" + focusId
    fun externalPlaceSearch(tripId: String): String = "trip/" + tripId + "/places/search"
    fun placeDetail(tripId: String, placeId: String): String =
        "trip/" + tripId + "/places/" + placeId + "/detail"

    fun checklist(tripId: String): String = "trip/" + tripId + "/checklist"
    fun expenses(tripId: String): String = "trip/" + tripId + "/expenses"
    fun newExpense(tripId: String): String = "trip/" + tripId + "/expenses/new"
    fun editExpense(tripId: String, itemId: String): String =
        "trip/" + tripId + "/expenses/" + itemId + "/edit"

    fun startDestination(onboardingCompleted: Boolean): String =
        if (onboardingCompleted) Home else Welcome

    val all = listOf(
        Welcome, Home, Settings, WorkspacePreview, DesignSystemPreview,
        CreateTrip, WorkspaceRoute, EditTripRoute, DayRoute,
        NewScheduleRoute, EditScheduleRoute, PlacesRoute, PickPlaceRoute,
        NewPlaceRoute, EditPlaceRoute, ChecklistRoute,
        ExpensesRoute, NewExpenseRoute, EditExpenseRoute, BackupRoute,
        MapRoute, ExternalPlaceSearchRoute, PlaceDetailRoute
    )
}
