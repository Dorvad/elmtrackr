package com.elmtrackr.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shifts",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "endTime"]),
        Index(value = ["userId", "startTime"]),
        Index(value = ["syncStatus"]),
        Index(value = ["userId", "syncStatus"]),
        Index(value = ["remoteId"]),
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
    val taskId: String? = null,
    val taskNameSnapshot: String? = null,
    val taskIconSnapshot: String? = null,
    val taskHourlyRateSnapshot: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)
