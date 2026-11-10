package com.hebi.tripcanvas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface TripBackupDao {
    @Query("SELECT * FROM trips ORDER BY id")
    suspend fun allTrips(): List<TripEntity>

    @Query("SELECT * FROM places ORDER BY id")
    suspend fun allPlaces(): List<PlaceEntity>

    @Query("SELECT * FROM itinerary_items ORDER BY id")
    suspend fun allItineraries(): List<ItineraryItemEntity>

    @Query("SELECT * FROM expenses ORDER BY id")
    suspend fun allExpenses(): List<ExpenseEntity>

    @Query("SELECT * FROM trip_budgets ORDER BY tripId")
    suspend fun allBudgets(): List<TripBudgetEntity>

    @Query("SELECT * FROM checklist_items ORDER BY id")
    suspend fun allChecklist(): List<ChecklistItemEntity>

    @Query("SELECT * FROM trip_participants ORDER BY id")
    suspend fun allParticipants(): List<TripParticipantEntity>

    @Query("SELECT * FROM expense_shares ORDER BY expenseId, participantId")
    suspend fun allShares(): List<ExpenseShareEntity>

    @Query("SELECT * FROM trip_settlements ORDER BY tripId")
    suspend fun allSettlements(): List<TripSettlementEntity>

    @Query("SELECT * FROM bookings ORDER BY id")
    suspend fun allBookings(): List<BookingEntity>

    @Query("DELETE FROM expense_shares")
    suspend fun deleteAllShares()

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    @Insert suspend fun insertTrips(items: List<TripEntity>)
    @Insert suspend fun insertPlaces(items: List<PlaceEntity>)
    @Insert suspend fun insertItineraries(items: List<ItineraryItemEntity>)
    @Insert suspend fun insertExpenses(items: List<ExpenseEntity>)
    @Insert suspend fun insertBudgets(items: List<TripBudgetEntity>)
    @Insert suspend fun insertChecklist(items: List<ChecklistItemEntity>)
    @Insert suspend fun insertParticipants(items: List<TripParticipantEntity>)
    @Insert suspend fun insertShares(items: List<ExpenseShareEntity>)
    @Insert suspend fun insertSettlements(items: List<TripSettlementEntity>)
    @Insert suspend fun insertBookings(items: List<BookingEntity>)

    @Transaction
    suspend fun snapshot(): TripBackupSnapshot = TripBackupSnapshot(
        trips = allTrips(),
        places = allPlaces(),
        itineraries = allItineraries(),
        expenses = allExpenses(),
        budgets = allBudgets(),
        checklist = allChecklist(),
        participants = allParticipants(),
        shares = allShares(),
        settlements = allSettlements(),
        bookings = allBookings()
    )

    @Transaction
    suspend fun replaceAll(snapshot: TripBackupSnapshot) {
        deleteAllShares()
        deleteAllTrips()
        insertTrips(snapshot.trips)
        insertPlaces(snapshot.places)
        insertItineraries(snapshot.itineraries)
        insertExpenses(snapshot.expenses)
        insertBudgets(snapshot.budgets)
        insertChecklist(snapshot.checklist)
        insertParticipants(snapshot.participants)
        insertShares(snapshot.shares)
        insertSettlements(snapshot.settlements)
        insertBookings(snapshot.bookings)
    }
}
