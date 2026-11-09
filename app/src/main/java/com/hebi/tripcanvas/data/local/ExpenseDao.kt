package com.hebi.tripcanvas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY occurredAtLocal DESC, createdAtMillis DESC, id")
    fun observeForTrip(tripId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE tripId = :tripId AND id = :id LIMIT 1")
    fun observeById(tripId: String, id: String): Flow<ExpenseEntity?>

    @Query("SELECT * FROM expenses WHERE tripId = :tripId AND id = :id LIMIT 1")
    suspend fun find(tripId: String, id: String): ExpenseEntity?

    @Insert
    suspend fun insert(item: ExpenseEntity)

    @Update
    suspend fun update(item: ExpenseEntity): Int

    @Query("DELETE FROM expenses WHERE tripId = :tripId AND id = :id")
    suspend fun delete(tripId: String, id: String): Int

    @Query("SELECT * FROM trip_budgets WHERE tripId = :tripId LIMIT 1")
    fun observeBudget(tripId: String): Flow<TripBudgetEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: TripBudgetEntity)

    @Query("DELETE FROM trip_budgets WHERE tripId = :tripId")
    suspend fun clearBudget(tripId: String)
}
