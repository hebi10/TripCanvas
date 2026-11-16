package com.hebi.tripcanvas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TravelJournalDao {
    @Query("SELECT * FROM visit_records WHERE tripId = :tripId ORDER BY visitAtUtcMillis DESC, id")
    fun observeVisits(tripId: String): Flow<List<VisitRecordEntity>>

    @Query("SELECT * FROM travel_moments WHERE tripId = :tripId ORDER BY createdAtUtcMillis DESC, id")
    fun observeMoments(tripId: String): Flow<List<TravelMomentEntity>>

    @Query("SELECT * FROM visit_records WHERE tripId = :tripId AND itineraryItemId = :itemId LIMIT 1")
    suspend fun findVisit(tripId: String, itemId: String): VisitRecordEntity?

    @Query("SELECT * FROM visit_records WHERE tripId = :tripId AND id = :visitId LIMIT 1")
    suspend fun findVisitById(tripId: String, visitId: String): VisitRecordEntity?

    @Insert
    suspend fun insertVisit(record: VisitRecordEntity)

    @Insert
    suspend fun insertMoment(record: TravelMomentEntity)

    @Query("DELETE FROM visit_records WHERE tripId = :tripId AND id = :visitId")
    suspend fun deleteVisit(tripId: String, visitId: String): Int

    @Query("UPDATE travel_moments SET visitId = NULL WHERE tripId = :tripId AND visitId = :visitId")
    suspend fun unlinkMoments(tripId: String, visitId: String)

    @Transaction
    suspend fun deleteVisitKeepingMemories(tripId: String, visitId: String): Boolean {
        unlinkMoments(tripId, visitId)
        return deleteVisit(tripId, visitId) == 1
    }
}
