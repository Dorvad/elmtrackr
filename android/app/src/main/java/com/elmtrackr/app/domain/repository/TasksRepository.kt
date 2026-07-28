package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.domain.model.Task
import kotlinx.coroutines.flow.Flow

interface TasksRepository {
    fun observeActiveTasks(userId: String): Flow<List<Task>>
    fun observeAllTasks(userId: String): Flow<List<Task>>
    suspend fun getActiveTasks(userId: String): List<Task>
    suspend fun getTaskById(userId: String, taskId: String): Task?
    suspend fun upsertTask(task: Task): Task
    suspend fun archiveTask(userId: String, taskId: String)

    /** Returns an archived task to the active list. Archiving must be reversible. */
    suspend fun unarchiveTask(userId: String, taskId: String)

    /**
     * Permanently deletes a task (local soft delete synced as a remote delete).
     * Shifts that used the task keep their name/icon/rate snapshots.
     */
    suspend fun deleteTask(userId: String, taskId: String)
    suspend fun markTaskUsed(userId: String, taskId: String)
}
