package com.hebi.tripcanvas.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptSuggestionParserTest {
    @Test fun recognizesExplicitKoreanTotalDateAndCurrency() {
        val candidate = ReceiptSuggestionParser.parse("서울식당\n2026.11.17\n부가세 1,200원\n결제금액 13,200원")
        assertEquals("13200", candidate.amount)
        assertEquals("KRW", candidate.currencyCode)
        assertEquals("2026-11-17", candidate.date)
        assertEquals("서울식당", candidate.merchant)
        assertTrue(candidate.hasApplicableFields)
    }

    @Test fun noInventedAmountWhenOnlyLineItemsExist() {
        val candidate = ReceiptSuggestionParser.parse("카페\n2026-11-17\n라떼 5000\n아메리카노 4000")
        assertNull(candidate.amount)
        assertNull(candidate.currencyCode)
    }

    @Test fun ambiguousTotalsCannotOverrideUserInput() {
        val candidate = ReceiptSuggestionParser.parse("합계 10,000원\n결제금액 11,000원")
        assertNull(candidate.amount)
        assertNull(candidate.currencyCode)
    }

    @Test fun impossibleDatesAreNotAccepted() {
        assertNull(ReceiptSuggestionParser.parse("총액 10,000원\n2026.02.31").date)
    }

    @Test fun unsupportedFormatStaysManual() {
        val candidate = ReceiptSuggestionParser.parse("카드 승인번호 1234")
        assertFalse(candidate.hasApplicableFields)
    }
}
