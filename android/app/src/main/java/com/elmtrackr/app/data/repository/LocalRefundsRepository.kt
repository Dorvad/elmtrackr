package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.model.RefundProvider
import com.elmtrackr.app.domain.repository.RefundsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

class LocalRefundsRepository(
    private val refundClaimDao: RefundClaimDao,
) : RefundsRepository {

    override fun observeClaimsForUser(userId: String): Flow<List<RefundClaim>> =
        refundClaimDao.observeClaimsForUser(userId).map { entities -> entities.map { it.toDomain() } }

    override fun observeClaimsForShift(shiftLocalId: String): Flow<List<RefundClaim>> =
        refundClaimDao.observeClaimsForShift(shiftLocalId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getClaimById(localId: String): RefundClaim? =
        refundClaimDao.getClaimById(localId)?.toDomain()

    override suspend fun addClaim(
        shiftLocalId: String,
        userId: String,
        direction: RefundDirection,
        provider: RefundProvider,
        amount: Double,
        notes: String?,
    ): RefundClaim {
        val now = Instant.now().toEpochMilli()
        val entity = RefundClaimEntity(
            localId = UUID.randomUUID().toString(),
            remoteId = null,
            shiftLocalId = shiftLocalId,
            userId = userId,
            direction = direction.name,
            provider = provider.name,
            amount = amount,
            rideAt = now,
            notes = notes,
            receiptPath = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_CREATE,
            lastSyncError = null,
            lastSyncedAt = null,
        )
        refundClaimDao.insertClaim(entity)
        return entity.toDomain()
    }

    override suspend fun updateClaim(claim: RefundClaim): RefundClaim {
        val existing = refundClaimDao.getClaimById(claim.id)
        val newStatus = if (existing?.syncStatus == SyncStatus.SYNCED)
            SyncStatus.PENDING_UPDATE else existing?.syncStatus ?: SyncStatus.PENDING_UPDATE
        val entity = claim.toEntity(
            syncStatus = newStatus,
            remoteId = existing?.remoteId,
            lastSyncedAt = existing?.lastSyncedAt,
        )
        refundClaimDao.upsertClaim(entity)
        return entity.toDomain()
    }

    override suspend fun deleteClaim(localId: String) {
        val now = Instant.now().toEpochMilli()
        refundClaimDao.softDeleteClaim(
            localId = localId,
            deletedAt = now,
            syncStatus = SyncStatus.PENDING_DELETE,
            updatedAt = now,
        )
    }

    override fun observePendingSyncClaims(): Flow<List<RefundClaim>> =
        refundClaimDao.observePendingSyncClaims().map { entities -> entities.map { it.toDomain() } }
}
