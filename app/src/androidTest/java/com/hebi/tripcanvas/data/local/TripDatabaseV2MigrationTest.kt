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
class TripDatabaseV2MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "tripcanvas-v2-upgrade-" + UUID.randomUUID() + ".db"

    @After fun cleanup() { context.deleteDatabase(dbName) }

    @Test
    fun v2ToV3RetainsTripPlacesAndScheduleAndCreatesNewTables() = runBlocking {
        val path = context.getDatabasePath(dbName)
        path.parentFile?.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            raw.execSQL(
                """CREATE TABLE IF NOT EXISTS "trips" (
                    "id" TEXT NOT NULL, "title" TEXT NOT NULL,
                    "startDate" TEXT NOT NULL, "endDate" TEXT NOT NULL,
                    "country" TEXT NOT NULL, "city" TEXT NOT NULL,
                    "timeZoneId" TEXT NOT NULL, "currencyCode" TEXT NOT NULL,
                    "purpose" TEXT NOT NULL, "note" TEXT NOT NULL,
                    "createdAtMillis" INTEGER NOT NULL, "updatedAtMillis" INTEGER NOT NULL,
                    PRIMARY KEY("id")
                )""".trimIndent()
            )
            raw.execSQL(
                """CREATE TABLE IF NOT EXISTS "places" (
                    "id" TEXT NOT NULL, "tripId" TEXT NOT NULL, "name" TEXT NOT NULL,
                    "address" TEXT NOT NULL, "note" TEXT NOT NULL,
                    "latitude" REAL, "longitude" REAL,
                    "createdAtMillis" INTEGER NOT NULL, "updatedAtMillis" INTEGER NOT NULL,
                    PRIMARY KEY("id"),
                    FOREIGN KEY("tripId") REFERENCES "trips"("id") ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent()
            )
            raw.execSQL("CREATE INDEX IF NOT EXISTS index_places_tripId ON places (tripId)")
            raw.execSQL(
                """CREATE TABLE IF NOT EXISTS "itinerary_items" (
                    "id" TEXT NOT NULL, "tripId" TEXT NOT NULL, "date" TEXT NOT NULL,
                    "title" TEXT NOT NULL, "startTime" TEXT, "endTime" TEXT,
                    "placeId" TEXT, "note" TEXT NOT NULL,
                    "important" INTEGER NOT NULL, "confirmed" INTEGER NOT NULL,
                    "sortOrder" INTEGER NOT NULL,
                    "createdAtMillis" INTEGER NOT NULL, "updatedAtMillis" INTEGER NOT NULL,
                    PRIMARY KEY("id"),
                    FOREIGN KEY("tripId") REFERENCES "trips"("id") ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY("placeId") REFERENCES "places"("id") ON UPDATE NO ACTION ON DELETE SET NULL
                )""".trimIndent()
            )
            raw.execSQL(
                "CREATE INDEX IF NOT EXISTS index_itinerary_items_tripId_date_sortOrder " +
                    "ON itinerary_items (tripId, date, sortOrder)"
            )
            raw.execSQL("CREATE INDEX IF NOT EXISTS index_itinerary_items_placeId ON itinerary_items (placeId)")
            raw.execSQL(
                "INSERT INTO trips VALUES (" +
                    "'trip-v2','기존 여행','2026-11-10','2026-11-12','','','Asia/Seoul','KRW','','',1,1)"
            )
            raw.execSQL(
                "INSERT INTO places VALUES ('place-v2','trip-v2','서울역','','',null,null,1,1)"
            )
            raw.execSQL(
                "INSERT INTO itinerary_items VALUES (" +
                    "'item-v2','trip-v2','2026-11-11','기차','09:00','10:00'," +
                    "'place-v2','',0,1,0,1,1)"
            )
            raw.version = 2
        } finally {
            raw.close()
        }
        val db = Room.databaseBuilder(context, TripDatabase::class.java, dbName)
            .addMigrations(TripDatabase.MIGRATION_2_3, TripDatabase.MIGRATION_3_4, TripDatabase.MIGRATION_4_5).build()
        try {
            assertEquals("기존 여행", db.tripDao().findById("trip-v2")?.title)
            assertEquals("서울역", db.placeDao().findForTrip("trip-v2", "place-v2")?.name)
            assertEquals("item-v2", db.itineraryDao().getDay("trip-v2", "2026-11-11").single().id)

            db.expenseDao().insert(ExpenseEntity(
                id = "expense-v3", tripId = "trip-v2", amountDecimal = "1200",
                currencyCode = "KRW", occurredAtLocal = "2026-11-11T09:00:00",
                category = "TRANSPORT", note = "", placeId = "place-v2",
                createdAtMillis = 1, updatedAtMillis = 1
            ))
            db.expenseDao().upsertBudget(TripBudgetEntity("trip-v2", "10000", "KRW", 1))
            db.checklistDao().insertAtEnd(ChecklistItemEntity(
                id = "check-v3", tripId = "trip-v2", title = "여권",
                checked = false, position = 0, createdAtMillis = 1, updatedAtMillis = 1
            ))
            assertEquals(1, db.expenseDao().observeForTrip("trip-v2").first().size)
            assertEquals(1, db.checklistDao().observeForTrip("trip-v2").first().size)
            assertEquals("10000", db.expenseDao().observeBudget("trip-v2").first()?.amountDecimal)

            db.placeDao().delete("trip-v2", "place-v2")
            assertNull(db.itineraryDao().getDay("trip-v2", "2026-11-11").single().placeId)
            assertNull(db.expenseDao().observeForTrip("trip-v2").first().single().placeId)
            db.tripDao().deleteById("trip-v2")
            assertTrue(db.expenseDao().observeForTrip("trip-v2").first().isEmpty())
            assertTrue(db.checklistDao().observeForTrip("trip-v2").first().isEmpty())
        } finally {
            db.close()
        }
    }
}
