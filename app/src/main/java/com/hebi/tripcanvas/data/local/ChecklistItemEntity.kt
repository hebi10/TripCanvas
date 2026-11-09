package com.hebi.tripcanvas.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "checklist_items",
    foreignKeys = [ForeignKey(
        entity = TripEntity::class,
        parentColumns = ["id"],
        childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["tripId", "position"])]
)
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val title: String,
    val checked: Boolean,
    val position: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
