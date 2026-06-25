package com.elmtrackr.app.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elmtrackr.app.data.local.ElmTrackrDatabase
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDaoTest {

    private lateinit var db: ElmTrackrDatabase
    private lateinit var dao: SettingsDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ElmTrackrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.settingsDao()
    }

    @After
    fun closeDb() = db.close()

    private fun settings(
        hourlyRate: Double? = 60.0,
        weekendDays: String = "5,6",
    ) = UserSettingsEntity(
        localId = "cfg-1",
        remoteId = null,
        userId = "u1",
        timezone = "UTC",
        dailyOvertimeThresholdMinutes = 480,
        weeklyOvertimeThresholdMinutes = 2400,
        weekendDays = weekendDays,
        hourlyRate = hourlyRate,
        onboardingCompleted = false,
        onboardingCompletedAt = null,
        featuresTravelRefunds = false,
        featuresPaidProjects = false,
        featuresInsights = true,
        featuresClockStyles = true,
        clockStyle = "CLASSIC",
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
        syncStatus = SyncStatus.PENDING_CREATE,
        lastSyncError = null,
        lastSyncedAt = null,
    )

    @Test
    fun insertAndObserveSettings() = runTest {
        dao.insertSettings(settings())
        val result = dao.observeSettings("u1").first()
        assertNotNull(result)
        assertEquals("cfg-1", result!!.localId)
        assertEquals("5,6", result.weekendDays)
    }

    @Test
    fun observeSettings_returnsNullWhenEmpty() = runTest {
        val result = dao.observeSettings("u1").first()
        assertNull(result)
    }

    @Test
    fun upsertSettings_updatesExistingRecord() = runTest {
        dao.insertSettings(settings(hourlyRate = 50.0))
        dao.upsertSettings(settings(hourlyRate = 80.0))
        val result = dao.getSettings("u1")
        assertEquals(80.0, result!!.hourlyRate!!, 0.01)
    }

    @Test
    fun getSettings_returnsNullForMissingUser() = runTest {
        assertNull(dao.getSettings("no-such-user"))
    }
}
