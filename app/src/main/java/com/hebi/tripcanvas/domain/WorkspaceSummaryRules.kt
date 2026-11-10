package com.hebi.tripcanvas.domain

import com.hebi.tripcanvas.data.local.ItineraryItemEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

fun findNextItineraryItem(
    items: List<ItineraryItemEntity>,
    timeZoneId: String,
    instant: Instant = Instant.now()
): ItineraryItemEntity? {
    val now = instant.atZone(ZoneId.of(timeZoneId))
    val today = now.toLocalDate()
    return items.asSequence()
        .filter { item ->
            val day = LocalDate.parse(item.date)
            when {
                day.isAfter(today) -> true
                day.isBefore(today) -> false
                item.startTime == null -> true
                else -> !LocalTime.parse(item.startTime).isBefore(now.toLocalTime())
            }
        }
        .sortedWith(
            compareBy<ItineraryItemEntity> { it.date }
                .thenBy { it.startTime ?: "" }
                .thenBy { it.sortOrder }
        )
        .firstOrNull()
}
