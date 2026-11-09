package com.hebi.tripcanvas.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

data class ValidMoney(val amountDecimal: String, val currencyCode: String)

fun validCurrency(code: String): Currency? {
    val currency = runCatching { Currency.getInstance(code.trim().uppercase(Locale.ROOT)) }.getOrNull()
    return currency?.takeIf { it.defaultFractionDigits in 0..3 }
}

fun validateMoney(input: String, currencyInput: String): ValidMoney? {
    val currency = validCurrency(currencyInput) ?: return null
    val raw = input.trim()
    if (!raw.matches(Regex("""[0-9]+(\.[0-9]+)?""")) || raw.length > 30) return null
    return try {
        val amount = BigDecimal(raw)
        if (amount <= BigDecimal.ZERO || amount.precision() > 22) return null
        val scaled = amount.setScale(currency.defaultFractionDigits, RoundingMode.UNNECESSARY)
        ValidMoney(scaled.toPlainString(), currency.currencyCode)
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }
}

fun displayMoney(amountDecimal: String, currencyCode: String): String {
    val currency = requireNotNull(validCurrency(currencyCode))
    val number = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = currency.defaultFractionDigits
        maximumFractionDigits = currency.defaultFractionDigits
        roundingMode = RoundingMode.UNNECESSARY
    }
    return currency.currencyCode + " " + number.format(BigDecimal(amountDecimal))
}
