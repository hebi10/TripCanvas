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

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    @Insert suspend fun insertTrips(items: List<TripEntity>)
    @Insert suspend fun insertPlaces(items: List<PlaceEntity>)
    @Insert suspend fun insertItineraries(items: List<ItineraryItemEntity>)
    @Insert suspend fun insertExpenses(items: List<ExpenseEntity>)
    @Insert suspend fun insertBudgets(items: List<TripBudgetEntity>)
    @Insert suspend fun insertChecklist(items: List<ChecklistItemEntity>)

    @Transaction
    suspend fun snapshot(): TripBackupSnapshot = TripBackupSnapshot(
        trips = allTrips(),
        places = allPlaces(),
        itineraries = allItineraries(),
        expenses = allExpenses(),
        budgets = allBudgets(),
        checklist = allChecklist()
    )

    @Transaction
    suspend fun replaceAll(snapshot: TripBackupSnapshot) {
        deleteAllTrips()
        insertTrips(snapshot.trips)
        insertPlaces(snapshot.places)
        insertItineraries(snapshot.itineraries)
        insertExpenses(snapshot.expenses)
        insertBudgets(snapshot.budgets)
        insertChecklist(snapshot.checklist)
    }
}
