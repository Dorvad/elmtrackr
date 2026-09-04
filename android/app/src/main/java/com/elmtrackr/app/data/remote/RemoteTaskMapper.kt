package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.TaskEntity
import java.util.UUID

/**
 * @param compensationProfileRemoteId the *remote* id of the profile this task is
 *   scoped to.
 *
 * It has to be translated, and was not. `tasks.compensation_profile_id` is a real
 * foreign key, and local and remote profile ids coincide only for profiles this
 * device created — a profile *pulled* from another device gets a fresh random
 * local id. So a task scoped to a pulled profile pushed an id no row on the
 * server had, was rejected 23503, and stayed FAILED forever: retried every
 * fifteen minutes and counted in "unsynced changes" for good.
 */
fun TaskEntity.toRemoteInsert(
    compensationProfileRemoteId: String? = null,
): RemoteTaskInsert = RemoteTaskInsert(
    id = localId,
    userId = userId,
    name = name,
    icon = icon,
    color = color,
    hourlyRate = hourlyRate,
    compensationProfileId = compensationProfileRemoteId,
    isArchived = isArchived,
    lastUsedAt = lastUsedAt?.let(::epochToIso),
    clientUpdatedAt = epochToIso(updatedAt),
)

fun TaskEntity.toRemoteUpdate(
    compensationProfileRemoteId: String? = null,
): RemoteTaskUpdate = RemoteTaskUpdate(
    name = name,
    icon = icon,
    color = color,
    hourlyRate = hourlyRate,
    compensationProfileId = compensationProfileRemoteId,
    isArchived = isArchived,
    lastUsedAt = lastUsedAt?.let(::epochToIso),
    deletedAt = deletedAt?.let(::epochToIso),
    clientUpdatedAt = epochToIso(updatedAt),
)

/**
 * @param compensationProfileLocalId the profile id translated back to this
 *   device's own. Writing the remote id straight into the local column matched no
 *   local profile, so the task silently reverted to the default one — the mirror
 *   image of the push defect.
 * @param preserveLocal keeps the existing scope when the profile has not been
 *   pulled yet, rather than dropping it.
 */
fun RemoteTaskRow.toLocalEntity(
    existingLocalId: String? = null,
    compensationProfileLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    preserveLocal: TaskEntity? = null,
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
        compensationProfileId = compensationProfileLocalId ?: preserveLocal?.compensationProfileId,
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
