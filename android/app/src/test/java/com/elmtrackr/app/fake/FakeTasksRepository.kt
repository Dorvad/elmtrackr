package com.elmtrackr.app.fake

import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.repository.TasksRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

class FakeTasksRepository : TasksRepository {
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())

    fun setTasks(vararg tasks: Task) {
        _tasks.value = tasks.toList()
    }

    override fun observeActiveTasks(userId: String): Flow<List<Task>> =
        _tasks.map { list -> list.filter { it.userId == userId && !it.isArchived } }

    override fun observeAllTasks(userId: String): Flow<List<Task>> =
        _tasks.map { list -> list.filter { it.userId == userId } }

    override suspend fun getActiveTasks(userId: String): List<Task> =
        _tasks.value.filter { it.userId == userId && !it.isArchived }

    override suspend fun getTaskById(userId: String, taskId: String): Task? =
        _tasks.value.firstOrNull { it.userId == userId && it.id == taskId }

    override suspend fun upsertTask(task: Task): Task {
        _tasks.value = _tasks.value.filter { it.id != task.id } + task.copy(updatedAt = Instant.now())
        return task
    }

    override suspend fun archiveTask(userId: String, taskId: String) {
        _tasks.value = _tasks.value.map {
            if (it.userId == userId && it.id == taskId) it.copy(isArchived = true) else it
        }
    }

    override suspend fun deleteTask(userId: String, taskId: String) {
        _tasks.value = _tasks.value.filterNot { it.userId == userId && it.id == taskId }
    }

    override suspend fun markTaskUsed(userId: String, taskId: String) {
        _tasks.value = _tasks.value.map {
            if (it.userId == userId && it.id == taskId) it.copy(lastUsedAt = Instant.now()) else it
        }
    }
}
