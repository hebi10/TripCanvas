package com.hebi.tripcanvas.domain

import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import com.hebi.tripcanvas.data.local.TripEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class ScheduleInput(
    val title: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val placeId: String = "",
    val note: String = "",
    val important: Boolean = false,
    val confirmed: Boolean = false
)

enum class ScheduleField { Title, Date, StartTime, EndTime, Note }

data class ValidSchedule(
    val title: String,
    val date: String,
    val startTime: String?,
    val endTime: String?,
    val placeId: String?,
    val note: String,
    val important: Boolean,
    val confirmed: Boolean
)

data class ScheduleValidation(
    val normalized: ValidSchedule?,
    val errors: Map<ScheduleField, String>
)

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

fun validateSchedule(input: ScheduleInput, trip: TripEntity): ScheduleValidation {
    val title = input.title.trim()
    val date = input.date.trim()
    val note = input.note.trim()
    val startText = input.startTime.trim()
    val endText = input.endTime.trim()
    val errors = mutableMapOf<ScheduleField, String>()
    if (title.length !in 1..100) {
        errors[ScheduleField.Title] = "일정 제목은 1~100자로 입력해 주세요."
    }
    if (note.length > 2000) {
        errors[ScheduleField.Note] = "메모는 2,000자 이내로 입력해 주세요."
    }
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
    val first = LocalDate.parse(trip.startDate)
    val last = LocalDate.parse(trip.endDate)
    if (parsedDate == null || parsedDate.isBefore(first) || parsedDate.isAfter(last)) {
        errors[ScheduleField.Date] = "여행 기간 안의 날짜를 선택해 주세요."
    }

    val bothEmpty = startText.isEmpty() && endText.isEmpty()
    val parsedStart = startText.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalTime.parse(it, clockFormat) }.getOrNull()
    }
    val parsedEnd = endText.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalTime.parse(it, clockFormat) }.getOrNull()
    }
    if (!bothEmpty) {
        if (parsedStart == null) errors[ScheduleField.StartTime] = "시작 시각을 HH:mm 형식으로 입력해 주세요."
        if (parsedEnd == null) errors[ScheduleField.EndTime] = "종료 시각을 HH:mm 형식으로 입력해 주세요."
        if (parsedStart != null && parsedEnd != null && parsedEnd.isBefore(parsedStart)) {
            errors[ScheduleField.EndTime] = "종료 시각은 시작 시각보다 빠를 수 없습니다."
        }
    }
    return ScheduleValidation(
        normalized = if (errors.isEmpty()) ValidSchedule(
            title = title,
            date = date,
            startTime = parsedStart?.format(clockFormat),
            endTime = parsedEnd?.format(clockFormat),
            placeId = input.placeId.trim().ifEmpty { null },
            note = note,
            important = input.important,
            confirmed = input.confirmed
        ) else null,
        errors = errors
    )
}

fun scheduleOverlaps(
    start: String?,
    end: String?,
    otherStart: String?,
    otherEnd: String?
): Boolean {
    if (start == null || end == null || otherStart == null || otherEnd == null) return false
    return start < otherEnd && otherStart < end
}

fun overlappingItems(
    schedule: ValidSchedule,
    existing: List<ItineraryItemEntity>,
    exceptId: String? = null
): List<ItineraryItemEntity> = existing.filter {
    it.id != exceptId && it.date == schedule.date &&
        scheduleOverlaps(schedule.startTime, schedule.endTime, it.startTime, it.endTime)
}

fun ItineraryItemEntity.toInput(): ScheduleInput = ScheduleInput(
    title = title,
    date = date,
    startTime = startTime.orEmpty(),
    endTime = endTime.orEmpty(),
    placeId = placeId.orEmpty(),
    note = note,
    important = important,
    confirmed = confirmed
)
