package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.AbsenceAllocationEntity
import com.elmtrackr.app.data.local.entity.AbsenceEventEntity
import com.elmtrackr.app.data.local.entity.LeaveBalanceSnapshotEntity
import com.elmtrackr.app.data.local.entity.LeavePolicyEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.WorkplaceEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.UUID

/**
 * Local ⇄ wire for the five leave tables.
 *
 * Two things are deliberate throughout.
 *
 * **Parent ids are passed in, not read here.** An allocation's `workplaceLocalId`
 * cannot be turned into a `workplace_id` without a lookup, and a mapper that did
 * its own lookups would be a mapper that can fail. The caller resolves them
 * through `SyncIdMapper` and skips the push when the parent has no remote id yet.
 *
 * **A tombstone is not an archive.** `deletedAt` is read only from `deleted_at`,
 * never derived from `is_archived`: deriving it once turned every archive into a
 * deletion on the next pull, and an archived workplace has to stay readable
 * because leave reported against it is still history.
 */
private val leaveJson = Json { ignoreUnknownKeys = true }

private fun JsonElement.encode(): String = leaveJson.encodeToString(JsonElement.serializer(), this)

private fun String.decodeJson(): JsonElement = leaveJson.parseToJsonElement(this)

// ── Workplaces ───────────────────────────────────────────────────────────────

fun WorkplaceEntity.toRemoteInsert(): RemoteWorkplaceInsert = RemoteWorkplaceInsert(
    id = localId,
    userId = userId,
    name = name,
    regionCode = regionCode,
    currencyCode = currencyCode,
    timezone = timezone,
    employmentStartDate = employmentStartDate?.toInt(),
    isDefault = isDefault,
    isArchived = isArchived,
    clientUpdatedAt = epochToIso(updatedAt),
)

fun WorkplaceEntity.toRemoteUpdate(): RemoteWorkplaceUpdate = RemoteWorkplaceUpdate(
    name = name,
    regionCode = regionCode,
    currencyCode = currencyCode,
    timezone = timezone,
    employmentStartDate = employmentStartDate?.toInt(),
    isDefault = isDefault,
    isArchived = isArchived,
    deletedAt = deletedAt?.let(::epochToIso),
    clientUpdatedAt = epochToIso(updatedAt),
)

fun RemoteWorkplaceRow.toLocalEntity(
    existingLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
): WorkplaceEntity {
    val updated = isoToEpoch(updatedAt)
    return WorkplaceEntity(
        localId = existingLocalId ?: UUID.randomUUID().toString(),
        remoteId = id,
        userId = userId,
        name = name,
        regionCode = regionCode,
        currencyCode = currencyCode,
        timezone = timezone,
        employmentStartDate = employmentStartDate?.toLong(),
        isDefault = isDefault,
        isArchived = isArchived,
        createdAt = isoToEpoch(createdAt),
        updatedAt = updated,
        deletedAt = deletedAt?.let(::isoToEpoch),
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = updated,
    )
}

// ── Leave policies ───────────────────────────────────────────────────────────

fun LeavePolicyEntity.toRemoteInsert(workplaceRemoteId: String): RemoteLeavePolicyInsert =
    RemoteLeavePolicyInsert(
        id = localId,
        userId = userId,
        workplaceId = workplaceRemoteId,
        regionCode = regionCode,
        rulesJson = rulesJson.decodeJson(),
        effectiveFrom = epochToIso(effectiveFrom),
        effectiveUntil = effectiveUntil?.let(::epochToIso),
        isActive = isActive,
        clientUpdatedAt = epochToIso(updatedAt),
    )

fun LeavePolicyEntity.toRemoteUpdate(): RemoteLeavePolicyUpdate = RemoteLeavePolicyUpdate(
    regionCode = regionCode,
    rulesJson = rulesJson.decodeJson(),
    effectiveFrom = epochToIso(effectiveFrom),
    effectiveUntil = effectiveUntil?.let(::epochToIso),
    isActive = isActive,
    deletedAt = deletedAt?.let(::epochToIso),
    clientUpdatedAt = epochToIso(updatedAt),
)

fun RemoteLeavePolicyRow.toLocalEntity(
    workplaceLocalId: String,
    existingLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
): LeavePolicyEntity {
    val updated = isoToEpoch(updatedAt)
    return LeavePolicyEntity(
        localId = existingLocalId ?: UUID.randomUUID().toString(),
        remoteId = id,
        userId = userId,
        workplaceLocalId = workplaceLocalId,
        regionCode = regionCode,
        rulesJson = rulesJson.encode(),
        effectiveFrom = isoToEpoch(effectiveFrom),
        effectiveUntil = effectiveUntil?.let(::isoToEpoch),
        isActive = isActive,
        createdAt = isoToEpoch(createdAt),
        updatedAt = updated,
        deletedAt = deletedAt?.let(::isoToEpoch),
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = updated,
    )
}

// ── Absence events ───────────────────────────────────────────────────────────

fun AbsenceEventEntity.toRemoteInsert(): RemoteAbsenceEventInsert = RemoteAbsenceEventInsert(
    id = localId,
    userId = userId,
    type = type,
    startDate = startDate.toInt(),
    endDate = endDate.toInt(),
    notes = notes,
    clientUpdatedAt = epochToIso(updatedAt),
)

