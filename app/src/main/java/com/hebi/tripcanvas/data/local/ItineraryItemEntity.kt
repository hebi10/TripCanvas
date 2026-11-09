package com.hebi.tripcanvas.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "itinerary_items",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["tripId", "date", "sortOrder"]),
        Index("placeId")
    ]
)
data class ItineraryItemEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val date: String,
    val title: String,
    val startTime: String?,
    val endTime: String?,
    val placeId: String?,
    val note: String,
    val important: Boolean,
    val confirmed: Boolean,
    val sortOrder: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
