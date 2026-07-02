package com.elmtrackr.app.fake

import com.elmtrackr.app.domain.model.Receipt
import com.elmtrackr.app.domain.repository.ReceiptsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

class FakeReceiptsRepository : ReceiptsRepository {
    val receipts = mutableMapOf<String, Receipt>()
    val receiptsByClaimId = mutableMapOf<String, Receipt>()
    var deleteFileFailed = false
    val deletedClaimIds = mutableListOf<String>()
    val deletedImagePaths = mutableListOf<String>()

    override suspend fun getById(id: String): Receipt? = receipts[id]

    override suspend fun getByRefundClaimId(refundClaimId: String): Receipt? =
        receiptsByClaimId[refundClaimId]

    override fun observeByRefundClaimId(refundClaimId: String): Flow<Receipt?> =
        flowOf(receiptsByClaimId[refundClaimId])

    override suspend fun save(receipt: Receipt): Receipt {
        receipts[receipt.id] = receipt
        receipt.refundClaimId?.let { receiptsByClaimId[it] = receipt }
        return receipt
    }

    override suspend fun linkToClaim(receiptId: String, refundClaimId: String?) {
        val existing = receipts[receiptId] ?: return
        existing.refundClaimId?.let { receiptsByClaimId.remove(it) }
        val updated = existing.copy(
            refundClaimId = refundClaimId,
            updatedAt = Instant.now(),
        )
        receipts[receiptId] = updated
        refundClaimId?.let { receiptsByClaimId[it] = updated }
    }

    override suspend fun deleteById(id: String) {
        val receipt = receipts.remove(id) ?: return
        receipt.refundClaimId?.let { receiptsByClaimId.remove(it) }
        deletedImagePaths += receipt.localImageUri
    }

    override suspend fun deleteByRefundClaimId(refundClaimId: String): Boolean {
        deletedClaimIds += refundClaimId
        val receipt = receiptsByClaimId.remove(refundClaimId) ?: return false
        receipts.remove(receipt.id)
        deletedImagePaths += receipt.localImageUri
        return deleteFileFailed
    }

    override suspend fun deleteAllForUser(userId: String) {
        val toDelete = receipts.values.filter { it.userId == userId }
        toDelete.forEach { receipt ->
            receipts.remove(receipt.id)
            receipt.refundClaimId?.let { receiptsByClaimId.remove(it) }
            deletedImagePaths += receipt.localImageUri
        }
    }

    fun seedForClaim(
        claimId: String,
        imagePath: String,
        userId: String = "local-user",
    ): Receipt {
        val receipt = Receipt(
            id = "receipt-$claimId",
            userId = userId,
            refundClaimId = claimId,
            localImageUri = imagePath,
            merchantName = "Test Merchant",
            amount = 25.0,
            currency = "ILS",
            receiptDate = Instant.parse("2024-01-08T12:00:00Z"),
            rawOcrText = "sample",
            parserVersion = "1.0.0",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
        receipts[receipt.id] = receipt
        receiptsByClaimId[claimId] = receipt
        return receipt
    }
}
