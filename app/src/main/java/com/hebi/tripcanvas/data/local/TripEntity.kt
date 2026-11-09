package com.hebi.tripcanvas.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startDate: String,
    val endDate: String,
    val country: String,
    val city: String,
    val timeZoneId: String,
    val currencyCode: String,
    val purpose: String,
    val note: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
