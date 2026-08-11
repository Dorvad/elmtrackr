package com.elmtrackr.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A workplace's leave arrangement, effective-dated like a compensation profile
 * so that editing it today cannot restate what last month's absence was
 * estimated to pay.
 *
 * The sick ladder and vacation basis live in [rulesJson] rather than in columns:
 * the shape is a list of tiers whose length is up to the user, and every
 * calculation reads it as data.
 */
@Entity(
    tableName = "leave_policies",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["workplaceLocalId"]),
        Index(value = ["userId", "syncStatus"]),
        Index(value = ["remoteId"]),
    ],
)
data class LeavePolicyEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val userId: String,
    /** The parent's local id, matching how shifts reference tasks. */
    val workplaceLocalId: String,
    val regionCode: String,
    val rulesJson: String,
    val effectiveFrom: Long,
    val effectiveUntil: Long?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)

/**
 * A period of absence as the user experienced it — one illness, one holiday —
 * and therefore user-level, not per employer.
 *
 * For sick leave that is the whole point: the ordinal day drives the pay ladder
 * and is counted from the illness, so a second job's first missed day keeps the
 * ordinal it has in the illness rather than restarting at one.
 */
@Entity(
    tableName = "absence_events",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "startDate"]),
        Index(value = ["userId", "syncStatus"]),
        Index(value = ["remoteId"]),
    ],
)
data class AbsenceEventEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val userId: String,
    /** `sick` or `vacation`; parsed with AbsenceType.fromPersisted. */
    val type: String,
    /** Epoch day. An absence is a calendar date and must not move with a timezone. */
    val startDate: Long,
    val endDate: Long,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)

/**
 * One absent date at one workplace: the workplace-level half of an absence.
 *
 * [policySnapshotJson] and [calculationSnapshotJson] freeze how the estimate was
 * reached, exactly as `shifts.compensationSnapshotJson` does, so a historical
 * figure stays reproducible after a wage or policy change and is never silently
 * recomputed.
 *
 * [estimatedGrossPay] is a REAL, unlike the TEXT that Paid Projects money uses.
 * These are two different kinds of number: a billed fee is an amount that must
 * round-trip byte for byte, while this is a derived estimate computed from an
 * hourly rate — itself a REAL — and shown as an estimate everywhere it appears.
 * Storing it as text would mean the reports could not add work and leave gross
 * together without crossing between two numeric systems.
 */
@Entity(
    tableName = "absence_allocations",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["absenceEventLocalId"]),
        Index(value = ["workplaceLocalId", "affectedDate"]),
        Index(value = ["userId", "syncStatus"]),
        Index(value = ["remoteId"]),
    ],
)
data class AbsenceAllocationEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val userId: String,
    val absenceEventLocalId: String,
    val workplaceLocalId: String,
    /** Epoch day. */
    val affectedDate: Long,
    val entitlementUnits: Double,
    /** `days` or `hours`. */
    val unit: String,
    val expectedWorkMinutes: Int?,
    val policySnapshotJson: String?,
    val calculationSnapshotJson: String?,
    val estimatedGrossPay: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)

/**
 * A balance read off a payslip, kept as history rather than as a mutable column
 * on the workplace. Entering August's payslip must not erase July's, because the
 * estimate is "the last official number minus what has been reported since" and
 * that needs the date the number was true.
 */
@Entity(
    tableName = "leave_balance_snapshots",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["workplaceLocalId", "balanceType", "asOfDate"]),
        Index(value = ["userId", "syncStatus"]),
        Index(value = ["remoteId"]),
    ],
)
data class LeaveBalanceSnapshotEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val userId: String,
    val workplaceLocalId: String,
    /** `sick` or `vacation`. */
    val balanceType: String,
    val balance: Double,
    /** `days` or `hours`. */
    val unit: String,
    /** Epoch day: a payslip balance is dated to a day. */
    val asOfDate: Long,
    /** `payslip` or `manual`. */
    val source: String,
    val label: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)
