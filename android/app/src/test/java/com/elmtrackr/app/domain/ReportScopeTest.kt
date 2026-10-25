package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReportScopeTest {

    private fun profile(id: String, isDefault: Boolean = false, workplaceId: String? = null, remoteId: String? = null) =
        CompensationProfile(
            id = id,
            remoteId = remoteId,
            userId = "u1",
            name = id,
            regionCode = RegionCode.US,
            currencyCode = "USD",
            timezone = "UTC",
            baseHourlyRate = 20.0,
            rules = RegionPresets.forRegion(RegionCode.US).rules,
            stackingPolicy = RegionPresets.forRegion(RegionCode.US).stackingPolicy,
            isDefault = isDefault,
            workplaceId = workplaceId,
        )

    private val cafe = profile("cafe", isDefault = true, workplaceId = "wp-cafe")
    private val bar = profile("bar", workplaceId = "wp-bar", remoteId = "remote-bar")
    private val profiles = listOf(cafe, bar)
    private val settings = UserSettings(id = "cfg", userId = "u1")

    private fun shift(id: String, profileId: String?) = Shift(
        id = id,
        userId = "u1",
        startTime = Instant.parse("2026-10-05T09:00:00Z"),
        endTime = Instant.parse("2026-10-05T17:00:00Z"),
        compensationProfileId = profileId,
    )

    @Test
    fun `a shift belongs to its own profile, matched by local or remote id`() {
        assertEquals("cafe", ReportScope.profileIdOf(shift("a", "cafe"), settings, profiles))
        assertEquals("bar", ReportScope.profileIdOf(shift("b", "remote-bar"), settings, profiles))
    }

    @Test
    fun `a shift recorded before profiles existed belongs to the default job`() {
        assertEquals("cafe", ReportScope.profileIdOf(shift("legacy", null), settings, profiles))
        val withDefaultSetting = settings.copy(defaultCompensationProfileId = "bar")
        assertEquals("bar", ReportScope.profileIdOf(shift("legacy", null), withDefaultSetting, profiles))
    }

    @Test
    fun `a shift whose job was deleted belongs to no current job`() {
        assertNull(ReportScope.profileIdOf(shift("orphan", "deleted-job"), settings, profiles))
        val shifts = listOf(shift("a", "cafe"), shift("b", "bar"), shift("orphan", "deleted-job"))
        // Still in the all-jobs report, never filed under another job.
        assertEquals(3, ReportScope.filter(ReportScope.AllJobs, shifts, settings, profiles).size)
        assertEquals(listOf("a"), ReportScope.filter(ReportScope.Job("cafe"), shifts, settings, profiles).map { it.id })
        assertEquals(listOf("b"), ReportScope.filter(ReportScope.Job("bar"), shifts, settings, profiles).map { it.id })
    }

    @Test
    fun `leave follows the job's workplace`() {
        assertTrue(ReportScope.includesLeave(ReportScope.Job("cafe"), "wp-cafe", profiles))
        assertFalse(ReportScope.includesLeave(ReportScope.Job("cafe"), "wp-bar", profiles))
        assertTrue(ReportScope.includesLeave(ReportScope.AllJobs, "wp-bar", profiles))
    }

    @Test
    fun `an older default profile with no workplace takes leave no other job claims`() {
        val legacyDefault = profile("main", isDefault = true, workplaceId = null)
        val all = listOf(legacyDefault, bar)
        assertTrue(ReportScope.includesLeave(ReportScope.Job("main"), "wp-default", all))
        assertFalse(ReportScope.includesLeave(ReportScope.Job("main"), "wp-bar", all))
    }

    @Test
    fun `a stale or pointless scope falls back to all jobs`() {
        assertEquals(ReportScope.AllJobs, ReportScope.valid(ReportScope.Job("gone"), profiles))
        assertEquals(ReportScope.AllJobs, ReportScope.valid(ReportScope.Job("cafe"), listOf(cafe)))
        assertEquals(ReportScope.Job("bar"), ReportScope.valid(ReportScope.Job("bar"), profiles))
    }
}
