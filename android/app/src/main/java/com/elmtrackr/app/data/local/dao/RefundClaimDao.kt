package com.elmtrackr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    /**
     * Writes a claim whether or not it already exists. See [upsertClaim], which
     * this is — the two names survive because callers read better with the one
     * that says what they expect to happen.
     */
    suspend fun insertClaim(claim: RefundClaimEntity) = upsertClaim(claim)

    @Update
    suspend fun updateClaim(claim: RefundClaimEntity)

    /**
     * Insert, or update in place — never delete.
     *
     * This used to be `@Insert(onConflict = REPLACE)`, and REPLACE is not an
     * update: SQLite resolves the conflict by deleting the existing row and
     * inserting the new one. `receipts.refundClaimId` references this table with
     * `ON DELETE SET NULL`, so that delete cleared the link on the claim's scanned
     * receipt. Every rewrite of an existing claim did it — an edit, a sync pull,
     * adopting the server's copy — and the receipt image is found through that
     * link alone: viewing it from the claim, restoring it into the form, and
     * retrying an upload that had not reached cloud storage yet.
     *
     * Insert-or-ignore then update is spelled out rather than left to `@Upsert`,
     * whose fallback decides it hit a conflict by reading the exception message.
     * The database here is SQLCipher, not the framework's SQLite, and a write
     * path whose correctness depends on another library's error text is not one
     * to take on for a refund claim. The primary key is this table's only unique
     * constraint, so "ignored" can only mean "this id already exists".
     */
    @Transaction
    suspend fun upsertClaim(claim: RefundClaimEntity) {
        if (insertClaimIfAbsent(claim) == ROW_NOT_INSERTED) updateClaim(claim)
    }

    /** Returns the new row id, or [ROW_NOT_INSERTED] when the id already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClaimIfAbsent(claim: RefundClaimEntity): Long

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

/** What Room returns from an `IGNORE` insert that did not insert. */
private const val ROW_NOT_INSERTED = -1L
