package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.ProfileEntity
import com.elmtrackr.app.data.local.entity.SyncStatus

fun ProfileEntity.toRemoteUpdate(): RemoteProfileUpdate = RemoteProfileUpdate(
    fullName = fullName,
)

fun RemoteProfileRow.toLocalEntity(
    existingLocalId: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
): ProfileEntity = ProfileEntity(
    localId = existingLocalId ?: id,
    remoteId = id,
    userId = id,
    email = email,
    fullName = fullName,
    createdAt = isoToEpoch(createdAt),
    updatedAt = isoToEpoch(updatedAt),
    deletedAt = null,
    syncStatus = syncStatus,
    lastSyncError = null,
    lastSyncedAt = isoToEpoch(updatedAt),
)
