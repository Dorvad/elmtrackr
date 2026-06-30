package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.RefundAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.util.UUID

private val snapshotJson = Json { ignoreUnknownKeys = true }

fun ShiftEntity.toRemoteInsert(): RemoteShiftInsert = RemoteShiftInsert(
    userId = userId,
    startTime = epochToIso(startTime),
    endTime = endTime?.let(::epochToIso),
    breakMinutes = breakMinutes,
    notes = notes,
    isSpecialDay = isSpecialDay,
    refundAction = refundAction?.let(::refundActionToWire),
    compensationProfileId = compensationProfileId,
    compensationSnapshotJson = compensationSnapshotJson?.toJsonElement(),
    taskId = taskId,
    taskNameSnapshot = taskNameSnapshot,
    taskIconSnapshot = taskIconSnapshot,
    taskHourlyRateSnapshot = taskHourlyRateSnapshot,
)

fun ShiftEntity.toRemoteUpdate(): RemoteShiftUpdate = RemoteShiftUpdate(
    startTime = epochToIso(startTime),
    endTime = endTime?.let(::epochToIso),
    breakMinutes = breakMinutes,
    notes = notes,
    isSpecialDay = isSpecialDay,
    refundAction = refundAction?.let(::refundActionToWire),
    compensationProfileId = compensationProfileId,
    compensationSnapshotJson = compensationSnapshotJson?.toJsonElement(),
    taskId = taskId,
    taskNameSnapshot = taskNameSnapshot,
    taskIconSnapshot = taskIconSnapshot,
    taskHourlyRateSnapshot = taskHourlyRateSnapshot,
)

fun RemoteShiftRow.toLocalEntity(
    existingLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
): ShiftEntity {
    val created = isoToEpoch(createdAt)
    val updated = isoToEpoch(updatedAt)
    return ShiftEntity(
        localId = existingLocalId ?: UUID.randomUUID().toString(),
        remoteId = id,
        userId = userId,
        startTime = isoToEpoch(startTime),
        endTime = endTime?.let(::isoToEpoch),
        breakMinutes = breakMinutes,
        notes = notes,
        isSpecialDay = isSpecialDay,
        refundAction = RefundAction.fromPersisted(refundAction)?.name,
        compensationProfileId = compensationProfileId,
        compensationSnapshotJson = compensationSnapshotJson?.toSnapshotString(),
        taskId = taskId,
        taskNameSnapshot = taskNameSnapshot,
        taskIconSnapshot = taskIconSnapshot,
        taskHourlyRateSnapshot = taskHourlyRateSnapshot,
        createdAt = created,
        updatedAt = updated,
        deletedAt = null,
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = updated,
    )
}

fun epochToIso(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).toString()

fun isoToEpoch(iso: String): Long =
    Instant.parse(iso).toEpochMilli()

private fun refundActionToWire(action: String): String =
    action.lowercase()

private fun String.toJsonElement(): JsonElement? =
    takeIf { it.isNotBlank() && !equals("null", ignoreCase = true) }
        ?.let { snapshotJson.parseToJsonElement(it) }

private fun JsonElement.toSnapshotString(): String =
    snapshotJson.encodeToString(JsonElement.serializer(), this)
