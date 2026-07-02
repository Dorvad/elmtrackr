package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.dao.ReceiptDao
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.local.mapper.toDomainOrNull
import com.elmtrackr.app.data.receipt.ReceiptImageStore
import com.elmtrackr.app.domain.model.Receipt
import com.elmtrackr.app.domain.repository.ReceiptsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalReceiptsRepository @Inject constructor(
    private val receiptDao: ReceiptDao,
    private val receiptImageStore: ReceiptImageStore,
) : ReceiptsRepository {

    override suspend fun getById(id: String): Receipt? =
        receiptDao.getById(id).toDomainOrNull { it.toDomain() }

    override suspend fun getByRefundClaimId(refundClaimId: String): Receipt? =
        receiptDao.getByRefundClaimId(refundClaimId).toDomainOrNull { it.toDomain() }

    override fun observeByRefundClaimId(refundClaimId: String): Flow<Receipt?> =
        receiptDao.observeByRefundClaimId(refundClaimId).map { entity ->
            entity.toDomainOrNull { it.toDomain() }
        }

    override suspend fun save(receipt: Receipt): Receipt {
        val now = Instant.now()
        val toSave = receipt.copy(
            updatedAt = now,
            createdAt = if (receipt.createdAt == Instant.EPOCH) now else receipt.createdAt,
        )
        receiptDao.insert(toSave.toEntity())
        return toSave
    }

    override suspend fun linkToClaim(receiptId: String, refundClaimId: String?) {
        receiptDao.linkToClaim(receiptId, refundClaimId, Instant.now().toEpochMilli())
    }

    override suspend fun deleteById(id: String) {
        val receipt = receiptDao.getById(id) ?: return
        receiptDao.deleteById(id)
        receiptImageStore.delete(receipt.localImageUri)
    }

    override suspend fun deleteByRefundClaimId(refundClaimId: String): Boolean {
        val receipt = receiptDao.getByRefundClaimId(refundClaimId) ?: return false
        receiptDao.deleteById(receipt.id)
        return runCatching { receiptImageStore.delete(receipt.localImageUri) }.isFailure
    }

    override suspend fun deleteAllForUser(userId: String) {
        receiptDao.getAllForUser(userId).forEach { receipt ->
            receiptImageStore.delete(receipt.localImageUri)
        }
        receiptDao.deleteAllForUser(userId)
    }
}
