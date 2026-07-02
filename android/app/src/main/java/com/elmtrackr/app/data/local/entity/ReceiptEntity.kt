package com.elmtrackr.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "receipts",
    foreignKeys = [
        ForeignKey(
            entity = RefundClaimEntity::class,
            parentColumns = ["localId"],
            childColumns = ["refundClaimId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["refundClaimId"]),
    ],
)
data class ReceiptEntity(
    @PrimaryKey val id: String,
    val userId: String?,
    val refundClaimId: String?,
    val localImageUri: String,
    val merchantName: String?,
    val amount: Double?,
    val currency: String?,
    val receiptDate: Long?,
    val rawOcrText: String?,
    val parserVersion: String,
    val createdAt: Long,
    val updatedAt: Long,
)
