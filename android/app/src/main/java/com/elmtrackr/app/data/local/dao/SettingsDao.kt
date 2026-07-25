package com.elmtrackr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("UPDATE user_settings SET userId = :userId WHERE userId = 'local-user'")
    suspend fun adoptLegacyUser(userId: String)

    @Query("SELECT * FROM user_settings WHERE userId = :userId AND deletedAt IS NULL LIMIT 1")
    fun observeSettings(userId: String): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE userId = :userId AND deletedAt IS NULL LIMIT 1")
    suspend fun getSettings(userId: String): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: UserSettingsEntity)

    @Update
    suspend fun updateSettings(settings: UserSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: UserSettingsEntity)

    @Query(
        "SELECT * FROM user_settings WHERE userId = :userId AND syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED')"
    )
    fun observePendingSyncSettings(userId: String): Flow<List<UserSettingsEntity>>

    @Query(
        "SELECT * FROM user_settings WHERE userId = :userId AND syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED')"
    )
    suspend fun getPendingSyncSettings(userId: String): List<UserSettingsEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM user_settings WHERE userId = :userId AND syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED') LIMIT 1)",
    )
    suspend fun hasPendingSyncSettings(userId: String): Boolean

    @Query(
        "UPDATE user_settings SET syncStatus = :syncStatus, remoteId = :remoteId, " +
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
        "UPDATE user_settings SET syncStatus = 'SYNCED', remoteId = :remoteId, " +
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
    @Query("UPDATE user_settings SET remoteId = :remoteId, lastSyncedAt = :lastSyncedAt WHERE localId = :localId")
    suspend fun attachRemoteId(localId: String, remoteId: String?, lastSyncedAt: Long?)

    @Query(
        "UPDATE user_settings SET syncStatus = 'PENDING_CREATE' WHERE userId = :userId " +
            "AND remoteId IS NULL AND syncStatus = 'SYNCED' AND deletedAt IS NULL"
    )
    suspend fun markNeverSyncedPendingCreate(userId: String)

    @Query("SELECT * FROM user_settings WHERE userId = :userId")
    suspend fun getAllSettingsForUser(userId: String): List<UserSettingsEntity>

    @Query("DELETE FROM user_settings WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT * FROM user_settings WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getSettingsByRemoteId(remoteId: String): UserSettingsEntity?
}
