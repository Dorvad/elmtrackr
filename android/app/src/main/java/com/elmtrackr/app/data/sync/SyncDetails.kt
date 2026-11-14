package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.entity.SyncStatus
import java.time.Instant

enum class SyncEntityType(val label: String) {
    TASKS("Tasks"),
    SHIFTS("Shifts"),
    REFUND_CLAIMS("Refund claims"),
    USER_SETTINGS("Settings"),
    /** User-facing name for compensation_profiles (+ the workplace each owns). */
    COMPENSATION_PROFILES("Work profiles"),
    /** Leave-owning half of a work profile; never called a "workplace" in the UI. */
    WORKPLACES("Work profile leave"),
    LEAVE_POLICIES("Leave policies"),
    ABSENCE_EVENTS("Absences"),
    ABSENCE_ALLOCATIONS("Absence days"),
    LEAVE_BALANCES("Leave balances"),
    PREMIUM_PROFILES("Premium profiles"),
    PROJECTS("Projects"),
    PROJECT_BILLING("Project billing"),
    PROJECT_PAYMENTS("Project payments"),
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
