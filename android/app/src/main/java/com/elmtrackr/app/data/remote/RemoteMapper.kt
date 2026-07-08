package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.RefundAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.util.UUID

private val snapshotJson = Json { ignoreUnknownKeys = true }

fun ShiftEntity.toRemoteInsert(
    compensationProfileRemoteId: String? = null,
    premiumProfileRemoteId: String? = null,
    taskRemoteId: String? = null,
): RemoteShiftInsert = RemoteShiftInsert(
    userId = userId,
    startTime = epochToIso(startTime),
    endTime = endTime?.let(::epochToIso),
    breakMinutes = breakMinutes,
    notes = notes,
    isSpecialDay = isSpecialDay,
    premiumProfileId = premiumProfileRemoteId,
    forceRegularRate = forceRegularRate,
    refundAction = refundAction?.let(::refundActionToWire),
    compensationProfileId = compensationProfileRemoteId,
    compensationSnapshotJson = compensationSnapshotJson?.toJsonElement(),
    taskId = taskRemoteId,
    taskNameSnapshot = taskNameSnapshot,
    taskIconSnapshot = taskIconSnapshot,
    taskHourlyRateSnapshot = taskHourlyRateSnapshot,
)

fun ShiftEntity.toRemoteUpdate(
    compensationProfileRemoteId: String? = null,
    premiumProfileRemoteId: String? = null,
    taskRemoteId: String? = null,
): RemoteShiftUpdate = RemoteShiftUpdate(
    startTime = epochToIso(startTime),
    endTime = endTime?.let(::epochToIso),
    breakMinutes = breakMinutes,
    notes = notes,
    isSpecialDay = isSpecialDay,
    premiumProfileId = premiumProfileRemoteId,
    forceRegularRate = forceRegularRate,
    refundAction = refundAction?.let(::refundActionToWire),
    compensationProfileId = compensationProfileRemoteId,
    compensationSnapshotJson = compensationSnapshotJson?.toJsonElement(),
    taskId = taskRemoteId,
    taskNameSnapshot = taskNameSnapshot,
    taskIconSnapshot = taskIconSnapshot,
    taskHourlyRateSnapshot = taskHourlyRateSnapshot,
)

fun RemoteShiftRow.toLocalEntity(
    existingLocalId: String? = null,
    compensationProfileLocalId: String? = compensationProfileId,
    premiumProfileLocalId: String? = premiumProfileId,
    taskLocalId: String? = taskId,
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
        premiumProfileId = premiumProfileLocalId,
        forceRegularRate = forceRegularRate,
        refundAction = RefundAction.fromPersisted(refundAction)?.name,
        compensationProfileId = compensationProfileLocalId,
        compensationSnapshotJson = compensationSnapshotJson?.toSnapshotString(),
        taskId = taskLocalId,
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
