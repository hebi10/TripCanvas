package com.hebi.tripcanvas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places WHERE tripId = :tripId ORDER BY name COLLATE NOCASE, createdAtMillis, id")
    fun observeForTrip(tripId: String): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE tripId = :tripId AND id = :placeId LIMIT 1")
    fun observeById(tripId: String, placeId: String): Flow<PlaceEntity?>

    @Query("SELECT * FROM places WHERE tripId = :tripId AND id = :placeId LIMIT 1")
    suspend fun findForTrip(tripId: String, placeId: String): PlaceEntity?

    @Query(
        "SELECT * FROM places WHERE tripId = :tripId AND provider = :provider " +
            "AND providerPlaceId = :providerPlaceId LIMIT 1"
    )
    suspend fun findByProvider(tripId: String, provider: String, providerPlaceId: String): PlaceEntity?

    @Insert
    suspend fun insert(place: PlaceEntity)

    @Update
    suspend fun update(place: PlaceEntity): Int

    @Query("DELETE FROM places WHERE id = :placeId AND tripId = :tripId")
    suspend fun delete(tripId: String, placeId: String): Int

    @Query("SELECT COUNT(*) FROM itinerary_items WHERE tripId = :tripId AND placeId = :placeId")
    suspend fun linkedCount(tripId: String, placeId: String): Int
}
