package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.AbsenceAllocationEntity
import com.elmtrackr.app.data.local.entity.AbsenceEventEntity
import com.elmtrackr.app.data.local.entity.LeaveBalanceSnapshotEntity
import com.elmtrackr.app.data.local.entity.LeavePolicyEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.WorkplaceEntity
import com.elmtrackr.app.domain.leave.LeavePolicyCodec
import com.elmtrackr.app.domain.model.AbsenceAllocation
import com.elmtrackr.app.domain.model.AbsenceEvent
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.LeaveBalanceSnapshot
import com.elmtrackr.app.domain.model.LeaveBalanceSource
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import com.elmtrackr.app.domain.model.LeavePolicy
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Workplace
import java.time.Instant
import java.time.LocalDate

// Every enum-ish column is read through fromPersisted, never valueOf: these rows
// arrive from the cloud as well as from Room, and a value this build does not
// recognise must not crash the screen that reads it.

// ── Workplace ────────────────────────────────────────────────────────────────

fun WorkplaceEntity.toDomain(): Workplace = Workplace(
    id = localId,
    userId = userId,
    name = name,
    regionCode = RegionCode.fromPersisted(regionCode),
    currencyCode = currencyCode,
    timezone = timezone,
    employmentStartDate = employmentStartDate?.toLocalDateOrNull(),
    isDefault = isDefault,
    isArchived = isArchived,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    remoteId = remoteId,
)

fun Workplace.toEntity(
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    remoteId: String? = this.remoteId,
    deletedAt: Long? = null,
    lastSyncError: String? = null,
    lastSyncedAt: Long? = null,
): WorkplaceEntity = WorkplaceEntity(
    localId = id,
    remoteId = remoteId,
    userId = userId,
    name = name,
    regionCode = regionCode.name.lowercase(),
    currencyCode = currencyCode,
    timezone = timezone,
    employmentStartDate = employmentStartDate?.toEpochDay(),
    isDefault = isDefault,
    isArchived = isArchived,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    lastSyncedAt = lastSyncedAt,
)

// ── Leave policy ─────────────────────────────────────────────────────────────

fun LeavePolicyEntity.toDomain(): LeavePolicy = LeavePolicy(
    id = localId,
    userId = userId,
    workplaceId = workplaceLocalId,
    regionCode = RegionCode.fromPersisted(regionCode),
    rules = LeavePolicyCodec.decode(rulesJson),
    effectiveFrom = Instant.ofEpochMilli(effectiveFrom),
    effectiveUntil = effectiveUntil?.let(Instant::ofEpochMilli),
    isActive = isActive,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    remoteId = remoteId,
)

fun LeavePolicy.toEntity(
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    remoteId: String? = this.remoteId,
    deletedAt: Long? = null,
    lastSyncError: String? = null,
    lastSyncedAt: Long? = null,
): LeavePolicyEntity = LeavePolicyEntity(
    localId = id,
    remoteId = remoteId,
    userId = userId,
    workplaceLocalId = workplaceId,
    regionCode = regionCode.name.lowercase(),
    rulesJson = LeavePolicyCodec.encode(rules),
    effectiveFrom = effectiveFrom.toEpochMilli(),
    effectiveUntil = effectiveUntil?.toEpochMilli(),
    isActive = isActive,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    lastSyncedAt = lastSyncedAt,
)

// ── Absence event ────────────────────────────────────────────────────────────

/**
 * Throws for a row whose leave type is unreadable, which is deliberate: callers
 * map through [mapToDomain] / [toDomainOrNull], so the one bad row is dropped
 * instead of being shown as the wrong kind of leave.
 */
fun AbsenceEventEntity.toDomain(): AbsenceEvent = AbsenceEvent(
    id = localId,
    userId = userId,
    type = requireNotNull(AbsenceType.fromPersisted(type)) { "unknown absence type: $type" },
    startDate = LocalDate.ofEpochDay(startDate),
    endDate = LocalDate.ofEpochDay(endDate),
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    remoteId = remoteId,
)

