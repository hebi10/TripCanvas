package com.hebi.tripcanvas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startDate ASC, createdAtMillis DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    fun observeById(tripId: String): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    suspend fun findById(tripId: String): TripEntity?

    @Query(
        "SELECT date FROM itinerary_items WHERE tripId = :tripId " +
            "AND (date < :startDate OR date > :endDate) ORDER BY date"
    )
    suspend fun outOfRangeDates(tripId: String, startDate: String, endDate: String): List<String>

    @Insert
    suspend fun insert(trip: TripEntity)

    @Update
    suspend fun update(trip: TripEntity): Int

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteById(tripId: String): Int
}
