package com.hebi.tripcanvas.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "travel_moments",
    foreignKeys = [ForeignKey(
        entity = TripEntity::class,
        parentColumns = ["id"],
        childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("tripId"), Index("visitId")]
)
data class TravelMomentEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val visitId: String?,
    val photoUri: String?,
    val caption: String,
    val createdAtUtcMillis: Long
)
