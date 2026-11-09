package com.hebi.tripcanvas.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TripEntity::class, PlaceEntity::class, ItineraryItemEntity::class,
        ExpenseEntity::class, TripBudgetEntity::class, ChecklistItemEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class TripDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun placeDao(): PlaceDao
    abstract fun itineraryDao(): ItineraryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun backupDao(): TripBackupDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS "places" (
                        "id" TEXT NOT NULL, "tripId" TEXT NOT NULL, "name" TEXT NOT NULL,
                        "address" TEXT NOT NULL, "note" TEXT NOT NULL,
                        "latitude" REAL, "longitude" REAL,
                        "createdAtMillis" INTEGER NOT NULL, "updatedAtMillis" INTEGER NOT NULL,
                        PRIMARY KEY("id"),
                        FOREIGN KEY("tripId") REFERENCES "trips"("id") ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_places_tripId ON places (tripId)")
                db.execSQL(
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
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_itinerary_items_tripId_date_sortOrder " +
                        "ON itinerary_items (tripId, date, sortOrder)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_itinerary_items_placeId ON itinerary_items (placeId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS "expenses" (
                        "id" TEXT NOT NULL, "tripId" TEXT NOT NULL,
                        "amountDecimal" TEXT NOT NULL, "currencyCode" TEXT NOT NULL,
                        "occurredAtLocal" TEXT NOT NULL, "category" TEXT NOT NULL,
                        "note" TEXT NOT NULL, "placeId" TEXT,
                        "createdAtMillis" INTEGER NOT NULL, "updatedAtMillis" INTEGER NOT NULL,
                        PRIMARY KEY("id"),
                        FOREIGN KEY("tripId") REFERENCES "trips"("id") ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY("placeId") REFERENCES "places"("id") ON UPDATE NO ACTION ON DELETE SET NULL
                    )""".trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_expenses_tripId_occurredAtLocal " +
                        "ON expenses (tripId, occurredAtLocal)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_expenses_placeId ON expenses (placeId)"
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS "trip_budgets" (
                        "tripId" TEXT NOT NULL, "amountDecimal" TEXT NOT NULL,
                        "currencyCode" TEXT NOT NULL, "updatedAtMillis" INTEGER NOT NULL,
                        PRIMARY KEY("tripId"),
                        FOREIGN KEY("tripId") REFERENCES "trips"("id") ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent()
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS "checklist_items" (
                        "id" TEXT NOT NULL, "tripId" TEXT NOT NULL,
                        "title" TEXT NOT NULL, "checked" INTEGER NOT NULL,
                        "position" INTEGER NOT NULL,
                        "createdAtMillis" INTEGER NOT NULL, "updatedAtMillis" INTEGER NOT NULL,
                        PRIMARY KEY("id"),
                        FOREIGN KEY("tripId") REFERENCES "trips"("id") ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_checklist_items_tripId_position " +
                        "ON checklist_items (tripId, position)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE places ADD COLUMN provider TEXT")
                db.execSQL("ALTER TABLE places ADD COLUMN providerPlaceId TEXT")
                db.execSQL("ALTER TABLE places ADD COLUMN sourceFetchedAtMillis INTEGER")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_places_tripId_provider_providerPlaceId " +
                        "ON places(tripId, provider, providerPlaceId)"
                )
            }
        }

        @Volatile private var instance: TripDatabase? = null

        fun get(context: Context): TripDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TripDatabase::class.java,
                    "tripcanvas.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }
    }
}
