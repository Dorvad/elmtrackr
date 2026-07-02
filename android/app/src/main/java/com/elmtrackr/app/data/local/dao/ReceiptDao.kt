package com.elmtrackr.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.elmtrackr.app.data.local.entity.ReceiptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {

    @Query("SELECT * FROM receipts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE refundClaimId = :refundClaimId LIMIT 1")
    suspend fun getByRefundClaimId(refundClaimId: String): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE refundClaimId = :refundClaimId LIMIT 1")
    fun observeByRefundClaimId(refundClaimId: String): Flow<ReceiptEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(receipt: ReceiptEntity)

    @Update
    suspend fun update(receipt: ReceiptEntity)

    @Query("UPDATE receipts SET refundClaimId = :refundClaimId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun linkToClaim(id: String, refundClaimId: String?, updatedAt: Long)

    @Query("SELECT * FROM receipts WHERE userId = :userId")
    suspend fun getAllForUser(userId: String): List<ReceiptEntity>

    @Query("DELETE FROM receipts WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteById(id: String)
}
