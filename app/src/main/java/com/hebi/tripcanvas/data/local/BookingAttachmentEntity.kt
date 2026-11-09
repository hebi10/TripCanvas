package com.hebi.tripcanvas.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "booking_attachments",
    foreignKeys = [
        ForeignKey(
            entity = BookingEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookingId")]
)
data class BookingAttachmentEntity(
    @PrimaryKey val id: String,
    val bookingId: String,
    val uriString: String,
    val displayName: String,
    val mimeType: String,
    val addedAtMillis: Long
)
