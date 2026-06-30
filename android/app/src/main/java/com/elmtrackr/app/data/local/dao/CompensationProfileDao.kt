package com.elmtrackr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CompensationProfileDao {

    @Query("SELECT * FROM compensation_profiles WHERE userId = :userId AND isArchived = 0 AND deletedAt IS NULL ORDER BY isDefault DESC, createdAt ASC")
    fun observeProfiles(userId: String): Flow<List<CompensationProfileEntity>>

    @Query("SELECT * FROM compensation_profiles WHERE userId = :userId AND isArchived = 0 AND deletedAt IS NULL ORDER BY isDefault DESC, createdAt ASC")
    suspend fun getByUser(userId: String): List<CompensationProfileEntity>

    @Query("SELECT * FROM compensation_profiles WHERE userId = :userId AND isArchived = 0 AND deletedAt IS NULL ORDER BY isDefault DESC, createdAt ASC LIMIT 1")
    suspend fun getDefaultProfile(userId: String): CompensationProfileEntity?

    @Query("SELECT * FROM compensation_profiles WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): CompensationProfileEntity?

    @Query("SELECT * FROM compensation_profiles WHERE localId = :localId AND userId = :userId LIMIT 1")
    suspend fun getById(userId: String, localId: String): CompensationProfileEntity?

    @Query("SELECT * FROM compensation_profiles WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): CompensationProfileEntity?

    @Query("SELECT * FROM compensation_profiles WHERE userId = :userId AND syncStatus IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED')")
    suspend fun getPendingSyncProfiles(userId: String): List<CompensationProfileEntity>

    @Query("SELECT * FROM compensation_profiles WHERE userId = :userId AND syncStatus IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE')")
    fun observePendingSyncProfiles(userId: String): Flow<List<CompensationProfileEntity>>

    @Query("SELECT * FROM compensation_profiles WHERE userId = :userId AND deletedAt IS NULL")
    suspend fun getAllProfilesForUser(userId: String): List<CompensationProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: CompensationProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: CompensationProfileEntity)

    @Update
    suspend fun update(profile: CompensationProfileEntity)

    @Query(
        "UPDATE compensation_profiles SET syncStatus = :status, remoteId = :remoteId, lastSyncedAt = :syncedAt, lastSyncError = :error WHERE localId = :localId",
    )
    suspend fun updateSyncState(
        localId: String,
        status: SyncStatus,
        remoteId: String?,
        syncedAt: Long?,
        error: String?,
    )

    @Query("UPDATE compensation_profiles SET isDefault = 0 WHERE userId = :userId")
    suspend fun clearDefaultForUser(userId: String)

    @Query("DELETE FROM compensation_profiles WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
