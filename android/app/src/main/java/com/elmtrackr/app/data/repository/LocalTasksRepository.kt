package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.dao.TaskDao
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.TaskEntity
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.repository.TasksRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalTasksRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val syncTrigger: SyncTrigger,
) : TasksRepository {

    override fun observeActiveTasks(userId: String): Flow<List<Task>> =
        taskDao.observeActiveTasks(userId).map { entities -> entities.map { it.toDomain() } }

    override fun observeAllTasks(userId: String): Flow<List<Task>> =
        taskDao.observeAllTasks(userId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getActiveTasks(userId: String): List<Task> =
        taskDao.getActiveTasks(userId).map { it.toDomain() }

    override suspend fun getTaskById(userId: String, taskId: String): Task? =
        taskDao.getById(userId, taskId)?.toDomain()

    override suspend fun upsertTask(task: Task): Task {
        val now = Instant.now()
        val taskId = task.id.ifBlank { UUID.randomUUID().toString() }
        val existing = taskDao.getById(task.userId, taskId)
            ?: task.remoteId?.let { taskDao.getByRemoteId(it) }
        val entity = task.copy(id = taskId).toEntity(
            syncStatus = syncStatusForMutation(existing),
            remoteId = existing?.remoteId ?: task.remoteId,
        ).copy(
            createdAt = existing?.createdAt ?: now.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
            lastUsedAt = task.lastUsedAt?.toEpochMilli() ?: existing?.lastUsedAt,
        )
        taskDao.insert(entity)
        syncTrigger.schedule()
        return entity.toDomain()
    }

    override suspend fun archiveTask(userId: String, taskId: String) {
        val existing = taskDao.getById(userId, taskId) ?: return
        val now = Instant.now().toEpochMilli()
        taskDao.insert(
            existing.copy(
                isArchived = true,
                syncStatus = SyncStatus.PENDING_UPDATE,
                updatedAt = now,
            ),
        )
        syncTrigger.schedule()
    }

    override suspend fun markTaskUsed(userId: String, taskId: String) {
        val existing = taskDao.getById(userId, taskId) ?: return
        val now = Instant.now().toEpochMilli()
        taskDao.insert(
            existing.copy(
                lastUsedAt = now,
                updatedAt = now,
                syncStatus = syncStatusForMutation(existing),
            ),
        )
        syncTrigger.schedule()
    }

    private fun syncStatusForMutation(existing: TaskEntity?): SyncStatus = when {
        existing == null -> SyncStatus.PENDING_CREATE
        existing.syncStatus == SyncStatus.PENDING_CREATE -> SyncStatus.PENDING_CREATE
        existing.syncStatus == SyncStatus.PENDING_DELETE -> SyncStatus.PENDING_DELETE
        else -> SyncStatus.PENDING_UPDATE
    }
}