fun AbsenceEventEntity.toRemoteUpdate(): RemoteAbsenceEventUpdate = RemoteAbsenceEventUpdate(
    type = type,
    startDate = startDate.toInt(),
    endDate = endDate.toInt(),
    notes = notes,
    deletedAt = deletedAt?.let(::epochToIso),
    clientUpdatedAt = epochToIso(updatedAt),
)

fun RemoteAbsenceEventRow.toLocalEntity(
    existingLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
): AbsenceEventEntity {
    val updated = isoToEpoch(updatedAt)
    return AbsenceEventEntity(
        localId = existingLocalId ?: UUID.randomUUID().toString(),
        remoteId = id,
        userId = userId,
        type = type,
        startDate = startDate.toLong(),
        endDate = endDate.toLong(),
        notes = notes,
        createdAt = isoToEpoch(createdAt),
        updatedAt = updated,
        deletedAt = deletedAt?.let(::isoToEpoch),
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = updated,
    )
}

// ── Absence allocations ──────────────────────────────────────────────────────

fun AbsenceAllocationEntity.toRemoteInsert(
    absenceEventRemoteId: String,
    workplaceRemoteId: String,
): RemoteAbsenceAllocationInsert = RemoteAbsenceAllocationInsert(
    id = localId,
    userId = userId,
    absenceEventId = absenceEventRemoteId,
    workplaceId = workplaceRemoteId,
    affectedDate = affectedDate.toInt(),
    entitlementUnits = entitlementUnits,
    unit = unit,
    expectedWorkMinutes = expectedWorkMinutes,
    policySnapshotJson = policySnapshotJson?.decodeJson(),
    calculationSnapshotJson = calculationSnapshotJson?.decodeJson(),
    estimatedGrossPay = estimatedGrossPay,
    clientUpdatedAt = epochToIso(updatedAt),
)

fun AbsenceAllocationEntity.toRemoteUpdate(): RemoteAbsenceAllocationUpdate =
    RemoteAbsenceAllocationUpdate(
        affectedDate = affectedDate.toInt(),
        entitlementUnits = entitlementUnits,
        unit = unit,
        expectedWorkMinutes = expectedWorkMinutes,
        policySnapshotJson = policySnapshotJson?.decodeJson(),
        calculationSnapshotJson = calculationSnapshotJson?.decodeJson(),
        estimatedGrossPay = estimatedGrossPay,
        deletedAt = deletedAt?.let(::epochToIso),
        clientUpdatedAt = epochToIso(updatedAt),
    )

fun RemoteAbsenceAllocationRow.toLocalEntity(
    absenceEventLocalId: String,
    workplaceLocalId: String,
    existingLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
): AbsenceAllocationEntity {
    val updated = isoToEpoch(updatedAt)
    return AbsenceAllocationEntity(
        localId = existingLocalId ?: UUID.randomUUID().toString(),
        remoteId = id,
        userId = userId,
        absenceEventLocalId = absenceEventLocalId,
        workplaceLocalId = workplaceLocalId,
        affectedDate = affectedDate.toLong(),
        entitlementUnits = entitlementUnits,
        unit = unit,
        expectedWorkMinutes = expectedWorkMinutes,
        policySnapshotJson = policySnapshotJson?.encode(),
        calculationSnapshotJson = calculationSnapshotJson?.encode(),
        estimatedGrossPay = estimatedGrossPay,
        createdAt = isoToEpoch(createdAt),
        updatedAt = updated,
        deletedAt = deletedAt?.let(::isoToEpoch),
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = updated,
    )
}

// ── Payslip balance snapshots ────────────────────────────────────────────────

fun LeaveBalanceSnapshotEntity.toRemoteInsert(
    workplaceRemoteId: String,
): RemoteLeaveBalanceSnapshotInsert = RemoteLeaveBalanceSnapshotInsert(
    id = localId,
    userId = userId,
    workplaceId = workplaceRemoteId,
    balanceType = balanceType,
    balance = balance,
    unit = unit,
    asOfDate = asOfDate.toInt(),
    source = source,
    label = label,
    notes = notes,
    clientUpdatedAt = epochToIso(updatedAt),
)

fun LeaveBalanceSnapshotEntity.toRemoteUpdate(): RemoteLeaveBalanceSnapshotUpdate =
    RemoteLeaveBalanceSnapshotUpdate(
        balanceType = balanceType,
        balance = balance,
        unit = unit,
        asOfDate = asOfDate.toInt(),
        source = source,
        label = label,
        notes = notes,
        deletedAt = deletedAt?.let(::epochToIso),
        clientUpdatedAt = epochToIso(updatedAt),
    )

fun RemoteLeaveBalanceSnapshotRow.toLocalEntity(
    workplaceLocalId: String,
    existingLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
): LeaveBalanceSnapshotEntity {
    val updated = isoToEpoch(updatedAt)
    return LeaveBalanceSnapshotEntity(
        localId = existingLocalId ?: UUID.randomUUID().toString(),
        remoteId = id,
        userId = userId,
        workplaceLocalId = workplaceLocalId,
        balanceType = balanceType,
        balance = balance,
        unit = unit,
        asOfDate = asOfDate.toLong(),
        source = source,
        label = label,
        notes = notes,
        createdAt = isoToEpoch(createdAt),
        updatedAt = updated,
        deletedAt = deletedAt?.let(::isoToEpoch),
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = updated,
    )
}
