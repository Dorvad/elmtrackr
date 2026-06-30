package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.entity.SyncStatus
import java.time.Instant

enum class SyncEntityType(val label: String) {
    TASKS("Tasks"),
    SHIFTS("Shifts"),
    REFUND_CLAIMS("Refund claims"),
    USER_SETTINGS("Settings"),
    COMPENSATION_PROFILES("Pay profiles"),
}

data class SyncPendingByType(
    val entityType: SyncEntityType,
    val pendingCount: Int,
    val syncedCount: Int,
)

data class SyncFailedRow(
    val entityType: SyncEntityType,
    val localId: String,
    val label: String,
    val syncStatus: SyncStatus,
    val errorMessage: String?,
)

data class SyncDetails(
    val lastSuccessfulSyncAt: Instant?,
    val lastSyncStatus: String?,
    val pendingByType: List<SyncPendingByType>,
    val failedRows: List<SyncFailedRow>,
    val totalPending: Int,
    val totalFailed: Int,
)
