package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.model.RefundProvider
import java.time.Instant

fun RefundClaimEntity.toDomain(): RefundClaim = RefundClaim(
    id = localId,
    shiftId = shiftLocalId,
    userId = userId,
    direction = RefundDirection.valueOf(direction),
    provider = RefundProvider.valueOf(provider),
    amount = amount,
    rideAt = Instant.ofEpochMilli(rideAt),
    notes = notes,
    receiptPath = receiptPath,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun RefundClaim.toEntity(
    syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    remoteId: String? = null,
    deletedAt: Long? = null,
    lastSyncError: String? = null,
    lastSyncedAt: Long? = null,
): RefundClaimEntity = RefundClaimEntity(
    localId = id,
    remoteId = remoteId,
    shiftLocalId = shiftId,
    userId = userId,
    direction = direction.name,
    provider = provider.name,
    amount = amount,
    rideAt = rideAt.toEpochMilli(),
    notes = notes,
    receiptPath = receiptPath,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    lastSyncedAt = lastSyncedAt,
)
