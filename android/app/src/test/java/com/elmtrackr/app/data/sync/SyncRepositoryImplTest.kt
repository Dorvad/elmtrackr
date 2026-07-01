package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.dao.CompensationProfileDao
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.dao.TaskDao
import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.TaskEntity
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import com.elmtrackr.app.data.remote.RemoteCompensationProfileDataSource
import com.elmtrackr.app.data.remote.RemoteCompensationProfileInsert
import com.elmtrackr.app.data.remote.RemoteCompensationProfileRow
import com.elmtrackr.app.data.remote.RemoteCompensationProfileUpdate
import com.elmtrackr.app.data.remote.RemoteRefundClaimDataSource
import com.elmtrackr.app.data.remote.RemoteRefundClaimInsert
import com.elmtrackr.app.data.remote.RemoteRefundClaimRow
import com.elmtrackr.app.data.remote.RemoteRefundClaimUpdate
import com.elmtrackr.app.data.remote.RemoteTaskDataSource
import com.elmtrackr.app.data.remote.RemoteTaskInsert
import com.elmtrackr.app.data.remote.RemoteTaskRow
import com.elmtrackr.app.data.remote.RemoteTaskUpdate
import com.elmtrackr.app.data.remote.RemoteShiftDataSource
import com.elmtrackr.app.data.remote.RemoteShiftInsert
import com.elmtrackr.app.data.remote.RemoteShiftRow
import com.elmtrackr.app.data.remote.RemoteShiftUpdate
import com.elmtrackr.app.data.remote.RemoteUserSettingsDataSource
import com.elmtrackr.app.data.remote.RemoteUserSettingsRow
import com.elmtrackr.app.data.remote.RemoteUserSettingsUpdate
import com.elmtrackr.app.data.remote.RemoteUserSettingsUpsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
        val repository = createRepository(shiftDao = dao, remoteShifts = remote)

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
        val repository = createRepository(shiftDao = dao, remoteShifts = remote)

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.Success)
        assertEquals(1, dao.currentShifts.size)
        assertEquals("remote-99", dao.currentShifts.first().remoteId)
    }

    @Test
    fun `reconcile marks legacy synced rows without remote id as pending create`() = runTest {
        val dao = InMemoryShiftDao()
        val remote = FakeRemoteShiftDataSource()
        val repository = createRepository(shiftDao = dao, remoteShifts = remote)

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

    @Test
    fun `empty full sync does not tombstone local shifts when remote returns no rows`() = runTest {
        val dao = InMemoryShiftDao()
        val remote = FakeRemoteShiftDataSource()
        val repository = createRepository(shiftDao = dao, remoteShifts = remote)

        dao.insertShift(
            shiftEntity(
                localId = "local-synced",
                syncStatus = SyncStatus.SYNCED,
                remoteId = "remote-existing",
            ),
        )

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.Success)
        assertEquals(1, dao.currentShifts.size)
        assertEquals("remote-existing", dao.currentShifts.first().remoteId)
        assertEquals(null, dao.currentShifts.first().deletedAt)
    }

    @Test
    fun `pull restores soft deleted shift when remote row matches`() = runTest {
        val dao = InMemoryShiftDao()
        val remote = FakeRemoteShiftDataSource(
            initial = listOf(
                RemoteShiftRow(
                    id = "remote-1",
                    userId = "user-1",
                    startTime = "2024-06-01T08:00:00Z",
                    endTime = "2024-06-01T16:00:00Z",
                    breakMinutes = 0,
                    createdAt = "2024-06-01T08:00:00Z",
                    updatedAt = "2024-06-01T16:00:00Z",
                ),
            ),
        )
        val repository = createRepository(shiftDao = dao, remoteShifts = remote)

        dao.insertShift(
            shiftEntity(
                localId = "local-1",
                syncStatus = SyncStatus.SYNCED,
                remoteId = "remote-1",
                startTime = 1_716_230_400_000L,
                endTime = 1_716_259_200_000L,
            ).copy(deletedAt = 9_999L),
        )

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.Success)
        assertEquals(1, dao.currentShifts.size)
        assertEquals(null, dao.currentShifts.first().deletedAt)
    }

    @Test
    fun `syncAll still pulls shifts when tasks table is missing`() = runTest {
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
        val repository = createRepository(
            shiftDao = dao,
            remoteShifts = remote,
            remoteTasks = MissingTasksRemoteDataSource(),
        )

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.Success)
        assertEquals(1, dao.currentShifts.size)
        assertEquals("remote-99", dao.currentShifts.first().remoteId)
    }

    private fun createRepository(
        shiftDao: ShiftDao = InMemoryShiftDao(),
        remoteShifts: RemoteShiftDataSource = FakeRemoteShiftDataSource(),
        syncCursorStore: SyncCursorStore = InMemorySyncCursorStore(),
        remoteTasks: RemoteTaskDataSource = EmptyRemoteTaskDataSource(),
    ) = SyncRepositoryImpl(
        shiftDao = shiftDao,
        refundClaimDao = EmptyRefundClaimDao(),
        settingsDao = EmptySettingsDao(),
        compensationProfileDao = EmptyCompensationProfileDao(),
        taskDao = EmptyTaskDao(),
        syncCursorStore = syncCursorStore,
        remoteTasks = remoteTasks,
        remoteShifts = remoteShifts,
        remoteRefundClaims = EmptyRemoteRefundClaimDataSource(),
        remoteSettings = EmptyRemoteUserSettingsDataSource(),
        remoteCompensationProfiles = EmptyRemoteCompensationProfileDataSource(),
    )

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

        override suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteShiftRow> =
            rows.filter { sinceIso == null || it.updatedAt >= sinceIso }.take(limit)

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

    private class EmptyRefundClaimDao : RefundClaimDao {
        override suspend fun adoptLegacyUser(userId: String) = Unit
        override fun observeClaimsForUser(userId: String): Flow<List<RefundClaimEntity>> = emptyFlow()
        override fun observeClaimsForShift(shiftLocalId: String): Flow<List<RefundClaimEntity>> = emptyFlow()
        override suspend fun getClaimById(localId: String): RefundClaimEntity? = null
        override suspend fun insertClaim(claim: RefundClaimEntity) = Unit
        override suspend fun updateClaim(claim: RefundClaimEntity) = Unit
        override suspend fun upsertClaim(claim: RefundClaimEntity) = Unit
        override suspend fun softDeleteClaim(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long) = Unit
        override fun observePendingSyncClaims(userId: String): Flow<List<RefundClaimEntity>> = emptyFlow()
        override suspend fun getPendingSyncClaims(userId: String): List<RefundClaimEntity> = emptyList()
        override suspend fun hasPendingSyncClaims(userId: String): Boolean = false
        override suspend fun updateSyncState(localId: String, syncStatus: SyncStatus, remoteId: String?, lastSyncedAt: Long?, lastSyncError: String?) = Unit
        override suspend fun getAllClaimsForUser(userId: String): List<RefundClaimEntity> = emptyList()
        override suspend fun deleteAllForUser(userId: String) = Unit
        override suspend fun getClaimByRemoteId(remoteId: String): RefundClaimEntity? = null
    }

    private class EmptySettingsDao : SettingsDao {
        override suspend fun adoptLegacyUser(userId: String) = Unit
        override fun observeSettings(userId: String): Flow<UserSettingsEntity?> = flowOf(null)
        override suspend fun getSettings(userId: String): UserSettingsEntity? = null
        override suspend fun insertSettings(settings: UserSettingsEntity) = Unit
        override suspend fun updateSettings(settings: UserSettingsEntity) = Unit
        override suspend fun upsertSettings(settings: UserSettingsEntity) = Unit
        override fun observePendingSyncSettings(userId: String): Flow<List<UserSettingsEntity>> = emptyFlow()
        override suspend fun getPendingSyncSettings(userId: String): List<UserSettingsEntity> = emptyList()
        override suspend fun hasPendingSyncSettings(userId: String): Boolean = false
        override suspend fun updateSyncState(localId: String, syncStatus: SyncStatus, remoteId: String?, lastSyncedAt: Long?, lastSyncError: String?) = Unit
        override suspend fun getAllSettingsForUser(userId: String): List<UserSettingsEntity> = emptyList()
        override suspend fun deleteAllForUser(userId: String) = Unit
        override suspend fun getSettingsByRemoteId(remoteId: String): UserSettingsEntity? = null
    }

    private class EmptyCompensationProfileDao : CompensationProfileDao {
        override fun observeProfiles(userId: String): Flow<List<CompensationProfileEntity>> = emptyFlow()
        override suspend fun getByUser(userId: String): List<CompensationProfileEntity> = emptyList()
        override suspend fun getDefaultProfile(userId: String): CompensationProfileEntity? = null
        override suspend fun getByLocalId(localId: String): CompensationProfileEntity? = null
        override suspend fun getById(userId: String, localId: String): CompensationProfileEntity? = null
        override suspend fun getByRemoteId(remoteId: String): CompensationProfileEntity? = null
        override suspend fun getPendingSyncProfiles(userId: String): List<CompensationProfileEntity> = emptyList()
        override suspend fun hasPendingSyncProfiles(userId: String): Boolean = false
        override fun observePendingSyncProfiles(userId: String): Flow<List<CompensationProfileEntity>> = emptyFlow()
        override suspend fun getAllProfilesForUser(userId: String): List<CompensationProfileEntity> = emptyList()
        override suspend fun insert(profile: CompensationProfileEntity) = Unit
        override suspend fun update(profile: CompensationProfileEntity) = Unit
        override suspend fun upsert(profile: CompensationProfileEntity) = Unit
        override suspend fun updateSyncState(localId: String, status: SyncStatus, remoteId: String?, syncedAt: Long?, error: String?) = Unit
        override suspend fun clearDefaultForUser(userId: String) = Unit
        override suspend fun deleteAllForUser(userId: String) = Unit
    }

    private class EmptyTaskDao : TaskDao {
        override fun observeActiveTasks(userId: String): Flow<List<TaskEntity>> = emptyFlow()
        override fun observeAllTasks(userId: String): Flow<List<TaskEntity>> = emptyFlow()
        override suspend fun getActiveTasks(userId: String): List<TaskEntity> = emptyList()
        override suspend fun getByLocalId(localId: String): TaskEntity? = null
        override suspend fun getById(userId: String, localId: String): TaskEntity? = null
        override suspend fun getByRemoteId(remoteId: String): TaskEntity? = null
        override suspend fun getPendingSyncTasks(userId: String): List<TaskEntity> = emptyList()
        override suspend fun hasPendingSyncTasks(userId: String): Boolean = false
        override fun observePendingSyncTasks(userId: String): Flow<List<TaskEntity>> = emptyFlow()
        override suspend fun getAllTasksForUser(userId: String): List<TaskEntity> = emptyList()
        override suspend fun upsert(task: TaskEntity) = Unit
        override suspend fun insert(task: TaskEntity) = Unit
        override suspend fun adoptLegacyUser(userId: String) = Unit
        override suspend fun updateSyncState(localId: String, status: SyncStatus, remoteId: String?, syncedAt: Long?, error: String?) = Unit
        override suspend fun updateLastUsed(localId: String, lastUsedAt: Long, updatedAt: Long) = Unit
        override suspend fun deleteAllForUser(userId: String) = Unit
    }

    private class InMemorySyncCursorStore : SyncCursorStore {
        private val cursors = mutableMapOf<String, Long>()

        override suspend fun lastPulledAt(userId: String, entity: String): Long? =
            cursors["$userId:$entity"]

        override suspend fun setLastPulledAt(userId: String, entity: String, epochMillis: Long) {
            cursors["$userId:$entity"] = epochMillis
        }

        override fun sinceIso(epochMillis: Long?): String? =
            epochMillis?.let { java.time.Instant.ofEpochMilli(it).toString() }
    }

    private class EmptyRemoteTaskDataSource : RemoteTaskDataSource {
        override suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteTaskRow> =
            emptyList()
        override suspend fun insert(task: RemoteTaskInsert): RemoteTaskRow = error("not used")
        override suspend fun update(remoteId: String, task: RemoteTaskUpdate) = Unit
        override suspend fun delete(remoteId: String) = Unit
    }

    private class MissingTasksRemoteDataSource : RemoteTaskDataSource {
        private val missingTableError = RuntimeException(
            "Could not find the table 'public.tasks' in the schema cache (PGRST205)",
        )

        override suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteTaskRow> =
            throw missingTableError

        override suspend fun insert(task: RemoteTaskInsert): RemoteTaskRow = throw missingTableError

        override suspend fun update(remoteId: String, task: RemoteTaskUpdate) = throw missingTableError

        override suspend fun delete(remoteId: String) = throw missingTableError
    }

    private class EmptyRemoteRefundClaimDataSource : RemoteRefundClaimDataSource {
        override suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteRefundClaimRow> =
            emptyList()
        override suspend fun insert(claim: RemoteRefundClaimInsert): RemoteRefundClaimRow =
            error("not used")
        override suspend fun update(remoteId: String, claim: RemoteRefundClaimUpdate) = Unit
        override suspend fun delete(remoteId: String) = Unit
    }

    private class EmptyRemoteUserSettingsDataSource : RemoteUserSettingsDataSource {
        override suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteUserSettingsRow> =
            emptyList()
        override suspend fun upsert(settings: RemoteUserSettingsUpsert): RemoteUserSettingsRow =
            error("not used")
        override suspend fun update(remoteId: String, settings: RemoteUserSettingsUpdate) = Unit
    }

    private class EmptyRemoteCompensationProfileDataSource : RemoteCompensationProfileDataSource {
        override suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteCompensationProfileRow> =
            emptyList()
        override suspend fun insert(profile: RemoteCompensationProfileInsert): RemoteCompensationProfileRow =
            error("not used")
        override suspend fun update(remoteId: String, profile: RemoteCompensationProfileUpdate) = Unit
        override suspend fun delete(remoteId: String) = Unit
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
                }
            }

        override fun observeShiftsForDay(
            userId: String,
            fromEpoch: Long,
            toEpoch: Long,
        ): Flow<List<ShiftEntity>> = observeShiftsByDateRange(userId, fromEpoch, toEpoch)

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
