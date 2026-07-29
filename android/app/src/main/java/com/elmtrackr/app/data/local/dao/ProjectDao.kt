package com.elmtrackr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elmtrackr.app.data.local.entity.ProjectBillingRecordEntity
import com.elmtrackr.app.data.local.entity.ProjectEntity
import com.elmtrackr.app.data.local.entity.ProjectPaymentEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

private const val PENDING_STATUSES =
    "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED')"

@Dao
interface ProjectDao {

    @Query(
        "SELECT * FROM projects WHERE userId = :userId AND deletedAt IS NULL " +
            "ORDER BY archivedAt IS NOT NULL, updatedAt DESC",
    )
    fun observeProjects(userId: String): Flow<List<ProjectEntity>>

    @Query(
        "SELECT * FROM projects WHERE userId = :userId AND deletedAt IS NULL " +
            "AND archivedAt IS NULL AND workStatus != 'ARCHIVED' ORDER BY updatedAt DESC",
    )
    fun observeActiveProjects(userId: String): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE localId = :localId AND deletedAt IS NULL LIMIT 1")
    fun observeProject(localId: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE userId = :userId AND deletedAt IS NULL")
    suspend fun getProjects(userId: String): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE localId = :localId AND userId = :userId LIMIT 1")
    suspend fun getById(userId: String, localId: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): ProjectEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM projects WHERE userId = :userId AND deletedAt IS NULL LIMIT 1)")
    suspend fun hasAnyProject(userId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE userId = :userId AND syncStatus IN $PENDING_STATUSES")
    suspend fun getPendingSyncProjects(userId: String): List<ProjectEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM projects WHERE userId = :userId " +
            "AND syncStatus IN $PENDING_STATUSES LIMIT 1)",
    )
    suspend fun hasPendingSyncProjects(userId: String): Boolean

    @Query(
        "UPDATE projects SET syncStatus = :status, remoteId = :remoteId, " +
            "lastSyncedAt = :syncedAt, lastSyncError = :error WHERE localId = :localId",
    )
    suspend fun updateSyncState(
        localId: String,
        status: SyncStatus,
        remoteId: String?,
        syncedAt: Long?,
        error: String?,
    )

    @Query(
        "UPDATE projects SET archivedAt = :archivedAt, workStatus = :workStatus, " +
            "syncStatus = :syncStatus, updatedAt = :updatedAt WHERE localId = :localId",
    )
    suspend fun setArchived(
        localId: String,
        archivedAt: Long?,
        workStatus: String,
        syncStatus: SyncStatus,
        updatedAt: Long,
    )

    @Query(
        "UPDATE projects SET deletedAt = :deletedAt, syncStatus = :syncStatus, " +
            "updatedAt = :updatedAt WHERE localId = :localId",
    )
    suspend fun softDelete(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long)

    @Query("UPDATE projects SET userId = :userId WHERE userId = 'local-user'")
    suspend fun adoptLegacyUser(userId: String)

    /** Every row including soft-deleted ones, for a full-fidelity backup. */
    @Query("SELECT * FROM projects WHERE userId = :userId")
    suspend fun getAllForUser(userId: String): List<ProjectEntity>

