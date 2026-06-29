package com.elmtrackr.app.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elmtrackr.app.data.local.ElmTrackrDatabase
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShiftDaoTest {

    private lateinit var db: ElmTrackrDatabase
    private lateinit var dao: ShiftDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ElmTrackrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.shiftDao()
    }

    @After
    fun closeDb() = db.close()

    private fun shift(
        localId: String = "s1",
        startTime: Long = 1_000_000L,
        endTime: Long? = 2_000_000L,
        syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    ) = ShiftEntity(
        localId = localId,
        remoteId = null,
        userId = "u1",
        startTime = startTime,
        endTime = endTime,
        breakMinutes = 0,
        notes = null,
        isSpecialDay = false,
        refundAction = null,
        createdAt = startTime,
        updatedAt = startTime,
        deletedAt = null,
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = null,
    )

    @Test
    fun insertAndObserveShift() = runTest {
        dao.insertShift(shift("s1"))
        val result = dao.observeShifts("u1").first()
        assertEquals(1, result.size)
        assertEquals("s1", result[0].localId)
    }

    @Test
    fun activeShiftQuery_returnsShiftWithNullEndTime() = runTest {
        dao.insertShift(shift("s1", endTime = null))
        dao.insertShift(shift("s2", endTime = 3_000_000L))
        val active = dao.observeActiveShift("u1").first()
        assertNotNull(active)
        assertEquals("s1", active!!.localId)
        assertNull(active.endTime)
    }

    @Test
    fun activeShiftQuery_returnsNullWhenNoActiveShift() = runTest {
        dao.insertShift(shift("s1", endTime = 2_000_000L))
        val active = dao.observeActiveShift("u1").first()
        assertNull(active)
    }

    @Test
    fun softDeleteSetsDeletedAt() = runTest {
        dao.insertShift(shift("s1"))
        dao.softDeleteShift("s1", deletedAt = 9_000_000L, syncStatus = SyncStatus.PENDING_DELETE, updatedAt = 9_000_000L)
        val result = dao.observeShifts("u1").first()
        assertTrue("Soft-deleted shift should be hidden from observe", result.isEmpty())
    }

    @Test
    fun pendingSyncObservesCreatedAndUpdated() = runTest {
        dao.insertShift(shift("s1", syncStatus = SyncStatus.PENDING_CREATE))
        dao.insertShift(shift("s2", syncStatus = SyncStatus.SYNCED))
        dao.insertShift(shift("s3", syncStatus = SyncStatus.PENDING_UPDATE))
        val pending = dao.observePendingSyncShifts("u1").first()
        val ids = pending.map { it.localId }
        assertTrue(ids.contains("s1"))
        assertTrue(ids.contains("s3"))
        assertTrue(!ids.contains("s2"))
    }

    @Test
    fun observeShiftsByDateRange_filtersCorrectly() = runTest {
        dao.insertShift(shift("s1", startTime = 1_000L))
        dao.insertShift(shift("s2", startTime = 5_000L))
        dao.insertShift(shift("s3", startTime = 9_000L))
        val result = dao.observeShiftsByDateRange("u1", fromEpoch = 2_000L, toEpoch = 8_000L).first()
        assertEquals(1, result.size)
        assertEquals("s2", result[0].localId)
    }

    @Test
    fun observeShiftsByDateRange_ordersMostRecentFirst() = runTest {
        dao.insertShift(shift("older", startTime = 1_000L))
        dao.insertShift(shift("newer", startTime = 5_000L))
        dao.insertShift(shift("newest", startTime = 9_000L))
        val result = dao.observeShiftsByDateRange("u1", fromEpoch = 0L, toEpoch = 10_000L).first()
        assertEquals(listOf("newest", "newer", "older"), result.map { it.localId })
    }

    @Test
    fun getShiftById_returnsNullForMissing() = runTest {
        assertNull(dao.getShiftById("nonexistent"))
    }
}
