package com.elmtrackr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.elmtrackr.app.data.local.entity.ProfileEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("UPDATE profiles SET userId = :userId WHERE userId = 'local-user'")
    suspend fun adoptLegacyUser(userId: String)

    @Query("SELECT * FROM profiles WHERE userId = :userId AND deletedAt IS NULL LIMIT 1")
    fun observeProfile(userId: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE userId = :userId AND deletedAt IS NULL LIMIT 1")
    suspend fun getProfile(userId: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ProfileEntity)

    @Query(
        "SELECT * FROM profiles WHERE userId = :userId AND syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED')"
    )
    suspend fun getPendingSyncProfiles(userId: String): List<ProfileEntity>

    @Query(
        "UPDATE profiles SET syncStatus = :syncStatus, remoteId = :remoteId, " +
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

    @Query("SELECT * FROM profiles WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getProfileByRemoteId(remoteId: String): ProfileEntity?

    @Query("DELETE FROM profiles WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
