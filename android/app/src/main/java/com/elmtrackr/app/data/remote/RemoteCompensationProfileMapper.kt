package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.StackingPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.UUID

private val profileJson = Json { ignoreUnknownKeys = true }

fun CompensationProfileEntity.toRemoteInsert(): RemoteCompensationProfileInsert =
    RemoteCompensationProfileInsert(
        id = localId,
        userId = userId,
        name = name,
        regionCode = regionToWire(regionCode),
        currencyCode = currencyCode,
        timezone = timezone,
        baseHourlyRate = baseHourlyRate,
        rulesJson = profileJson.parseToJsonElement(rulesJson),
        stackingPolicy = stackingPolicyToWire(stackingPolicy),
        effectiveFrom = epochToIso(effectiveFrom),
        effectiveUntil = effectiveUntil?.let(::epochToIso),
        isDefault = isDefault,
        isArchived = isArchived,
    )

fun CompensationProfileEntity.toRemoteUpdate(): RemoteCompensationProfileUpdate =
    RemoteCompensationProfileUpdate(
        name = name,
        regionCode = regionToWire(regionCode),
        currencyCode = currencyCode,
        timezone = timezone,
        baseHourlyRate = baseHourlyRate,
        rulesJson = profileJson.parseToJsonElement(rulesJson),
        stackingPolicy = stackingPolicyToWire(stackingPolicy),
        effectiveFrom = epochToIso(effectiveFrom),
        effectiveUntil = effectiveUntil?.let(::epochToIso),
        isDefault = isDefault,
        isArchived = isArchived,
    )

fun RemoteCompensationProfileRow.toLocalEntity(
    existingLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
): CompensationProfileEntity {
    val created = isoToEpoch(createdAt)
    val updated = isoToEpoch(updatedAt)
    return CompensationProfileEntity(
        localId = existingLocalId ?: UUID.randomUUID().toString(),
        remoteId = id,
        userId = userId,
        name = name,
        regionCode = RegionCode.fromPersisted(regionCode).name,
        currencyCode = currencyCode,
        timezone = timezone,
        baseHourlyRate = baseHourlyRate,
        rulesJson = profileJson.encodeToString(JsonElement.serializer(), rulesJson),
        stackingPolicy = StackingPolicy.fromPersisted(stackingPolicy).name,
        effectiveFrom = isoToEpoch(effectiveFrom),
        effectiveUntil = effectiveUntil?.let(::isoToEpoch),
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
