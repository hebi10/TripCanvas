package com.hebi.tripcanvas.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class BookingPresentationTest {
    @Test fun formatsLocalDateWithoutTimezoneConversion() {
        assertEquals("2026.11.11 (수) 14:00", bookingDateLabel("2026-11-11T14:00"))
        assertEquals("2026.11.11 (수) 14:00", bookingDateLabel("2026-11-11T14:00:00"))
    }

    @Test fun distinguishesMissingAndInvalidDatesSafely() {
        assertEquals("일시 미설정", bookingDateLabel(null))
        assertEquals("일시 미설정", bookingDateLabel(""))
        assertEquals("일시 확인 필요", bookingDateLabel("invalid-private-value"))
    }

    @Test fun reportsFileCountWithoutExposingDocumentNames() {
        assertEquals("첨부 없음", bookingAttachmentLabel(0))
        assertEquals("첨부 1개", bookingAttachmentLabel(1))
        assertEquals("첨부 3개", bookingAttachmentLabel(3))
    }
}
