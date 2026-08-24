package com.elmtrackr.app.domain.model

import java.time.Instant

data class Task(
    val id: String,
    val userId: String,
    val name: String,
    val icon: String,
    val color: String? = null,
    val hourlyRate: Double,
    /**
     * The work profile this task belongs to, or null for a task created before
     * tasks were scoped to a job. A null task belongs to the default profile:
     * hiding it instead would take an upgrading user's task list away.
     */
    val compensationProfileId: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val lastUsedAt: Instant? = null,
    val remoteId: String? = null,
)

data class TaskSnapshot(
    val taskId: String,
    val name: String,
    val icon: String,
    val hourlyRate: Double,
) {
    companion object {
        fun from(task: Task): TaskSnapshot = TaskSnapshot(
            taskId = task.id,
            name = task.name,
            icon = task.icon,
            hourlyRate = task.hourlyRate,
        )
    }
}
