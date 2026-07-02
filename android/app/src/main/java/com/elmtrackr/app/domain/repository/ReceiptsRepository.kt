package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.domain.model.Receipt
import kotlinx.coroutines.flow.Flow

interface ReceiptsRepository {
    suspend fun getById(id: String): Receipt?
    suspend fun getByRefundClaimId(refundClaimId: String): Receipt?
    fun observeByRefundClaimId(refundClaimId: String): Flow<Receipt?>
    suspend fun save(receipt: Receipt): Receipt
    suspend fun linkToClaim(receiptId: String, refundClaimId: String?)
    suspend fun deleteById(id: String)
    suspend fun deleteByRefundClaimId(refundClaimId: String): Boolean
    suspend fun deleteAllForUser(userId: String)
}
