package com.hebi.tripcanvas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItineraryDao {
    @Query(
        "SELECT * FROM itinerary_items WHERE tripId = :tripId AND date = :date " +
            "ORDER BY CASE WHEN startTime IS NULL THEN 1 ELSE 0 END, sortOrder, createdAtMillis, id"
    )
    fun observeDay(tripId: String, date: String): Flow<List<ItineraryItemEntity>>

    @Query(
        "SELECT * FROM itinerary_items WHERE tripId = :tripId AND date = :date " +
            "ORDER BY CASE WHEN startTime IS NULL THEN 1 ELSE 0 END, sortOrder, createdAtMillis, id"
    )
    suspend fun getDay(tripId: String, date: String): List<ItineraryItemEntity>

    @Query("SELECT * FROM itinerary_items WHERE tripId = :tripId AND id = :itemId LIMIT 1")
    fun observeItem(tripId: String, itemId: String): Flow<ItineraryItemEntity?>

    @Query("SELECT * FROM itinerary_items WHERE tripId = :tripId AND id = :itemId LIMIT 1")
    suspend fun findForTrip(tripId: String, itemId: String): ItineraryItemEntity?

    @Query(
        "SELECT * FROM itinerary_items WHERE tripId = :tripId " +
            "ORDER BY date, sortOrder, createdAtMillis, id"
    )
    fun observeTrip(tripId: String): Flow<List<ItineraryItemEntity>>

    @Query(
        "SELECT * FROM itinerary_items WHERE tripId = :tripId AND placeId = :placeId " +
            "ORDER BY date, sortOrder, id"
    )
    fun observeLinkedPlace(tripId: String, placeId: String): Flow<List<ItineraryItemEntity>>

    @Query("SELECT COUNT(*) FROM itinerary_items WHERE tripId = :tripId")
    fun observeTripCount(tripId: String): Flow<Int>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM itinerary_items WHERE tripId = :tripId AND date = :date")
    suspend fun nextOrder(tripId: String, date: String): Int

    @Insert
    suspend fun insert(item: ItineraryItemEntity)

    @Update
    suspend fun update(item: ItineraryItemEntity): Int

    @Query("DELETE FROM itinerary_items WHERE id = :itemId AND tripId = :tripId")
    suspend fun delete(tripId: String, itemId: String): Int

    @Query("UPDATE itinerary_items SET sortOrder = :position WHERE tripId = :tripId AND id = :itemId")
    suspend fun setPosition(tripId: String, itemId: String, position: Int)

    @Transaction
    suspend fun insertAtEnd(item: ItineraryItemEntity) {
        insert(item.copy(sortOrder = nextOrder(item.tripId, item.date)))
    }

    @Transaction
    suspend fun updateSafely(item: ItineraryItemEntity): Boolean {
        val old = findForTrip(item.tripId, item.id) ?: return false
        val groupChanged = (old.startTime == null) != (item.startTime == null)
        val position = if (old.date != item.date || groupChanged) {
            nextOrder(item.tripId, item.date)
        } else {
            old.sortOrder
        }
        return update(item.copy(sortOrder = position)) == 1
    }

    @Transaction
    suspend fun moveWithinDay(tripId: String, date: String, itemId: String, offset: Int): Boolean {
        val day = getDay(tripId, date)
        val item = day.firstOrNull { it.id == itemId } ?: return false
        val timed = item.startTime != null
        val group = day.filter { (it.startTime != null) == timed }.toMutableList()
        val oldIndex = group.indexOfFirst { it.id == itemId }
        val nextIndex = (oldIndex + offset).coerceIn(0, group.lastIndex)
        if (oldIndex == nextIndex) return false
        group.removeAt(oldIndex)
        group.add(nextIndex, item)
        group.forEachIndexed { index, element ->
            setPosition(tripId, element.id, index)
        }
        return true
    }
}
