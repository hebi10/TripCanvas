package com.hebi.tripcanvas.data

import com.hebi.tripcanvas.data.local.ItineraryDao
import com.hebi.tripcanvas.data.local.TravelJournalDao
import com.hebi.tripcanvas.data.local.TravelMomentEntity
import com.hebi.tripcanvas.data.local.TripDao
import com.hebi.tripcanvas.data.local.VisitRecordEntity
import java.time.Clock
import java.util.UUID

class TravelJournalRepository(
    private val dao: TravelJournalDao,
    private val trips: TripDao,
    private val itineraries: ItineraryDao,
    private val clock: Clock = Clock.systemUTC()
) {
    fun visits(tripId: String) = dao.observeVisits(tripId)
    fun moments(tripId: String) = dao.observeMoments(tripId)

    suspend fun checkIn(tripId: String, itemId: String, note: String = ""): Boolean {
        require(note.length <= 2000)
        check(trips.findById(tripId) != null) { "Trip not found" }
        val item = itineraries.findForTrip(tripId, itemId) ?: return false
        if (dao.findVisit(tripId, itemId) != null) return false
        val now = clock.millis()
        return dao.insertVisit(VisitRecordEntity(
            id = UUID.randomUUID().toString(), tripId = tripId,
            itineraryItemId = itemId, titleSnapshot = item.title,
            visitAtUtcMillis = now, note = note, createdAtMillis = now
        )) >= 0L
    }

    suspend fun addMoment(tripId: String, visitId: String?, photoUri: String?, caption: String) {
        require(caption.length <= 2000)
        require(photoUri == null || (photoUri.startsWith("content://") && photoUri.length <= 2048)) {
            "Only a persisted content URI can be stored"
        }
        require(photoUri != null || caption.isNotBlank()) { "Empty moment" }
        check(trips.findById(tripId) != null) { "Trip not found" }
        if (visitId != null) require(dao.findVisitById(tripId, visitId) != null) {
            "Visit does not belong to this trip"
        }
        dao.insertMoment(TravelMomentEntity(
            id = UUID.randomUUID().toString(), tripId = tripId,
            visitId = visitId, photoUri = photoUri,
            caption = caption.trim(), createdAtUtcMillis = clock.millis()
        ))
    }

    suspend fun deleteVisit(tripId: String, visitId: String): Boolean =
        dao.deleteVisitKeepingMemories(tripId, visitId)
}
