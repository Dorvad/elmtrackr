package com.elmtrackr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elmtrackr.app.data.local.entity.PremiumProfileEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PremiumProfileDao {

    @Query(
        "SELECT * FROM premium_profiles WHERE userId = :userId AND isArchived = 0 AND deletedAt IS NULL " +
            "ORDER BY isDefault DESC, name ASC",
    )
    fun observeProfiles(userId: String): Flow<List<PremiumProfileEntity>>

    @Query(
        "SELECT * FROM premium_profiles WHERE userId = :userId AND isArchived = 0 AND deletedAt IS NULL " +
            "ORDER BY isDefault DESC, name ASC",
    )
    suspend fun getByUser(userId: String): List<PremiumProfileEntity>

    @Query("SELECT * FROM premium_profiles WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): PremiumProfileEntity?

    @Query("SELECT * FROM premium_profiles WHERE localId = :localId AND userId = :userId LIMIT 1")
    suspend fun getById(userId: String, localId: String): PremiumProfileEntity?

    @Query("SELECT * FROM premium_profiles WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): PremiumProfileEntity?

    @Query(
        "SELECT * FROM premium_profiles WHERE userId = :userId AND syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED')",
    )
    suspend fun getPendingSyncProfiles(userId: String): List<PremiumProfileEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM premium_profiles WHERE userId = :userId AND syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED') LIMIT 1)",
    )
    suspend fun hasPendingSyncProfiles(userId: String): Boolean

    @Query(
        "SELECT * FROM premium_profiles WHERE userId = :userId AND syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED')",
    )
    fun observePendingSyncProfiles(userId: String): Flow<List<PremiumProfileEntity>>

    @Query("SELECT * FROM premium_profiles WHERE userId = :userId AND deletedAt IS NULL")
    suspend fun getAllProfilesForUser(userId: String): List<PremiumProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: PremiumProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: PremiumProfileEntity)

    @Query(
        "UPDATE premium_profiles SET syncStatus = :status, remoteId = :remoteId, " +
            "lastSyncedAt = :syncedAt, lastSyncError = :error WHERE localId = :localId",
    )
    suspend fun updateSyncState(
        localId: String,
        status: SyncStatus,
        remoteId: String?,
        syncedAt: Long?,
        error: String?,
    )

    @Query("UPDATE premium_profiles SET isDefault = 0 WHERE userId = :userId")
    suspend fun clearDefaultForUser(userId: String)

    @Query("DELETE FROM premium_profiles WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
