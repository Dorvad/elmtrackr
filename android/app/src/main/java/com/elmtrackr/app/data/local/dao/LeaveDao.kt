package com.elmtrackr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elmtrackr.app.data.local.entity.AbsenceAllocationEntity
import com.elmtrackr.app.data.local.entity.AbsenceEventEntity
import com.elmtrackr.app.data.local.entity.LeaveBalanceSnapshotEntity
import com.elmtrackr.app.data.local.entity.LeavePolicyEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

private const val PENDING_STATUSES = "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED')"

@Dao
interface LeavePolicyDao {

    @Query(
        "SELECT * FROM leave_policies WHERE userId = :userId AND deletedAt IS NULL " +
            "ORDER BY effectiveFrom DESC",
    )
    fun observePolicies(userId: String): Flow<List<LeavePolicyEntity>>

    @Query("SELECT * FROM leave_policies WHERE userId = :userId AND deletedAt IS NULL ORDER BY effectiveFrom DESC")
    suspend fun getByUser(userId: String): List<LeavePolicyEntity>

    /**
     * Every policy the workplace has had, newest first. The caller picks the one
     * effective on the date being calculated rather than assuming the newest
     * applies — an absence reported for last month must be priced by the policy
     * that was in force then.
     */
    @Query(
        "SELECT * FROM leave_policies WHERE workplaceLocalId = :workplaceLocalId AND deletedAt IS NULL " +
            "ORDER BY effectiveFrom DESC",
    )
    suspend fun getForWorkplace(workplaceLocalId: String): List<LeavePolicyEntity>

