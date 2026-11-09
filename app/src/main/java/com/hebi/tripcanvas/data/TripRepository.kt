package com.hebi.tripcanvas.data

import com.hebi.tripcanvas.data.local.TripDao
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.domain.TripInput
import com.hebi.tripcanvas.domain.validateTrip
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class TripPeriodConflictException(val affectedDates: List<String>) :
    IllegalStateException("Trip period leaves " + affectedDates.size + " schedules outside its dates")

class TripRepository(
    private val dao: TripDao,
    private val clock: Clock = Clock.systemUTC()
) {
    val trips: Flow<List<TripEntity>> = dao.observeAll()

    fun observeTrip(tripId: String): Flow<TripEntity?> = dao.observeById(tripId)

    suspend fun create(input: TripInput): String {
        val valid = requireValid(input)
        val now = clock.millis()
        val id = UUID.randomUUID().toString()
        dao.insert(
            TripEntity(
                id = id,
                title = valid.title,
                startDate = valid.startDate,
                endDate = valid.endDate,
                country = valid.country,
                city = valid.city,
                timeZoneId = valid.timeZoneId,
                currencyCode = valid.currencyCode,
                purpose = valid.purpose,
                note = valid.note,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        return id
    }

    suspend fun update(tripId: String, input: TripInput) {
        val valid = requireValid(input)
        val original = dao.findById(tripId) ?: throw NoSuchElementException("Trip not found")
        if (valid.startDate != original.startDate || valid.endDate != original.endDate) {
            val outside = dao.outOfRangeDates(tripId, valid.startDate, valid.endDate)
            if (outside.isNotEmpty()) throw TripPeriodConflictException(outside)
        }
        val updated = dao.update(
            original.copy(
                title = valid.title,
                startDate = valid.startDate,
                endDate = valid.endDate,
                country = valid.country,
                city = valid.city,
                timeZoneId = valid.timeZoneId,
                currencyCode = valid.currencyCode,
                purpose = valid.purpose,
                note = valid.note,
                updatedAtMillis = clock.millis()
            )
        )
        if (updated != 1) throw NoSuchElementException("Trip was removed during editing")
    }

    suspend fun delete(tripId: String) {
        if (dao.deleteById(tripId) != 1) {
            throw NoSuchElementException("Trip already deleted")
        }
    }

    private fun requireValid(input: TripInput): TripInput =
        requireNotNull(validateTrip(input).normalized) { "Invalid trip fields" }
}
