package com.hebi.tripcanvas.domain

import com.hebi.tripcanvas.data.local.ExpenseEntity
import com.hebi.tripcanvas.data.local.ExpenseShareEntity
import com.hebi.tripcanvas.data.local.TripParticipantEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale

enum class SplitMode(val label: String) {
    EQUAL("균등"), AMOUNT("금액 지정"), PERCENT("비율 지정")
}

data class SplitAmount(val participantId: String, val amountMinor: Long)

data class SettlementBalance(
    val participantId: String,
    val displayName: String,
    val paidMinor: Long,
    val owedMinor: Long
) {
    val netMinor: Long get() = paidMinor - owedMinor
}

data class SettlementTransfer(
    val fromParticipantId: String,
    val toParticipantId: String,
    val amountMinor: Long
)

data class SettlementSummary(
    val baseCurrencyCode: String,
    val balances: List<SettlementBalance>,
    val transfers: List<SettlementTransfer>,
    val unconverted: Int,
    val unallocated: Int,
    val includedExpenses: Int,
    val fingerprint: String
) {
    val readyToConfirm: Boolean
        get() = includedExpenses > 0 && unconverted == 0 && unallocated == 0
}

fun validateExchangeRate(raw: String, date: String): String {
    require(runCatching { LocalDate.parse(date) }.isSuccess) { "환율 기준일이 올바르지 않습니다." }
    val normalized = raw.trim()
    require(normalized.matches(Regex("""[0-9]{1,10}(\.[0-9]{1,10})?"""))) {
        "환율은 양수이며 소수점 최대 10자리입니다."
    }
    val amount = BigDecimal(normalized)
    require(amount > BigDecimal.ZERO && amount <= BigDecimal("1000000000")) {
        "환율은 0보다 커야 합니다."
    }
    return amount.stripTrailingZeros().toPlainString()
}

fun convertedMinor(
    expense: ExpenseEntity,
    baseCurrencyCode: String,
    rate: String?
): Long? {
    val base = validCurrency(baseCurrencyCode) ?: return null
    val original = validCurrency(expense.currencyCode) ?: return null
    val sourceAmount = BigDecimal(expense.amountDecimal)
    val factor = if (original.currencyCode == base.currencyCode) {
        BigDecimal.ONE
    } else {
        if (rate == null || expense.rateDate == null || expense.rateSource.isNullOrBlank() ||
            expense.baseCurrencyCode != base.currencyCode
        ) return null
        runCatching { BigDecimal(validateExchangeRate(rate, expense.rateDate)) }.getOrNull()
            ?: return null
    }
    return runCatching {
        sourceAmount.multiply(factor)
            .setScale(base.defaultFractionDigits, RoundingMode.HALF_UP)
            .movePointRight(base.defaultFractionDigits)
            .longValueExact()
    }.getOrNull()
}

fun allocateShares(
    totalMinor: Long,
    mode: SplitMode,
    inputs: List<Pair<String, String>>,
    baseCurrencyCode: String
): List<SplitAmount> {
    require(totalMinor > 0 && inputs.isNotEmpty())
    require(inputs.map { it.first }.distinct().size == inputs.size)
    val base = requireNotNull(validCurrency(baseCurrencyCode))
    val totals: List<Long> = when (mode) {
        SplitMode.EQUAL -> {
            val quotient = totalMinor / inputs.size
            val remainder = totalMinor % inputs.size
            inputs.indices.map { quotient + if (it < remainder) 1L else 0L }
        }
        SplitMode.AMOUNT -> {
            inputs.map { (_, raw) ->
                val text = raw.trim()
                require(text.matches(Regex("""[0-9]+(\.[0-9]+)?""")) && text.length <= 30)
                BigDecimal(text)
                    .setScale(base.defaultFractionDigits, RoundingMode.UNNECESSARY)
                    .movePointRight(base.defaultFractionDigits).longValueExact()
            }
        }
        SplitMode.PERCENT -> {
            val weights = inputs.map { (_, raw) ->
                val value = raw.trim()
                require(value.matches(Regex("""[0-9]+(\.[0-9]{1,4})?""")))
                BigDecimal(value).also { require(it >= BigDecimal.ZERO && it <= BigDecimal("100")) }
            }
            require(weights.fold(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal("100")) == 0) {
                "비율 합계는 100%여야 합니다."
            }
            val divided = weights.map {
                BigDecimal.valueOf(totalMinor).multiply(it)
                    .divide(BigDecimal("100"), 0, RoundingMode.DOWN)
                    .longValueExact()
            }.toMutableList()
            val assigned = divided.fold(0L, Math::addExact)
            val remainder = totalMinor - assigned
            for (i in 0 until remainder.toInt()) {
                divided[i % divided.size] = Math.addExact(divided[i % divided.size], 1L)
            }
            divided
        }
    }
    require(totals.all { it >= 0L })
    require(totals.fold(0L, Math::addExact) == totalMinor) {
        "분담 금액 합계가 지출액과 일치하지 않습니다."
    }
    return inputs.mapIndexed { index, entry -> SplitAmount(entry.first, totals[index]) }
}

