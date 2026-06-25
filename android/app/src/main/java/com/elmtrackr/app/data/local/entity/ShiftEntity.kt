package com.elmtrackr.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shifts",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "endTime"]),
        Index(value = ["syncStatus"]),
    ],
)
data class ShiftEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val userId: String,
    val startTime: Long,
    val endTime: Long?,
    val breakMinutes: Int,
    val notes: String?,
    val isSpecialDay: Boolean,
    val refundAction: String?,
    val compensationProfileId: String? = null,
    val compensationSnapshotJson: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)
