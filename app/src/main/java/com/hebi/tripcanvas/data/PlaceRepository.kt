package com.hebi.tripcanvas.data

import com.hebi.tripcanvas.data.local.PlaceDao
import com.hebi.tripcanvas.data.local.PlaceEntity
import com.hebi.tripcanvas.data.local.TripDao
import com.hebi.tripcanvas.data.search.RemotePlace
import com.hebi.tripcanvas.domain.PlaceInput
import com.hebi.tripcanvas.domain.ValidPlace
import com.hebi.tripcanvas.domain.validatePlace
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class ImportedPlace(val id: String, val alreadySaved: Boolean)

class PlaceRepository(
    private val placeDao: PlaceDao,
    private val tripDao: TripDao,
    private val clock: Clock = Clock.systemUTC()
) {
    fun observePlaces(tripId: String): Flow<List<PlaceEntity>> = placeDao.observeForTrip(tripId)

    fun observePlace(tripId: String, placeId: String): Flow<PlaceEntity?> =
        placeDao.observeById(tripId, placeId)

    suspend fun find(tripId: String, placeId: String): PlaceEntity? =
        placeDao.findForTrip(tripId, placeId)

    suspend fun linkedCount(tripId: String, placeId: String): Int =
        placeDao.linkedCount(tripId, placeId)

    suspend fun create(tripId: String, input: PlaceInput): String {
        val valid = requireValid(input)
        requireNotNull(tripDao.findById(tripId)) { "Trip not found" }
        val id = UUID.randomUUID().toString()
        val now = clock.millis()
        placeDao.insert(
            PlaceEntity(id, tripId, valid.name, valid.address, valid.note,
                valid.latitude, valid.longitude, now, now)
        )
        return id
    }

    suspend fun importRemote(tripId: String, remote: RemotePlace): ImportedPlace {
        requireNotNull(tripDao.findById(tripId)) { "Trip not found" }
        require(remote.provider == "Photon/OSM" && remote.providerId.isNotBlank())
        val existing = placeDao.findByProvider(tripId, remote.provider, remote.providerId)
        if (existing != null) return ImportedPlace(existing.id, true)
        val fields = requireValid(
            PlaceInput(
                name = remote.name.take(100),
                address = remote.address.take(500),
                latitude = remote.latitude.toString(),
                longitude = remote.longitude.toString()
            )
        )
        val now = clock.millis()
        val id = UUID.randomUUID().toString()
        try {
            placeDao.insert(
                PlaceEntity(
                    id, tripId, fields.name, fields.address, "",
                    fields.latitude, fields.longitude, now, now,
                    remote.provider, remote.providerId, now
                )
            )
        } catch (error: android.database.sqlite.SQLiteConstraintException) {
            val duplicate = placeDao.findByProvider(tripId, remote.provider, remote.providerId)
            if (duplicate != null) return ImportedPlace(duplicate.id, true)
            throw error
        }
        return ImportedPlace(id, false)
    }

    suspend fun update(tripId: String, placeId: String, input: PlaceInput) {
        val valid = requireValid(input)
        val original = placeDao.findForTrip(tripId, placeId)
            ?: throw NoSuchElementException("Place not found")
        if (placeDao.update(
                original.copy(
                    name = valid.name, address = valid.address, note = valid.note,
                    latitude = valid.latitude, longitude = valid.longitude,
                    updatedAtMillis = clock.millis()
                )
            ) != 1
        ) {
            throw NoSuchElementException("Place was removed")
        }
    }

    suspend fun delete(tripId: String, placeId: String) {
        if (placeDao.delete(tripId, placeId) != 1) {
            throw NoSuchElementException("Place not found")
        }
    }

    private fun requireValid(input: PlaceInput): ValidPlace =
        requireNotNull(validatePlace(input).normalized) { "Invalid place fields" }
}
