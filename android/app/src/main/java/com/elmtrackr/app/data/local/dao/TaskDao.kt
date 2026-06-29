package com.elmtrackr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query(
        "SELECT * FROM tasks WHERE userId = :userId AND isArchived = 0 AND deletedAt IS NULL ORDER BY createdAt DESC",
    )
    fun observeActiveTasks(userId: String): Flow<List<TaskEntity>>

    @Query(
        "SELECT * FROM tasks WHERE userId = :userId AND deletedAt IS NULL ORDER BY isArchived ASC, createdAt DESC",
    )
    fun observeAllTasks(userId: String): Flow<List<TaskEntity>>

    @Query(
        "SELECT * FROM tasks WHERE userId = :userId AND isArchived = 0 AND deletedAt IS NULL ORDER BY createdAt DESC",
    )
    suspend fun getActiveTasks(userId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE localId = :localId AND userId = :userId LIMIT 1")
    suspend fun getById(userId: String, localId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE userId = :userId AND syncStatus != 'SYNCED' AND deletedAt IS NULL")
    suspend fun getPendingSyncTasks(userId: String): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Query(
        "UPDATE tasks SET syncStatus = :status, remoteId = :remoteId, lastSyncedAt = :syncedAt, lastSyncError = :error WHERE localId = :localId",
    )
    suspend fun updateSyncState(
        localId: String,
        status: SyncStatus,
        remoteId: String?,
        syncedAt: Long?,
        error: String?,
    )

    @Query("UPDATE tasks SET lastUsedAt = :lastUsedAt, updatedAt = :updatedAt WHERE localId = :localId")
    suspend fun updateLastUsed(localId: String, lastUsedAt: Long, updatedAt: Long)

    @Query("DELETE FROM tasks WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
