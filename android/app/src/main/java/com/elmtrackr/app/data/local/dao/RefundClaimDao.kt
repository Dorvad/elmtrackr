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
        "SELECT * FROM refund_claims WHERE syncStatus IN " +
            "('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE')"
    )
    fun observePendingSyncClaims(): Flow<List<RefundClaimEntity>>
}
