package com.hebi.tripcanvas.data.local

data class TripBackupSnapshot(
    val trips: List<TripEntity>,
    val places: List<PlaceEntity>,
    val itineraries: List<ItineraryItemEntity>,
    val expenses: List<ExpenseEntity>,
    val budgets: List<TripBudgetEntity>,
    val checklist: List<ChecklistItemEntity>
) {
    val totalRows: Int get() =
        trips.size + places.size + itineraries.size + expenses.size + budgets.size + checklist.size
}
