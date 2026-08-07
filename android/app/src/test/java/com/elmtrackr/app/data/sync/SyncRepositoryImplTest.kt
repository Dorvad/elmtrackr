package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.dao.CompensationProfileDao
import com.elmtrackr.app.data.local.dao.PremiumProfileDao
import com.elmtrackr.app.data.local.entity.PremiumProfileEntity
import com.elmtrackr.app.data.remote.RemotePremiumProfileDataSource
import com.elmtrackr.app.data.remote.RemotePremiumProfileInsert
import com.elmtrackr.app.data.remote.RemotePremiumProfileRow
import com.elmtrackr.app.data.remote.RemotePremiumProfileUpdate
import com.elmtrackr.app.data.local.dao.ProfileDao
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.dao.TaskDao
import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.ProfileEntity
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.TaskEntity
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import com.elmtrackr.app.data.remote.RemoteCompensationProfileDataSource
import com.elmtrackr.app.data.remote.RemoteCompensationProfileInsert
import com.elmtrackr.app.data.remote.RemoteCompensationProfileRow
import com.elmtrackr.app.data.remote.RemoteCompensationProfileUpdate
import com.elmtrackr.app.data.remote.RemoteProfileDataSource
import com.elmtrackr.app.data.remote.RemoteProfileRow
import com.elmtrackr.app.data.remote.RemoteProfileUpdate
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `pull terminates when a full page shares a single updated_at timestamp`() = runTest {
        val dao = InMemoryShiftDao()
        // 200 rows (a full pull page) that all share the same updated_at. The
        // gte cursor can never advance past this page, so without the stalled
        // cursor guard the pull loop would fetch the same page forever. Start
        // times are distinct so start-time dedup doesn't collapse the rows.
        val rows = (1..200).map { index ->
            val start = java.time.Instant.parse("2024-06-01T08:00:00Z").plusSeconds(index * 60L)
            RemoteShiftRow(
                id = "remote-$index",
                userId = "user-1",
                startTime = start.toString(),
                endTime = "2024-06-01T16:00:00Z",
                breakMinutes = 0,
                createdAt = "2024-06-01T08:00:00Z",
                updatedAt = "2024-06-01T16:00:00Z",
            )
        }
        val remote = FakeRemoteShiftDataSource(initial = rows)
        val repository = createRepository(shiftDao = dao, remoteShifts = remote)

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.Success)
        assertEquals(200, dao.currentShifts.size)
    }

    @Test
    fun `pull links remote shift to local row with matching start time`() = runTest {
        // Must equal isoToEpoch("2024-06-01T08:00:00Z") for the start-time match to apply.
        val startEpoch = 1_717_228_800_000L
        val dao = InMemoryShiftDao()
        val remote = FakeRemoteShiftDataSource(
            initial = listOf(
                RemoteShiftRow(
                    id = "remote-dup",
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
                remoteId = null,
                startTime = startEpoch,
                endTime = 1_717_257_600_000L,
            ),
        )

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.Success)
        assertEquals(1, dao.currentShifts.size)
        assertEquals("local-1", dao.currentShifts.first().localId)
        assertEquals("remote-dup", dao.currentShifts.first().remoteId)
    }

    @Test
    fun `push links to existing remote shift with matching start time instead of inserting`() = runTest {
        // Must equal isoToEpoch("2024-06-01T08:00:00Z") for the start-time match to apply.
        val startEpoch = 1_717_228_800_000L
        val dao = InMemoryShiftDao()
        val remote = FakeRemoteShiftDataSource(
            initial = listOf(
                RemoteShiftRow(
                    id = "remote-existing",
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
                syncStatus = SyncStatus.PENDING_CREATE,
                startTime = startEpoch,
                endTime = 1_717_257_600_000L,
            ),
        )

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.Success)
        assertEquals(0, remote.inserts.size)
        assertEquals("remote-existing", dao.getShiftById("local-1")!!.remoteId)
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

    @Test
    fun `failed delete is retried as delete instead of resurrecting the remote row`() = runTest {
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

        // A soft-deleted shift whose delete push already failed once.
        dao.insertShift(
            shiftEntity(
                localId = "local-1",
                syncStatus = SyncStatus.FAILED,
                remoteId = "remote-1",
                startTime = 1_716_230_400_000L,
                endTime = 1_716_259_200_000L,
            ).copy(deletedAt = 9_999L),
        )

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.Success)
        // Published as a tombstone, not as a plain edit: the row must keep
        // existing remotely so other devices see the deletion, and it must not be
        // resurrected by pushing the shift's fields without deleted_at.
        assertEquals(1, remote.updates.size)
        assertNotNull(remote.updates.single().second.deletedAt)
        assertNotNull(remote.findById("remote-1")!!.deletedAt)
        assertEquals(SyncStatus.SYNCED, dao.getShiftById("local-1")!!.syncStatus)
    }

    /**
     * The pull used to refuse a second open shift and hold its cursor there, which
     * stalled every later shift behind it. It now materialises the row and lets the
     * resolver collapse the pair, so the pull always makes progress.
     */
    @Test
    fun `a second open shift from the cloud no longer stalls the shifts pull`() = runTest {
        val dao = InMemoryShiftDao()
        val remote = FakeRemoteShiftDataSource(
            initial = listOf(
                RemoteShiftRow(
                    id = "remote-active",
                    userId = "user-1",
                    startTime = "2024-06-02T08:00:00Z",
                    endTime = null,
                    breakMinutes = 0,
                    createdAt = "2024-06-02T08:00:00Z",
                    updatedAt = "2024-06-02T08:00:00Z",
                ),
                RemoteShiftRow(
                    id = "remote-later",
                    userId = "user-1",
                    startTime = "2024-06-03T08:00:00Z",
                    endTime = "2024-06-03T16:00:00Z",
                    breakMinutes = 0,
                    createdAt = "2024-06-03T08:00:00Z",
                    updatedAt = "2024-06-03T16:00:00Z",
                ),
            ),
        )
        val repository = createRepository(shiftDao = dao, remoteShifts = remote)

        // Already on the clock here, since a start time earlier than either remote row.
        dao.insertShift(
            shiftEntity(
                localId = "local-active",
                syncStatus = SyncStatus.PENDING_CREATE,
                startTime = 1_716_000_000_000L,
                endTime = null,
            ),
        )

        repository.syncAll("user-1")

        // The completed shift that sorted *after* the duplicate still arrived —
        // that is the row the old cursor-holding behaviour lost.
        assertNotNull(dao.getShiftByRemoteId("remote-later"))

        // And exactly one shift is still running.
        val active = dao.getActiveShifts("user-1")
        assertEquals(1, active.size)
        assertEquals("local-active", active.single().localId)
    }

    @Test
    fun `shift edited while push is in flight stays pending`() = runTest {
        val dao = InMemoryShiftDao()
        val base = FakeRemoteShiftDataSource()
        val remote = object : RemoteShiftDataSource by base {
            override suspend fun update(
                remoteId: String,
                shift: RemoteShiftUpdate,
            ): RemoteShiftRow? {
                // Simulate the user editing the shift while the push request is
                // still on the wire: the row gains a newer updatedAt and goes
                // back to PENDING_UPDATE before the push result is recorded.
                val current = dao.getShiftById("local-1")!!
                dao.upsertShift(
                    current.copy(
                        notes = "edited mid-push",
                        updatedAt = current.updatedAt + 5_000,
                        syncStatus = SyncStatus.PENDING_UPDATE,
                    ),
                )
                return base.update(remoteId, shift)
            }
        }
        val repository = createRepository(shiftDao = dao, remoteShifts = remote)

        dao.insertShift(
            shiftEntity(
                localId = "local-1",
                syncStatus = SyncStatus.PENDING_UPDATE,
                remoteId = "remote-1",
            ),
        )

        repository.syncAll("user-1")

        // The mid-push edit must survive: the row stays pending so the newer
        // state is pushed by the next sync instead of being silently dropped.
        val after = dao.getShiftById("local-1")!!
        assertEquals("edited mid-push", after.notes)
        assertEquals(SyncStatus.PENDING_UPDATE, after.syncStatus)
        assertEquals("remote-1", after.remoteId)
    }

    @Test
    fun `create adopted mid-edit keeps remote id and stays pending`() = runTest {
        val dao = InMemoryShiftDao()
        val base = FakeRemoteShiftDataSource()
        val remote = object : RemoteShiftDataSource by base {
            override suspend fun insert(shift: RemoteShiftInsert): RemoteShiftRow {
                val current = dao.getShiftById("local-1")!!
                dao.upsertShift(
                    current.copy(
                        notes = "edited mid-push",
                        updatedAt = current.updatedAt + 5_000,
                        syncStatus = SyncStatus.PENDING_CREATE,
                    ),
                )
                return base.insert(shift)
            }
        }
        val repository = createRepository(shiftDao = dao, remoteShifts = remote)

        dao.insertShift(
            shiftEntity(
                localId = "local-1",
                syncStatus = SyncStatus.PENDING_CREATE,
            ),
        )

        repository.syncAll("user-1")

        // The remote row now exists, so the local row must remember its id
        // (the retry becomes an update, not a duplicate insert) while the
        // newer edit stays pending.
        val after = dao.getShiftById("local-1")!!
        assertEquals("remote-1", after.remoteId)
        assertEquals("edited mid-push", after.notes)
        assertEquals(SyncStatus.PENDING_CREATE, after.syncStatus)
    }

    @Test
    fun `push updates remote profile display name`() = runTest {
        val dao = InMemoryProfileDao()
        val remote = FakeRemoteProfileDataSource()
        val repository = createRepository(profileDao = dao, remoteProfiles = remote)

        dao.upsertProfile(
            profileEntity(
                localId = "user-1",
                fullName = "Alex",
                syncStatus = SyncStatus.PENDING_UPDATE,
            ),
        )

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.Success)
        val synced = dao.getProfile("user-1")
        assertNotNull(synced)
        assertEquals(SyncStatus.SYNCED, synced!!.syncStatus)
        assertEquals("Alex", synced.fullName)
        assertEquals(1, remote.updates.size)
        assertEquals("Alex", remote.updates.first().second.fullName)
    }

    @Test
    fun `pull restores profile display name for reinstall scenario`() = runTest {
        val dao = InMemoryProfileDao()
        val remote = FakeRemoteProfileDataSource(
            initial = listOf(
                RemoteProfileRow(
                    id = "user-1",
                    email = "alex@example.com",
                    fullName = "Alex Remote",
                    createdAt = "2024-06-01T08:00:00Z",
                    updatedAt = "2024-06-01T16:00:00Z",
                ),
            ),
        )
        val repository = createRepository(profileDao = dao, remoteProfiles = remote)

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.Success)
        val profile = dao.getProfile("user-1")
        assertNotNull(profile)
        assertEquals("Alex Remote", profile!!.fullName)
    }

    @Test
    fun `push deletes remote task for locally deleted task`() = runTest {
        val taskDao = InMemoryTaskDao()
        val remoteTasks = RecordingRemoteTaskDataSource()
        val repository = createRepository(taskDao = taskDao, remoteTasks = remoteTasks)

        taskDao.upsert(
            TaskEntity(
                localId = "task-1",
                remoteId = "remote-task-1",
                userId = "user-1",
                name = "Deliveries",
                icon = "🚚",
                color = null,
                hourlyRate = 55.0,
                isArchived = true,
                lastUsedAt = null,
                createdAt = 1_000L,
                updatedAt = 2_000L,
                deletedAt = 2_000L,
                syncStatus = SyncStatus.PENDING_DELETE,
                lastSyncError = null,
                lastSyncedAt = null,
            ),
        )

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.Success)
        assertEquals(listOf("remote-task-1"), remoteTasks.deletes)
        assertEquals(SyncStatus.SYNCED, taskDao.getByLocalId("task-1")!!.syncStatus)
    }

    @Test
    fun `sync reports auth expired when the remote rejects the session`() = runTest {
        val dao = InMemoryShiftDao()
        val remote = object : RemoteShiftDataSource by FakeRemoteShiftDataSource() {
            override suspend fun fetchUpdatedSince(
                sinceIso: String?,
                limit: Int,
                offset: Int,
            ): List<RemoteShiftRow> = throw RuntimeException("JWT expired")
        }
        val repository = createRepository(shiftDao = dao, remoteShifts = remote)

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.AuthExpired)
    }

    @Test
    fun `sync reports plain error for non-auth failures`() = runTest {
        val dao = InMemoryShiftDao()
        val remote = object : RemoteShiftDataSource by FakeRemoteShiftDataSource() {
            override suspend fun fetchUpdatedSince(
                sinceIso: String?,
                limit: Int,
                offset: Int,
            ): List<RemoteShiftRow> = throw RuntimeException("connection reset")
        }
        val repository = createRepository(shiftDao = dao, remoteShifts = remote)

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.Error)
    }

    /**
     * A second layer behind RLS. The remote queries do not filter by user, and
     * SyncWorker resolves the current user from a stored preference while
     * PostgREST resolves it from the session JWT — the two can disagree.
     */
    @Test
    fun `a pulled row belonging to another user is not written locally`() = runTest {
        val dao = InMemoryShiftDao()
        val remote = FakeRemoteShiftDataSource(
            initial = listOf(
                RemoteShiftRow(
                    id = "remote-theirs",
                    userId = "someone-else",
                    startTime = "2024-06-01T08:00:00Z",
                    endTime = "2024-06-01T16:00:00Z",
                    breakMinutes = 0,
                    createdAt = "2024-06-01T08:00:00Z",
                    updatedAt = "2024-06-01T16:00:00Z",
                ),
                RemoteShiftRow(
                    id = "remote-mine",
                    userId = "user-1",
                    startTime = "2024-06-02T08:00:00Z",
                    endTime = "2024-06-02T16:00:00Z",
                    breakMinutes = 0,
                    createdAt = "2024-06-02T08:00:00Z",
                    updatedAt = "2024-06-02T16:00:00Z",
                ),
            ),
        )
        val repository = createRepository(shiftDao = dao, remoteShifts = remote)

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.Success)
        assertEquals(1, dao.currentShifts.size)
        assertEquals("remote-mine", dao.currentShifts.single().remoteId)
    }

    /**
     * Two devices clocking in produce two open remote shifts. The local-active
     * check used to read a snapshot taken before the pull, which is false for
     * every row in the batch — so both were materialised and the user ended the
     * pull with two running shifts.
     */
    @Test
    fun `two open remote shifts in one page do not both become active locally`() = runTest {
        val dao = InMemoryShiftDao()
        val remote = FakeRemoteShiftDataSource(
            initial = listOf(
                RemoteShiftRow(
                    id = "remote-open-1",
                    userId = "user-1",
                    startTime = "2024-06-01T08:00:00Z",
                    endTime = null,
                    breakMinutes = 0,
                    createdAt = "2024-06-01T08:00:00Z",
                    updatedAt = "2024-06-01T08:00:00Z",
                ),
                RemoteShiftRow(
                    id = "remote-open-2",
                    userId = "user-1",
                    startTime = "2024-06-02T09:00:00Z",
                    endTime = null,
                    breakMinutes = 0,
                    createdAt = "2024-06-02T09:00:00Z",
                    updatedAt = "2024-06-02T09:00:00Z",
                ),
            ),
        )
        val repository = createRepository(shiftDao = dao, remoteShifts = remote)

        repository.syncAll("user-1")

        assertEquals(1, dao.currentShifts.count { it.endTime == null && it.deletedAt == null })
    }

    /**
     * A row the server will never accept must not produce a clean "Synced <time>".
     *
     * Per-row push failures are recorded on the row rather than on the pipeline
     * step, so they never reach `PipelineIssues` and the run still returns
     * Success. That part is intentional — one bad row should not fail the whole
     * sync — but the status line has to tell the truth, otherwise the user sees
     * "synced" while nothing left the device.
     */
    @Test
    fun `push failure the server will never accept is reported in the sync status`() = runTest {
        val dao = InMemoryShiftDao()
        val remote = object : RemoteShiftDataSource by FakeRemoteShiftDataSource() {
            override suspend fun insert(shift: RemoteShiftInsert): RemoteShiftRow =
                throw IllegalStateException(
                    "new row violates row-level security policy for table \"shifts\"",
                )
        }
        val repository = createRepository(shiftDao = dao, remoteShifts = remote)

        dao.insertShift(shiftEntity(localId = "local-1", syncStatus = SyncStatus.PENDING_CREATE))

        val result = repository.syncAll("user-1")

        assertTrue(result is SyncResult.Success)
        assertEquals(SyncStatus.FAILED, dao.getShiftById("local-1")!!.syncStatus)
        val status = repository.observeLastSyncStatus().first()
        assertEquals("SyncedUnsent: 1", status)
    }

    /**
     * The loop guard. `hasPendingWork` stays true for a permanently failing row,
     * and SyncWorker used it to decide whether to enqueue another immediate run —
     * an unbounded chain, because each follow-up starts at runAttemptCount 0.
     */
    @Test
    fun `hasRetryablePendingWork is false once every pending row has failed`() = runTest {
        val dao = InMemoryShiftDao()
        val remote = object : RemoteShiftDataSource by FakeRemoteShiftDataSource() {
            override suspend fun insert(shift: RemoteShiftInsert): RemoteShiftRow =
                throw IllegalStateException(
                    "new row violates row-level security policy for table \"shifts\"",
                )
        }
        val repository = createRepository(shiftDao = dao, remoteShifts = remote)

        dao.insertShift(shiftEntity(localId = "local-1", syncStatus = SyncStatus.PENDING_CREATE))
        assertTrue(repository.hasRetryablePendingWork("user-1"))

        repository.syncAll("user-1")

        // Still unsynced — the badge is right to keep showing it — but no longer
        // worth an immediate retry. The 15-minute periodic sync picks it back up.
        assertTrue(repository.hasPendingWork("user-1"))
        assertFalse(repository.hasRetryablePendingWork("user-1"))
    }

    @Test
    fun `hasRetryablePendingWork stays true when a fresh edit joins failed rows`() = runTest {
        val dao = InMemoryShiftDao()
        val repository = createRepository(shiftDao = dao)

        dao.insertShift(shiftEntity(localId = "stuck", syncStatus = SyncStatus.FAILED))
        assertFalse(repository.hasRetryablePendingWork("user-1"))

        dao.insertShift(shiftEntity(localId = "fresh", syncStatus = SyncStatus.PENDING_CREATE))

        assertTrue(repository.hasRetryablePendingWork("user-1"))
    }

    // ── Receipt photos ──────────────────────────────────────────────────────
    //
    // Both of these live in the sync pipeline rather than at the call site
    // because both have to survive the process dying: an upload retried only
    // while the form is open is not a retry, and a shift deleted offline still
    // has to clean up its storage objects when the device comes back.

    /**
     * A photo whose upload failed left the claim saved with no receipt_path while
     * the image sat in local receipt storage, and nothing ever tried again — so
     * the receipt existed on exactly one device, forever.
     */
    @Test
    fun `a receipt whose upload failed is uploaded by a later sync`() = runTest {
        val claimDao = com.elmtrackr.app.fake.FakeRefundClaimDao()
        val receiptDao = com.elmtrackr.app.fake.FakeReceiptDao()
        val storage = RecordingReceiptStorage()

        claimDao.upsertClaim(refundClaimEntity(receiptPath = null))
        receiptDao.insert(receiptEntity(claimLocalId = "claim-1", path = "/receipts/claim-1.jpg"))

        val repository = createRepository(
            refundClaimDao = claimDao,
            receiptDao = receiptDao,
            refundReceiptStorage = storage,
            receiptFileReader = StubReceiptFileReader(),
        )

        repository.syncAll(USER)

        assertEquals(listOf("claim-1.jpg"), storage.uploads)
        assertEquals("uploaded/claim-1.jpg", claimDao.getClaimById("claim-1")!!.receiptPath)
    }

    @Test
    fun `a claim whose local image is gone is left alone rather than retried`() = runTest {
        val claimDao = com.elmtrackr.app.fake.FakeRefundClaimDao()
        val receiptDao = com.elmtrackr.app.fake.FakeReceiptDao()
        val storage = RecordingReceiptStorage()

        claimDao.upsertClaim(refundClaimEntity(receiptPath = null))
        receiptDao.insert(receiptEntity(claimLocalId = "claim-1", path = "/receipts/gone.jpg"))

        val repository = createRepository(
            refundClaimDao = claimDao,
            receiptDao = receiptDao,
            refundReceiptStorage = storage,
            receiptFileReader = StubReceiptFileReader(readable = false),
        )

        repository.syncAll(USER)

        assertTrue(storage.uploads.isEmpty())
        assertNull(claimDao.getClaimById("claim-1")!!.receiptPath)
    }

    /**
     * Deleting a shift cascades to its claims through the database and stopped
     * there, so every receipt photo on a deleted shift stayed in the bucket.
     */
    @Test
    fun `deleting a claim removes its receipt from cloud storage`() = runTest {
        val claimDao = com.elmtrackr.app.fake.FakeRefundClaimDao()
        val storage = RecordingReceiptStorage()

        claimDao.upsertClaim(
            refundClaimEntity(receiptPath = "user-1/shift-1/to_work/1.jpg").copy(
                remoteId = "remote-claim-1",
                deletedAt = 5_000L,
                syncStatus = SyncStatus.PENDING_DELETE,
            ),
        )

        val repository = createRepository(
            refundClaimDao = claimDao,
            refundReceiptStorage = storage,
            receiptFileReader = StubReceiptFileReader(),
        )

        repository.syncAll(USER)

        assertEquals(listOf("user-1/shift-1/to_work/1.jpg"), storage.deletes)
    }

    private fun refundClaimEntity(receiptPath: String?) = RefundClaimEntity(
        localId = "claim-1",
        remoteId = null,
        shiftLocalId = "shift-1",
        userId = USER,
        direction = com.elmtrackr.app.domain.model.RefundDirection.TO_WORK.name,
        provider = "LIME",
        amount = 12.5,
        rideAt = 1_000L,
        notes = null,
        receiptPath = receiptPath,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        deletedAt = null,
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = null,
    )

    private fun receiptEntity(claimLocalId: String, path: String) =
        com.elmtrackr.app.data.local.entity.ReceiptEntity(
            id = "receipt-1",
            userId = USER,
            refundClaimId = claimLocalId,
            localImageUri = path,
            merchantName = null,
            amount = null,
            currency = null,
            receiptDate = null,
            rawOcrText = null,
            parserVersion = "",
            createdAt = 1_000L,
            updatedAt = 1_000L,
        )

    private class RecordingReceiptStorage : com.elmtrackr.app.domain.repository.RefundReceiptStorage {
        val uploads = mutableListOf<String>()
        val deletes = mutableListOf<String>()

        override suspend fun upload(
            userId: String,
            shiftId: String,
            direction: com.elmtrackr.app.domain.model.RefundDirection,
            receipt: com.elmtrackr.app.domain.model.ReceiptUpload,
        ): String {
            uploads += receipt.fileName
            return "uploaded/${receipt.fileName}"
        }

        override suspend fun createSignedUrl(path: String): String = "https://example.test/$path"

        override suspend fun delete(path: String) {
            deletes += path
        }
    }

    /** Stands in for the filesystem; [readable] false is a missing or oversize file. */
    private class StubReceiptFileReader(
        private val readable: Boolean = true,
    ) : com.elmtrackr.app.domain.repository.ReceiptFileReader {
        override suspend fun toReceiptUpload(
            path: String,
        ): com.elmtrackr.app.domain.model.ReceiptUpload? {
            if (!readable) return null
            return com.elmtrackr.app.domain.model.ReceiptUpload(
                fileName = path.substringAfterLast('/'),
                bytes = byteArrayOf(1, 2, 3),
                mimeType = "image/jpeg",
            )
        }
    }

    // ── Two devices, one cloud ──────────────────────────────────────────────
    //
    // Each "device" is its own Room database and its own pull cursor, talking to a
    // shared FakeRemoteShiftDataSource. That separation is the whole point: every
    // bug in this section is a bug about what device B sees after device A acts,
    // and a single-repository test cannot express it.

    private class Device(
        val dao: InMemoryShiftDao,
        val repository: SyncRepositoryImpl,
    ) {
        suspend fun sync() = repository.syncAll(USER)

        val liveShifts: List<ShiftEntity> get() = dao.currentShifts
    }

    private fun device(remote: RemoteShiftDataSource): Device {
        val dao = InMemoryShiftDao()
        return Device(
            dao = dao,
            repository = createRepository(
                shiftDao = dao,
                remoteShifts = remote,
                syncCursorStore = InMemorySyncCursorStore(),
            ),
        )
    }

    private fun seededShiftRow(
        id: String = "remote-1",
        startTime: String = "2024-06-01T08:00:00Z",
        endTime: String? = "2024-06-01T16:00:00Z",
        updatedAt: String = "2024-06-01T16:00:00Z",
    ) = RemoteShiftRow(
        id = id,
        userId = USER,
        startTime = startTime,
        endTime = endTime,
        breakMinutes = 0,
        createdAt = startTime,
        updatedAt = updatedAt,
        clientUpdatedAt = updatedAt,
    )

    /**
     * The reported bug: delete a shift on the phone, and it stays on the tablet.
     *
     * The tablet syncs incrementally here — it has already pulled the shift once,
     * so its cursor sits past the row. That is the case a DELETE cannot reach, and
     * why the delete has to be published as a tombstone.
     */
    @Test
    fun `deleting a shift on one device removes it from the other device`() = runTest {
        val cloud = FakeRemoteShiftDataSource(initial = listOf(seededShiftRow()))
        val phone = device(cloud)
        val tablet = device(cloud)

        phone.sync()
        tablet.sync()
        assertEquals(1, phone.liveShifts.size)
        assertEquals(1, tablet.liveShifts.size)

        // Deleted "now", which is what the delete path stamps and what makes this
        // edit newer than the copy the cloud holds.
        val onPhone = phone.liveShifts.single()
        val deletedAt = 1_717_300_000_000L
        phone.dao.softDeleteShift(onPhone.localId, deletedAt, SyncStatus.PENDING_UPDATE, deletedAt)
        phone.sync()

        tablet.sync()

        assertTrue("tablet still shows the deleted shift", tablet.liveShifts.isEmpty())
    }

    /**
     * The reported bug: an older offline edit silently overwrites a newer one.
     *
     * The phone here has been offline since before the tablet's edit, so its copy
     * of the shift is stale. Pushing it unconditionally is what used to reinstate
     * the old notes over the newer ones, on every device, with nothing recorded.
     */
    @Test
    fun `an older offline edit does not overwrite a newer edit from another device`() = runTest {
        val cloud = FakeRemoteShiftDataSource(initial = listOf(seededShiftRow()))
        val phone = device(cloud)
        val tablet = device(cloud)

        phone.sync()
        tablet.sync()

        // The tablet edits second by the clock, and syncs first.
        val onTablet = tablet.liveShifts.single()
        tablet.dao.upsertShift(
            onTablet.copy(
                notes = "newer, from the tablet",
                updatedAt = 2_000_000_000_000L,
                syncStatus = SyncStatus.PENDING_UPDATE,
            ),
        )
        tablet.sync()

        // The phone's edit was made earlier but reaches the server later.
        val onPhone = phone.liveShifts.single()
        phone.dao.upsertShift(
            onPhone.copy(
                notes = "older, from the phone",
                updatedAt = 1_000_000_000_000L,
                syncStatus = SyncStatus.PENDING_UPDATE,
            ),
        )
        phone.sync()

        assertEquals(1, cloud.rejectedUpdates.size)
        assertEquals("newer, from the tablet", cloud.rowsNow().single().notes)
        // The phone does not keep an edit the cloud refused: it takes the newer
        // copy, so both devices agree without waiting for a later pull that the
        // cursor might never make.
        assertEquals("newer, from the tablet", phone.liveShifts.single().notes)
        assertEquals(SyncStatus.SYNCED, phone.liveShifts.single().syncStatus)
    }

    /**
     * The reported bug: clocking in on two devices leaves two running shifts.
     *
     * Both clock-ins are genuine rows that sync, so every device ends up holding
     * both. They converge on the earlier start — the time the user actually began
     * working — and the later one is tombstoned so it disappears everywhere.
     */
    @Test
    fun `clocking in on two devices converges on a single running shift`() = runTest {
        val cloud = FakeRemoteShiftDataSource()
        val phone = device(cloud)
        val tablet = device(cloud)

        phone.dao.insertShift(
            shiftEntity(
                localId = "phone-clock-in",
                syncStatus = SyncStatus.PENDING_CREATE,
                startTime = CLOCK_IN_0900,
                endTime = null,
            ),
        )
        tablet.dao.insertShift(
            shiftEntity(
                localId = "tablet-clock-in",
                syncStatus = SyncStatus.PENDING_CREATE,
                startTime = CLOCK_IN_0905,
                endTime = null,
            ),
        )

        // Both push, then both pull what the other pushed, then the tombstone the
        // resolution produced travels back. Three rounds is settling time, not a
        // requirement — the assertions are about where they land, not when.
        repeat(3) {
            phone.sync()
            tablet.sync()
        }

        val phoneActive = phone.dao.getActiveShifts(USER)
        val tabletActive = tablet.dao.getActiveShifts(USER)
        assertEquals("phone should have one running shift", 1, phoneActive.size)
        assertEquals("tablet should have one running shift", 1, tabletActive.size)
        assertEquals(CLOCK_IN_0900, phoneActive.single().startTime)
        assertEquals(CLOCK_IN_0900, tabletActive.single().startTime)
    }

    /**
     * The reported bug: rare large syncs skip information.
     *
     * A restore, an import, or simply a busy second leaves many rows sharing one
     * `updated_at`. The cursor is that timestamp, so it cannot advance through the
     * block, and the pull used to notice it was stuck and give up — losing every
     * row past the first page.
     */
    @Test
    fun `a block of rows sharing one timestamp is drained past the page size`() = runTest {
        val sameInstant = "2024-06-01T12:00:00Z"
        val rows = (0 until 250).map { index ->
            seededShiftRow(
                // Ids are zero-padded so the fake's (updated_at, id) order matches
                // the numeric order the assertions below reason about.
                id = "remote-%03d".format(index),
                startTime = java.time.Instant.parse("2024-06-01T00:00:00Z")
                    .plusSeconds(index * 3600L).toString(),
                endTime = null,
                updatedAt = sameInstant,
            ).copy(endTime = java.time.Instant.parse("2024-06-01T00:00:00Z")
                .plusSeconds(index * 3600L + 1800).toString())
        }
        val cloud = FakeRemoteShiftDataSource(initial = rows)
        val onlyDevice = device(cloud)

        onlyDevice.sync()

        assertEquals(250, onlyDevice.liveShifts.size)
    }

    private fun createRepository(
        shiftDao: ShiftDao = InMemoryShiftDao(),
        remoteShifts: RemoteShiftDataSource = FakeRemoteShiftDataSource(),
        syncCursorStore: SyncCursorStore = InMemorySyncCursorStore(),
        remoteTasks: RemoteTaskDataSource = EmptyRemoteTaskDataSource(),
        taskDao: TaskDao = EmptyTaskDao(),
        profileDao: ProfileDao = InMemoryProfileDao(),
        remoteProfiles: RemoteProfileDataSource = FakeRemoteProfileDataSource(),
        refundClaimDao: RefundClaimDao = EmptyRefundClaimDao(),
        receiptDao: com.elmtrackr.app.data.local.dao.ReceiptDao = com.elmtrackr.app.fake.FakeReceiptDao(),
        refundReceiptStorage: com.elmtrackr.app.domain.repository.RefundReceiptStorage? = null,
        receiptFileReader: com.elmtrackr.app.domain.repository.ReceiptFileReader? = null,
        remoteProjects: com.elmtrackr.app.data.remote.RemoteProjectDataSource =
            EmptyRemoteProjectDataSource(),
        remoteBillingRecords: com.elmtrackr.app.data.remote.RemoteProjectBillingRecordDataSource =
            EmptyRemoteProjectBillingRecordDataSource(),
        remoteProjectPayments: com.elmtrackr.app.data.remote.RemoteProjectPaymentDataSource =
            EmptyRemoteProjectPaymentDataSource(),
    ) = SyncRepositoryImpl(
        shiftDao = shiftDao,
        refundClaimDao = refundClaimDao,
        settingsDao = EmptySettingsDao(),
        compensationProfileDao = EmptyCompensationProfileDao(),
        premiumProfileDao = EmptyPremiumProfileDao(),
        receiptDao = receiptDao,
        projectDao = com.elmtrackr.app.fake.FakeProjectDao(),
        projectBillingRecordDao = com.elmtrackr.app.fake.FakeProjectBillingRecordDao(),
        projectPaymentDao = com.elmtrackr.app.fake.FakeProjectPaymentDao(),
        taskDao = taskDao,
        profileDao = profileDao,
        transactionRunner = com.elmtrackr.app.data.local.DirectTransactionRunner,
        syncCursorStore = syncCursorStore,
        remoteTasks = remoteTasks,
        remoteShifts = remoteShifts,
        remoteRefundClaims = EmptyRemoteRefundClaimDataSource(),
        remoteSettings = EmptyRemoteUserSettingsDataSource(),
        remoteCompensationProfiles = EmptyRemoteCompensationProfileDataSource(),
        remotePremiumProfiles = EmptyRemotePremiumProfileDataSource(),
        remoteProfiles = remoteProfiles,
        refundReceiptStorage = refundReceiptStorage,
        receiptFileReader = receiptFileReader,
        remoteProjects = remoteProjects,
        remoteBillingRecords = remoteBillingRecords,
        remoteProjectPayments = remoteProjectPayments,
    )

    private companion object {
        const val USER = "user-1"

        /** 2024-06-01T09:00:00Z and five minutes later, as epoch millis. */
        const val CLOCK_IN_0900 = 1_717_232_400_000L
        const val CLOCK_IN_0905 = 1_717_232_700_000L
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

    private fun profileEntity(
        localId: String,
        userId: String = "user-1",
        email: String = "user@example.com",
        fullName: String? = null,
        syncStatus: SyncStatus,
        remoteId: String? = localId,
        updatedAt: Long = 1_000L,
    ) = ProfileEntity(
        localId = localId,
        remoteId = remoteId,
        userId = userId,
        email = email,
        fullName = fullName,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        deletedAt = null,
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = null,
    )

    private class FakeRemoteProfileDataSource(
        initial: List<RemoteProfileRow> = emptyList(),
    ) : RemoteProfileDataSource {
        private val rows = initial.toMutableList()
        val updates = mutableListOf<Pair<String, RemoteProfileUpdate>>()

        override suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteProfileRow> =
            rows.filter { sinceIso == null || it.updatedAt >= sinceIso }.take(limit)

        override suspend fun update(userId: String, profile: RemoteProfileUpdate): RemoteProfileRow {
            updates += userId to profile
            val existing = rows.firstOrNull { it.id == userId }
            val updated = RemoteProfileRow(
                id = userId,
                email = existing?.email ?: "user@example.com",
                fullName = profile.fullName,
                createdAt = existing?.createdAt ?: "2024-06-01T08:00:00Z",
                updatedAt = "2024-06-01T16:00:00Z",
            )
            if (existing == null) {
                rows += updated
            } else {
                val index = rows.indexOfFirst { it.id == userId }
                rows[index] = updated
            }
            return updated
        }
    }

    /**
     * An in-memory stand-in for the shifts table that keeps the behaviour the sync
     * engine actually depends on: a total `(updated_at, id)` order, `range`-style
     * paging, tombstones instead of removal, and the `client_updated_at` guard that
     * makes a stale write match no row.
     *
     * Faking those away would make the two-device tests below prove nothing — the
     * bugs they cover are all in how the client reacts to what the server does.
     */
    private class FakeRemoteShiftDataSource(
        initial: List<RemoteShiftRow> = emptyList(),
    ) : RemoteShiftDataSource {
        private val rows = initial.toMutableList()
        val inserts = mutableListOf<RemoteShiftInsert>()
        val updates = mutableListOf<Pair<String, RemoteShiftUpdate>>()
        /** Writes the guard turned away, for tests that assert a conflict happened. */
        val rejectedUpdates = mutableListOf<Pair<String, RemoteShiftUpdate>>()
        private var nextId = 1
        private var serverTick = 0

        /**
         * Stands in for the trigger that stamps updated_at server-side. Always
         * ahead of the seeded 2024 rows and strictly increasing, so a write is
         * guaranteed to land past another device's pull cursor — which is exactly
         * what makes a tombstone reach that device.
         */
        private fun instant(iso: String): java.time.Instant = java.time.Instant.parse(iso)

        private fun nextServerTime(): String =
            java.time.Instant.parse("2025-01-01T00:00:00Z").plusSeconds((++serverTick).toLong()).toString()

        fun rowsNow(): List<RemoteShiftRow> = rows.toList()

        fun seed(row: RemoteShiftRow) {
            rows += row
        }

        override suspend fun fetchUpdatedSince(
            sinceIso: String?,
            limit: Int,
            offset: Int,
        ): List<RemoteShiftRow> =
            rows.asSequence()
                .filter { sinceIso == null || instant(it.updatedAt) >= instant(sinceIso) }
                .sortedWith(compareBy({ instant(it.updatedAt) }, { it.id }))
                .drop(offset)
                .take(limit)
                .toList()

        override suspend fun findById(remoteId: String): RemoteShiftRow? =
            rows.firstOrNull { it.id == remoteId }

        override suspend fun findByUserAndStartTime(
            userId: String,
            startTimeIso: String,
        ): RemoteShiftRow? = rows.firstOrNull {
            it.userId == userId && it.startTime == startTimeIso && it.deletedAt == null
        }

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
                clientUpdatedAt = shift.clientUpdatedAt,
            )
            nextId++
            rows += row
            return row
        }

        override suspend fun update(remoteId: String, shift: RemoteShiftUpdate): RemoteShiftRow? {
            updates += remoteId to shift
            val index = rows.indexOfFirst { it.id == remoteId }
            if (index < 0) return null
            val stored = rows[index]
            // `client_updated_at <= :incoming` — the stale-write guard. Compared as
            // instants because Postgres compares timestamps, and the two orders
            // disagree: Instant.toString() drops trailing zeros, so "…:00.500Z"
            // sorts before "…:00Z" as text while being the later moment.
            val storedClientTime = stored.clientUpdatedAt?.let(::instant)
            if (storedClientTime != null && storedClientTime > instant(shift.clientUpdatedAt)) {
                rejectedUpdates += remoteId to shift
                return null
            }
            val updated = stored.copy(
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
                deletedAt = shift.deletedAt,
                clientUpdatedAt = shift.clientUpdatedAt,
                updatedAt = nextServerTime(),
            )
            rows[index] = updated
            return updated
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
        override suspend fun softDeleteClaimsForShift(
            shiftLocalId: String,
            deletedAt: Long,
            syncStatus: SyncStatus,
            updatedAt: Long,
        ) = Unit
        override fun observePendingSyncClaims(userId: String): Flow<List<RefundClaimEntity>> = emptyFlow()
        override suspend fun getPendingSyncClaims(userId: String): List<RefundClaimEntity> = emptyList()
        override suspend fun hasPendingSyncClaims(userId: String): Boolean = false
        override suspend fun updateSyncState(localId: String, syncStatus: SyncStatus, remoteId: String?, lastSyncedAt: Long?, lastSyncError: String?) = Unit
        override suspend fun markSyncedIfUnchanged(localId: String, remoteId: String?, lastSyncedAt: Long?, expectedUpdatedAt: Long): Int = 1
        override suspend fun attachRemoteId(localId: String, remoteId: String?, lastSyncedAt: Long?) = Unit
        override suspend fun getAllClaimsForUser(userId: String): List<RefundClaimEntity> = emptyList()
        override suspend fun deleteAllForUser(userId: String) = Unit
        override suspend fun getClaimByRemoteId(remoteId: String): RefundClaimEntity? = null
        override suspend fun getClaimsAwaitingReceiptUpload(userId: String): List<RefundClaimEntity> =
            emptyList()
        override suspend fun markNeverSyncedPendingCreate(userId: String) = Unit
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
        override suspend fun markSyncedIfUnchanged(localId: String, remoteId: String?, lastSyncedAt: Long?, expectedUpdatedAt: Long): Int = 1
        override suspend fun attachRemoteId(localId: String, remoteId: String?, lastSyncedAt: Long?) = Unit
        override suspend fun getAllSettingsForUser(userId: String): List<UserSettingsEntity> = emptyList()
        override suspend fun deleteAllForUser(userId: String) = Unit
        override suspend fun getSettingsByRemoteId(remoteId: String): UserSettingsEntity? = null
        override suspend fun markNeverSyncedPendingCreate(userId: String) = Unit
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
        override suspend fun markSyncedIfUnchanged(localId: String, remoteId: String?, syncedAt: Long?, expectedUpdatedAt: Long): Int = 1
        override suspend fun attachRemoteId(localId: String, remoteId: String?, syncedAt: Long?) = Unit
        override suspend fun clearDefaultForUser(userId: String) = Unit
        override suspend fun deleteAllForUser(userId: String) = Unit
        override suspend fun markNeverSyncedPendingCreate(userId: String) = Unit
        override suspend fun adoptLegacyUser(userId: String) = Unit
    }

    private class EmptyPremiumProfileDao : PremiumProfileDao {
        override fun observeProfiles(userId: String): Flow<List<PremiumProfileEntity>> = emptyFlow()
        override suspend fun getByUser(userId: String): List<PremiumProfileEntity> = emptyList()
        override suspend fun getByLocalId(localId: String): PremiumProfileEntity? = null
        override suspend fun getById(userId: String, localId: String): PremiumProfileEntity? = null
        override suspend fun getByRemoteId(remoteId: String): PremiumProfileEntity? = null
        override suspend fun getPendingSyncProfiles(userId: String): List<PremiumProfileEntity> = emptyList()
        override suspend fun hasPendingSyncProfiles(userId: String): Boolean = false
        override fun observePendingSyncProfiles(userId: String): Flow<List<PremiumProfileEntity>> = emptyFlow()
        override suspend fun getAllProfilesForUser(userId: String): List<PremiumProfileEntity> = emptyList()
        override suspend fun upsert(profile: PremiumProfileEntity) = Unit
        override suspend fun insert(profile: PremiumProfileEntity) = Unit
        override suspend fun updateSyncState(localId: String, status: SyncStatus, remoteId: String?, syncedAt: Long?, error: String?) = Unit
        override suspend fun markSyncedIfUnchanged(localId: String, remoteId: String?, syncedAt: Long?, expectedUpdatedAt: Long): Int = 1
        override suspend fun attachRemoteId(localId: String, remoteId: String?, syncedAt: Long?) = Unit
        override suspend fun clearDefaultForUser(userId: String) = Unit
        override suspend fun deleteAllForUser(userId: String) = Unit
        override suspend fun adoptLegacyUser(userId: String) = Unit
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
        override suspend fun markSyncedIfUnchanged(localId: String, remoteId: String?, syncedAt: Long?, expectedUpdatedAt: Long): Int = 1
        override suspend fun attachRemoteId(localId: String, remoteId: String?, syncedAt: Long?) = Unit
        override suspend fun updateLastUsed(localId: String, lastUsedAt: Long, updatedAt: Long) = Unit
        override suspend fun softDeleteTask(
            localId: String,
            deletedAt: Long,
            syncStatus: SyncStatus,
            updatedAt: Long,
        ) = Unit
        override suspend fun deleteAllForUser(userId: String) = Unit
        override suspend fun markNeverSyncedPendingCreate(userId: String) = Unit
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
        override suspend fun fetchUpdatedSince(
            sinceIso: String?,
            limit: Int,
            offset: Int,
        ): List<RemoteTaskRow> = emptyList()
        override suspend fun findById(remoteId: String): RemoteTaskRow? = null
        override suspend fun insert(task: RemoteTaskInsert): RemoteTaskRow = error("not used")
        override suspend fun update(remoteId: String, task: RemoteTaskUpdate): RemoteTaskRow? = null
    }

    private class RecordingRemoteTaskDataSource : RemoteTaskDataSource {
        /** Ids published as tombstones — a delete is an update carrying deleted_at. */
        val deletes = mutableListOf<String>()

        override suspend fun fetchUpdatedSince(
            sinceIso: String?,
            limit: Int,
            offset: Int,
        ): List<RemoteTaskRow> = emptyList()
        override suspend fun findById(remoteId: String): RemoteTaskRow? = null
        override suspend fun insert(task: RemoteTaskInsert): RemoteTaskRow = error("not used")
        override suspend fun update(remoteId: String, task: RemoteTaskUpdate): RemoteTaskRow? {
            if (task.deletedAt != null) deletes += remoteId
            // A row means the write landed. Returning null would mean the guard
            // turned it away, which is a different thing entirely.
            return RemoteTaskRow(
                id = remoteId,
                userId = "user-1",
                name = task.name,
                icon = task.icon,
                color = task.color,
                hourlyRate = task.hourlyRate,
                isArchived = task.isArchived,
                lastUsedAt = task.lastUsedAt,
                createdAt = task.clientUpdatedAt,
                updatedAt = task.clientUpdatedAt,
                deletedAt = task.deletedAt,
                clientUpdatedAt = task.clientUpdatedAt,
            )
        }
    }

    private class InMemoryTaskDao : TaskDao {
        private val store = mutableMapOf<String, TaskEntity>()

        override suspend fun getAllTasksForUser(userId: String): List<TaskEntity> =
            store.values.filter { it.userId == userId && it.deletedAt == null }

        override suspend fun getPendingSyncTasks(userId: String): List<TaskEntity> =
            store.values.filter { it.userId == userId && it.syncStatus != SyncStatus.SYNCED }

        override suspend fun hasPendingSyncTasks(userId: String): Boolean =
            store.values.any { it.userId == userId && it.syncStatus != SyncStatus.SYNCED }

        override fun observePendingSyncTasks(userId: String): Flow<List<TaskEntity>> = emptyFlow()

        override suspend fun markNeverSyncedPendingCreate(userId: String) = Unit

        override suspend fun upsert(task: TaskEntity) {
            store[task.localId] = task
        }

        override suspend fun adoptLegacyUser(userId: String) = Unit

        override fun observeActiveTasks(userId: String): Flow<List<TaskEntity>> = emptyFlow()

        override fun observeAllTasks(userId: String): Flow<List<TaskEntity>> = emptyFlow()

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
            return 1
        }

        override suspend fun attachRemoteId(localId: String, remoteId: String?, syncedAt: Long?) {
            store[localId]?.let {
                store[localId] = it.copy(remoteId = remoteId, lastSyncedAt = syncedAt)
            }
        }

        override suspend fun updateLastUsed(localId: String, lastUsedAt: Long, updatedAt: Long) = Unit

        override suspend fun softDeleteTask(
            localId: String,
            deletedAt: Long,
            syncStatus: SyncStatus,
            updatedAt: Long,
        ) {
            store[localId]?.let {
                store[localId] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            }
        }

        override suspend fun deleteAllForUser(userId: String) {
            store.entries.removeIf { it.value.userId == userId }
        }
    }

    private class MissingTasksRemoteDataSource : RemoteTaskDataSource {
        private val missingTableError = RuntimeException(
            "Could not find the table 'public.tasks' in the schema cache (PGRST205)",
        )

        override suspend fun fetchUpdatedSince(
            sinceIso: String?,
            limit: Int,
            offset: Int,
        ): List<RemoteTaskRow> = throw missingTableError

        override suspend fun findById(remoteId: String): RemoteTaskRow? = throw missingTableError

        override suspend fun insert(task: RemoteTaskInsert): RemoteTaskRow = throw missingTableError

        override suspend fun update(
            remoteId: String,
            task: RemoteTaskUpdate,
        ): RemoteTaskRow? = throw missingTableError
    }

    private class EmptyRemoteRefundClaimDataSource : RemoteRefundClaimDataSource {
        override suspend fun fetchUpdatedSince(
            sinceIso: String?,
            limit: Int,
            offset: Int,
        ): List<RemoteRefundClaimRow> = emptyList()
        override suspend fun findById(remoteId: String): RemoteRefundClaimRow? = null
        override suspend fun insert(claim: RemoteRefundClaimInsert): RemoteRefundClaimRow =
            error("not used")

        // Returns a row, not null. Null is the guard rejecting a stale write, which
        // is a different outcome entirely — a stub that returns it makes every push
        // look like a conflict.
        override suspend fun update(
            remoteId: String,
            claim: RemoteRefundClaimUpdate,
        ): RemoteRefundClaimRow = RemoteRefundClaimRow(
            id = remoteId,
            shiftId = "shift-1",
            userId = "user-1",
            direction = claim.direction,
            provider = claim.provider,
            amount = claim.amount,
            rideAt = claim.rideAt,
            notes = claim.notes,
            receiptPath = claim.receiptPath,
            createdAt = claim.clientUpdatedAt,
            updatedAt = claim.clientUpdatedAt,
            deletedAt = claim.deletedAt,
            clientUpdatedAt = claim.clientUpdatedAt,
        )
    }

    private class EmptyRemoteUserSettingsDataSource : RemoteUserSettingsDataSource {
        override suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteUserSettingsRow> =
            emptyList()
        override suspend fun upsert(settings: RemoteUserSettingsUpsert): RemoteUserSettingsRow =
            error("not used")
        override suspend fun update(remoteId: String, settings: RemoteUserSettingsUpdate) = Unit
    }

    private class EmptyRemoteCompensationProfileDataSource : RemoteCompensationProfileDataSource {
        override suspend fun fetchUpdatedSince(
            sinceIso: String?,
            limit: Int,
            offset: Int,
        ): List<RemoteCompensationProfileRow> = emptyList()
        override suspend fun findById(remoteId: String): RemoteCompensationProfileRow? = null
        override suspend fun insert(profile: RemoteCompensationProfileInsert): RemoteCompensationProfileRow =
            error("not used")
        override suspend fun update(
            remoteId: String,
            profile: RemoteCompensationProfileUpdate,
        ): RemoteCompensationProfileRow? = null
    }

    private class EmptyRemotePremiumProfileDataSource : RemotePremiumProfileDataSource {
        override suspend fun fetchUpdatedSince(
            sinceIso: String?,
            limit: Int,
            offset: Int,
        ): List<RemotePremiumProfileRow> = emptyList()
        override suspend fun findById(remoteId: String): RemotePremiumProfileRow? = null
        override suspend fun insert(profile: RemotePremiumProfileInsert): RemotePremiumProfileRow =
            error("not used")
        override suspend fun update(
            remoteId: String,
            profile: RemotePremiumProfileUpdate,
        ): RemotePremiumProfileRow? = null
    }

    private class EmptyRemoteProjectDataSource : com.elmtrackr.app.data.remote.RemoteProjectDataSource {
        override suspend fun fetchUpdatedSince(
            sinceIso: String?,
            limit: Int,
            offset: Int,
        ): List<com.elmtrackr.app.data.remote.RemoteProjectRow> = emptyList()
        override suspend fun findById(remoteId: String): com.elmtrackr.app.data.remote.RemoteProjectRow? = null
        override suspend fun insert(
            project: com.elmtrackr.app.data.remote.RemoteProjectInsert,
        ): com.elmtrackr.app.data.remote.RemoteProjectRow = error("not used")
        override suspend fun update(
            remoteId: String,
            project: com.elmtrackr.app.data.remote.RemoteProjectUpdate,
        ): com.elmtrackr.app.data.remote.RemoteProjectRow? = null
    }

    private class EmptyRemoteProjectBillingRecordDataSource :
        com.elmtrackr.app.data.remote.RemoteProjectBillingRecordDataSource {
        override suspend fun fetchUpdatedSince(
            sinceIso: String?,
            limit: Int,
            offset: Int,
        ): List<com.elmtrackr.app.data.remote.RemoteProjectBillingRecordRow> = emptyList()
        override suspend fun findById(
            remoteId: String,
        ): com.elmtrackr.app.data.remote.RemoteProjectBillingRecordRow? = null
        override suspend fun insert(
            record: com.elmtrackr.app.data.remote.RemoteProjectBillingRecordInsert,
        ): com.elmtrackr.app.data.remote.RemoteProjectBillingRecordRow = error("not used")
        override suspend fun update(
            remoteId: String,
            record: com.elmtrackr.app.data.remote.RemoteProjectBillingRecordUpdate,
        ): com.elmtrackr.app.data.remote.RemoteProjectBillingRecordRow? = null
    }

    private class EmptyRemoteProjectPaymentDataSource :
        com.elmtrackr.app.data.remote.RemoteProjectPaymentDataSource {
        override suspend fun fetchUpdatedSince(
            sinceIso: String?,
            limit: Int,
            offset: Int,
        ): List<com.elmtrackr.app.data.remote.RemoteProjectPaymentRow> = emptyList()
        override suspend fun findById(
            remoteId: String,
        ): com.elmtrackr.app.data.remote.RemoteProjectPaymentRow? = null
        override suspend fun insert(
            payment: com.elmtrackr.app.data.remote.RemoteProjectPaymentInsert,
        ): com.elmtrackr.app.data.remote.RemoteProjectPaymentRow = error("not used")
        override suspend fun update(
            remoteId: String,
            payment: com.elmtrackr.app.data.remote.RemoteProjectPaymentUpdate,
        ): com.elmtrackr.app.data.remote.RemoteProjectPaymentRow? = null
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

        override suspend fun getShiftByStartTime(userId: String, startTime: Long): ShiftEntity? =
            shifts.value.firstOrNull {
                it.userId == userId && it.startTime == startTime && it.deletedAt == null
            }

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

        override fun observeShiftsForProject(
            userId: String,
            projectId: String,
        ): Flow<List<ShiftEntity>> = shifts.map { list ->
            list.filter { it.userId == userId && it.projectId == projectId && it.deletedAt == null }
        }

    /** Faithful rather than a stub, for the same reason as the other fakes. */
        override suspend fun unlinkProject(userId: String, projectId: String, updatedAt: Long): Int {
            val affected = shifts.value.filter {
                it.userId == userId && it.projectId == projectId && it.deletedAt == null
            }
            shifts.value = shifts.value.map { shift ->
                if (shift in affected) {
                    shift.copy(
                        projectId = null,
                        projectNameSnapshot = null,
                        compensationSource = "EMPLOYEE",
                        compensationSnapshotJson = null,
                        syncStatus = if (shift.syncStatus == SyncStatus.PENDING_CREATE) {
                            SyncStatus.PENDING_CREATE
                        } else {
                            SyncStatus.PENDING_UPDATE
                        },
                        updatedAt = updatedAt,
                    )
                } else {
                    shift
                }
            }
            return affected.size
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

    private class InMemoryProfileDao : ProfileDao {
        private val profiles = MutableStateFlow<List<ProfileEntity>>(emptyList())

        override suspend fun adoptLegacyUser(userId: String) = Unit

        override fun observeProfile(userId: String): Flow<ProfileEntity?> =
            profiles.map { list -> list.firstOrNull { it.userId == userId && it.deletedAt == null } }

        override suspend fun getProfile(userId: String): ProfileEntity? =
            profiles.value.firstOrNull { it.userId == userId && it.deletedAt == null }

        override suspend fun insertProfile(profile: ProfileEntity) {
            upsertProfile(profile)
        }

        override suspend fun updateProfile(profile: ProfileEntity) {
            upsertProfile(profile)
        }

        override suspend fun upsertProfile(profile: ProfileEntity) {
            profiles.value = profiles.value.filterNot { it.localId == profile.localId } + profile
        }

        override suspend fun getPendingSyncProfiles(userId: String): List<ProfileEntity> =
            profiles.value.filter {
                it.userId == userId &&
                    it.syncStatus in setOf(
                        SyncStatus.PENDING_CREATE,
                        SyncStatus.PENDING_UPDATE,
                        SyncStatus.PENDING_DELETE,
                        SyncStatus.FAILED,
                    )
            }

        override suspend fun updateSyncState(
            localId: String,
            syncStatus: SyncStatus,
            remoteId: String?,
            lastSyncedAt: Long?,
            lastSyncError: String?,
        ) {
            profiles.value = profiles.value.map {
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

        override suspend fun getProfileByRemoteId(remoteId: String): ProfileEntity? =
            profiles.value.firstOrNull { it.remoteId == remoteId }

        override suspend fun markNeverSyncedPendingUpdate(userId: String) {
            profiles.value = profiles.value.map {
                if (it.userId == userId && it.syncStatus == SyncStatus.SYNCED &&
                    it.lastSyncedAt == null && it.deletedAt == null
                ) {
                    it.copy(syncStatus = SyncStatus.PENDING_UPDATE)
                } else {
                    it
                }
            }
        }

        override suspend fun hasPendingSyncProfiles(userId: String): Boolean =
            getPendingSyncProfiles(userId).isNotEmpty()

        override fun observePendingSyncProfiles(userId: String): Flow<List<ProfileEntity>> =
            profiles.map { list ->
                list.filter {
                    it.userId == userId &&
                        it.syncStatus in setOf(
                            SyncStatus.PENDING_CREATE,
                            SyncStatus.PENDING_UPDATE,
                            SyncStatus.PENDING_DELETE,
                            SyncStatus.FAILED,
                        )
                }
            }

        override suspend fun deleteAllForUser(userId: String) {
            profiles.value = profiles.value.filterNot { it.userId == userId }
        }
    }
}
