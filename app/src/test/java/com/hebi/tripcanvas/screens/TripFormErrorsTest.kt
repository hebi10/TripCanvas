package com.hebi.tripcanvas.screens

import com.hebi.tripcanvas.domain.TripField
import com.hebi.tripcanvas.domain.TripInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripFormErrorsTest {
    @Test fun editingOneFieldPreservesUnrelatedValidationMessages() {
        val original = TripInput(
            title = "", startDate = "2026-11-11", endDate = "2026-11-10",
            timeZoneId = "invalid", currencyCode = "XXX"
        )
        val errors = mapOf(
            TripField.Title to "제목 입력",
            TripField.EndDate to "기간 검증",
            TripField.TimeZone to "현지 시간대 검증"
        )
        val next = original.copy(title = "제주 여행")
        val actual = retainTripFormErrors(original, next, errors)
        assertFalse(actual.containsKey(TripField.Title))
        assertEquals("기간 검증", actual[TripField.EndDate])
        assertEquals("현지 시간대 검증", actual[TripField.TimeZone])
    }

    @Test fun changingEitherDateClearsStalePeriodErrorsTogether() {
        val original = TripInput(startDate = "2026-11-20", endDate = "2026-11-19")
        val errors = mapOf(
            TripField.StartDate to "시작일",
            TripField.EndDate to "종료일",
            TripField.Note to "메모"
        )
        val changed = original.copy(endDate = "2026-11-22")
        val actual = retainTripFormErrors(original, changed, errors)
        assertFalse(actual.containsKey(TripField.StartDate))
        assertFalse(actual.containsKey(TripField.EndDate))
        assertTrue(actual.containsKey(TripField.Note))
    }

    @Test fun unchangedInputRetainsAllMessages() {
        val current = TripInput(title = "제주 여행")
        val errors = mapOf(TripField.Currency to "기준 통화")
        assertEquals(errors, retainTripFormErrors(current, current, errors))
    }
}
