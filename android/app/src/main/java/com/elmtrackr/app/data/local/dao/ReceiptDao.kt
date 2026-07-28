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

    // `userId IS NULL` is included deliberately. A receipt saved before the session
    // resolved a user id (or in local-only mode) is owned by whoever used this device,
    // but keying strictly on userId meant those rows escaped account deletion — leaving
    // the receipt image and its full OCR text on disk — and were missing from backups.
    @Query("SELECT * FROM receipts WHERE userId = :userId OR userId IS NULL")
    suspend fun getAllForUser(userId: String): List<ReceiptEntity>

    @Query("DELETE FROM receipts WHERE userId = :userId OR userId IS NULL")
    suspend fun deleteAllForUser(userId: String)

    /** Claims receipts written before a user id existed, matching the other DAOs' adoption. */
    @Query("UPDATE receipts SET userId = :userId WHERE userId IS NULL OR userId = 'local-user'")
    suspend fun adoptOrphanedReceipts(userId: String)

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteById(id: String)
}
