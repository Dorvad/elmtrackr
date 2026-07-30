package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import com.elmtrackr.app.fake.FakeRefundsRepository
import com.elmtrackr.app.fake.FakeSyncTrigger
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalShiftsRepositoryTest {

    @Test
    fun `clockIn returns existing active shift instead of creating duplicate`() = runTest {
        val dao = InMemoryShiftDao()
        dao.insertShift(shiftEntity(localId = "active-1", startTime = 1_000L))
        val repository = LocalShiftsRepository(dao, FakeRefundsRepository(), FakeSyncTrigger())

        val shift = repository.clockIn(
            userId = "u1",
            compensationProfileId = "new-profile",
            taskId = null,
            taskNameSnapshot = null,
            taskIconSnapshot = null,
            taskHourlyRateSnapshot = null,
        )

        assertEquals("active-1", shift.id)
        assertEquals(1, dao.currentShifts.size)
    }

    @Test
    fun `clockIn creates shift with compensation profile when no active shift exists`() = runTest {
        val dao = InMemoryShiftDao()
        val repository = LocalShiftsRepository(dao, FakeRefundsRepository(), FakeSyncTrigger())

        val shift = repository.clockIn(
            userId = "u1",
            compensationProfileId = "profile-1",
            taskId = null,
            taskNameSnapshot = null,
            taskIconSnapshot = null,
            taskHourlyRateSnapshot = null,
        )

        assertEquals("profile-1", shift.compensationProfileId)
        assertEquals(1, dao.currentShifts.size)
        assertEquals(SyncStatus.PENDING_CREATE, dao.currentShifts.first().syncStatus)
    }

    private fun shiftEntity(
        localId: String,
        userId: String = "u1",
        startTime: Long,
        endTime: Long? = null,
        deletedAt: Long? = null,
    ) = ShiftEntity(
        localId = localId,
        remoteId = null,
        userId = userId,
        startTime = startTime,
        endTime = endTime,
        breakMinutes = 0,
        notes = null,
        isSpecialDay = false,
        refundAction = null,
        compensationProfileId = "existing-profile",
        compensationSnapshotJson = null,
        taskId = null,
        taskNameSnapshot = null,
        taskIconSnapshot = null,
        taskHourlyRateSnapshot = null,
        createdAt = startTime,
        updatedAt = startTime,
        deletedAt = deletedAt,
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = null,
    )

    @Test
    fun `getShiftById hides soft-deleted shifts from the domain layer`() = runTest {
        val dao = InMemoryShiftDao()
        dao.insertShift(shiftEntity(localId = "gone", startTime = 1_000L, deletedAt = 2_000L))
        val repository = LocalShiftsRepository(dao, FakeRefundsRepository(), FakeSyncTrigger())

        assertEquals(null, repository.getShiftById("gone"))
    }

    @Test
    fun `clockOut refuses a soft-deleted shift instead of resurrecting it`() = runTest {
        val dao = InMemoryShiftDao()
        dao.insertShift(shiftEntity(localId = "gone", startTime = 1_000L, deletedAt = 2_000L))
        val repository = LocalShiftsRepository(dao, FakeRefundsRepository(), FakeSyncTrigger())

        val result = runCatching { repository.clockOut("gone") }

        assertEquals(true, result.isFailure)
        val stored = dao.currentShifts.single()
        assertEquals(null, stored.endTime)
        assertEquals(2_000L, stored.deletedAt)
        assertEquals(SyncStatus.SYNCED, stored.syncStatus)
    }

    private class InMemoryShiftDao : ShiftDao {
        private val shifts = MutableStateFlow<List<ShiftEntity>>(emptyList())

        val currentShifts: List<ShiftEntity>
            get() = shifts.value

        override suspend fun adoptLegacyUser(userId: String) {
            shifts.value = shifts.value.map { if (it.userId == "local-user") it.copy(userId = userId) else it }
        }

        override fun observeShifts(userId: String): Flow<List<ShiftEntity>> =
            shifts.map { list -> list.filter { it.userId == userId && it.deletedAt == null }.sortedByDescending { it.startTime } }

        override fun observeActiveShift(userId: String): Flow<ShiftEntity?> =
            shifts.map { list -> list.filter { it.userId == userId && it.endTime == null && it.deletedAt == null }.maxByOrNull { it.startTime } }

        override suspend fun getShiftById(localId: String): ShiftEntity? =
            shifts.value.firstOrNull { it.localId == localId }

        override suspend fun getShiftByStartTime(userId: String, startTime: Long): ShiftEntity? =
            shifts.value.firstOrNull { it.userId == userId && it.startTime == startTime && it.deletedAt == null }

        override suspend fun insertShift(shift: ShiftEntity) {
            shifts.value = shifts.value.filterNot { it.localId == shift.localId } + shift
        }

        override suspend fun updateShift(shift: ShiftEntity) {
            insertShift(shift)
        }

        override suspend fun upsertShift(shift: ShiftEntity) {
            insertShift(shift)
        }

        override suspend fun detachTaskFromShifts(userId: String, taskId: String) {
            shifts.value = shifts.value.map {
                if (it.userId == userId && it.taskId == taskId) it.copy(taskId = null) else it
            }
        }

        override suspend fun softDeleteShift(
            localId: String,
            deletedAt: Long,
            syncStatus: SyncStatus,
            updatedAt: Long,
        ) {
            shifts.value = shifts.value.map {
                if (it.localId == localId) {
                    it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
                } else {
                    it
                }
            }
        }

        override fun observePendingSyncShifts(userId: String): Flow<List<ShiftEntity>> =
            shifts.map { list -> list.filter { it.userId == userId && it.syncStatus in pendingStatuses } }

        override suspend fun getPendingSyncShifts(userId: String): List<ShiftEntity> =
            shifts.value.filter { it.userId == userId && it.syncStatus in pendingStatuses }

        override suspend fun updateSyncState(
            localId: String,
            syncStatus: SyncStatus,
            remoteId: String?,
            lastSyncedAt: Long?,
            lastSyncError: String?,
        ) {
            shifts.value = shifts.value.map {
                if (it.localId == localId) {
                    it.copy(
                        syncStatus = syncStatus,
                        remoteId = remoteId,
                        lastSyncedAt = lastSyncedAt,
                        lastSyncError = lastSyncError,
                    )
                } else {
                    it
                }
            }
        }

        override suspend fun markSyncedIfUnchanged(
            localId: String,
            remoteId: String?,
            lastSyncedAt: Long?,
            expectedUpdatedAt: Long,
        ): Int {
            var updatedRows = 0
            shifts.value = shifts.value.map {
                if (it.localId == localId && it.updatedAt == expectedUpdatedAt) {
                    updatedRows = 1
                    it.copy(
                        syncStatus = SyncStatus.SYNCED,
                        remoteId = remoteId,
                        lastSyncedAt = lastSyncedAt,
                        lastSyncError = null,
                    )
                } else {
                    it
                }
            }
            return updatedRows
        }

        override suspend fun attachRemoteId(localId: String, remoteId: String?, lastSyncedAt: Long?) {
            shifts.value = shifts.value.map {
                if (it.localId == localId) it.copy(remoteId = remoteId, lastSyncedAt = lastSyncedAt) else it
            }
        }

        override suspend fun markNeverSyncedPendingCreate(userId: String) {
            shifts.value = shifts.value.map {
                if (it.userId == userId && it.remoteId == null &&
                    it.syncStatus == SyncStatus.SYNCED && it.deletedAt == null
                ) {
                    it.copy(syncStatus = SyncStatus.PENDING_CREATE)
                } else {
                    it
                }
            }
        }

        override suspend fun getAllShiftsForUser(userId: String): List<ShiftEntity> =
            shifts.value.filter { it.userId == userId && it.deletedAt == null }

        override suspend fun hasAnyShifts(userId: String): Boolean =
            shifts.value.any { it.userId == userId && it.deletedAt == null }

        override suspend fun hasPendingSyncShifts(userId: String): Boolean =
            shifts.value.any { it.userId == userId && it.syncStatus in pendingStatuses }

        override suspend fun deleteAllForUser(userId: String) {
            shifts.value = shifts.value.filterNot { it.userId == userId }
        }

        override suspend fun getActiveShifts(userId: String): List<ShiftEntity> =
            shifts.value.filter { it.userId == userId && it.endTime == null && it.deletedAt == null }

        override suspend fun getShiftByRemoteId(remoteId: String): ShiftEntity? =
            shifts.value.firstOrNull { it.remoteId == remoteId }

        override fun observeRecentCompletedShifts(userId: String, limit: Int): Flow<List<ShiftEntity>> =
            shifts.map { list ->
                list.filter { it.userId == userId && it.endTime != null && it.deletedAt == null }
                    .sortedByDescending { it.startTime }
                    .take(limit)
            }

        override fun observeShiftsByDateRange(
            userId: String,
            fromEpoch: Long,
            toEpoch: Long,
        ): Flow<List<ShiftEntity>> =
            shifts.map { list ->
                list.filter {
                    it.userId == userId &&
                        it.startTime >= fromEpoch &&
                        it.startTime < toEpoch &&
                        it.deletedAt == null
                }.sortedBy { it.startTime }
            }

        override fun observeShiftsForDay(
            userId: String,
            fromEpoch: Long,
            toEpoch: Long,
        ): Flow<List<ShiftEntity>> = observeShiftsByDateRange(userId, fromEpoch, toEpoch)

        override fun observeShiftsForProject(
            userId: String,
            projectId: String,
        ): Flow<List<ShiftEntity>> = shifts.map { list ->
            list.filter { it.userId == userId && it.projectId == projectId && it.deletedAt == null }
        }

        private companion object {
            val pendingStatuses = setOf(
                SyncStatus.PENDING_CREATE,
                SyncStatus.PENDING_UPDATE,
                SyncStatus.PENDING_DELETE,
                SyncStatus.FAILED,
            )
        }
    }
}
