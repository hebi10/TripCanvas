package com.hebi.tripcanvas.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "expense_shares",
    primaryKeys = ["expenseId", "participantId"],
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TripParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["participantId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index("expenseId"), Index("participantId")]
)
data class ExpenseShareEntity(
    val expenseId: String,
    val participantId: String,
    val amountMinor: Long,
    val baseCurrencyCode: String,
    val splitMode: String = "AMOUNT",
    val enteredValue: String = ""
)
