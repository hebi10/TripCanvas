package com.hebi.tripcanvas.data

import com.hebi.tripcanvas.data.local.ExpenseDao
import com.hebi.tripcanvas.data.local.ExpenseEntity
import com.hebi.tripcanvas.data.local.ExpenseShareEntity
import com.hebi.tripcanvas.data.local.TripFinanceDao
import com.hebi.tripcanvas.data.local.TripParticipantEntity
import com.hebi.tripcanvas.domain.SplitMode
import com.hebi.tripcanvas.domain.allocateShares
import com.hebi.tripcanvas.domain.convertedMinor
import com.hebi.tripcanvas.domain.validateExchangeRate
import java.time.Clock
import java.util.UUID

class TripFinanceRepository(
    private val finance: TripFinanceDao,
    private val expenses: ExpenseDao,
    private val clock: Clock = Clock.systemUTC()
) {
    fun participants(tripId: String) = finance.observeParticipants(tripId)
    fun shares(tripId: String) = finance.observeTripShares(tripId)
    fun expenseShares(expenseId: String) = finance.observeExpenseShares(expenseId)
    fun confirmation(tripId: String) = finance.observeConfirmation(tripId)

    suspend fun addParticipant(tripId: String, raw: String): String {
        requireNotNull(finance.getTrip(tripId)) { "여행을 찾을 수 없습니다." }
        val name = raw.trim()
        require(name.length in 1..80) { "참가자 이름은 1~80자입니다." }
        val id = UUID.randomUUID().toString()
        val time = clock.millis()
        finance.addParticipant(TripParticipantEntity(id, tripId, name, time, time))
        return id
    }

    suspend fun renameParticipant(tripId: String, id: String, raw: String) {
        val current = requireNotNull(finance.findParticipant(tripId, id))
        val name = raw.trim()
        require(name.length in 1..80)
        require(finance.renameParticipant(current.copy(
            displayName = name, updatedAtMillis = clock.millis()
        )) == 1)
    }

    suspend fun deleteParticipant(tripId: String, id: String) {
        require(finance.deleteUnusedParticipant(tripId, id)) {
            "참가자를 삭제할 수 없습니다."
        }
    }

    suspend fun saveRate(
        tripId: String,
        expenseId: String,
        rateInput: String,
        rateDateInput: String
    ) {
        val trip = requireNotNull(finance.getTrip(tripId))
        val expense = requireNotNull(expenses.find(tripId, expenseId))
        val foreign = expense.currencyCode != trip.currencyCode
        val rate = if (foreign) validateExchangeRate(rateInput, rateDateInput) else null
        val date = if (foreign) rateDateInput.trim() else null
        finance.replaceExpenseFinance(
            tripId, expenseId, null, trip.currencyCode, rate, date,
            if (foreign) "사용자 입력" else null,
            clock.millis(), emptyList()
        )
    }

    suspend fun saveAllocation(
        tripId: String,
        expenseId: String,
        payerParticipantId: String,
        rateInput: String,
        rateDateInput: String,
        mode: SplitMode,
        shareInputs: List<Pair<String, String>>
    ) {
        val trip = requireNotNull(finance.getTrip(tripId))
        val expense = requireNotNull(expenses.find(tripId, expenseId))
        require(finance.findParticipant(tripId, payerParticipantId) != null) {
            "결제자가 이 여행의 참가자가 아닙니다."
        }
        require(shareInputs.isNotEmpty() && shareInputs.size <= 100)
        val unique = shareInputs.map { it.first }
        require(unique.size == unique.distinct().size) { "중복 참가자입니다." }
        for (id in unique) {
            require(finance.findParticipant(tripId, id) != null) { "다른 여행의 참가자입니다." }
        }
        val foreign = expense.currencyCode != trip.currencyCode
        val rate = if (foreign) validateExchangeRate(rateInput, rateDateInput) else null
        val date = if (foreign) rateDateInput.trim() else null
        val context = expense.copy(
            payerParticipantId = payerParticipantId,
            baseCurrencyCode = trip.currencyCode,
            rateToBaseDecimal = rate,
            rateDate = date,
            rateSource = if (foreign) "사용자 입력" else null
        )
        val totalMinor = requireNotNull(
            convertedMinor(context, trip.currencyCode, rate)
        ) { "환산 결과가 유효하지 않습니다." }
        val amounts = allocateShares(totalMinor, mode, shareInputs, trip.currencyCode)
        val rows = amounts.map { amount ->
            ExpenseShareEntity(expenseId, amount.participantId, amount.amountMinor, trip.currencyCode)
        }
        finance.replaceExpenseFinance(
            tripId, expenseId, payerParticipantId, trip.currencyCode,
            rate, date, if (foreign) "사용자 입력" else null,
            clock.millis(), rows
        )
    }

    suspend fun confirm(tripId: String) =
        finance.confirmCurrentSettlement(tripId, clock.millis())

    suspend fun revokeConfirmation(tripId: String) = finance.clearConfirmation(tripId)
}
