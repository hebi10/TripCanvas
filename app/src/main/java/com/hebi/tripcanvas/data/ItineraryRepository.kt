package com.hebi.tripcanvas.data

import com.hebi.tripcanvas.data.local.ItineraryDao
import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.PlaceDao
import com.hebi.tripcanvas.data.local.TripDao
import com.hebi.tripcanvas.domain.ScheduleInput
import com.hebi.tripcanvas.domain.ValidSchedule
import com.hebi.tripcanvas.domain.overlappingItems
import com.hebi.tripcanvas.domain.toInput
import com.hebi.tripcanvas.domain.validateSchedule
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ScheduleOverlapException(val conflicts: Int) :
    IllegalStateException("Schedule overlaps " + conflicts + " existing items")

class ItineraryRepository(
    private val itemDao: ItineraryDao,
    private val tripDao: TripDao,
    private val placeDao: PlaceDao,
    private val clock: Clock = Clock.systemUTC()
) {
    fun observeDay(tripId: String, date: String): Flow<List<ItineraryItemEntity>> =
        itemDao.observeDay(tripId, date)

    fun observeItem(tripId: String, itemId: String): Flow<ItineraryItemEntity?> =
        itemDao.observeItem(tripId, itemId)

    fun observeTripCount(tripId: String): Flow<Int> = itemDao.observeTripCount(tripId)

    suspend fun save(
        tripId: String,
        input: ScheduleInput,
        itemId: String? = null,
        allowOverlap: Boolean = false
    ): String {
        val trip = tripDao.findById(tripId) ?: throw NoSuchElementException("Trip not found")
        val normalized = requireNotNull(validateSchedule(input, trip).normalized) {
            "Invalid schedule fields"
        }
        verifyPlace(tripId, normalized)
        val original = itemId?.let {
            itemDao.findForTrip(tripId, it) ?: throw NoSuchElementException("Schedule not found")
        }
        val conflicts = overlappingItems(
            normalized, itemDao.getDay(tripId, normalized.date), exceptId = original?.id
        )
        if (conflicts.isNotEmpty() && !allowOverlap) throw ScheduleOverlapException(conflicts.size)

        val now = clock.millis()
        if (original == null) {
            val id = UUID.randomUUID().toString()
            itemDao.insertAtEnd(
                ItineraryItemEntity(
                    id = id, tripId = tripId, date = normalized.date,
                    title = normalized.title, startTime = normalized.startTime,
                    endTime = normalized.endTime, placeId = normalized.placeId,
                    note = normalized.note, important = normalized.important,
                    confirmed = normalized.confirmed, sortOrder = 0,
                    createdAtMillis = now, updatedAtMillis = now
                )
            )
            return id
        }
        if (!itemDao.updateSafely(
                original.copy(
                    title = normalized.title, date = normalized.date,
                    startTime = normalized.startTime, endTime = normalized.endTime,
                    placeId = normalized.placeId, note = normalized.note,
                    important = normalized.important, confirmed = normalized.confirmed,
                    updatedAtMillis = now
                )
            )
        ) throw NoSuchElementException("Schedule no longer exists")
        return original.id
    }

    suspend fun duplicate(tripId: String, itemId: String): String {
        val original = itemDao.findForTrip(tripId, itemId)
            ?: throw NoSuchElementException("Schedule not found")
        return save(tripId = tripId, input = original.toInput(), allowOverlap = true)
    }

    suspend fun delete(tripId: String, itemId: String) {
        if (itemDao.delete(tripId, itemId) != 1) {
            throw NoSuchElementException("Schedule not found")
        }
    }

    suspend fun moveWithinDay(
        tripId: String, date: String, itemId: String, offset: Int
    ): Boolean = itemDao.moveWithinDay(tripId, date, itemId, offset)

    private suspend fun verifyPlace(tripId: String, schedule: ValidSchedule) {
        val placeId = schedule.placeId ?: return
        if (placeDao.findForTrip(tripId, placeId) == null) {
            throw IllegalArgumentException("The selected place is not part of this trip")
        }
    }
}
