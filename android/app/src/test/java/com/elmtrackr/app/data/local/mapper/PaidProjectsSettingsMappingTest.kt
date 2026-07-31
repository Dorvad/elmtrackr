package com.elmtrackr.app.data.local.mapper

import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.remote.RemoteUserSettingsRow
import com.elmtrackr.app.data.remote.toLocalEntity
import com.elmtrackr.app.data.sync.toBackupRow
import com.elmtrackr.app.data.sync.toEntity
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The Paid Projects flag and its defaults across every persistence boundary.
 *
 * The flag itself is part of the Supabase contract already; the project
 * defaults are local-only until the contract carries them, which makes the
 * "a pull must not wipe them" case the important one here.
 */
class PaidProjectsSettingsMappingTest {

    private fun settings() = UserSettings(
        id = "s1",
        userId = "u1",
        featuresPaidProjects = true,
        projectsDefaultRegionCode = RegionCode.IL,
        projectsDefaultCurrencyCode = "ILS",
        projectsTaxLabel = "VAT",
        projectsTaxRateBasisPoints = 1800,
        projectsTaxInclusive = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `domain to entity and back preserves the flag and defaults`() {
        val round = settings().toEntity().toDomain()

        assertTrue(round.featuresPaidProjects)
        assertEquals(RegionCode.IL, round.projectsDefaultRegionCode)
        assertEquals("ILS", round.projectsDefaultCurrencyCode)
        assertEquals("VAT", round.projectsTaxLabel)
        assertEquals(1800, round.projectsTaxRateBasisPoints)
        assertTrue(round.projectsTaxInclusive)
    }

    @Test
    fun `an existing user upgrading keeps the module off with no defaults`() {
        // What Room produces for a row written before this migration: the new
        // columns default to NULL / 0.
        val upgraded = UserSettings(
            id = "s1",
            userId = "u1",
            hourlyRate = 62.5,
            onboardingCompleted = true,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        ).toEntity().toDomain()

        assertFalse(upgraded.featuresPaidProjects)
        assertEquals(null, upgraded.projectsDefaultRegionCode)
        assertEquals(null, upgraded.projectsDefaultCurrencyCode)
        assertEquals(null, upgraded.projectsTaxLabel)
        assertEquals(0, upgraded.projectsTaxRateBasisPoints)
        assertFalse(upgraded.projectsTaxInclusive)
        assertFalse(upgraded.projectsTaxEnabled)
        // Existing hours-and-pay data is untouched by the upgrade.
        assertEquals(62.5, upgraded.hourlyRate)
        assertTrue(upgraded.onboardingCompleted)
    }

    @Test
    fun `backup round trip preserves the flag and defaults`() {
        val round = settings().toEntity().toBackupRow().toEntity("u1").toDomain()

        assertTrue(round.featuresPaidProjects)
        assertEquals(RegionCode.IL, round.projectsDefaultRegionCode)
        assertEquals("VAT", round.projectsTaxLabel)
        assertEquals(1800, round.projectsTaxRateBasisPoints)
        assertTrue(round.projectsTaxInclusive)
    }

    @Test
    fun `a remote pull does not wipe locally stored project defaults`() {
        val local = settings().toEntity(syncStatus = SyncStatus.SYNCED, remoteId = "r1")
        val remote = RemoteUserSettingsRow(
            id = "r1",
            userId = "u1",
            timezone = "Asia/Jerusalem",
            dailyOvertimeThresholdMinutes = 516,
            weeklyOvertimeThresholdMinutes = 2520,
            weekendDays = listOf(5, 6),
            hourlyRate = 62.5,
            onboardingCompleted = true,
            featuresTravelRefunds = false,
            featuresPaidProjects = true,
            featuresInsights = true,
            featuresClockStyles = true,
            featuresOvertimeReminders = true,
            clockStyle = "classic",
            createdAt = "1970-01-01T00:00:00Z",
            updatedAt = "1970-01-01T00:00:01Z",
        )

        val applied = remote.toLocalEntity(
            existingLocalId = local.localId,
            preserveLocal = local,
        )

        assertEquals("ILS", applied.projectsDefaultCurrencyCode)
        assertEquals("VAT", applied.projectsTaxLabel)
        assertEquals(1800, applied.projectsTaxRateBasisPoints)
        assertTrue(applied.projectsTaxInclusive)
        // The flag itself is on the wire, so the remote value wins.
        assertTrue(applied.featuresPaidProjects)
    }

    @Test
    fun `a first pull with no local row starts with no project defaults`() {
        val remote = RemoteUserSettingsRow(
            id = "r1",
            userId = "u1",
            timezone = "UTC",
            dailyOvertimeThresholdMinutes = 480,
            weeklyOvertimeThresholdMinutes = 2400,
            weekendDays = listOf(5, 6),
            onboardingCompleted = true,
            featuresTravelRefunds = false,
            featuresPaidProjects = false,
            featuresInsights = true,
            featuresClockStyles = true,
            featuresOvertimeReminders = true,
            clockStyle = "classic",
            createdAt = "1970-01-01T00:00:00Z",
            updatedAt = "1970-01-01T00:00:00Z",
        )

        val applied = remote.toLocalEntity()

        assertEquals(null, applied.projectsTaxLabel)
        assertEquals(0, applied.projectsTaxRateBasisPoints)
        assertFalse(applied.projectsTaxInclusive)
    }
}
