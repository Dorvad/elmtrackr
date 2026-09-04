package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.ProfileEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `user_settings` and `profiles` were the two tables without an edit-version
 * guard.
 *
 * `20260806000000_sync_tombstones_and_row_versions.sql` gave `client_updated_at`
 * to shifts, refund claims, compensation profiles, premium profiles and tasks,
 * and every update to those is filtered on it — a write carrying an older edit
 * than the stored one matches nothing and is read as a conflict. These two were
 * left out of that array, and `supabase-contract.md` claimed the filter applied
 * to *every* update, so nothing pointed at the gap.
 *
 * It mattered most for `user_settings`, which holds the overtime thresholds, the
 * hourly rate, weekend days, currency, region and all six feature flags. A device
 * that had been offline overwrote the server unconditionally, and the pull running
 * after the push in the same pass carried those stale values back to the device
 * that had the newer ones — both converging on the old settings, silently, with
 * every pay figure recomputed against them.
 *
 * These tests fix the wire contract. The filter itself lives in the Supabase data
 * sources and needs a server to exercise.
 */
class RemoteRowVersionGuardTest {

    @Test
    fun `a settings update carries the device's edit time`() {
        val entity = settings(updatedAt = 1_700_000_000_000L)

        assertEquals(
            epochToIso(1_700_000_000_000L),
            entity.toRemoteUpdate().clientUpdatedAt,
        )
    }

    @Test
    fun `a profile update carries the device's edit time`() {
        val entity = ProfileEntity(
            localId = "p-1",
            remoteId = "remote-1",
            userId = "u1",
            email = "user@example.com",
            fullName = "Dana",
            createdAt = 0L,
            updatedAt = 1_700_000_000_000L,
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_UPDATE,
            lastSyncError = null,
            lastSyncedAt = null,
        )

        assertEquals(
            epochToIso(1_700_000_000_000L),
            entity.toRemoteUpdate().clientUpdatedAt,
        )
    }

    /**
     * The value sent is the row's own edit time, not the moment of the push.
     *
     * That is what makes the guard work across a queue: a row edited an hour ago
     * and pushed now must lose to a row edited half an hour ago on another device.
     * Sending "now" would make whichever device synced last always win, which is
     * the behaviour the guard replaces.
     */
    @Test
    fun `the guard is the edit time, not the push time`() {
        val older = settings(updatedAt = 1_000L).toRemoteUpdate().clientUpdatedAt
        val newer = settings(updatedAt = 2_000L).toRemoteUpdate().clientUpdatedAt

        assertEquals(epochToIso(1_000L), older)
        assertEquals(epochToIso(2_000L), newer)
    }

    /**
     * A row decoded from a database that has not had the migration applied.
     *
     * The column is nullable on the read models for exactly this: an app build
     * carrying the client change can still read a server that has not been
     * migrated, rather than failing to decode every row.
     */
    @Test
    fun `a row without the column still decodes`() {
        val row = RemoteProfileRow(
            id = "u1",
            email = "user@example.com",
            fullName = "Dana",
            createdAt = "2024-06-01T08:00:00Z",
            updatedAt = "2024-06-01T08:00:00Z",
        )

        assertEquals(null, row.clientUpdatedAt)
    }

    private fun settings(updatedAt: Long) = UserSettingsEntity(
        localId = "s-1",
        remoteId = "remote-1",
        userId = "u1",
        timezone = "Asia/Jerusalem",
        dailyOvertimeThresholdMinutes = 516,
        weeklyOvertimeThresholdMinutes = 2520,
        weekendDays = "5,6",
        hourlyRate = 60.0,
        currency = "ILS",
        onboardingCompleted = true,
        onboardingCompletedAt = null,
        featuresTravelRefunds = false,
        featuresPaidProjects = false,
        featuresInsights = true,
        featuresClockStyles = true,
        featuresOvertimeReminders = true,
        clockStyle = "CLASSIC",
        createdAt = 0L,
        updatedAt = updatedAt,
        deletedAt = null,
        syncStatus = SyncStatus.PENDING_UPDATE,
        lastSyncError = null,
        lastSyncedAt = null,
    )
}