fun AbsenceEvent.toEntity(
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    remoteId: String? = this.remoteId,
    deletedAt: Long? = null,
    lastSyncError: String? = null,
    lastSyncedAt: Long? = null,
): AbsenceEventEntity = AbsenceEventEntity(
    localId = id,
    remoteId = remoteId,
    userId = userId,
    type = type.persistedValue,
    startDate = startDate.toEpochDay(),
    endDate = endDate.toEpochDay(),
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    lastSyncedAt = lastSyncedAt,
)

// ── Absence allocation ───────────────────────────────────────────────────────

fun AbsenceAllocationEntity.toDomain(): AbsenceAllocation = AbsenceAllocation(
    id = localId,
    userId = userId,
    absenceEventId = absenceEventLocalId,
    workplaceId = workplaceLocalId,
    affectedDate = LocalDate.ofEpochDay(affectedDate),
    entitlementUnits = entitlementUnits,
    unit = LeaveBalanceUnit.fromPersisted(unit),
    expectedWorkMinutes = expectedWorkMinutes,
    policySnapshot = LeavePolicyCodec.decodePolicySnapshot(policySnapshotJson),
    calculationSnapshot = LeavePolicyCodec.decodeCalculationSnapshot(calculationSnapshotJson),
    estimatedGrossPay = estimatedGrossPay,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    remoteId = remoteId,
)

fun AbsenceAllocation.toEntity(
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    remoteId: String? = this.remoteId,
    deletedAt: Long? = null,
    lastSyncError: String? = null,
    lastSyncedAt: Long? = null,
): AbsenceAllocationEntity = AbsenceAllocationEntity(
    localId = id,
    remoteId = remoteId,
    userId = userId,
    absenceEventLocalId = absenceEventId,
    workplaceLocalId = workplaceId,
    affectedDate = affectedDate.toEpochDay(),
    entitlementUnits = entitlementUnits,
    unit = unit.persistedValue,
    expectedWorkMinutes = expectedWorkMinutes,
    policySnapshotJson = policySnapshot?.let(LeavePolicyCodec::encodePolicySnapshot),
    calculationSnapshotJson = calculationSnapshot?.let(LeavePolicyCodec::encodeCalculationSnapshot),
    estimatedGrossPay = estimatedGrossPay,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    lastSyncedAt = lastSyncedAt,
)

// ── Balance snapshot ─────────────────────────────────────────────────────────

fun LeaveBalanceSnapshotEntity.toDomain(): LeaveBalanceSnapshot = LeaveBalanceSnapshot(
    id = localId,
    userId = userId,
    workplaceId = workplaceLocalId,
    balanceType = requireNotNull(AbsenceType.fromPersisted(balanceType)) {
        "unknown balance type: $balanceType"
    },
    balance = balance,
    unit = LeaveBalanceUnit.fromPersisted(unit),
    asOfDate = LocalDate.ofEpochDay(asOfDate),
    source = LeaveBalanceSource.fromPersisted(source),
    label = label,
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    remoteId = remoteId,
)

fun LeaveBalanceSnapshot.toEntity(
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    remoteId: String? = this.remoteId,
    deletedAt: Long? = null,
    lastSyncError: String? = null,
    lastSyncedAt: Long? = null,
): LeaveBalanceSnapshotEntity = LeaveBalanceSnapshotEntity(
    localId = id,
    remoteId = remoteId,
    userId = userId,
    workplaceLocalId = workplaceId,
    balanceType = balanceType.persistedValue,
    balance = balance,
    unit = unit.persistedValue,
    asOfDate = asOfDate.toEpochDay(),
    source = source.persistedValue,
    label = label,
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    lastSyncedAt = lastSyncedAt,
)

private fun Long.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.ofEpochDay(this) }.getOrNull()
