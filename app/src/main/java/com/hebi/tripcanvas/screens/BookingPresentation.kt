package com.hebi.tripcanvas.screens

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val bookingDateFormatter =
    DateTimeFormatter.ofPattern("yyyy.MM.dd (E) HH:mm", Locale.KOREAN)

internal fun bookingDateLabel(localDateTime: String?): String {
    if (localDateTime.isNullOrBlank()) return "일시 미설정"
    return runCatching {
        LocalDateTime.parse(localDateTime).format(bookingDateFormatter)
    }.getOrDefault("일시 확인 필요")
}

internal fun bookingAttachmentLabel(count: Int): String =
    if (count == 0) "첨부 없음" else "첨부 ${count}개"
