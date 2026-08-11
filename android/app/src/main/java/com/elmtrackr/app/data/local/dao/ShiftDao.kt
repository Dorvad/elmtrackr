package com.elmtrackr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {

    @Query("UPDATE shifts SET userId = :userId WHERE userId = 'local-user'")
    suspend fun adoptLegacyUser(userId: String)

    @Query(
        "SELECT * FROM shifts WHERE userId = :userId AND deletedAt IS NULL " +
            "ORDER BY startTime DESC"
    )
    fun observeShifts(userId: String): Flow<List<ShiftEntity>>

    @Query(
        "SELECT * FROM shifts WHERE userId = :userId AND endTime IS NULL " +
            "AND deletedAt IS NULL LIMIT 1"
    )
    fun observeActiveShift(userId: String): Flow<ShiftEntity?>

    /**
     * Returns soft-deleted rows too, on purpose — do not add `deletedAt IS NULL`.
     *
     * Three callers need to see them: `SyncIdMapper` resolves a deleted row's
     * remote id so the delete can be pushed, and `LocalBackupImporter` checks
     * whether a localId is already taken (filtering deleted rows would make the
     * insert collide on the primary key). Screen and notification paths must not
     * see them, which is enforced one layer up: `LocalShiftsRepository` applies
     * `takeIf { it.deletedAt == null }`.
     */
    @Query("SELECT * FROM shifts WHERE localId = :localId")
    suspend fun getShiftById(localId: String): ShiftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: ShiftEntity)

    @Update
    suspend fun updateShift(shift: ShiftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShift(shift: ShiftEntity)

    @Query(
        "UPDATE shifts SET deletedAt = :deletedAt, syncStatus = :syncStatus, " +
            "updatedAt = :updatedAt WHERE localId = :localId"
    )
    suspend fun softDeleteShift(
        localId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    )

    // Mirrors the server's ON DELETE SET NULL on shifts.task_id when a task is
    // deleted locally; the task_* snapshot columns keep the shift's history.
    @Query("UPDATE shifts SET taskId = NULL WHERE userId = :userId AND taskId = :taskId")
    suspend fun detachTaskFromShifts(userId: String, taskId: String)

    @Query(
        "SELECT * FROM shifts WHERE userId = :userId AND syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED')"
    )
    fun observePendingSyncShifts(userId: String): Flow<List<ShiftEntity>>

    @Query(
        "SELECT * FROM shifts WHERE userId = :userId AND syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED')"
    )
    suspend fun getPendingSyncShifts(userId: String): List<ShiftEntity>

    @Query(
        "UPDATE shifts SET syncStatus = :syncStatus, remoteId = :remoteId, " +
            "lastSyncedAt = :lastSyncedAt, lastSyncError = :lastSyncError " +
            "WHERE localId = :localId"
    )
    suspend fun updateSyncState(
        localId: String,
        syncStatus: SyncStatus,
        remoteId: String?,
        lastSyncedAt: Long?,
        lastSyncError: String?,
    )

    /**
     * Marks a row SYNCED only if it hasn't been edited since the push snapshot
     * was taken. Returns 0 when a concurrent edit won; the row must then stay
     * pending so the newer state is pushed by a follow-up sync.
     */
    @Query(
        "UPDATE shifts SET syncStatus = 'SYNCED', remoteId = :remoteId, " +
            "lastSyncedAt = :lastSyncedAt, lastSyncError = NULL " +
            "WHERE localId = :localId AND updatedAt = :expectedUpdatedAt"
    )
    suspend fun markSyncedIfUnchanged(
        localId: String,
        remoteId: String?,
        lastSyncedAt: Long?,
        expectedUpdatedAt: Long,
    ): Int

    /** Records the remote id without touching syncStatus or updatedAt. */
    @Query("UPDATE shifts SET remoteId = :remoteId, lastSyncedAt = :lastSyncedAt WHERE localId = :localId")
    suspend fun attachRemoteId(localId: String, remoteId: String?, lastSyncedAt: Long?)

    @Query(
        "UPDATE shifts SET syncStatus = 'PENDING_CREATE' WHERE userId = :userId " +
            "AND remoteId IS NULL AND syncStatus = 'SYNCED' AND deletedAt IS NULL"
    )
    suspend fun markNeverSyncedPendingCreate(userId: String)

    @Query("SELECT * FROM shifts WHERE userId = :userId AND deletedAt IS NULL")
    suspend fun getAllShiftsForUser(userId: String): List<ShiftEntity>

    /**
     * Completed shifts in a window, for the earnings base a leave estimate
     * averages over. Bounded deliberately: that estimate looks back twelve
     * months, and loading a long-standing user's entire history to average three
     * of them would grow without limit.
     */
    @Query(
        "SELECT * FROM shifts WHERE userId = :userId AND deletedAt IS NULL AND endTime IS NOT NULL " +
            "AND startTime >= :fromEpoch AND startTime < :toEpoch ORDER BY startTime ASC",
    )
    suspend fun getCompletedShiftsInRange(userId: String, fromEpoch: Long, toEpoch: Long): List<ShiftEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM shifts WHERE userId = :userId AND deletedAt IS NULL LIMIT 1)",
    )
    suspend fun hasAnyShifts(userId: String): Boolean

    @Query(
        "SELECT EXISTS(SELECT 1 FROM shifts WHERE userId = :userId AND syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED') LIMIT 1)",
    )
    suspend fun hasPendingSyncShifts(userId: String): Boolean

    @Query("DELETE FROM shifts WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query(
        "SELECT * FROM shifts WHERE userId = :userId AND endTime IS NULL AND deletedAt IS NULL"
    )
    suspend fun getActiveShifts(userId: String): List<ShiftEntity>

    @Query("SELECT * FROM shifts WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getShiftByRemoteId(remoteId: String): ShiftEntity?

    @Query(
        "SELECT * FROM shifts WHERE userId = :userId AND startTime = :startTime " +
            "AND deletedAt IS NULL LIMIT 1",
    )
    suspend fun getShiftByStartTime(userId: String, startTime: Long): ShiftEntity?

    @Query(
        "SELECT * FROM shifts WHERE userId = :userId " +
            "AND endTime IS NOT NULL AND deletedAt IS NULL " +
            "ORDER BY startTime DESC LIMIT :limit"
    )
    fun observeRecentCompletedShifts(userId: String, limit: Int): Flow<List<ShiftEntity>>

    @Query(
        "SELECT * FROM shifts WHERE userId = :userId " +
            "AND startTime >= :fromEpoch AND startTime < :toEpoch " +
            "AND deletedAt IS NULL ORDER BY startTime DESC"
    )
    fun observeShiftsByDateRange(
        userId: String,
        fromEpoch: Long,
        toEpoch: Long,
    ): Flow<List<ShiftEntity>>

    @Query(
        "SELECT * FROM shifts WHERE userId = :userId " +
            "AND startTime >= :fromEpoch AND startTime < :toEpoch " +
            "AND deletedAt IS NULL ORDER BY startTime DESC"
    )
    fun observeShiftsForDay(
        userId: String,
        fromEpoch: Long,
        toEpoch: Long,
    ): Flow<List<ShiftEntity>>

    /**
     * Every shift linked to a project, active or completed. Scoped to the project
     * rather than to a month so a project's tracked hours are complete however
     * long the work ran.
     */
    @Query(
        "SELECT * FROM shifts WHERE userId = :userId AND projectId = :projectId " +
            "AND deletedAt IS NULL ORDER BY startTime DESC"
    )
    fun observeShiftsForProject(userId: String, projectId: String): Flow<List<ShiftEntity>>

    /**
     * Recasts every shift linked to [projectId] as ordinary employee-paid work.
     *
     * Used when a project is deleted. The hours are the user's work record and must
     * survive the project, but a shift left pointing at a deleted project would
     * belong to nothing and be paid by nothing — the state
     * [com.elmtrackr.app.domain.projects.ShiftCompensationReassignment] exists to
     * prevent. Written as one statement rather than a read-modify-write loop so a
     * project with a year of shifts cannot be half-converted.
     *
     * `compensationSnapshot` is cleared deliberately: a snapshot taken while the
     * shift was project time describes rules that never applied to it, so pay is
     * recalculated from current rules instead.
     */
    @Query(
        "UPDATE shifts SET projectId = NULL, projectNameSnapshot = NULL, " +
            "compensationSource = 'EMPLOYEE', compensationSnapshotJson = NULL, " +
            "syncStatus = CASE WHEN syncStatus = 'PENDING_CREATE' THEN 'PENDING_CREATE' " +
            "ELSE 'PENDING_UPDATE' END, updatedAt = :updatedAt " +
            "WHERE userId = :userId AND projectId = :projectId AND deletedAt IS NULL"
    )
    suspend fun unlinkProject(userId: String, projectId: String, updatedAt: Long): Int
}
