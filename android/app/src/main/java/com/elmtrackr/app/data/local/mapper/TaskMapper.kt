package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.TaskEntity
import com.elmtrackr.app.domain.model.Task
import java.time.Instant

fun TaskEntity.toDomain(): Task = Task(
    id = localId,
    userId = userId,
    name = name,
    icon = icon,
    color = color,
    hourlyRate = hourlyRate,
    compensationProfileId = compensationProfileId,
    isArchived = isArchived,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    lastUsedAt = lastUsedAt?.let { Instant.ofEpochMilli(it) },
    remoteId = remoteId,
)

fun Task.toEntity(
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    remoteId: String? = this.remoteId,
    deletedAt: Long? = null,
    lastSyncError: String? = null,
    lastSyncedAt: Long? = null,
): TaskEntity = TaskEntity(
    localId = id,
    remoteId = remoteId,
    userId = userId,
    name = name,
    icon = icon,
    color = color,
    hourlyRate = hourlyRate,
    compensationProfileId = compensationProfileId,
    isArchived = isArchived,
    lastUsedAt = lastUsedAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    lastSyncedAt = lastSyncedAt,
)
