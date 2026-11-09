package com.hebi.tripcanvas.domain

import com.hebi.tripcanvas.data.local.TripEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency
import java.util.Locale

data class TripInput(
    val title: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val country: String = "",
    val city: String = "",
    val timeZoneId: String = "",
    val currencyCode: String = "",
    val purpose: String = "",
    val note: String = ""
)

enum class TripField {
    Title, StartDate, EndDate, Country, City, TimeZone, Currency, Purpose, Note
}

data class TripValidation(
    val normalized: TripInput?,
    val errors: Map<TripField, String>
) {
    val isValid: Boolean get() = normalized != null && errors.isEmpty()
}

fun validateTrip(input: TripInput): TripValidation {
    val cleaned = input.copy(
        title = input.title.trim(),
        startDate = input.startDate.trim(),
        endDate = input.endDate.trim(),
        country = input.country.trim(),
        city = input.city.trim(),
        timeZoneId = input.timeZoneId.trim(),
        currencyCode = input.currencyCode.trim().uppercase(Locale.ROOT),
        purpose = input.purpose.trim(),
        note = input.note.trim()
    )
    val errors = mutableMapOf<TripField, String>()
    if (cleaned.title.length !in 1..80) {
        errors[TripField.Title] = "여행 제목은 1~80자로 입력해 주세요."
    }
    val start = runCatching { LocalDate.parse(cleaned.startDate) }.getOrNull()
    val end = runCatching { LocalDate.parse(cleaned.endDate) }.getOrNull()
    if (start == null) errors[TripField.StartDate] = "시작일을 선택해 주세요."
    if (end == null) errors[TripField.EndDate] = "종료일을 선택해 주세요."
    if (start != null && end != null && end.isBefore(start)) {
        errors[TripField.EndDate] = "종료일은 시작일보다 빠를 수 없습니다."
    }
    if (cleaned.country.length > 80) {
        errors[TripField.Country] = "국가는 80자 이내로 입력해 주세요."
    }
    if (cleaned.city.length > 80) {
        errors[TripField.City] = "도시는 80자 이내로 입력해 주세요."
    }
    if (runCatching { ZoneId.of(cleaned.timeZoneId) }.isFailure) {
        errors[TripField.TimeZone] = "올바른 IANA 시간대를 입력해 주세요. 예: Asia/Seoul"
    }
    if (runCatching { Currency.getInstance(cleaned.currencyCode) }.isFailure) {
        errors[TripField.Currency] = "올바른 ISO 4217 통화 코드를 입력해 주세요. 예: KRW"
    }
    if (cleaned.purpose.length > 120) {
        errors[TripField.Purpose] = "여행 목적은 120자 이내로 입력해 주세요."
    }
    if (cleaned.note.length > 2000) {
        errors[TripField.Note] = "메모는 2,000자 이내로 입력해 주세요."
    }
    return TripValidation(if (errors.isEmpty()) cleaned else null, errors)
}

fun defaultTripInput(): TripInput {
    val zone = ZoneId.systemDefault().id
    val currency = runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }
        .getOrDefault("KRW")
    val today = LocalDate.now(ZoneId.systemDefault()).toString()
    return TripInput(startDate = today, endDate = today, timeZoneId = zone, currencyCode = currency)
}

fun TripEntity.toInput(): TripInput = TripInput(
    title = title,
    startDate = startDate,
    endDate = endDate,
    country = country,
    city = city,
    timeZoneId = timeZoneId,
    currencyCode = currencyCode,
    purpose = purpose,
    note = note
)

enum class TripPhase(val label: String) {
    Upcoming("예정"),
    Active("여행 중"),
    Completed("완료")
}

fun tripPhase(
    startDate: String,
    endDate: String,
    timeZoneId: String,
    instant: Instant = Instant.now()
): TripPhase {
    val today = instant.atZone(ZoneId.of(timeZoneId)).toLocalDate()
    val start = LocalDate.parse(startDate)
    val end = LocalDate.parse(endDate)
    return when {
        today.isBefore(start) -> TripPhase.Upcoming
        today.isAfter(end) -> TripPhase.Completed
        else -> TripPhase.Active
    }
}

fun TripEntity.phase(instant: Instant = Instant.now()): TripPhase =
    tripPhase(startDate, endDate, timeZoneId, instant)

fun TripEntity.daysUntil(instant: Instant = Instant.now()): Long =
    java.time.temporal.ChronoUnit.DAYS.between(
        instant.atZone(ZoneId.of(timeZoneId)).toLocalDate(),
        LocalDate.parse(startDate)
    )
