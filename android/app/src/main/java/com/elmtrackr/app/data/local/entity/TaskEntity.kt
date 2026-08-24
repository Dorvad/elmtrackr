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
        // The clock-in picker reads a profile's tasks on every dashboard emission.
        Index(value = ["compensationProfileId"]),
    ],
)
data class TaskEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val userId: String,
    val name: String,
    val icon: String,
    val color: String?,
    /**
     * The work profile this task belongs to. A task is something you do at one
     * job, so the clock-in picker only offers the tasks of the profile being
     * clocked into.
     *
     * Nullable for tasks created before the link existed: those are treated as
     * belonging to the default profile rather than being hidden, so an upgrading
     * user does not lose their task list.
     */
    val compensationProfileId: String? = null,
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
