package com.hebi.tripcanvas.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
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
    indices = [Index(value = ["tripId", "occurredAtLocal"]), Index("placeId")]
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val amountDecimal: String,
    val currencyCode: String,
    val occurredAtLocal: String,
    val category: String,
    val note: String,
    val placeId: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
