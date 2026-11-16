package com.hebi.tripcanvas.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "visit_records",
    foreignKeys = [ForeignKey(
        entity = TripEntity::class,
        parentColumns = ["id"],
        childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("tripId"), Index("itineraryItemId")]
)
data class VisitRecordEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val itineraryItemId: String?,
    val titleSnapshot: String,
    val visitAtUtcMillis: Long,
    val note: String,
    val createdAtMillis: Long
)
