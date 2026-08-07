package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.RefundAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
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
    clientUpdatedAt = epochToIso(updatedAt),
)

/**
 * @param deleted pushes the row as a tombstone. Passing the entity's own
 *   `deletedAt` keeps the delete carrying the same edit time as any other
 *   change, so a delete and a concurrent edit are ordered by the same rule.
 */
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
    deletedAt = deletedAt?.let(::epochToIso),
    clientUpdatedAt = epochToIso(updatedAt),
)

/**
 * @param preserveLocal the row being overwritten, when there is one.
 *
 * The Paid Projects fields — the project link, its name snapshot and the
 * compensation source — have no columns in the remote shift schema, so a pull
 * carries no value for them. Rebuilding the row without them would drop a shift's
 * project and silently turn tracked project time back into paid hourly work on
 * the next sync, which is why the local values are carried forward here rather
 * than defaulted.
 */
fun RemoteShiftRow.toLocalEntity(
    existingLocalId: String? = null,
    compensationProfileLocalId: String? = compensationProfileId,
    premiumProfileLocalId: String? = premiumProfileId,
    taskLocalId: String? = taskId,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    preserveLocal: ShiftEntity? = null,
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
        projectId = preserveLocal?.projectId,
        projectNameSnapshot = preserveLocal?.projectNameSnapshot,
        compensationSource = preserveLocal?.compensationSource,
        createdAt = created,
        updatedAt = updated,
        // Carries the cloud tombstone through to the local row. Without this a
        // delete made on another device arrived as an ordinary update and the
        // shift stayed visible here forever.
        deletedAt = deletedAt?.let(::isoToEpoch),
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = updated,
    )
}

fun epochToIso(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).toString()

/**
 * Parses a timestamp as PostgREST actually writes it, not as `Instant.parse`
 * wishes it were.
 *
 * `Instant.parse` uses `ISO_INSTANT`, which before JDK 12 accepted only a
 * literal `Z` offset. This module is minSdk 26 with no core library desugaring,
 * so on older devices the platform `java.time` is the pre-12 behaviour — and
 * PostgREST serialises `timestamptz` with a numeric offset (`+00:00`), not `Z`.
 * The failure mode is "sync fails on old phones only", which is close to
 * invisible in testing: every fixture in this repo uses `Z`.
 *
 * Three forms are accepted, in order of likelihood: a `Z` instant (what this app
 * writes), an offset date-time (what the server returns), and a local
 * date-time with no offset at all (a `timestamp without time zone` column),
 * which is read as UTC because that is what the schema stores.
 */
fun isoToEpoch(iso: String): Long =
    runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        ?: throw DateTimeParseException("Unrecognised remote timestamp: $iso", iso, 0)

private fun refundActionToWire(action: String): String =
    action.lowercase()

private fun String.toJsonElement(): JsonElement? =
    takeIf { it.isNotBlank() && !equals("null", ignoreCase = true) }
        ?.let { snapshotJson.parseToJsonElement(it) }

private fun JsonElement.toSnapshotString(): String =
    snapshotJson.encodeToString(JsonElement.serializer(), this)
