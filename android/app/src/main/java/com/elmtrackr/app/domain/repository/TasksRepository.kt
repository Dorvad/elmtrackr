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
    suspend fun markTaskUsed(userId: String, taskId: String)
}
