package com.elmtrackr.app.sync

import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.repository.SyncRepositoryImpl
import com.elmtrackr.app.domain.model.SyncResult
import com.elmtrackr.app.fake.FakeCompensationProfileDao
import com.elmtrackr.app.fake.FakeProfileDao
import com.elmtrackr.app.fake.FakeRefundClaimDao
import com.elmtrackr.app.fake.FakeRemoteProfileDataSource
import com.elmtrackr.app.fake.FakeRemoteRefundsDataSource
import com.elmtrackr.app.fake.FakeRemoteSettingsDataSource
import com.elmtrackr.app.fake.FakeRemoteShiftsDataSource
import com.elmtrackr.app.fake.FakeSettingsDao
import com.elmtrackr.app.fake.FakeShiftDao
import com.elmtrackr.app.fake.FakeTaskDao
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SyncRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val shiftDao = FakeShiftDao()
    private val refundDao = FakeRefundClaimDao()
    private val settingsDao = FakeSettingsDao()
    private val profileDao = FakeProfileDao()
    private val compensationProfileDao = FakeCompensationProfileDao()
    private val taskDao = FakeTaskDao()
    private val remoteShifts = FakeRemoteShiftsDataSource()
    private val remoteRefunds = FakeRemoteRefundsDataSource()
    private val remoteSettings = FakeRemoteSettingsDataSource()
    private val remoteProfile = FakeRemoteProfileDataSource()

    private fun buildRepo(configured: Boolean = true) = SyncRepositoryImpl(
        shiftDao = shiftDao,
        refundClaimDao = refundDao,
        settingsDao = settingsDao,
        profileDao = profileDao,
        compensationProfileDao = compensationProfileDao,
        taskDao = taskDao,
        remoteShifts = if (configured) remoteShifts else null,
        remoteRefunds = if (configured) remoteRefunds else null,
        remoteSettings = if (configured) remoteSettings else null,
        remoteProfile = if (configured) remoteProfile else null,
        remoteCompensationProfiles = null,
        remoteTasks = null,
    )

    private val now = Instant.now().toEpochMilli()

    private fun shift(
        localId: String = "local-1",
        remoteId: String? = null,
        userId: String = "user-1",
        syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
        updatedAt: Long = now,
        endTime: Long? = null,
    ) = ShiftEntity(
        localId = localId,
        remoteId = remoteId,
        userId = userId,
        startTime = now - 3600_000,
        endTime = endTime,
        breakMinutes = 0,
        notes = null,
        isSpecialDay = false,
        refundAction = null,
        createdAt = now - 3600_000,
        updatedAt = updatedAt,
        deletedAt = null,
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = null,
    )

    // ---- NotConfigured ----

    @Test
    fun `returns NotConfigured when remote data sources are null`() = runTest {
        val result = buildRepo(configured = false).syncAll("user-1")
        assertEquals(SyncResult.NotConfigured, result)
    }

    // ---- Push: PENDING_CREATE ----

    @Test
    fun `pushes PENDING_CREATE shift and marks SYNCED with remoteId`() = runTest {
        val entity = shift(syncStatus = SyncStatus.PENDING_CREATE)
        shiftDao.insertShift(entity)

        val result = buildRepo().syncAll("user-1")

        assertEquals(SyncResult.Success, result)
        assertTrue(remoteShifts.upserted.isNotEmpty())

        val updated = shiftDao.getShiftById("local-1")
        assertEquals(SyncStatus.SYNCED, updated?.syncStatus)
        assertNotNull(updated?.remoteId)
    }

    // ---- Push: PENDING_UPDATE ----

    @Test
    fun `pushes PENDING_UPDATE shift reusing existing remoteId`() = runTest {
        val entity = shift(remoteId = "remote-1", syncStatus = SyncStatus.PENDING_UPDATE)
        shiftDao.insertShift(entity)

        buildRepo().syncAll("user-1")

        val json = remoteShifts.upserted.first()
        assertEquals("remote-1", json["id"]?.toString()?.trim('"'))

        val updated = shiftDao.getShiftById("local-1")
        assertEquals("remote-1", updated?.remoteId)
        assertEquals(SyncStatus.SYNCED, updated?.syncStatus)
    }

    // ---- Push: PENDING_DELETE ----

    @Test
    fun `pushes PENDING_DELETE shift and marks SYNCED`() = runTest {
        val entity = shift(remoteId = "remote-1", syncStatus = SyncStatus.PENDING_DELETE)
        shiftDao.insertShift(entity)

        buildRepo().syncAll("user-1")

        assertTrue(remoteShifts.deleted.contains("remote-1"))
        assertEquals(SyncStatus.SYNCED, shiftDao.getShiftById("local-1")?.syncStatus)
    }

    // ---- Push: PENDING_DELETE with no remoteId ----

    @Test
    fun `PENDING_DELETE with no remoteId skips remote call and marks SYNCED`() = runTest {
        val entity = shift(remoteId = null, syncStatus = SyncStatus.PENDING_DELETE)
        shiftDao.insertShift(entity)

        buildRepo().syncAll("user-1")

        assertTrue(remoteShifts.deleted.isEmpty())
        assertEquals(SyncStatus.SYNCED, shiftDao.getShiftById("local-1")?.syncStatus)
    }

    // ---- Push: error marks FAILED ----

    @Test
    fun `push failure marks shift as FAILED and returns PartialSuccess`() = runTest {
        remoteShifts.shouldThrow = RuntimeException("network error")
        val entity = shift(syncStatus = SyncStatus.PENDING_CREATE)
        shiftDao.insertShift(entity)

        val result = buildRepo().syncAll("user-1")

        assertTrue(result is SyncResult.PartialSuccess)
        assertEquals(SyncStatus.FAILED, shiftDao.getShiftById("local-1")?.syncStatus)
        assertEquals("Network unavailable.", shiftDao.getShiftById("local-1")?.lastSyncError)
    }

    // ---- Pull: new remote record ----

    @Test
    fun `pull inserts new remote shift locally`() = runTest {
        remoteShifts.addRemoteItem(buildJsonObject {
            put("id", "remote-new")
            put("user_id", "user-1")
            put("start_time", now - 3600_000)
            put("break_minutes", 0)
            put("is_special_day", false)
            put("created_at", now - 3600_000)
            put("updated_at", now)
        })

        buildRepo().syncAll("user-1")

        val local = shiftDao.getShiftByRemoteId("remote-new")
        assertNotNull(local)
        assertEquals(SyncStatus.SYNCED, local?.syncStatus)
    }

    // ---- Pull: remote newer → update local ----

    @Test
    fun `pull updates local SYNCED shift when remote is newer`() = runTest {
        val entity = shift(remoteId = "remote-1", syncStatus = SyncStatus.SYNCED, updatedAt = now - 1000)
        shiftDao.insertShift(entity)

        remoteShifts.addRemoteItem(buildJsonObject {
            put("id", "remote-1")
            put("user_id", "user-1")
            put("start_time", now - 3600_000)
            put("break_minutes", 15)
            put("is_special_day", false)
            put("created_at", now - 3600_000)
            put("updated_at", now)
        })

        buildRepo().syncAll("user-1")

        val local = shiftDao.getShiftByRemoteId("remote-1")
        assertEquals(15, local?.breakMinutes)
    }

    // ---- Conflict: keep local pending changes ----

    @Test
    fun `successful push keeps local changes when subsequent pull is stale`() = runTest {
        val entity = shift(remoteId = "remote-1", syncStatus = SyncStatus.PENDING_UPDATE, updatedAt = now - 1000)
        shiftDao.insertShift(entity)

        remoteShifts.addRemoteItem(buildJsonObject {
            put("id", "remote-1")
            put("user_id", "user-1")
            put("start_time", now - 3600_000)
            put("break_minutes", 30)
            put("is_special_day", false)
            put("created_at", now - 3600_000)
            put("updated_at", now)
        })

        buildRepo().syncAll("user-1")

        // Local should still have original break_minutes (0), not remote's 30
        val local = shiftDao.getShiftById("local-1")
        assertEquals(0, local?.breakMinutes)
        assertEquals(SyncStatus.SYNCED, local?.syncStatus)
    }

    // ---- No duplicate active shift ----

    @Test
    fun `pull does not create second active shift when one already exists locally`() = runTest {
        val active = shift(localId = "active-local", remoteId = "active-remote", syncStatus = SyncStatus.SYNCED, endTime = null)
        shiftDao.insertShift(active)

        // Remote has a *different* active shift (no endTime)
        remoteShifts.addRemoteItem(buildJsonObject {
            put("id", "other-remote")
            put("user_id", "user-1")
            put("start_time", now - 7200_000)
            put("break_minutes", 0)
            put("is_special_day", false)
            put("created_at", now - 7200_000)
            put("updated_at", now - 7200_000)
        })

        buildRepo().syncAll("user-1")

        val allActive = shiftDao.getActiveShifts("user-1")
        assertEquals(1, allActive.size)
        assertEquals("active-local", allActive.first().localId)
    }

    // ---- Success result when no errors ----

    @Test
    fun `returns Success when no pending items and no remote items`() = runTest {
        val result = buildRepo().syncAll("user-1")
        assertEquals(SyncResult.Success, result)
    }

    @Test
    fun `sync only pushes pending records owned by requested user`() = runTest {
        shiftDao.insertShift(shift(localId = "mine", userId = "user-1"))
        shiftDao.insertShift(shift(localId = "theirs", userId = "user-2"))

        buildRepo().syncAll("user-1")

        assertEquals(1, remoteShifts.upserted.size)
        assertEquals("user-1", remoteShifts.upserted.single()["user_id"]?.jsonPrimitive?.content)
        assertEquals(SyncStatus.PENDING_CREATE, shiftDao.getShiftById("theirs")?.syncStatus)
    }
}
