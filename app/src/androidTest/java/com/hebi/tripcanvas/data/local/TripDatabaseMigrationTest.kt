package com.hebi.tripcanvas.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "tripcanvas-migration-test-" + UUID.randomUUID().toString() + ".db"

    @After
    fun cleanUp() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun v1ToV2PreservesTripsAndEnforcesPlaceScheduleReferences() = runBlocking {
        val path = context.getDatabasePath(dbName)
        path.parentFile?.mkdirs()
        val original = SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            original.execSQL(
                """CREATE TABLE IF NOT EXISTS trips (
                    id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL,
                    startDate TEXT NOT NULL, endDate TEXT NOT NULL,
                    country TEXT NOT NULL, city TEXT NOT NULL,
                    timeZoneId TEXT NOT NULL, currencyCode TEXT NOT NULL,
                    purpose TEXT NOT NULL, note TEXT NOT NULL,
                    createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL
                )""".trimIndent()
            )
            original.execSQL(
                "INSERT INTO trips (id,title,startDate,endDate,country,city,timeZoneId," +
                    "currencyCode,purpose,note,createdAtMillis,updatedAtMillis) VALUES (" +
                    "'old-trip','기존 여행','2026-11-10','2026-11-12','한국','서울'," +
                    "'Asia/Seoul','KRW','','',1,1)"
            )
            original.version = 1
        } finally {
            original.close()
        }

        val db = Room.databaseBuilder(context, TripDatabase::class.java, dbName)
            .addMigrations(TripDatabase.MIGRATION_1_2, TripDatabase.MIGRATION_2_3)
            .build()
        try {
            val oldTrips = db.tripDao().observeAll().first()
            assertEquals(1, oldTrips.size)
            assertEquals("기존 여행", oldTrips.single().title)

            db.placeDao().insert(
                PlaceEntity(
                    id = "place-1", tripId = "old-trip", name = "서울역",
                    address = "", note = "", latitude = null, longitude = null,
                    createdAtMillis = 1L, updatedAtMillis = 1L
                )
            )
            db.itineraryDao().insertAtEnd(
                ItineraryItemEntity(
                    id = "item-1", tripId = "old-trip", date = "2026-11-11",
                    title = "기차", startTime = "09:00", endTime = "10:00",
                    placeId = "place-1", note = "", important = false, confirmed = false,
                    sortOrder = 0, createdAtMillis = 1L, updatedAtMillis = 1L
                )
            )
            assertEquals(1, db.itineraryDao().getDay("old-trip", "2026-11-11").size)

            db.placeDao().delete("old-trip", "place-1")
            assertNull(db.itineraryDao().getDay("old-trip", "2026-11-11").single().placeId)

            db.tripDao().deleteById("old-trip")
            assertTrue(db.tripDao().observeAll().first().isEmpty())
            assertTrue(db.itineraryDao().getDay("old-trip", "2026-11-11").isEmpty())
        } finally {
            db.close()
        }
    }
}
