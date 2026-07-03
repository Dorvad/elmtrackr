package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.TaskEntity
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import com.elmtrackr.app.fake.FakeCompensationProfileDao
import com.elmtrackr.app.fake.FakeRefundClaimDao
import com.elmtrackr.app.fake.FakeSettingsDao
import com.elmtrackr.app.fake.FakeShiftDao
import com.elmtrackr.app.fake.FakeTaskDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocalBackupImporterTest {

    private val taskDao = FakeTaskDao()
    private val shiftDao = FakeShiftDao()
    private val claimDao = FakeRefundClaimDao()
    private val settingsDao = FakeSettingsDao()
    private val profileDao = FakeCompensationProfileDao()

    private fun shift(localId: String = "shift-1", userId: String = "u1") = ShiftEntity(
        localId = localId, remoteId = "remote-$localId", userId = userId,
        startTime = 1_700_000_000_000, endTime = 1_700_030_000_000, breakMinutes = 30,
        notes = "Night shift", isSpecialDay = true, refundAction = "REFUND",
        compensationProfileId = "profile-1", compensationSnapshotJson = """{"v":1}""",
        taskId = "task-1", taskNameSnapshot = "Delivery", taskIconSnapshot = "bike",
        taskHourlyRateSnapshot = 52.5, createdAt = 1, updatedAt = 2, deletedAt = null,
        syncStatus = SyncStatus.SYNCED, lastSyncError = null, lastSyncedAt = 3,
    )

    private fun task(localId: String = "task-1", userId: String = "u1") = TaskEntity(
        localId = localId, remoteId = "remote-$localId", userId = userId,
        name = "Delivery", icon = "bike", color = "#FF0000", hourlyRate = 52.5,
        isArchived = false, lastUsedAt = 9, createdAt = 1, updatedAt = 2, deletedAt = null,
        syncStatus = SyncStatus.SYNCED, lastSyncError = null, lastSyncedAt = 3,
    )

    private fun claim(localId: String = "claim-1", userId: String = "u1") = RefundClaimEntity(
        localId = localId, remoteId = "remote-$localId", shiftLocalId = "shift-1",
        userId = userId, direction = "TO_WORK", provider = "LIME", amount = 12.5,
        rideAt = 1_700_000_100_000, notes = "morning", receiptPath = "receipts/a.jpg",
        createdAt = 1, updatedAt = 2, deletedAt = null,
        syncStatus = SyncStatus.SYNCED, lastSyncError = null, lastSyncedAt = 3,
    )

    private fun settings(userId: String = "u1") = UserSettingsEntity(
        localId = "settings-1", remoteId = "remote-settings", userId = userId,
        timezone = "Asia/Jerusalem", dailyOvertimeThresholdMinutes = 480,
        weeklyOvertimeThresholdMinutes = 2400, weekendDays = "5,6", hourlyRate = 50.0,
        currency = "ILS", regionCode = "IL", currencyCode = "ILS",
        defaultCompensationProfileId = "profile-1", onboardingCompleted = true,
        onboardingCompletedAt = 4, featuresTravelRefunds = true, featuresPaidProjects = false,
        featuresInsights = true, featuresClockStyles = true, featuresOvertimeReminders = false,
        clockStyle = "MINIMAL", createdAt = 1, updatedAt = 2, deletedAt = null,
        syncStatus = SyncStatus.SYNCED, lastSyncError = null, lastSyncedAt = 3,
    )

    private fun profile(localId: String = "profile-1", userId: String = "u1") = CompensationProfileEntity(
        localId = localId, remoteId = "remote-$localId", userId = userId,
        name = "Israel default", regionCode = "IL", currencyCode = "ILS",
        timezone = "Asia/Jerusalem", baseHourlyRate = 50.0, rulesJson = """{"rules":[]}""",
        stackingPolicy = "MAX", effectiveFrom = 1, effectiveUntil = null,
        isDefault = true, isArchived = false, createdAt = 1, updatedAt = 2, deletedAt = null,
        syncStatus = SyncStatus.SYNCED, lastSyncError = null, lastSyncedAt = 3,
    )

    private suspend fun exportSeeded(userId: String): String {
        taskDao.insert(task(userId = userId))
        shiftDao.insertShift(shift(userId = userId))
        claimDao.insertClaim(claim(userId = userId))
        settingsDao.insertSettings(settings(userId = userId))
        profileDao.insert(profile(userId = userId))
        return LocalBackupExporter.export(userId, taskDao, shiftDao, claimDao, settingsDao, profileDao, "1.0")
    }

    private val shiftDao2 = FakeShiftDao()
    private val claimDao2 = FakeRefundClaimDao()
    private val settingsDao2 = FakeSettingsDao()
    private val profileDao2 = FakeCompensationProfileDao()
    private val taskDao2 = FakeTaskDao()

    @Test
    fun `export then import round-trips all entities for the same user`() = runTest {
        val json = exportSeeded("u1")

        val summary = LocalBackupImporter.import(json, "u1", taskDao2, shiftDao2, claimDao2, settingsDao2, profileDao2)

        assertEquals(1, summary.importedTasks)
        assertEquals(1, summary.importedShifts)
        assertEquals(1, summary.importedRefundClaims)
        assertEquals(1, summary.importedCompensationProfiles)
        assertEquals(1, summary.importedSettings)
        assertEquals(0, summary.skipped)

        assertEquals(shift(), shiftDao2.getShiftById("shift-1"))
        assertEquals(task(), taskDao2.getByLocalId("task-1"))
        assertEquals(claim(), claimDao2.getClaimById("claim-1"))
        assertEquals(settings(), settingsDao2.getSettings("u1"))
        assertEquals(profile(), profileDao2.getByLocalId("profile-1"))
    }

    @Test
    fun `re-importing the same backup skips every row`() = runTest {
        val json = exportSeeded("u1")
        LocalBackupImporter.import(json, "u1", taskDao2, shiftDao2, claimDao2, settingsDao2, profileDao2)

        val second = LocalBackupImporter.import(json, "u1", taskDao2, shiftDao2, claimDao2, settingsDao2, profileDao2)

        assertEquals(0, second.importedTotal)
        assertEquals(5, second.skipped)
    }

    @Test
    fun `importing another user's backup adopts rows and strips remote linkage`() = runTest {
        val json = exportSeeded("u1")

        LocalBackupImporter.import(json, "u2", taskDao2, shiftDao2, claimDao2, settingsDao2, profileDao2)

        val imported = shiftDao2.getShiftById("shift-1")
        assertNotNull(imported)
        assertEquals("u2", imported!!.userId)
        assertNull(imported.remoteId)
        assertNull(imported.lastSyncedAt)
        assertEquals(SyncStatus.PENDING_CREATE, imported.syncStatus)
        assertEquals("Night shift", imported.notes)
        assertEquals(52.5, imported.taskHourlyRateSnapshot!!, 0.001)
    }

    @Test
    fun `existing settings are never overwritten`() = runTest {
        val json = exportSeeded("u1")
        val mySettings = settings(userId = "u2").copy(localId = "settings-mine", timezone = "UTC")
        settingsDao2.insertSettings(mySettings)

        val summary = LocalBackupImporter.import(json, "u2", taskDao2, shiftDao2, claimDao2, settingsDao2, profileDao2)

        assertEquals(0, summary.importedSettings)
        assertEquals("UTC", settingsDao2.getSettings("u2")!!.timezone)
    }

    @Test
    fun `claims without their shift are skipped`() = runTest {
        taskDao.insert(task())
        claimDao.insertClaim(claim(localId = "orphan-claim").copy(shiftLocalId = "missing-shift"))
        val json = LocalBackupExporter.export("u1", taskDao, shiftDao, claimDao, settingsDao, profileDao, "1.0")

        val summary = LocalBackupImporter.import(json, "u1", taskDao2, shiftDao2, claimDao2, settingsDao2, profileDao2)

        assertEquals(0, summary.importedRefundClaims)
        assertNull(claimDao2.getClaimById("orphan-claim"))
    }

    @Test
    fun `non-backup json is rejected with a friendly message`() = runTest {
        val error = runCatching {
            LocalBackupImporter.import("""{"hello":"world"}""", "u1", taskDao2, shiftDao2, claimDao2, settingsDao2, profileDao2)
        }.exceptionOrNull()

        assertEquals("This file is not an ElmTrackr backup.", (error as BackupImportException).message)
    }

    @Test
    fun `old format backups are rejected with a friendly message`() = runTest {
        val v1 = """
            {"formatVersion":1,"exportedAt":"now","userId":"u1","appVersion":"0.9",
             "tasks":[],"shifts":[],"refundClaims":[],"userSettings":[],"compensationProfiles":[]}
        """.trimIndent()
        val error = runCatching {
            LocalBackupImporter.import(v1, "u1", taskDao2, shiftDao2, claimDao2, settingsDao2, profileDao2)
        }.exceptionOrNull()

        assertEquals(
            "This backup was created by an older app version and can't be imported.",
            (error as BackupImportException).message,
        )
    }
}
