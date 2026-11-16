package com.hebi.tripcanvas.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.TravelMomentEntity
import com.hebi.tripcanvas.data.local.TripDatabase
import com.hebi.tripcanvas.data.local.TripEntity
import com.hebi.tripcanvas.data.local.VisitRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripP3BackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun visitAndMomentSurviveBackupAndRestore() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TripDatabase::class.java).build()
        try {
            db.tripDao().insert(TripEntity(
                "trip", "여행", "2026-11-10", "2026-11-12", "", "",
                "Asia/Seoul", "KRW", "", "", 1, 1
            ))
            db.itineraryDao().insert(ItineraryItemEntity(
                "schedule", "trip", "2026-11-11", "박물관", "10:00", null,
                null, "", false, false, 0, 1, 1
            ))
            db.travelJournalDao().insertVisit(VisitRecordEntity(
                "visit", "trip", "schedule", "박물관", 1000, "도착", 1000
            ))
            db.travelJournalDao().insertMoment(TravelMomentEntity(
                "moment", "trip", "visit", "content://example.provider/photo/1", "사진", 2000
            ))
            val original = db.backupDao().snapshot()
            val encoded = TripBackupCodec.encode(original)
            assertEquals(4, JSONObject(encoded).getInt("version"))
            val restored = TripBackupCodec.decode(encoded)
            assertEquals(original, restored)
            db.backupDao().replaceAll(restored)
            assertEquals(1, db.travelJournalDao().observeVisits("trip").first().size)
            assertEquals(1, db.travelJournalDao().observeMoments("trip").first().size)
            assertTrue(db.travelJournalDao().deleteVisitKeepingMemories("trip", "visit"))
            assertEquals(null, db.travelJournalDao().observeMoments("trip").first().single().visitId)
        } finally { db.close() }
    }

    @Test fun olderBackupCanBeRestoredWithoutP3Data() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TripDatabase::class.java).build()
        try {
            db.tripDao().insert(TripEntity(
                "trip", "여행", "2026-11-10", "2026-11-12", "", "",
                "Asia/Seoul", "KRW", "", "", 1, 1
            ))
            val json = JSONObject(TripBackupCodec.encode(db.backupDao().snapshot()))
            json.put("version", 3).remove("visits").remove("moments")
            val restored = TripBackupCodec.decode(json.toString())
            assertTrue(restored.visits.isEmpty())
            assertTrue(restored.moments.isEmpty())
        } finally { db.close() }
    }
}
