package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.TaskEntity
import java.util.UUID

fun TaskEntity.toRemoteInsert(): RemoteTaskInsert = RemoteTaskInsert(
    id = localId,
    userId = userId,
    name = name,
    icon = icon,
    color = color,
    hourlyRate = hourlyRate,
    compensationProfileId = compensationProfileId,
    isArchived = isArchived,
    lastUsedAt = lastUsedAt?.let(::epochToIso),
    clientUpdatedAt = epochToIso(updatedAt),
)

fun TaskEntity.toRemoteUpdate(): RemoteTaskUpdate = RemoteTaskUpdate(
    name = name,
    icon = icon,
    color = color,
    hourlyRate = hourlyRate,
    compensationProfileId = compensationProfileId,
    isArchived = isArchived,
    lastUsedAt = lastUsedAt?.let(::epochToIso),
    deletedAt = deletedAt?.let(::epochToIso),
    clientUpdatedAt = epochToIso(updatedAt),
)

fun RemoteTaskRow.toLocalEntity(
    existingLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
): TaskEntity {
    val created = isoToEpoch(createdAt)
    val updated = isoToEpoch(updatedAt)
    return TaskEntity(
        localId = existingLocalId ?: UUID.randomUUID().toString(),
        remoteId = id,
        userId = userId,
        name = name,
        icon = icon,
        color = color,
        hourlyRate = hourlyRate,
        compensationProfileId = compensationProfileId,
        isArchived = isArchived,
        lastUsedAt = lastUsedAt?.let(::isoToEpoch),
        createdAt = created,
        updatedAt = updated,
        // Archived is not deleted, and the two now have separate columns.
        // Deriving deletedAt from is_archived once turned every archive into a
        // deletion on the next pull: the row vanished from the Archived list
        // (which filters deletedAt IS NULL) and, with no unarchive path, was
        // unrecoverable. deleted_at is the real deletion signal, so it is the
        // only thing read here.
        deletedAt = deletedAt?.let(::isoToEpoch),
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = updated,
    )
}
