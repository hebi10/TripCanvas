package com.hebi.tripcanvas.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "trip_budgets",
    foreignKeys = [ForeignKey(
        entity = TripEntity::class,
        parentColumns = ["id"],
        childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class TripBudgetEntity(
    @PrimaryKey val tripId: String,
    val amountDecimal: String,
    val currencyCode: String,
    val updatedAtMillis: Long
)
