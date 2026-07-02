package com.elmtrackr.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "syncStatus"]),
        Index(value = ["remoteId"]),
    ],
)
data class TaskEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val userId: String,
    val name: String,
    val icon: String,
    val color: String?,
    val hourlyRate: Double,
    val isArchived: Boolean,
    val lastUsedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)
