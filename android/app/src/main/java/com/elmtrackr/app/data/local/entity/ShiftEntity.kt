package com.elmtrackr.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shifts",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "endTime"]),
        // Unique: the sync engine treats (userId, startTime) as a shift's natural key —
        // pushShiftCreate resolves conflicts through findByUserAndStartTime and
        // applyRemoteShift matches remote rows by start time. With two local rows sharing
        // a start time, both adopted the same remoteId and then overwrote each other on
        // the server. Mirrors shifts_user_id_start_time_uidx in Postgres.
        Index(value = ["userId", "startTime"], unique = true),
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
    val premiumProfileId: String? = null,
    @ColumnInfo(defaultValue = "0")
    val forceRegularRate: Boolean = false,
    val refundAction: String?,
    val compensationProfileId: String? = null,
    val compensationSnapshotJson: String? = null,
    val taskId: String? = null,
    val taskNameSnapshot: String? = null,
    val taskIconSnapshot: String? = null,
    val taskHourlyRateSnapshot: Double? = null,
    // Paid Projects link. Nullable and never backfilled: existing shifts stay
    // exactly as they were. Deliberately no project rate column — a project fee
    // must never reach CompensationResolver, which substitutes a shift's task
    // rate for the employee's base pay rate.
    val projectId: String? = null,
    val projectNameSnapshot: String? = null,
    /**
     * EMPLOYEE / PROJECT. Nullable, and NULL reads as EMPLOYEE, so every row
     * written before this column existed keeps its exact wage behaviour.
     */
    val compensationSource: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)
