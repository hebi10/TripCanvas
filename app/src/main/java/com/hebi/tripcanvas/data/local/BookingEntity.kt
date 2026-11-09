package com.hebi.tripcanvas.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookings",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class BookingEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val type: String,
    val title: String,
    val startAtLocal: String?,
    val referenceNumber: String,
    val note: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
