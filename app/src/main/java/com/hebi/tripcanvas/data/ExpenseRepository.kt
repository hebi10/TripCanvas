package com.hebi.tripcanvas.data

import com.hebi.tripcanvas.data.local.ExpenseDao
import com.hebi.tripcanvas.data.local.ExpenseEntity
import com.hebi.tripcanvas.data.local.PlaceDao
import com.hebi.tripcanvas.data.local.TripBudgetEntity
import com.hebi.tripcanvas.data.local.TripDao
import com.hebi.tripcanvas.domain.ExpenseInput
import com.hebi.tripcanvas.domain.ValidExpense
import com.hebi.tripcanvas.domain.validateExpense
import com.hebi.tripcanvas.domain.validateMoney
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(
    private val dao: ExpenseDao,
    private val trips: TripDao,
    private val places: PlaceDao,
    private val clock: Clock = Clock.systemUTC()
) {
    fun observe(tripId: String): Flow<List<ExpenseEntity>> = dao.observeForTrip(tripId)
    fun observeItem(tripId: String, itemId: String): Flow<ExpenseEntity?> = dao.observeById(tripId, itemId)
    fun observeBudget(tripId: String): Flow<TripBudgetEntity?> = dao.observeBudget(tripId)

    suspend fun save(tripId: String, input: ExpenseInput, itemId: String? = null): String {
        requireNotNull(trips.findById(tripId)) { "Trip not found" }
        val valid = requireNotNull(validateExpense(input).valid) { "Invalid expense" }
        verifyPlace(tripId, valid)
        val previous = itemId?.let {
            dao.find(tripId, it) ?: throw NoSuchElementException("Expense not found")
        }
        val now = clock.millis()
        if (previous == null) {
            val id = UUID.randomUUID().toString()
            dao.insert(ExpenseEntity(
                id, tripId, valid.amountDecimal, valid.currencyCode,
                valid.occurredAtLocal, valid.category, valid.note, valid.placeId,
                now, now
            ))
            return id
        }
        val financialValueChanged =
            previous.amountDecimal != valid.amountDecimal || previous.currencyCode != valid.currencyCode
        val updated = previous.copy(
                amountDecimal = valid.amountDecimal,
                currencyCode = valid.currencyCode,
                occurredAtLocal = valid.occurredAtLocal,
                category = valid.category,
                note = valid.note,
                placeId = valid.placeId,
                updatedAtMillis = now,
                payerParticipantId = if (financialValueChanged) null else previous.payerParticipantId,
                baseCurrencyCode = if (financialValueChanged) null else previous.baseCurrencyCode,
                rateToBaseDecimal = if (financialValueChanged) null else previous.rateToBaseDecimal,
                rateDate = if (financialValueChanged) null else previous.rateDate,
                rateSource = if (financialValueChanged) null else previous.rateSource
            )
        val affected = if (financialValueChanged) dao.updateAndResetAllocation(updated)
            else dao.update(updated)
        if (affected != 1) throw NoSuchElementException("Expense was removed")
        return previous.id
    }

    suspend fun delete(tripId: String, itemId: String) {
        if (dao.delete(tripId, itemId) != 1) throw NoSuchElementException("Expense not found")
    }

    suspend fun setBudget(tripId: String, amount: String, currencyCode: String) {
        requireNotNull(trips.findById(tripId)) { "Trip not found" }
        val valid = requireNotNull(validateMoney(amount, currencyCode)) { "Invalid budget" }
        dao.upsertBudget(TripBudgetEntity(
            tripId = tripId,
            amountDecimal = valid.amountDecimal,
            currencyCode = valid.currencyCode,
            updatedAtMillis = clock.millis()
        ))
    }

    suspend fun clearBudget(tripId: String) {
        requireNotNull(trips.findById(tripId)) { "Trip not found" }
        dao.clearBudget(tripId)
    }

    private suspend fun verifyPlace(tripId: String, expense: ValidExpense) {
        val placeId = expense.placeId ?: return
        requireNotNull(places.findForTrip(tripId, placeId)) {
            "The selected place is not part of this trip"
        }
    }
}