    @Query("DELETE FROM projects WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface ProjectBillingRecordDao {

    @Query(
        "SELECT * FROM project_billing_records WHERE projectLocalId = :projectLocalId " +
            "AND deletedAt IS NULL ORDER BY billedOn ASC",
    )
    fun observeForProject(projectLocalId: String): Flow<List<ProjectBillingRecordEntity>>

    @Query(
        "SELECT * FROM project_billing_records WHERE userId = :userId AND deletedAt IS NULL " +
            "ORDER BY billedOn ASC",
    )
    fun observeForUser(userId: String): Flow<List<ProjectBillingRecordEntity>>

    @Query(
        "SELECT * FROM project_billing_records WHERE projectLocalId = :projectLocalId " +
            "AND deletedAt IS NULL ORDER BY billedOn ASC",
    )
    suspend fun getForProject(projectLocalId: String): List<ProjectBillingRecordEntity>

    @Query("SELECT * FROM project_billing_records WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): ProjectBillingRecordEntity?

    @Query("SELECT * FROM project_billing_records WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): ProjectBillingRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: ProjectBillingRecordEntity)

    @Query(
        "SELECT * FROM project_billing_records WHERE userId = :userId " +
            "AND syncStatus IN $PENDING_STATUSES",
    )
    suspend fun getPendingSyncRecords(userId: String): List<ProjectBillingRecordEntity>

    @Query(
        "UPDATE project_billing_records SET cancelledAt = :cancelledAt, " +
            "syncStatus = :syncStatus, updatedAt = :updatedAt WHERE localId = :localId",
    )
    suspend fun setCancelled(
        localId: String,
        cancelledAt: Long?,
        syncStatus: SyncStatus,
        updatedAt: Long,
    )

    @Query(
        "UPDATE project_billing_records SET deletedAt = :deletedAt, syncStatus = :syncStatus, " +
            "updatedAt = :updatedAt WHERE localId = :localId",
    )
    suspend fun softDelete(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long)

    /** Soft-deletes every record of a project, used when the project is deleted. */
    @Query(
        "UPDATE project_billing_records SET deletedAt = :deletedAt, syncStatus = :syncStatus, " +
            "updatedAt = :updatedAt WHERE projectLocalId = :projectLocalId AND deletedAt IS NULL",
    )
    suspend fun softDeleteForProject(
        projectLocalId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    )

    /** Every row including soft-deleted ones, for a full-fidelity backup. */
    @Query("SELECT * FROM project_billing_records WHERE userId = :userId")
    suspend fun getAllForUser(userId: String): List<ProjectBillingRecordEntity>

    @Query("DELETE FROM project_billing_records WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Dao
interface ProjectPaymentDao {

    @Query(
        "SELECT * FROM project_payments WHERE projectLocalId = :projectLocalId " +
            "AND deletedAt IS NULL ORDER BY paidOn ASC",
    )
    fun observeForProject(projectLocalId: String): Flow<List<ProjectPaymentEntity>>

    @Query(
        "SELECT * FROM project_payments WHERE userId = :userId AND deletedAt IS NULL " +
            "ORDER BY paidOn ASC",
    )
    fun observeForUser(userId: String): Flow<List<ProjectPaymentEntity>>

    @Query(
        "SELECT * FROM project_payments WHERE projectLocalId = :projectLocalId " +
            "AND deletedAt IS NULL ORDER BY paidOn ASC",
    )
    suspend fun getForProject(projectLocalId: String): List<ProjectPaymentEntity>

    @Query(
        "SELECT * FROM project_payments WHERE billingRecordLocalId = :billingRecordLocalId " +
            "AND deletedAt IS NULL ORDER BY paidOn ASC",
    )
    suspend fun getForBillingRecord(billingRecordLocalId: String): List<ProjectPaymentEntity>

    @Query("SELECT * FROM project_payments WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): ProjectPaymentEntity?

    @Query("SELECT * FROM project_payments WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): ProjectPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(payment: ProjectPaymentEntity)

    @Query("SELECT * FROM project_payments WHERE userId = :userId AND syncStatus IN $PENDING_STATUSES")
    suspend fun getPendingSyncPayments(userId: String): List<ProjectPaymentEntity>

    @Query(
        "UPDATE project_payments SET deletedAt = :deletedAt, syncStatus = :syncStatus, " +
            "updatedAt = :updatedAt WHERE localId = :localId",
    )
    suspend fun softDelete(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long)

    @Query(
        "UPDATE project_payments SET deletedAt = :deletedAt, syncStatus = :syncStatus, " +
            "updatedAt = :updatedAt WHERE projectLocalId = :projectLocalId AND deletedAt IS NULL",
    )
    suspend fun softDeleteForProject(
        projectLocalId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    )

    @Query(
        "UPDATE project_payments SET deletedAt = :deletedAt, syncStatus = :syncStatus, " +
            "updatedAt = :updatedAt WHERE billingRecordLocalId = :billingRecordLocalId " +
            "AND deletedAt IS NULL",
    )
    suspend fun softDeleteForBillingRecord(
        billingRecordLocalId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    )

    /** Every row including soft-deleted ones, for a full-fidelity backup. */
    @Query("SELECT * FROM project_payments WHERE userId = :userId")
    suspend fun getAllForUser(userId: String): List<ProjectPaymentEntity>

    @Query("DELETE FROM project_payments WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
