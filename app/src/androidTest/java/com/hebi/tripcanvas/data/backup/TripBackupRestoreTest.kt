package com.hebi.tripcanvas.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.hebi.tripcanvas.data.local.ChecklistItemEntity
import com.hebi.tripcanvas.data.local.ExpenseEntity
import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.PlaceEntity
import com.hebi.tripcanvas.data.local.TripBackupSnapshot
import com.hebi.tripcanvas.data.local.TripBudgetEntity
import com.hebi.tripcanvas.data.local.TripDatabase
import com.hebi.tripcanvas.data.local.TripEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripBackupRestoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun exportImportRoundTripPreservesAllEntitiesAndReplacesData() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TripDatabase::class.java).build()
        try {
            val original = sample()
            db.backupDao().replaceAll(original)
            val fromDb = db.backupDao().snapshot()
            val json = TripBackupCodec.encode(fromDb)
            val decoded = TripBackupCodec.decode(json)
            assertEquals(fromDb, decoded)
            db.tripDao().insert(trip("temporary"))
            assertEquals(2, db.tripDao().observeAll().first().size)
            db.backupDao().replaceAll(decoded)
            assertEquals(fromDb, db.backupDao().snapshot())
            assertEquals("여행", db.tripDao().findById("trip")?.title)
            assertEquals(1, db.expenseDao().observeForTrip("trip").first().size)
            assertEquals(1, db.checklistDao().observeForTrip("trip").first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun invalidBackupFailsBeforeAnyDeletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TripDatabase::class.java).build()
        try {
            val original = sample()
            db.backupDao().replaceAll(original)
            val json = TripBackupCodec.encode(original)
            val invalidVersion = JSONObject(json).put("version", 99).toString()
            assertTrue(runCatching { TripBackupCodec.decode(invalidVersion) }.isFailure)
            val badLink = original.copy(places = original.places.map { it.copy(tripId = "other") })
            assertTrue(runCatching { TripBackupCodec.validate(badLink) }.isFailure)
            assertEquals(original, db.backupDao().snapshot())
        } finally {
            db.close()
        }
    }

    @Test
    fun transactionRollbackPreservesOriginalDataIfInsertFails() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TripDatabase::class.java).build()
        try {
            val original = sample()
            db.backupDao().replaceAll(original)
            val different = original.copy(
                trips = listOf(trip("new")),
                places = listOf(
                    original.places.first().copy(tripId = "new"),
                    original.places.first().copy(tripId = "new")
                ),
                itineraries = emptyList(),
                expenses = emptyList(),
                budgets = emptyList(),
                checklist = emptyList()
            )
            assertTrue(runCatching { db.backupDao().replaceAll(different) }.isFailure)
            assertEquals(original, db.backupDao().snapshot())
        } finally {
            db.close()
        }
    }

    @Test
    fun previousVersionOneBackupStillRestoresWithoutSourceMetadata() {
        val original = sample()
        val json = JSONObject(TripBackupCodec.encode(original))
            .put("version", 1)
        val oldPlace = json.getJSONArray("places").getJSONObject(0)
        oldPlace.remove("provider")
        oldPlace.remove("providerPlaceId")
        oldPlace.remove("sourceFetchedAtMillis")

        val restored = TripBackupCodec.decode(json.toString())
        assertEquals(original, restored)
    }

    @Test
    fun versionTwoBackupRetainsExternalProviderIdentity() {
        val original = sample()
        val sourced = original.copy(
            places = listOf(
                original.places.single().copy(
                    provider = "Photon/OSM",
                    providerPlaceId = "N:1234",
                    sourceFetchedAtMillis = 1000L
                )
            )
        )
        assertEquals(sourced, TripBackupCodec.decode(TripBackupCodec.encode(sourced)))
        val broken = sourced.copy(
            places = sourced.places.map { it.copy(providerPlaceId = null) }
        )
        assertTrue(runCatching { TripBackupCodec.validate(broken) }.isFailure)
    }

    private fun sample(): TripBackupSnapshot {
        return TripBackupSnapshot(
            trips = listOf(trip("trip")),
            places = listOf(PlaceEntity(
                id = "place", tripId = "trip", name = "서울역", address = "",
                note = "", latitude = null, longitude = null,
                createdAtMillis = 1, updatedAtMillis = 1
            )),
            itineraries = listOf(ItineraryItemEntity(
                id = "schedule", tripId = "trip", date = "2026-11-11",
                title = "기차", startTime = "09:00", endTime = "10:00",
                placeId = "place", note = "좌석 메모", important = true,
                confirmed = false, sortOrder = 0, createdAtMillis = 1,
                updatedAtMillis = 1
            )),
            expenses = listOf(ExpenseEntity(
                id = "expense", tripId = "trip", amountDecimal = "12.50",
                currencyCode = "USD", occurredAtLocal = "2026-11-11T09:30:00",
                category = "FOOD", note = "식사", placeId = "place",
                createdAtMillis = 1, updatedAtMillis = 1
            )),
            budgets = listOf(TripBudgetEntity("trip", "100000", "KRW", 1)),
            checklist = listOf(ChecklistItemEntity(
                id = "check", tripId = "trip", title = "여권",
                checked = true, position = 0, createdAtMillis = 1, updatedAtMillis = 1
            ))
        )
    }

    private fun trip(id: String) = TripEntity(
        id = id, title = "여행", startDate = "2026-11-10",
        endDate = "2026-11-12", country = "한국", city = "서울",
        timeZoneId = "Asia/Seoul", currencyCode = "KRW",
        purpose = "", note = "",
        createdAtMillis = 1, updatedAtMillis = 1
    )
}
