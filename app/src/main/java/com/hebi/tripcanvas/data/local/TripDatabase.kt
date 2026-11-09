package com.hebi.tripcanvas.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TripEntity::class, PlaceEntity::class, ItineraryItemEntity::class],
    version = 2,
    exportSchema = true
)
abstract class TripDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun placeDao(): PlaceDao
    abstract fun itineraryDao(): ItineraryDao

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

        @Volatile private var instance: TripDatabase? = null

        fun get(context: Context): TripDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TripDatabase::class.java,
                    "tripcanvas.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
