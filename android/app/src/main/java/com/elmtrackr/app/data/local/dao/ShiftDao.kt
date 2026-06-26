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

    @Query(
        "SELECT * FROM shifts WHERE userId = :userId AND syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE')"
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

    @Query("SELECT * FROM shifts WHERE userId = :userId AND deletedAt IS NULL")
    suspend fun getAllShiftsForUser(userId: String): List<ShiftEntity>

    @Query("DELETE FROM shifts WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query(
        "SELECT * FROM shifts WHERE userId = :userId AND endTime IS NULL AND deletedAt IS NULL"
    )
    suspend fun getActiveShifts(userId: String): List<ShiftEntity>

    @Query("SELECT * FROM shifts WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getShiftByRemoteId(remoteId: String): ShiftEntity?

    @Query(
        "SELECT * FROM shifts WHERE userId = :userId " +
            "AND endTime IS NOT NULL AND deletedAt IS NULL " +
            "ORDER BY startTime DESC LIMIT :limit"
    )
    fun observeRecentCompletedShifts(userId: String, limit: Int): Flow<List<ShiftEntity>>

    @Query(
        "SELECT * FROM shifts WHERE userId = :userId " +
            "AND startTime >= :fromEpoch AND startTime < :toEpoch " +
            "AND deletedAt IS NULL ORDER BY startTime ASC"
    )
    fun observeShiftsByDateRange(
        userId: String,
        fromEpoch: Long,
        toEpoch: Long,
    ): Flow<List<ShiftEntity>>
}
