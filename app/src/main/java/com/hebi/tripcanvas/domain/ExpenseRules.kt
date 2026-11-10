package com.hebi.tripcanvas.domain

import com.hebi.tripcanvas.data.local.ExpenseEntity
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class ExpenseCategory(val label: String) {
    FOOD("식비"),
    TRANSPORT("교통"),
    LODGING("숙박"),
    SIGHTSEEING("관광"),
    SHOPPING("쇼핑"),
    OTHER("기타")
}

data class ExpenseInput(
    val amount: String = "",
    val currencyCode: String = "KRW",
    val date: String = "",
    val time: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val note: String = "",
    val placeId: String = ""
)

enum class ExpenseField { Amount, Currency, Date, Time, Note }

data class ValidExpense(
    val amountDecimal: String,
    val currencyCode: String,
    val occurredAtLocal: String,
    val category: String,
    val note: String,
    val placeId: String?
)

data class ExpenseValidation(val valid: ValidExpense?, val errors: Map<ExpenseField, String>)

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

fun validateExpense(input: ExpenseInput): ExpenseValidation {
    val errors = mutableMapOf<ExpenseField, String>()
    val currency = validCurrency(input.currencyCode)
    if (currency == null) errors[ExpenseField.Currency] = "ISO 4217 통화를 입력해 주세요."
    val money = validateMoney(input.amount, input.currencyCode)
    if (money == null) errors[ExpenseField.Amount] =
        "0보다 큰 금액을 통화별 소수 자릿수에 맞게 입력해 주세요."
    val date = runCatching { LocalDate.parse(input.date.trim()) }.getOrNull()
    if (date == null) errors[ExpenseField.Date] = "지출 날짜를 선택해 주세요."
    val time = runCatching { LocalTime.parse(input.time.trim(), timeFormat) }.getOrNull()
    if (time == null) errors[ExpenseField.Time] = "24시간제 HH:mm 시각을 입력해 주세요."
    if (input.note.trim().length > 2000) {
        errors[ExpenseField.Note] = "메모는 2,000자 이내로 입력해 주세요."
    }
    return ExpenseValidation(
        if (errors.isEmpty()) ValidExpense(
            amountDecimal = requireNotNull(money).amountDecimal,
            currencyCode = money.currencyCode,
            occurredAtLocal = LocalDateTime.of(requireNotNull(date), requireNotNull(time))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
            category = input.category.name,
            note = input.note.trim(),
            placeId = input.placeId.trim().ifBlank { null }
        ) else null,
        errors
    )
}

fun ExpenseEntity.toInput(): ExpenseInput {
    val local = LocalDateTime.parse(occurredAtLocal)
    return ExpenseInput(
        amount = amountDecimal,
        currencyCode = currencyCode,
        date = local.toLocalDate().toString(),
        time = local.toLocalTime().format(timeFormat),
        category = ExpenseCategory.valueOf(category),
        note = note,
        placeId = placeId.orEmpty()
    )
}

data class ExpenseTotals(
    val baseCurrency: String,
    val baseSpent: BigDecimal,
    val unconvertedByCurrency: Map<String, BigDecimal>,
    val count: Int
)

fun calculateExpenseTotals(
    expenses: List<ExpenseEntity>,
    baseCurrency: String
): ExpenseTotals {
    var spent = BigDecimal.ZERO
    val other = linkedMapOf<String, BigDecimal>()
    for (expense in expenses) {
        val amount = BigDecimal(expense.amountDecimal)
        val converted = com.hebi.tripcanvas.domain.convertedMinor(
            expense, baseCurrency, expense.rateToBaseDecimal
        )
        if (converted != null) {
            val digits = requireNotNull(validCurrency(baseCurrency)).defaultFractionDigits
            spent = spent.add(BigDecimal.valueOf(converted, digits))
        } else {
            other[expense.currencyCode] =
                (other[expense.currencyCode] ?: BigDecimal.ZERO).add(amount)
        }
    }
    return ExpenseTotals(baseCurrency, spent, other, expenses.size)
}
