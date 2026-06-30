package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.model.RefundProvider
import java.util.UUID

fun RefundClaimEntity.toRemoteInsert(shiftRemoteId: String): RemoteRefundClaimInsert =
    RemoteRefundClaimInsert(
        shiftId = shiftRemoteId,
        userId = userId,
        direction = enumToSnakeWire(direction),
        provider = providerToWire(provider),
        amount = amount,
        rideAt = epochToIso(rideAt),
        notes = notes,
        receiptPath = receiptPath,
    )

fun RefundClaimEntity.toRemoteUpdate(): RemoteRefundClaimUpdate = RemoteRefundClaimUpdate(
    direction = enumToSnakeWire(direction),
    provider = providerToWire(provider),
    amount = amount,
    rideAt = epochToIso(rideAt),
    notes = notes,
    receiptPath = receiptPath,
)

fun RemoteRefundClaimRow.toLocalEntity(
    shiftLocalId: String,
    existingLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
): RefundClaimEntity {
    val created = isoToEpoch(createdAt)
    val updated = isoToEpoch(updatedAt)
    return RefundClaimEntity(
        localId = existingLocalId ?: UUID.randomUUID().toString(),
        remoteId = id,
        shiftLocalId = shiftLocalId,
        userId = userId,
        direction = RefundDirection.fromPersisted(direction).name,
        provider = RefundProvider.fromPersisted(provider).name,
        amount = amount,
        rideAt = isoToEpoch(rideAt),
        notes = notes,
        receiptPath = receiptPath,
        createdAt = created,
        updatedAt = updated,
        deletedAt = null,
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = updated,
    )
}
