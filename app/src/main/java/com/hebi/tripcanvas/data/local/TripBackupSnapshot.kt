package com.hebi.tripcanvas.data.local

data class TripBackupSnapshot(
    val trips: List<TripEntity>,
    val places: List<PlaceEntity>,
    val itineraries: List<ItineraryItemEntity>,
    val expenses: List<ExpenseEntity>,
    val budgets: List<TripBudgetEntity>,
    val checklist: List<ChecklistItemEntity>,
    val participants: List<TripParticipantEntity> = emptyList(),
    val shares: List<ExpenseShareEntity> = emptyList(),
    val settlements: List<TripSettlementEntity> = emptyList(),
    val bookings: List<BookingEntity> = emptyList(),
    val visits: List<VisitRecordEntity> = emptyList(),
    val moments: List<TravelMomentEntity> = emptyList()
) {
    val totalRows: Int get() =
        trips.size + places.size + itineraries.size + expenses.size + budgets.size + checklist.size +
            participants.size + shares.size + settlements.size + bookings.size + visits.size + moments.size
}
