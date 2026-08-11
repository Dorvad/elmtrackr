package com.elmtrackr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.WorkplaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkplaceDao {

    @Query(
        "SELECT * FROM workplaces WHERE userId = :userId AND isArchived = 0 AND deletedAt IS NULL " +
            "ORDER BY isDefault DESC, createdAt ASC",
    )
    fun observeWorkplaces(userId: String): Flow<List<WorkplaceEntity>>

    @Query(
        "SELECT * FROM workplaces WHERE userId = :userId AND isArchived = 0 AND deletedAt IS NULL " +
            "ORDER BY isDefault DESC, createdAt ASC",
    )
    suspend fun getByUser(userId: String): List<WorkplaceEntity>

    /**
     * Archived workplaces included. A user who left a job still needs to read the
     * leave they reported there, and its history is unreadable without the row
     * that names it.
     */
    @Query("SELECT * FROM workplaces WHERE userId = :userId AND deletedAt IS NULL ORDER BY isDefault DESC, createdAt ASC")
    suspend fun getAllIncludingArchived(userId: String): List<WorkplaceEntity>

    @Query("SELECT * FROM workplaces WHERE userId = :userId AND deletedAt IS NULL ORDER BY isDefault DESC, createdAt ASC")
    fun observeAllIncludingArchived(userId: String): Flow<List<WorkplaceEntity>>

    @Query(
        "SELECT * FROM workplaces WHERE userId = :userId AND isArchived = 0 AND deletedAt IS NULL " +
            "ORDER BY isDefault DESC, createdAt ASC LIMIT 1",
    )
    suspend fun getDefaultWorkplace(userId: String): WorkplaceEntity?

    @Query("SELECT * FROM workplaces WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): WorkplaceEntity?

    @Query("SELECT * FROM workplaces WHERE localId = :localId AND userId = :userId LIMIT 1")
    suspend fun getById(userId: String, localId: String): WorkplaceEntity?

    @Query("SELECT * FROM workplaces WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): WorkplaceEntity?

    @Query("SELECT COUNT(*) FROM workplaces WHERE userId = :userId AND deletedAt IS NULL")
    suspend fun countForUser(userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workplace: WorkplaceEntity)

    @Query("UPDATE workplaces SET isDefault = 0 WHERE userId = :userId")
    suspend fun clearDefaultForUser(userId: String)

    @Query(
        "UPDATE workplaces SET isArchived = 1, syncStatus = :syncStatus, updatedAt = :updatedAt " +
            "WHERE localId = :localId",
    )
    suspend fun archive(localId: String, syncStatus: SyncStatus, updatedAt: Long)

    @Query(
        "UPDATE workplaces SET deletedAt = :deletedAt, syncStatus = :syncStatus, updatedAt = :updatedAt " +
            "WHERE localId = :localId",
    )
    suspend fun softDelete(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long)

    // ── Adoption of pre-workplace data ────────────────────────────────────────
    //
    // Run once, when the first workplace is created. The 18→19 upgrade
    // deliberately leaves shifts and profiles unassigned rather than rewriting
    // them, so this is where existing rows join a workplace — as a normal edit
    // the user can see, not as an invisible migration step.

    @Query(
        "UPDATE compensation_profiles SET workplaceId = :workplaceLocalId, syncStatus = " +
            "CASE WHEN syncStatus = 'SYNCED' THEN 'PENDING_UPDATE' ELSE syncStatus END, " +
            "updatedAt = :updatedAt WHERE userId = :userId AND workplaceId IS NULL AND deletedAt IS NULL",
    )
    suspend fun adoptCompensationProfiles(userId: String, workplaceLocalId: String, updatedAt: Long): Int

    @Query(
        "UPDATE shifts SET workplaceId = :workplaceLocalId, syncStatus = " +
            "CASE WHEN syncStatus = 'SYNCED' THEN 'PENDING_UPDATE' ELSE syncStatus END, " +
            "updatedAt = :updatedAt WHERE userId = :userId AND workplaceId IS NULL AND deletedAt IS NULL",
    )
    suspend fun adoptShifts(userId: String, workplaceLocalId: String, updatedAt: Long): Int

    // ── Sync ──────────────────────────────────────────────────────────────────

    @Query(
        "SELECT * FROM workplaces WHERE userId = :userId AND syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED')",
    )
    suspend fun getPendingSyncWorkplaces(userId: String): List<WorkplaceEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM workplaces WHERE userId = :userId AND syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED') LIMIT 1)",
    )
    suspend fun hasPendingSyncWorkplaces(userId: String): Boolean

    @Query(
        "UPDATE workplaces SET syncStatus = :status, remoteId = :remoteId, lastSyncedAt = :syncedAt, " +
            "lastSyncError = :error WHERE localId = :localId",
    )
    suspend fun updateSyncState(
        localId: String,
        status: SyncStatus,
        remoteId: String?,
        syncedAt: Long?,
        error: String?,
    )

    /** Includes tombstones: a backup is a full-fidelity copy. */
    @Query("SELECT * FROM workplaces WHERE userId = :userId")
    suspend fun getAllForUser(userId: String): List<WorkplaceEntity>

    @Query("UPDATE workplaces SET userId = :userId WHERE userId = 'local-user'")
    suspend fun adoptLegacyUser(userId: String)

    @Query("DELETE FROM workplaces WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
