package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.remote.RemoteShiftDataSource
import com.elmtrackr.app.data.remote.RemoteShiftInsert
import com.elmtrackr.app.data.remote.RemoteShiftRow
import com.elmtrackr.app.data.remote.RemoteShiftUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRepositoryImplTest {

    @Test
    fun `push creates remote shift and stores remote id`() = runTest {
        val dao = InMemoryShiftDao()
        val remote = FakeRemoteShiftDataSource()
        val repository = SyncRepositoryImpl(dao, remote)

        dao.insertShift(
            shiftEntity(
                localId = "local-1",
                syncStatus = SyncStatus.PENDING_CREATE,
            ),
        )

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.Success)
        val synced = dao.getShiftById("local-1")
        assertNotNull(synced)
        assertEquals(SyncStatus.SYNCED, synced!!.syncStatus)
        assertEquals("remote-1", synced.remoteId)
        assertEquals(1, remote.inserts.size)
    }

    @Test
    fun `pull restores shifts for reinstall scenario`() = runTest {
        val dao = InMemoryShiftDao()
        val remote = FakeRemoteShiftDataSource(
            initial = listOf(
                RemoteShiftRow(
                    id = "remote-99",
                    userId = "user-1",
                    startTime = "2024-06-01T08:00:00Z",
                    endTime = "2024-06-01T16:00:00Z",
                    breakMinutes = 0,
                    createdAt = "2024-06-01T08:00:00Z",
                    updatedAt = "2024-06-01T16:00:00Z",
                ),
            ),
        )
        val repository = SyncRepositoryImpl(dao, remote)

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.Success)
        assertEquals(1, dao.currentShifts.size)
        assertEquals("remote-99", dao.currentShifts.first().remoteId)
    }

    @Test
    fun `reconcile marks legacy synced rows without remote id as pending create`() = runTest {
        val dao = InMemoryShiftDao()
        val remote = FakeRemoteShiftDataSource()
        val repository = SyncRepositoryImpl(dao, remote)

        dao.insertShift(
            shiftEntity(
                localId = "legacy-1",
                syncStatus = SyncStatus.SYNCED,
            ),
        )

        repository.syncAll("user-1")

        val synced = dao.getShiftById("legacy-1")
        assertEquals(SyncStatus.SYNCED, synced!!.syncStatus)
        assertEquals("remote-1", synced.remoteId)
        assertEquals(1, remote.inserts.size)
    }

    private fun shiftEntity(
        localId: String,
        userId: String = "user-1",
        syncStatus: SyncStatus,
        remoteId: String? = null,
        startTime: Long = 1_000L,
        endTime: Long? = 2_000L,
    ) = ShiftEntity(
        localId = localId,
        remoteId = remoteId,
        userId = userId,
        startTime = startTime,
        endTime = endTime,
        breakMinutes = 0,
        notes = null,
        isSpecialDay = false,
        refundAction = null,
        compensationProfileId = null,
        compensationSnapshotJson = null,
        taskId = null,
        taskNameSnapshot = null,
        taskIconSnapshot = null,
        taskHourlyRateSnapshot = null,
        createdAt = startTime,
        updatedAt = startTime,
        deletedAt = null,
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = null,
    )

    private class FakeRemoteShiftDataSource(
        initial: List<RemoteShiftRow> = emptyList(),
    ) : RemoteShiftDataSource {
        private val rows = initial.toMutableList()
        val inserts = mutableListOf<RemoteShiftInsert>()
        private var nextId = 1

        override suspend fun fetchAll(): List<RemoteShiftRow> = rows.toList()

        override suspend fun insert(shift: RemoteShiftInsert): RemoteShiftRow {
            inserts += shift
            val row = RemoteShiftRow(
                id = "remote-$nextId",
                userId = shift.userId,
                startTime = shift.startTime,
                endTime = shift.endTime,
                breakMinutes = shift.breakMinutes,
                notes = shift.notes,
                isSpecialDay = shift.isSpecialDay,
                refundAction = shift.refundAction,
                compensationProfileId = shift.compensationProfileId,
                compensationSnapshotJson = shift.compensationSnapshotJson,
                taskId = shift.taskId,
                taskNameSnapshot = shift.taskNameSnapshot,
                taskIconSnapshot = shift.taskIconSnapshot,
                taskHourlyRateSnapshot = shift.taskHourlyRateSnapshot,
                createdAt = shift.startTime,
                updatedAt = shift.startTime,
            )
            nextId++
            rows += row
            return row
        }

        override suspend fun update(remoteId: String, shift: RemoteShiftUpdate) = Unit

        override suspend fun delete(remoteId: String) {
            rows.removeAll { it.id == remoteId }
        }
    }

    private class InMemoryShiftDao : ShiftDao {
        private val shifts = MutableStateFlow<List<ShiftEntity>>(emptyList())

        val currentShifts: List<ShiftEntity>
            get() = shifts.value.filter { it.deletedAt == null }

        override suspend fun adoptLegacyUser(userId: String) = Unit

        override fun observeShifts(userId: String): Flow<List<ShiftEntity>> =
            shifts.map { list -> list.filter { it.userId == userId && it.deletedAt == null } }

        override fun observeActiveShift(userId: String): Flow<ShiftEntity?> =
            shifts.map { list ->
                list.filter { it.userId == userId && it.endTime == null && it.deletedAt == null }
                    .maxByOrNull { it.startTime }
            }

        override suspend fun getShiftById(localId: String): ShiftEntity? =
            shifts.value.firstOrNull { it.localId == localId }

        override suspend fun insertShift(shift: ShiftEntity) {
            shifts.value = shifts.value.filterNot { it.localId == shift.localId } + shift
        }

        override suspend fun updateShift(shift: ShiftEntity) {
            insertShift(shift)
        }

        override suspend fun upsertShift(shift: ShiftEntity) {
            insertShift(shift)
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

        override suspend fun getAllShiftsForUser(userId: String): List<ShiftEntity> =
            shifts.value.filter { it.userId == userId && it.deletedAt == null }

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
                }
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
