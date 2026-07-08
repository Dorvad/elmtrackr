package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.compensation.CompensationRulesCodec
import com.elmtrackr.app.domain.model.RefundAction
import com.elmtrackr.app.domain.model.Shift
import java.time.Instant

fun ShiftEntity.toDomain(): Shift = Shift(
    id = localId,
    userId = userId,
    startTime = Instant.ofEpochMilli(startTime),
    endTime = endTime?.let { Instant.ofEpochMilli(it) },
    breakMinutes = breakMinutes,
    notes = notes,
    isSpecialDay = isSpecialDay,
    premiumProfileId = premiumProfileId,
    forceRegularRate = forceRegularRate,
    refundAction = RefundAction.fromPersisted(refundAction),
    compensationProfileId = compensationProfileId,
    compensationSnapshot = compensationSnapshotJson
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?.let { CompensationRulesCodec.decodeSnapshot(it) },
    taskId = taskId,
    taskNameSnapshot = taskNameSnapshot,
    taskIconSnapshot = taskIconSnapshot,
    taskHourlyRateSnapshot = taskHourlyRateSnapshot,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun Shift.toEntity(
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    remoteId: String? = null,
    deletedAt: Long? = null,
    lastSyncError: String? = null,
    lastSyncedAt: Long? = null,
): ShiftEntity = ShiftEntity(
    localId = id,
    remoteId = remoteId,
    userId = userId,
    startTime = startTime.toEpochMilli(),
    endTime = endTime?.toEpochMilli(),
    breakMinutes = breakMinutes,
    notes = notes,
    isSpecialDay = isSpecialDay,
    premiumProfileId = premiumProfileId,
    forceRegularRate = forceRegularRate,
    refundAction = refundAction?.name,
    compensationProfileId = compensationProfileId,
    compensationSnapshotJson = compensationSnapshot?.let { CompensationRulesCodec.encodeSnapshot(it) },
    taskId = taskId,
    taskNameSnapshot = taskNameSnapshot,
    taskIconSnapshot = taskIconSnapshot,
    taskHourlyRateSnapshot = taskHourlyRateSnapshot,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    lastSyncedAt = lastSyncedAt,
)
