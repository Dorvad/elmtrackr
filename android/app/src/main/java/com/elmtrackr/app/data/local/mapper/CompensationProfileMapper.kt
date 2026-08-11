package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.compensation.CompensationRulesCodec
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.StackingPolicy
import java.time.Instant

fun CompensationProfileEntity.toDomain(): CompensationProfile = CompensationProfile(
    id = localId,
    userId = userId,
    name = name,
    regionCode = RegionCode.fromPersisted(regionCode),
    currencyCode = currencyCode,
    timezone = timezone,
    baseHourlyRate = baseHourlyRate,
    rules = CompensationRulesCodec.decode(rulesJson),
    stackingPolicy = StackingPolicy.fromPersisted(stackingPolicy),
    effectiveFrom = Instant.ofEpochMilli(effectiveFrom),
    effectiveUntil = effectiveUntil?.let { Instant.ofEpochMilli(it) },
    isDefault = isDefault,
    isArchived = isArchived,
    workplaceId = workplaceId,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    remoteId = remoteId,
)

fun CompensationProfile.toEntity(
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    remoteId: String? = null,
    deletedAt: Long? = null,
    lastSyncError: String? = null,
    lastSyncedAt: Long? = null,
): CompensationProfileEntity = CompensationProfileEntity(
    localId = id,
    remoteId = remoteId,
    userId = userId,
    name = name,
    regionCode = regionCode.name,
    currencyCode = currencyCode,
    timezone = timezone,
    baseHourlyRate = baseHourlyRate,
    rulesJson = CompensationRulesCodec.encode(rules),
    stackingPolicy = stackingPolicy.name.lowercase(),
    effectiveFrom = effectiveFrom.toEpochMilli(),
    effectiveUntil = effectiveUntil?.toEpochMilli(),
    isDefault = isDefault,
    isArchived = isArchived,
    workplaceId = workplaceId,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    lastSyncedAt = lastSyncedAt,
)
