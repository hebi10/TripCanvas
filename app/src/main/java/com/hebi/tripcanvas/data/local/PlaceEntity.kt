package com.hebi.tripcanvas.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "places",
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
data class PlaceEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val name: String,
    val address: String,
    val note: String,
    val latitude: Double?,
    val longitude: Double?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
