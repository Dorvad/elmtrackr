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
    isArchived = isArchived,
    lastUsedAt = lastUsedAt?.let(::epochToIso),
)

fun TaskEntity.toRemoteUpdate(): RemoteTaskUpdate = RemoteTaskUpdate(
    name = name,
    icon = icon,
    color = color,
    hourlyRate = hourlyRate,
    isArchived = isArchived,
    lastUsedAt = lastUsedAt?.let(::epochToIso),
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
        isArchived = isArchived,
        lastUsedAt = lastUsedAt?.let(::isoToEpoch),
        createdAt = created,
        updatedAt = updated,
        // Archived is not deleted. Deriving deletedAt from is_archived turned every
        // archive into a deletion on the next pull: the row disappeared from the
        // Archived list (which filters deletedAt IS NULL) and, with no unarchive
        // path, was unrecoverable. The remote row carries no deletion timestamp, so
        // a pulled task is by definition not deleted.
        deletedAt = null,
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = updated,
    )
}