    @Query("SELECT * FROM leave_policies WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): LeavePolicyEntity?

    @Query("SELECT * FROM leave_policies WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): LeavePolicyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(policy: LeavePolicyEntity)

    @Query(
        "UPDATE leave_policies SET deletedAt = :deletedAt, syncStatus = :syncStatus, updatedAt = :updatedAt " +
            "WHERE localId = :localId",
    )
    suspend fun softDelete(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long)

    @Query(
        "UPDATE leave_policies SET deletedAt = :deletedAt, syncStatus = :syncStatus, updatedAt = :updatedAt " +
            "WHERE workplaceLocalId = :workplaceLocalId AND deletedAt IS NULL",
    )
    suspend fun softDeleteForWorkplace(
        workplaceLocalId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    )

    @Query("SELECT * FROM leave_policies WHERE userId = :userId AND syncStatus IN $PENDING_STATUSES")
    suspend fun getPendingSyncPolicies(userId: String): List<LeavePolicyEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM leave_policies WHERE userId = :userId AND syncStatus IN " +
            "$PENDING_STATUSES LIMIT 1)",
    )
    suspend fun hasPendingSyncPolicies(userId: String): Boolean

    @Query(
        "UPDATE leave_policies SET syncStatus = :status, remoteId = :remoteId, lastSyncedAt = :syncedAt, " +
            "lastSyncError = :error WHERE localId = :localId",
    )
    suspend fun updateSyncState(
        localId: String,
        status: SyncStatus,
        remoteId: String?,
        syncedAt: Long?,
        error: String?,
    )

    @Query("SELECT * FROM leave_policies WHERE userId = :userId")
    suspend fun getAllForUser(userId: String): List<LeavePolicyEntity>

    @Query("UPDATE leave_policies SET userId = :userId WHERE userId = 'local-user'")
    suspend fun adoptLegacyUser(userId: String)

    @Query("DELETE FROM leave_policies WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface AbsenceEventDao {

    @Query("SELECT * FROM absence_events WHERE userId = :userId AND deletedAt IS NULL ORDER BY startDate DESC")
    fun observeEvents(userId: String): Flow<List<AbsenceEventEntity>>

    @Query("SELECT * FROM absence_events WHERE userId = :userId AND deletedAt IS NULL ORDER BY startDate DESC")
    suspend fun getByUser(userId: String): List<AbsenceEventEntity>

    /**
     * Sick periods overlapping or touching a window, for the adjacency check. The
     * one-day margin on each side is what makes "this continues yesterday's
     * illness" detectable, which matters because the ordinal drives the pay
     * ladder.
     */
    @Query(
        "SELECT * FROM absence_events WHERE userId = :userId AND type = :type AND deletedAt IS NULL " +
            "AND endDate >= :fromDate - 1 AND startDate <= :toDate + 1 ORDER BY startDate ASC",
    )
    suspend fun getOverlapping(userId: String, type: String, fromDate: Long, toDate: Long): List<AbsenceEventEntity>

    @Query("SELECT * FROM absence_events WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): AbsenceEventEntity?

    @Query("SELECT * FROM absence_events WHERE localId = :localId AND userId = :userId LIMIT 1")
    suspend fun getById(userId: String, localId: String): AbsenceEventEntity?

    @Query("SELECT * FROM absence_events WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): AbsenceEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: AbsenceEventEntity)

    @Query(
        "UPDATE absence_events SET deletedAt = :deletedAt, syncStatus = :syncStatus, updatedAt = :updatedAt " +
            "WHERE localId = :localId",
    )
    suspend fun softDelete(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long)

    @Query("SELECT * FROM absence_events WHERE userId = :userId AND syncStatus IN $PENDING_STATUSES")
    suspend fun getPendingSyncEvents(userId: String): List<AbsenceEventEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM absence_events WHERE userId = :userId AND syncStatus IN " +
            "$PENDING_STATUSES LIMIT 1)",
    )
    suspend fun hasPendingSyncEvents(userId: String): Boolean

    @Query(
        "UPDATE absence_events SET syncStatus = :status, remoteId = :remoteId, lastSyncedAt = :syncedAt, " +
            "lastSyncError = :error WHERE localId = :localId",
    )
    suspend fun updateSyncState(
        localId: String,
        status: SyncStatus,
        remoteId: String?,
        syncedAt: Long?,
        error: String?,
    )

    @Query("SELECT * FROM absence_events WHERE userId = :userId")
    suspend fun getAllForUser(userId: String): List<AbsenceEventEntity>

    @Query("UPDATE absence_events SET userId = :userId WHERE userId = 'local-user'")
    suspend fun adoptLegacyUser(userId: String)

    @Query("DELETE FROM absence_events WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface AbsenceAllocationDao {

    @Query("SELECT * FROM absence_allocations WHERE userId = :userId AND deletedAt IS NULL ORDER BY affectedDate DESC")
    fun observeAllocations(userId: String): Flow<List<AbsenceAllocationEntity>>

    @Query("SELECT * FROM absence_allocations WHERE userId = :userId AND deletedAt IS NULL ORDER BY affectedDate DESC")
    suspend fun getByUser(userId: String): List<AbsenceAllocationEntity>

    @Query(
        "SELECT * FROM absence_allocations WHERE userId = :userId AND deletedAt IS NULL " +
            "AND affectedDate >= :fromDate AND affectedDate <= :toDate ORDER BY affectedDate ASC",
    )
    fun observeInDateRange(userId: String, fromDate: Long, toDate: Long): Flow<List<AbsenceAllocationEntity>>

    @Query(
        "SELECT * FROM absence_allocations WHERE userId = :userId AND deletedAt IS NULL " +
            "AND affectedDate >= :fromDate AND affectedDate <= :toDate ORDER BY affectedDate ASC",
    )
    suspend fun getInDateRange(userId: String, fromDate: Long, toDate: Long): List<AbsenceAllocationEntity>

    @Query(
        "SELECT * FROM absence_allocations WHERE absenceEventLocalId = :eventLocalId AND deletedAt IS NULL " +
            "ORDER BY affectedDate ASC",
    )
    suspend fun getForEvent(eventLocalId: String): List<AbsenceAllocationEntity>

    /** Drives the estimated balance: this workplace's usage after a payslip date. */
    @Query(
        "SELECT * FROM absence_allocations WHERE workplaceLocalId = :workplaceLocalId AND deletedAt IS NULL " +
            "AND affectedDate > :afterDate ORDER BY affectedDate ASC",
    )
    suspend fun getForWorkplaceAfter(workplaceLocalId: String, afterDate: Long): List<AbsenceAllocationEntity>

    @Query(
        "SELECT * FROM absence_allocations WHERE workplaceLocalId = :workplaceLocalId AND deletedAt IS NULL " +
            "AND affectedDate >= :fromDate AND affectedDate <= :toDate",
    )
    suspend fun getForWorkplaceInRange(
        workplaceLocalId: String,
        fromDate: Long,
        toDate: Long,
    ): List<AbsenceAllocationEntity>

    @Query("SELECT * FROM absence_allocations WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): AbsenceAllocationEntity?

    @Query("SELECT * FROM absence_allocations WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): AbsenceAllocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(allocation: AbsenceAllocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(allocations: List<AbsenceAllocationEntity>)

    @Query(
        "UPDATE absence_allocations SET deletedAt = :deletedAt, syncStatus = :syncStatus, updatedAt = :updatedAt " +
            "WHERE localId = :localId",
    )
    suspend fun softDelete(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long)

    /** Cascade for an edited or deleted event; the repository rebuilds afterwards. */
    @Query(
        "UPDATE absence_allocations SET deletedAt = :deletedAt, syncStatus = :syncStatus, updatedAt = :updatedAt " +
            "WHERE absenceEventLocalId = :eventLocalId AND deletedAt IS NULL",
    )
    suspend fun softDeleteForEvent(
        eventLocalId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    )

    @Query(
        "UPDATE absence_allocations SET deletedAt = :deletedAt, syncStatus = :syncStatus, updatedAt = :updatedAt " +
            "WHERE workplaceLocalId = :workplaceLocalId AND deletedAt IS NULL",
    )
    suspend fun softDeleteForWorkplace(
        workplaceLocalId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    )

    @Query("SELECT * FROM absence_allocations WHERE userId = :userId AND syncStatus IN $PENDING_STATUSES")
    suspend fun getPendingSyncAllocations(userId: String): List<AbsenceAllocationEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM absence_allocations WHERE userId = :userId AND syncStatus IN " +
            "$PENDING_STATUSES LIMIT 1)",
    )
    suspend fun hasPendingSyncAllocations(userId: String): Boolean

    @Query(
        "UPDATE absence_allocations SET syncStatus = :status, remoteId = :remoteId, lastSyncedAt = :syncedAt, " +
            "lastSyncError = :error WHERE localId = :localId",
    )
    suspend fun updateSyncState(
        localId: String,
        status: SyncStatus,
        remoteId: String?,
        syncedAt: Long?,
        error: String?,
    )

    @Query("SELECT * FROM absence_allocations WHERE userId = :userId")
    suspend fun getAllForUser(userId: String): List<AbsenceAllocationEntity>

    @Query("UPDATE absence_allocations SET userId = :userId WHERE userId = 'local-user'")
    suspend fun adoptLegacyUser(userId: String)

    @Query("DELETE FROM absence_allocations WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface LeaveBalanceSnapshotDao {

    @Query(
        "SELECT * FROM leave_balance_snapshots WHERE userId = :userId AND deletedAt IS NULL " +
            "ORDER BY asOfDate DESC, createdAt DESC",
    )
    fun observeSnapshots(userId: String): Flow<List<LeaveBalanceSnapshotEntity>>

    @Query(
        "SELECT * FROM leave_balance_snapshots WHERE userId = :userId AND deletedAt IS NULL " +
            "ORDER BY asOfDate DESC, createdAt DESC",
    )
    suspend fun getByUser(userId: String): List<LeaveBalanceSnapshotEntity>

    @Query(
        "SELECT * FROM leave_balance_snapshots WHERE workplaceLocalId = :workplaceLocalId " +
            "AND balanceType = :balanceType AND deletedAt IS NULL ORDER BY asOfDate DESC, createdAt DESC",
    )
    suspend fun getHistory(workplaceLocalId: String, balanceType: String): List<LeaveBalanceSnapshotEntity>

    /**
     * The balance that governs today. Ordered by date and then by when it was
     * entered, so correcting a mistyped balance for a date already recorded
     * supersedes the earlier row while leaving it in the history.
     */
    @Query(
        "SELECT * FROM leave_balance_snapshots WHERE workplaceLocalId = :workplaceLocalId " +
            "AND balanceType = :balanceType AND deletedAt IS NULL " +
            "ORDER BY asOfDate DESC, createdAt DESC LIMIT 1",
    )
    suspend fun getLatest(workplaceLocalId: String, balanceType: String): LeaveBalanceSnapshotEntity?

    @Query("SELECT * FROM leave_balance_snapshots WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): LeaveBalanceSnapshotEntity?

    @Query("SELECT * FROM leave_balance_snapshots WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): LeaveBalanceSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: LeaveBalanceSnapshotEntity)

    @Query(
        "UPDATE leave_balance_snapshots SET deletedAt = :deletedAt, syncStatus = :syncStatus, " +
            "updatedAt = :updatedAt WHERE localId = :localId",
    )
    suspend fun softDelete(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long)

    @Query(
        "UPDATE leave_balance_snapshots SET deletedAt = :deletedAt, syncStatus = :syncStatus, " +
            "updatedAt = :updatedAt WHERE workplaceLocalId = :workplaceLocalId AND deletedAt IS NULL",
    )
    suspend fun softDeleteForWorkplace(
        workplaceLocalId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    )

    @Query("SELECT * FROM leave_balance_snapshots WHERE userId = :userId AND syncStatus IN $PENDING_STATUSES")
    suspend fun getPendingSyncSnapshots(userId: String): List<LeaveBalanceSnapshotEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM leave_balance_snapshots WHERE userId = :userId AND syncStatus IN " +
            "$PENDING_STATUSES LIMIT 1)",
    )
    suspend fun hasPendingSyncSnapshots(userId: String): Boolean

    @Query(
        "UPDATE leave_balance_snapshots SET syncStatus = :status, remoteId = :remoteId, " +
            "lastSyncedAt = :syncedAt, lastSyncError = :error WHERE localId = :localId",
    )
    suspend fun updateSyncState(
        localId: String,
        status: SyncStatus,
        remoteId: String?,
        syncedAt: Long?,
        error: String?,
    )

    @Query("SELECT * FROM leave_balance_snapshots WHERE userId = :userId")
    suspend fun getAllForUser(userId: String): List<LeaveBalanceSnapshotEntity>

    @Query("UPDATE leave_balance_snapshots SET userId = :userId WHERE userId = 'local-user'")
    suspend fun adoptLegacyUser(userId: String)

    @Query("DELETE FROM leave_balance_snapshots WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
