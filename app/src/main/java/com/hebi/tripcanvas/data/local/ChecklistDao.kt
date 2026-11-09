package com.hebi.tripcanvas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_items WHERE tripId = :tripId ORDER BY position, createdAtMillis, id")
    fun observeForTrip(tripId: String): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE tripId = :tripId ORDER BY position, createdAtMillis, id")
    suspend fun ordered(tripId: String): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE tripId = :tripId AND id = :id LIMIT 1")
    suspend fun find(tripId: String, id: String): ChecklistItemEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM checklist_items WHERE tripId = :tripId")
    suspend fun nextPosition(tripId: String): Int

    @Insert
    suspend fun insert(item: ChecklistItemEntity)

    @Update
    suspend fun update(item: ChecklistItemEntity): Int

    @Query("DELETE FROM checklist_items WHERE tripId = :tripId AND id = :id")
    suspend fun delete(tripId: String, id: String): Int

    @Query("UPDATE checklist_items SET position = :position WHERE tripId = :tripId AND id = :id")
    suspend fun setPosition(tripId: String, id: String, position: Int)

    @Transaction
    suspend fun insertAtEnd(item: ChecklistItemEntity) {
        insert(item.copy(position = nextPosition(item.tripId)))
    }

    @Transaction
    suspend fun move(tripId: String, itemId: String, offset: Int): Boolean {
        val items = ordered(tripId)
        val index = items.indexOfFirst { it.id == itemId }
        if (index < 0 || offset == 0) return false
        val target = (index + offset).coerceIn(0, items.lastIndex)
        if (target == index) return false
        val ordered = items.toMutableList()
        val item = ordered.removeAt(index)
        ordered.add(target, item)
        ordered.forEachIndexed { position, entry ->
            setPosition(tripId, entry.id, position)
        }
        return true
    }
}
