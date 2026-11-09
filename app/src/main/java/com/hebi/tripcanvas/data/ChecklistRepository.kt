package com.hebi.tripcanvas.data

import com.hebi.tripcanvas.data.local.ChecklistDao
import com.hebi.tripcanvas.data.local.ChecklistItemEntity
import com.hebi.tripcanvas.data.local.TripDao
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ChecklistRepository(
    private val dao: ChecklistDao,
    private val trips: TripDao,
    private val clock: Clock = Clock.systemUTC()
) {
    fun observe(tripId: String): Flow<List<ChecklistItemEntity>> = dao.observeForTrip(tripId)

    suspend fun find(tripId: String, itemId: String): ChecklistItemEntity? = dao.find(tripId, itemId)

    suspend fun create(tripId: String, title: String): String {
        val validTitle = requireTitle(title)
        requireNotNull(trips.findById(tripId)) { "Trip not found" }
        val id = UUID.randomUUID().toString()
        val now = clock.millis()
        dao.insertAtEnd(ChecklistItemEntity(
            id = id, tripId = tripId, title = validTitle, checked = false,
            position = 0, createdAtMillis = now, updatedAtMillis = now
        ))
        return id
    }

    suspend fun rename(tripId: String, itemId: String, title: String) {
        val validTitle = requireTitle(title)
        val original = dao.find(tripId, itemId) ?: throw NoSuchElementException("Item not found")
        check(dao.update(original.copy(title = validTitle, updatedAtMillis = clock.millis())) == 1)
    }

    suspend fun setChecked(tripId: String, itemId: String, checked: Boolean) {
        val original = dao.find(tripId, itemId) ?: throw NoSuchElementException("Item not found")
        check(dao.update(original.copy(checked = checked, updatedAtMillis = clock.millis())) == 1)
    }

    suspend fun delete(tripId: String, itemId: String): ChecklistItemEntity {
        val original = dao.find(tripId, itemId) ?: throw NoSuchElementException("Item not found")
        check(dao.delete(tripId, itemId) == 1)
        return original
    }

    suspend fun undoDelete(item: ChecklistItemEntity) {
        requireNotNull(trips.findById(item.tripId)) { "Trip not found" }
        dao.insert(item)
    }

    suspend fun move(tripId: String, itemId: String, offset: Int): Boolean =
        dao.move(tripId, itemId, offset)

    private fun requireTitle(title: String): String =
        title.trim().takeIf { it.length in 1..100 }
            ?: throw IllegalArgumentException("Title must be 1-100 characters")
}
