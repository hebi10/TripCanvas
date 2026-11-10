package com.hebi.tripcanvas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.hebi.tripcanvas.domain.calculateSettlement

@Dao
interface TripFinanceDao {
    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    suspend fun getTrip(tripId: String): TripEntity?

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY id")
    suspend fun getExpenses(tripId: String): List<ExpenseEntity>

    @Query(
        "SELECT s.* FROM expense_shares s JOIN expenses e ON s.expenseId = e.id " +
            "WHERE e.tripId = :tripId ORDER BY s.expenseId,s.participantId"
    )
    suspend fun getTripShares(tripId: String): List<ExpenseShareEntity>

    @Query("SELECT * FROM trip_participants WHERE tripId = :tripId ORDER BY createdAtMillis,id")
    fun observeParticipants(tripId: String): Flow<List<TripParticipantEntity>>

    @Query("SELECT * FROM trip_participants WHERE tripId = :tripId ORDER BY createdAtMillis,id")
    suspend fun getParticipants(tripId: String): List<TripParticipantEntity>

    @Query("SELECT * FROM trip_participants WHERE id = :id AND tripId = :tripId LIMIT 1")
    suspend fun findParticipant(tripId: String, id: String): TripParticipantEntity?

    @Insert
    suspend fun addParticipant(item: TripParticipantEntity)

    @Update
    suspend fun renameParticipant(item: TripParticipantEntity): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE tripId = :tripId AND payerParticipantId = :id")
    suspend fun payerReferences(tripId: String, id: String): Int

    @Query(
        "SELECT COUNT(*) FROM expense_shares s JOIN expenses e ON s.expenseId = e.id " +
            "WHERE e.tripId = :tripId AND s.participantId = :id"
    )
    suspend fun shareReferences(tripId: String, id: String): Int

    @Transaction
    suspend fun deleteUnusedParticipant(tripId: String, id: String): Boolean {
        require(payerReferences(tripId, id) == 0 && shareReferences(tripId, id) == 0) {
            "정산에 연결된 참가자는 삭제할 수 없습니다."
        }
        return deleteParticipant(tripId, id) == 1
    }

    @Query("DELETE FROM trip_participants WHERE tripId = :tripId AND id = :id")
    suspend fun deleteParticipant(tripId: String, id: String): Int

    @Query("SELECT * FROM expense_shares WHERE expenseId = :expenseId ORDER BY participantId")
    fun observeExpenseShares(expenseId: String): Flow<List<ExpenseShareEntity>>

    @Query(
        "SELECT s.* FROM expense_shares s JOIN expenses e ON s.expenseId = e.id " +
            "WHERE e.tripId = :tripId ORDER BY s.expenseId,s.participantId"
    )
    fun observeTripShares(tripId: String): Flow<List<ExpenseShareEntity>>

    @Query("DELETE FROM expense_shares WHERE expenseId = :expenseId")
    suspend fun clearShares(expenseId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShares(items: List<ExpenseShareEntity>)

    @Query("UPDATE expenses SET payerParticipantId = :payer, baseCurrencyCode = :baseCurrency, " +
        "rateToBaseDecimal = :rate, rateDate = :rateDate, rateSource = :rateSource, " +
        "updatedAtMillis = :now WHERE tripId = :tripId AND id = :expenseId")
    suspend fun updateExpenseFinance(
        tripId: String,
        expenseId: String,
        payer: String?,
        baseCurrency: String?,
        rate: String?,
        rateDate: String?,
        rateSource: String?,
        now: Long
    ): Int

    @Transaction
    suspend fun replaceExpenseFinance(
        tripId: String,
        expenseId: String,
        payer: String?,
        baseCurrency: String?,
        rate: String?,
        rateDate: String?,
        rateSource: String?,
        now: Long,
        rows: List<ExpenseShareEntity>
    ) {
        require(updateExpenseFinance(
            tripId, expenseId, payer, baseCurrency, rate, rateDate, rateSource, now
        ) == 1)
        clearShares(expenseId)
        if (rows.isNotEmpty()) insertShares(rows)
    }

    @Query("SELECT * FROM trip_settlements WHERE tripId = :tripId LIMIT 1")
    fun observeConfirmation(tripId: String): Flow<TripSettlementEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfirmation(item: TripSettlementEntity)

    @Transaction
    suspend fun confirmCurrentSettlement(tripId: String, now: Long) {
        val trip = requireNotNull(getTrip(tripId))
        val summary = calculateSettlement(
            trip.currencyCode, getParticipants(tripId), getExpenses(tripId), getTripShares(tripId)
        )
        require(summary.readyToConfirm) { "환산 및 분담을 완료한 뒤 정산을 확인해 주세요." }
        saveConfirmation(TripSettlementEntity(tripId, summary.fingerprint, now))
    }

    @Query("DELETE FROM trip_settlements WHERE tripId = :tripId")
    suspend fun clearConfirmation(tripId: String)
}
