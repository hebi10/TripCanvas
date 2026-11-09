package com.hebi.tripcanvas.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyRulesTest {
    @Test fun currencyFractionsAreExact() {
        assertEquals("1200", validateMoney("1200", "krw")?.amountDecimal)
        assertEquals("12.50", validateMoney("12.5", "USD")?.amountDecimal)
        assertEquals("12.500", validateMoney("12.5", "KWD")?.amountDecimal)
        assertNull(validateMoney("1.01", "JPY"))
        assertNull(validateMoney("12.345", "USD"))
    }

    @Test fun rejectsZeroNegativeScientificAndInvalidCurrency() {
        assertNull(validateMoney("0", "KRW"))
        assertNull(validateMoney("-12", "KRW"))
        assertNull(validateMoney("1e5", "USD"))
        assertNull(validateMoney("1.0", "XXX"))
        assertNull(validateMoney("100.00", "BAD"))
        assertNull(validateMoney("9".repeat(25), "KRW"))
    }

    @Test fun avoidsBinaryFloatingPointArithmetic() {
        val a = validateMoney("0.10", "USD")!!
        val b = validateMoney("0.20", "USD")!!
        val total = BigDecimal(a.amountDecimal) + BigDecimal(b.amountDecimal)
        assertEquals(BigDecimal("0.30"), total)
    }
}
