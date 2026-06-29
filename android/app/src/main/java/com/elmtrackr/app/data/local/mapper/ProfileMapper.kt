package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.ProfileEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.model.Profile
import java.time.Instant

fun ProfileEntity.toDomain(): Profile = Profile(
    id = localId,
    email = email,
    fullName = fullName,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun Profile.toEntity(
    userId: String,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    remoteId: String? = null,
    deletedAt: Long? = null,
    lastSyncError: String? = null,
    lastSyncedAt: Long? = null,
): ProfileEntity = ProfileEntity(
    localId = id,
    remoteId = remoteId ?: id,
    userId = userId,
    email = email,
    fullName = fullName,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    lastSyncedAt = lastSyncedAt,
)
