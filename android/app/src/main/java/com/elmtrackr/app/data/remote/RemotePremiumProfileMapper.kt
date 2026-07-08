package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.PremiumProfileEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.PremiumType
import java.util.UUID

fun PremiumProfileEntity.toRemoteInsert(): RemotePremiumProfileInsert =
    RemotePremiumProfileInsert(
        id = localId,
        userId = userId,
        name = name,
        multiplier = multiplier,
        premiumType = PremiumType.toWire(PremiumType.fromPersisted(premiumType)),
        isDefault = isDefault,
        isArchived = isArchived,
    )

fun PremiumProfileEntity.toRemoteUpdate(): RemotePremiumProfileUpdate =
    RemotePremiumProfileUpdate(
        name = name,
        multiplier = multiplier,
        premiumType = PremiumType.toWire(PremiumType.fromPersisted(premiumType)),
        isDefault = isDefault,
        isArchived = isArchived,
    )

fun RemotePremiumProfileRow.toLocalEntity(
    existingLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
): PremiumProfileEntity {
    val created = isoToEpoch(createdAt)
    val updated = isoToEpoch(updatedAt)
    return PremiumProfileEntity(
        localId = existingLocalId ?: UUID.randomUUID().toString(),
        remoteId = id,
        userId = userId,
        name = name,
        multiplier = multiplier,
        premiumType = PremiumType.fromPersisted(premiumType).let(PremiumType::toWire),
        isDefault = isDefault,
        isArchived = isArchived,
        createdAt = created,
        updatedAt = updated,
        deletedAt = if (isArchived) updated else null,
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = updated,
    )
}
