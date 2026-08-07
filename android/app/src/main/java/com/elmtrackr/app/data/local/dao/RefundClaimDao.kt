package com.elmtrackr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RefundClaimDao {

    @Query("UPDATE refund_claims SET userId = :userId WHERE userId = 'local-user'")
    suspend fun adoptLegacyUser(userId: String)

    @Query(
        "SELECT * FROM refund_claims WHERE userId = :userId AND deletedAt IS NULL " +
            "ORDER BY rideAt DESC"
    )
    fun observeClaimsForUser(userId: String): Flow<List<RefundClaimEntity>>

    @Query(
        "SELECT * FROM refund_claims WHERE shiftLocalId = :shiftLocalId " +
            "AND deletedAt IS NULL ORDER BY rideAt ASC"
    )
    fun observeClaimsForShift(shiftLocalId: String): Flow<List<RefundClaimEntity>>

    @Query("SELECT * FROM refund_claims WHERE localId = :localId")
    suspend fun getClaimById(localId: String): RefundClaimEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClaim(claim: RefundClaimEntity)

    @Update
    suspend fun updateClaim(claim: RefundClaimEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClaim(claim: RefundClaimEntity)

    @Query(
        "UPDATE refund_claims SET deletedAt = :deletedAt, syncStatus = :syncStatus, " +
            "updatedAt = :updatedAt WHERE localId = :localId"
    )
    suspend fun softDeleteClaim(
        localId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    )

    @Query(
        "UPDATE refund_claims SET deletedAt = :deletedAt, syncStatus = :syncStatus, " +
            "updatedAt = :updatedAt WHERE shiftLocalId = :shiftLocalId AND deletedAt IS NULL"
    )
    suspend fun softDeleteClaimsForShift(
        shiftLocalId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    )

    @Query(
        "SELECT * FROM refund_claims WHERE userId = :userId AND syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED')"
    )
    fun observePendingSyncClaims(userId: String): Flow<List<RefundClaimEntity>>

    @Query(
        "SELECT * FROM refund_claims WHERE userId = :userId AND syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED')"
    )
    suspend fun getPendingSyncClaims(userId: String): List<RefundClaimEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM refund_claims WHERE userId = :userId AND syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE', 'FAILED') LIMIT 1)",
    )
    suspend fun hasPendingSyncClaims(userId: String): Boolean

    @Query(
        "UPDATE refund_claims SET syncStatus = :syncStatus, remoteId = :remoteId, " +
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
        "UPDATE refund_claims SET syncStatus = 'SYNCED', remoteId = :remoteId, " +
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
    @Query("UPDATE refund_claims SET remoteId = :remoteId, lastSyncedAt = :lastSyncedAt WHERE localId = :localId")
    suspend fun attachRemoteId(localId: String, remoteId: String?, lastSyncedAt: Long?)

    @Query(
        "UPDATE refund_claims SET syncStatus = 'PENDING_CREATE' WHERE userId = :userId " +
            "AND remoteId IS NULL AND syncStatus = 'SYNCED' AND deletedAt IS NULL"
    )
    suspend fun markNeverSyncedPendingCreate(userId: String)

    @Query("SELECT * FROM refund_claims WHERE userId = :userId AND deletedAt IS NULL")
    suspend fun getAllClaimsForUser(userId: String): List<RefundClaimEntity>

    @Query("DELETE FROM refund_claims WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT * FROM refund_claims WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getClaimByRemoteId(remoteId: String): RefundClaimEntity?

    /**
     * Live claims whose receipt never reached cloud storage.
     *
     * A photo that failed to upload leaves the claim saved with receiptPath null
     * while the image sits in local receipt storage, reachable only from this
     * device. These are the rows the retry pass picks up.
     */
    @Query(
        "SELECT * FROM refund_claims WHERE userId = :userId " +
            "AND deletedAt IS NULL AND receiptPath IS NULL"
    )
    suspend fun getClaimsAwaitingReceiptUpload(userId: String): List<RefundClaimEntity>
}
