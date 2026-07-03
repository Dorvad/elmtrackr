package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.PremiumProfileEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.PremiumType
import java.time.Instant

fun PremiumProfileEntity.toDomain(): PremiumProfile = PremiumProfile(
    id = localId,
    userId = userId,
    name = name,
    multiplier = multiplier,
    premiumType = PremiumType.fromPersisted(premiumType),
    isDefault = isDefault,
    isArchived = isArchived,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    remoteId = remoteId,
)

fun PremiumProfile.toEntity(
    syncStatus: SyncStatus,
    remoteId: String? = this.remoteId,
): PremiumProfileEntity = PremiumProfileEntity(
    localId = id,
    remoteId = remoteId,
    userId = userId,
    name = name,
    multiplier = multiplier,
    premiumType = PremiumType.toWire(premiumType),
    isDefault = isDefault,
    isArchived = isArchived,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = if (isArchived) updatedAt.toEpochMilli() else null,
    syncStatus = syncStatus,
    lastSyncError = null,
    lastSyncedAt = null,
)
