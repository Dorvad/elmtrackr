package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.dao.TaskDao
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.TaskEntity
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.fake.FakeShiftDao
import com.elmtrackr.app.fake.FakeSyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocalTasksRepositoryTest {

    @Test
    fun `deleteTask soft deletes and marks pending delete`() = runTest {
        val taskDao = InMemoryTaskDao()
        val repository = LocalTasksRepository(taskDao, FakeShiftDao(), FakeSyncTrigger())
        val saved = repository.upsertTask(task(id = "t1"))

        repository.deleteTask("u1", saved.id)

        val entity = taskDao.getByLocalId(saved.id)
        assertNotNull(entity)
        assertNotNull(entity!!.deletedAt)
        assertEquals(SyncStatus.PENDING_DELETE, entity.syncStatus)
    }

    @Test
    fun `deleteTask detaches the task from shifts but keeps snapshots`() = runTest {
        val taskDao = InMemoryTaskDao()
        val shiftDao = FakeShiftDao()
        val repository = LocalTasksRepository(taskDao, shiftDao, FakeSyncTrigger())
        val saved = repository.upsertTask(task(id = "t1"))
        shiftDao.insertShift(shiftWithTask(localId = "s1", taskId = saved.id))

        repository.deleteTask("u1", saved.id)

        val shift = shiftDao.getShiftById("s1")
        assertNull(shift!!.taskId)
        assertEquals("Deliveries", shift.taskNameSnapshot)
        assertEquals(55.0, shift.taskHourlyRateSnapshot!!, 0.0)
    }

    @Test
    fun `deleteTask ignores unknown ids`() = runTest {
        val taskDao = InMemoryTaskDao()
        val repository = LocalTasksRepository(taskDao, FakeShiftDao(), FakeSyncTrigger())

        repository.deleteTask("u1", "missing")

        assertEquals(0, taskDao.size)
    }

    private fun task(id: String) = Task(
        id = id,
        userId = "u1",
        name = "Deliveries",
        icon = "🚚",
        hourlyRate = 55.0,
    )

    private fun shiftWithTask(localId: String, taskId: String) =
        com.elmtrackr.app.data.local.entity.ShiftEntity(
            localId = localId,
            remoteId = null,
            userId = "u1",
            startTime = 1_000L,
            endTime = 2_000L,
            breakMinutes = 0,
            notes = null,
            isSpecialDay = false,
            refundAction = null,
            compensationProfileId = null,
            compensationSnapshotJson = null,
            taskId = taskId,
            taskNameSnapshot = "Deliveries",
            taskIconSnapshot = "🚚",
            taskHourlyRateSnapshot = 55.0,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            deletedAt = null,
            syncStatus = SyncStatus.SYNCED,
            lastSyncError = null,
            lastSyncedAt = null,
        )

    private class InMemoryTaskDao : TaskDao {
        private val store = mutableMapOf<String, TaskEntity>()
        private val flow = MutableStateFlow<List<TaskEntity>>(emptyList())

        val size: Int get() = store.size

        private fun refresh() {
            flow.value = store.values.toList()
        }

        override suspend fun getAllTasksForUser(userId: String): List<TaskEntity> =
            store.values.filter { it.userId == userId && it.deletedAt == null }

        override suspend fun getPendingSyncTasks(userId: String): List<TaskEntity> =
            store.values.filter { it.userId == userId && it.syncStatus != SyncStatus.SYNCED }

        override suspend fun hasPendingSyncTasks(userId: String): Boolean =
            store.values.any { it.userId == userId && it.syncStatus != SyncStatus.SYNCED }

        override fun observePendingSyncTasks(userId: String): Flow<List<TaskEntity>> =
            flow.map { list -> list.filter { it.userId == userId && it.syncStatus != SyncStatus.SYNCED } }

        override suspend fun markNeverSyncedPendingCreate(userId: String) = Unit

        override suspend fun upsert(task: TaskEntity) {
            store[task.localId] = task
            refresh()
        }

        override suspend fun adoptLegacyUser(userId: String) = Unit

        override fun observeActiveTasks(userId: String): Flow<List<TaskEntity>> =
            flow.map { list -> list.filter { it.userId == userId && !it.isArchived && it.deletedAt == null } }

        override fun observeAllTasks(userId: String): Flow<List<TaskEntity>> =
            flow.map { list -> list.filter { it.userId == userId && it.deletedAt == null } }

        override suspend fun getActiveTasks(userId: String): List<TaskEntity> =
            store.values.filter { it.userId == userId && !it.isArchived && it.deletedAt == null }

        override suspend fun getByLocalId(localId: String): TaskEntity? = store[localId]

        override suspend fun getById(userId: String, localId: String): TaskEntity? =
            store[localId]?.takeIf { it.userId == userId }

        override suspend fun getByRemoteId(remoteId: String): TaskEntity? =
            store.values.firstOrNull { it.remoteId == remoteId }

        override suspend fun insert(task: TaskEntity) = upsert(task)

        override suspend fun updateSyncState(
            localId: String,
            status: SyncStatus,
            remoteId: String?,
            syncedAt: Long?,
            error: String?,
        ) {
            store[localId]?.let {
                store[localId] = it.copy(
                    syncStatus = status, remoteId = remoteId, lastSyncedAt = syncedAt, lastSyncError = error,
                )
            }
            refresh()
        }

        override suspend fun markSyncedIfUnchanged(
            localId: String,
            remoteId: String?,
            syncedAt: Long?,
            expectedUpdatedAt: Long,
        ): Int {
            val current = store[localId] ?: return 0
            if (current.updatedAt != expectedUpdatedAt) return 0
            store[localId] = current.copy(
                syncStatus = SyncStatus.SYNCED, remoteId = remoteId, lastSyncedAt = syncedAt, lastSyncError = null,
            )
            refresh()
            return 1
        }

        override suspend fun attachRemoteId(localId: String, remoteId: String?, syncedAt: Long?) {
            store[localId]?.let { store[localId] = it.copy(remoteId = remoteId, lastSyncedAt = syncedAt) }
            refresh()
        }

        override suspend fun updateLastUsed(localId: String, lastUsedAt: Long, updatedAt: Long) {
            store[localId]?.let { store[localId] = it.copy(lastUsedAt = lastUsedAt, updatedAt = updatedAt) }
            refresh()
        }

        override suspend fun softDeleteTask(
            localId: String,
            deletedAt: Long,
            syncStatus: SyncStatus,
            updatedAt: Long,
        ) {
            store[localId]?.let {
                store[localId] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            }
            refresh()
        }

        override suspend fun deleteAllForUser(userId: String) {
            store.entries.removeIf { it.value.userId == userId }
            refresh()
        }
    }
}
