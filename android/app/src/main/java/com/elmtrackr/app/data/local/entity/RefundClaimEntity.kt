package com.elmtrackr.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "refund_claims",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["shiftLocalId"]),
        Index(value = ["syncStatus"]),
    ],
)
data class RefundClaimEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val shiftLocalId: String,
    val userId: String,
    val direction: String,
    val provider: String,
    val amount: Double,
    val rideAt: Long,
    val notes: String?,
    val receiptPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)
