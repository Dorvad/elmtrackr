package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.StackingPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.UUID

private val profileJson = Json { ignoreUnknownKeys = true }

fun CompensationProfileEntity.toRemoteInsert(
    workplaceRemoteId: String? = null,
): RemoteCompensationProfileInsert =
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
        workplaceId = workplaceRemoteId,
        color = color,
        icon = icon,
        clientUpdatedAt = epochToIso(updatedAt),
    )

fun CompensationProfileEntity.toRemoteUpdate(
    workplaceRemoteId: String? = null,
): RemoteCompensationProfileUpdate =
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
        workplaceId = workplaceRemoteId,
        color = color,
        icon = icon,
        deletedAt = deletedAt?.let(::epochToIso),
        clientUpdatedAt = epochToIso(updatedAt),
    )

fun RemoteCompensationProfileRow.toLocalEntity(
    existingLocalId: String? = null,
    workplaceLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    preserveLocal: CompensationProfileEntity? = null,
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
        color = color,
        icon = icon,
        // The translated remote link, falling back to what the local row held.
        //
        // Without the fallback a pull whose workplace has not landed yet — the link
        // travels as a *remote* id while the entity holds a *local* one — would
        // reset the profile's workplace to null, taking its leave entitlement and
        // payslip balances with it. Before the column travelled at all, every pull
        // did exactly that.
        workplaceId = workplaceLocalId ?: preserveLocal?.workplaceId,
        createdAt = created,
        updatedAt = updated,
        // An explicit tombstone wins; the archived fallback is kept so profiles
        // archived before deleted_at existed keep behaving exactly as they did.
        deletedAt = deletedAt?.let(::isoToEpoch) ?: if (isArchived) updated else null,
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = updated,
    )
}