fun calculateSettlement(
    baseCurrencyCode: String,
    participants: List<TripParticipantEntity>,
    expenses: List<ExpenseEntity>,
    shares: List<ExpenseShareEntity>
): SettlementSummary {
    val participantById = participants.associateBy { it.id }
    val amountsPaid = participants.associate { it.id to 0L }.toMutableMap()
    val amountsOwed = participants.associate { it.id to 0L }.toMutableMap()
    val grouped = shares.groupBy { it.expenseId }
    var missingRate = 0
    var missingSplit = 0
    var included = 0
    for (expense in expenses) {
        val total = convertedMinor(expense, baseCurrencyCode, expense.rateToBaseDecimal)
        if (total == null) {
            missingRate++
            continue
        }
        val rows = grouped[expense.id].orEmpty()
        val payer = expense.payerParticipantId
        val sum = runCatching { rows.fold(0L) { acc, row -> Math.addExact(acc, row.amountMinor) } }
            .getOrNull()
        if (payer == null || payer !in participantById || rows.isEmpty() ||
            rows.any {
                it.participantId !in participantById ||
                    it.baseCurrencyCode != baseCurrencyCode || it.amountMinor < 0
            } || sum != total
        ) {
            missingSplit++
            continue
        }
        included++
        amountsPaid[payer] = Math.addExact(requireNotNull(amountsPaid[payer]), total)
        rows.forEach {
            amountsOwed[it.participantId] = Math.addExact(
                requireNotNull(amountsOwed[it.participantId]), it.amountMinor
            )
        }
    }
    val balances = participants.map {
        SettlementBalance(
            it.id, it.displayName,
            requireNotNull(amountsPaid[it.id]), requireNotNull(amountsOwed[it.id])
        )
    }
    val creditors = balances.filter { it.netMinor > 0 }.map { it.participantId to it.netMinor }
        .toMutableList()
    val debtors = balances.filter { it.netMinor < 0 }.map { it.participantId to -it.netMinor }
        .toMutableList()
    val transfers = mutableListOf<SettlementTransfer>()
    var creditorIndex = 0
    var debtorIndex = 0
    while (debtorIndex < debtors.size && creditorIndex < creditors.size) {
        val (debtor, owes) = debtors[debtorIndex]
        val (creditor, receives) = creditors[creditorIndex]
        val amount = minOf(owes, receives)
        if (amount > 0) transfers.add(SettlementTransfer(debtor, creditor, amount))
        debtors[debtorIndex] = debtor to (owes - amount)
        creditors[creditorIndex] = creditor to (receives - amount)
        if (owes == amount) debtorIndex++
        if (receives == amount) creditorIndex++
    }
    return SettlementSummary(
        baseCurrencyCode, balances, transfers, missingRate, missingSplit, included,
        settlementFingerprint(baseCurrencyCode, participants, expenses, shares)
    )
}

fun settlementFingerprint(
    baseCurrencyCode: String,
    participants: List<TripParticipantEntity>,
    expenses: List<ExpenseEntity>,
    shares: List<ExpenseShareEntity>
): String {
    val lines = mutableListOf(baseCurrencyCode)
    participants.sortedBy { it.id }.forEach {
        lines.add(listOf("P", it.id, it.displayName, it.updatedAtMillis).joinToString("|"))
    }
    expenses.sortedBy { it.id }.forEach {
        lines.add(listOf(
            "E", it.id, it.amountDecimal, it.currencyCode, it.occurredAtLocal,
            it.payerParticipantId, it.baseCurrencyCode, it.rateToBaseDecimal,
            it.rateDate, it.rateSource, it.updatedAtMillis
        ).joinToString("|"))
    }
    shares.sortedWith(compareBy({ it.expenseId }, { it.participantId })).forEach {
        lines.add(listOf("S", it.expenseId, it.participantId, it.amountMinor, it.baseCurrencyCode, it.splitMode, it.enteredValue).joinToString("|"))
    }
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
}

fun displayMinor(value: Long, currencyCode: String): String {
    val digits = requireNotNull(validCurrency(currencyCode)).defaultFractionDigits
    return displayMoney(BigDecimal.valueOf(value, digits).toPlainString(), currencyCode)
}
