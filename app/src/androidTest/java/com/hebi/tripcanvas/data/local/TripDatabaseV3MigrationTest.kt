package com.hebi.tripcanvas.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripDatabaseV3MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "tripcanvas-v3-upgrade-" + UUID.randomUUID() + ".db"

    @After fun cleanup() { context.deleteDatabase(dbName) }

    @Test
    fun existingTripPlaceAndExpenseSurviveV3ToV4() = runBlocking {
        val path = context.getDatabasePath(dbName)
        path.parentFile?.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            raw.execSQL(
                """CREATE TABLE trips (
                    id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL,
                    startDate TEXT NOT NULL, endDate TEXT NOT NULL,
                    country TEXT NOT NULL, city TEXT NOT NULL,
                    timeZoneId TEXT NOT NULL, currencyCode TEXT NOT NULL,
                    purpose TEXT NOT NULL, note TEXT NOT NULL,
                    createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL
                )""".trimIndent()
            )
            raw.execSQL(
                """CREATE TABLE places (
                    id TEXT NOT NULL PRIMARY KEY, tripId TEXT NOT NULL,
                    name TEXT NOT NULL, address TEXT NOT NULL, note TEXT NOT NULL,
                    latitude REAL, longitude REAL,
                    createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL,
                    FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent()
            )
            raw.execSQL("CREATE INDEX index_places_tripId ON places(tripId)")
            raw.execSQL(
                """CREATE TABLE itinerary_items (
                    id TEXT NOT NULL PRIMARY KEY, tripId TEXT NOT NULL,
                    date TEXT NOT NULL, title TEXT NOT NULL, startTime TEXT, endTime TEXT,
                    placeId TEXT, note TEXT NOT NULL, important INTEGER NOT NULL,
                    confirmed INTEGER NOT NULL, sortOrder INTEGER NOT NULL,
                    createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL,
                    FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(placeId) REFERENCES places(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )""".trimIndent()
            )
            raw.execSQL("CREATE INDEX index_itinerary_items_tripId_date_sortOrder ON itinerary_items(tripId,date,sortOrder)")
            raw.execSQL("CREATE INDEX index_itinerary_items_placeId ON itinerary_items(placeId)")
            raw.execSQL(
                """CREATE TABLE expenses (
                    id TEXT NOT NULL PRIMARY KEY, tripId TEXT NOT NULL,
                    amountDecimal TEXT NOT NULL, currencyCode TEXT NOT NULL,
                    occurredAtLocal TEXT NOT NULL, category TEXT NOT NULL,
                    note TEXT NOT NULL, placeId TEXT,
                    createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL,
                    FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(placeId) REFERENCES places(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )""".trimIndent()
            )
            raw.execSQL("CREATE INDEX index_expenses_tripId_occurredAtLocal ON expenses(tripId,occurredAtLocal)")
            raw.execSQL("CREATE INDEX index_expenses_placeId ON expenses(placeId)")
            raw.execSQL(
                """CREATE TABLE trip_budgets (
                    tripId TEXT NOT NULL PRIMARY KEY, amountDecimal TEXT NOT NULL,
                    currencyCode TEXT NOT NULL, updatedAtMillis INTEGER NOT NULL,
                    FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent()
            )
            raw.execSQL(
                """CREATE TABLE checklist_items (
                    id TEXT NOT NULL PRIMARY KEY, tripId TEXT NOT NULL,
                    title TEXT NOT NULL, checked INTEGER NOT NULL,
                    position INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL,
                    updatedAtMillis INTEGER NOT NULL,
                    FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent()
            )
            raw.execSQL("CREATE INDEX index_checklist_items_tripId_position ON checklist_items(tripId,position)")
            raw.execSQL(
                "INSERT INTO trips VALUES ('t','기존 여행','2026-11-10','2026-11-12'," +
                    "'','','Asia/Seoul','KRW','','',1,1)"
            )
            raw.execSQL("INSERT INTO places VALUES ('p','t','서울역','', '',37.55,126.97,1,1)")
            raw.execSQL("INSERT INTO itinerary_items VALUES ('s','t','2026-11-11'," +
                "'이동','09:00','10:00','p','',0,1,0,1,1)")
            raw.execSQL("INSERT INTO expenses VALUES ('e','t','15000','KRW'," +
                "'2026-11-11T09:30:00','TRANSPORT','', 'p',1,1)")
            raw.version = 3
        } finally {
            raw.close()
        }
        val db = Room.databaseBuilder(context, TripDatabase::class.java, dbName)
            .addMigrations(TripDatabase.MIGRATION_3_4, TripDatabase.MIGRATION_4_5)
            .build()
        try {
            val original = db.placeDao().findForTrip("t", "p")!!
            assertEquals("서울역", original.name)
            assertNull(original.provider)
            assertEquals("p", db.itineraryDao().getDay("t", "2026-11-11").single().placeId)
            assertEquals("p", db.backupDao().allExpenses().single().placeId)

            val remote = original.copy(
                id = "remote", name = "다른 장소", provider = "Photon/OSM",
                providerPlaceId = "N:123", sourceFetchedAtMillis = 1000
            )
            db.placeDao().insert(remote)
            val duplicate = remote.copy(id = "duplicate")
            assertTrue(runCatching { db.placeDao().insert(duplicate) }.isFailure)
            assertEquals(2, db.backupDao().allPlaces().size)
        } finally {
            db.close()
        }
    }
}
