package com.elmtrackr.app.data.repository

import android.app.Application
import androidx.room.Room
import com.elmtrackr.app.data.local.DirectTransactionRunner
import com.elmtrackr.app.data.local.ElmTrackrDatabase
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.fake.FakeCompensationProfileDao
import com.elmtrackr.app.fake.FakeRefundsRepository
import com.elmtrackr.app.fake.FakeSyncTrigger
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A watch replaying its offline queue must not bring back a shift the user deleted.
 *
 * `LocalShiftsRepositoryTest` covers the replay that matches a *live* shift. This is
 * the other half, and it needs a real database: the damage is done by the unique
 * `(userId, startTime)` index together with `OnConflictStrategy.REPLACE`, neither
 * of which an in-memory map keyed by `localId` reproduces. The guard looked up the
 * matching shift with `deletedAt IS NULL`, so a tombstone was invisible to it, the
 * insert went ahead, and REPLACE deleted the tombstone to make room — leaving an
 * open shift where the user had removed one, and no pending delete to send.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ShiftTombstoneReplayTest {

    private lateinit var db: ElmTrackrDatabase
    private lateinit var dao: ShiftDao
    private lateinit var repository: LocalShiftsRepository

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ElmTrackrDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.shiftDao()
        repository = LocalShiftsRepository(
            dao,
            FakeRefundsRepository(),
            FakeSyncTrigger(),
            FakeCompensationProfileDao(),
            DirectTransactionRunner,
        )
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun `a replayed punch-in matching a deleted shift leaves the deletion in place`() = runTest {
        val start = System.currentTimeMillis() - 7_200_000L
        val deletedAt = start + 5_400_000L
        dao.insertShift(
            shift(localId = "deleted-1", startTime = start, endTime = start + 3_600_000L).copy(
                remoteId = "remote-1",
                deletedAt = deletedAt,
                syncStatus = SyncStatus.PENDING_DELETE,
            ),
        )

        repository.clockIn(userId = USER, startTimeMillis = start)

        val stored = dao.getShiftById("deleted-1")
        assertNotNull("the tombstone was replaced by the replayed punch", stored)
        assertEquals(deletedAt, stored!!.deletedAt)
        assertEquals(SyncStatus.PENDING_DELETE, stored.syncStatus)
        assertEquals("remote-1", stored.remoteId)
        assertTrue("the replay opened a new shift", dao.getActiveShifts(USER).isEmpty())
    }

    @Test
    fun `a punch-in at a new start time still opens a shift beside a tombstone`() = runTest {
        val start = System.currentTimeMillis() - 7_200_000L
        dao.insertShift(
            shift(localId = "deleted-1", startTime = start, endTime = start + 3_600_000L).copy(
                deletedAt = start + 5_400_000L,
                syncStatus = SyncStatus.PENDING_DELETE,
            ),
        )

        val opened = repository.clockIn(userId = USER, startTimeMillis = start + 60_000L)

        assertEquals(opened.id, dao.getActiveShifts(USER).single().localId)
        assertNotNull(dao.getShiftById("deleted-1"))
    }

    private fun shift(localId: String, startTime: Long, endTime: Long?) = ShiftEntity(
        localId = localId,
        remoteId = null,
        userId = USER,
        startTime = startTime,
        endTime = endTime,
        breakMinutes = 0,
        notes = null,
        isSpecialDay = false,
        refundAction = null,
        createdAt = startTime,
        updatedAt = startTime,
        deletedAt = null,
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = null,
    )

    private companion object {
        const val USER = "u1"
    }
}
