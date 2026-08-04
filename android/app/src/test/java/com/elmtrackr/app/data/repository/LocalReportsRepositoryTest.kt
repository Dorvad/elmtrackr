package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.fake.FakeCompensationProfileDao
import com.elmtrackr.app.fake.FakePremiumProfileDao
import com.elmtrackr.app.fake.FakeSettingsDao
import com.elmtrackr.app.fake.FakeShiftDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * The repository has to agree with the ViewModel path, because both feed report
 * surfaces and a user can see them side by side.
 *
 * It did not. `buildMonthlyReport` was called with no compensation profiles, so
 * every shift resolved through the legacy settings rules: overtime hours were
 * counted against `settings.dailyOvertimeThresholdMinutes` rather than the shift's
 * own profile. That is the same defect as "premium profiles are inert", still live
 * on this one path after the ViewModels were fixed.
 */
class LocalReportsRepositoryTest {

    private val shiftDao = FakeShiftDao()
    private val settingsDao = FakeSettingsDao()
    private val profileDao = FakeCompensationProfileDao()
    private val premiumDao = FakePremiumProfileDao()

    private val repository = LocalReportsRepository(shiftDao, settingsDao, profileDao, premiumDao)

    /** An IL profile whose daily standard (516) differs from the legacy field (480). */
    private val ilProfile = CompensationProfile(
        id = "p-il",
        userId = "u1",
        name = "Main job",
        regionCode = RegionCode.IL,
        currencyCode = "ILS",
        timezone = "Asia/Jerusalem",
        baseHourlyRate = 60.0,
        rules = RegionPresets.forRegion(RegionCode.IL).rules,
        stackingPolicy = RegionPresets.forRegion(RegionCode.IL).stackingPolicy,
        isDefault = true,
    )

    private val settings = UserSettings(
        id = "cfg1",
        userId = "u1",
        timezone = "Asia/Jerusalem",
        regionCode = RegionCode.IL,
        defaultCompensationProfileId = "p-il",
        dailyOvertimeThresholdMinutes = 480,
        weeklyOvertimeThresholdMinutes = 2400,
        weekendDays = listOf(5, 6),
        hourlyRate = 60.0,
    )

    /** Sunday 14 Jan 2024, 09:00–18:00 Jerusalem: 540 minutes on an IL weekday. */
    private val nineHourShift = Shift(
        id = "s1",
        userId = "u1",
        startTime = Instant.parse("2024-01-14T07:00:00Z"),
        endTime = Instant.parse("2024-01-14T16:00:00Z"),
        breakMinutes = 0,
    )

    private suspend fun seed(withProfile: Boolean = true) {
        settingsDao.insertSettings(settings.toEntity())
        shiftDao.insertShift(nineHourShift.toEntity())
        if (withProfile) profileDao.insert(ilProfile.toEntity())
    }

    @Test
    fun `the monthly report resolves overtime through the stored profile`() = runTest {
        seed()

        val report = repository.observeMonthlyReport("u1", 2024, 1).first()

        assertEquals(540, report!!.totalMinutes)
        // 540 - 516 from the profile, not 540 - 480 from the legacy settings field.
        assertEquals(24, report.overtimeMinutes)
    }

    /**
     * The invariant that keeps the two paths from drifting: the repository must return
     * what the builder returns for the same inputs. Asserted against the builder
     * rather than a literal, so a future change to either side has to change both.
     */
    @Test
    fun `the repository agrees with the builder called directly`() = runTest {
        seed()

        val fromRepository = repository.observeMonthlyReport("u1", 2024, 1).first()
        val fromBuilder = MonthlyReportBuilder.buildMonthlyReport(
            year = 2024,
            month = 1,
            shifts = listOf(nineHourShift),
            settings = settings,
            profiles = listOf(ilProfile),
        )

        assertEquals(fromBuilder, fromRepository)
    }

    /** A user with no profiles still resolves through the settings fields. */
    @Test
    fun `with no stored profile the settings thresholds still apply`() = runTest {
        seed(withProfile = false)

        val report = repository.observeMonthlyReport("u1", 2024, 1).first()

        assertEquals(60, report!!.overtimeMinutes)
    }

    /** A profile added later must reach the report without an app restart. */
    @Test
    fun `the report re-emits when a profile is stored`() = runTest {
        seed(withProfile = false)
        assertEquals(60, repository.observeMonthlyReport("u1", 2024, 1).first()!!.overtimeMinutes)

        profileDao.insert(ilProfile.toEntity())

        assertEquals(24, repository.observeMonthlyReport("u1", 2024, 1).first()!!.overtimeMinutes)
    }

    /**
     * The weekly card reads a rolling twelve-week window off the wall clock, so its
     * fixture has to be dated relative to now rather than pinned like the monthly
     * ones. Totalled across buckets, because which day-of-month bucket a shift lands
     * in depends on today's date and is not what this asserts.
     *
     * `groupByWeek` already resolved through [com.elmtrackr.app.domain.compensation.CompensationResolver];
     * what was missing was the repository handing it any profiles to resolve against.
     * 600 minutes distinguishes the two thresholds: 84 over 516, 120 over 480.
     */
    @Test
    fun `weekly totals resolve through the stored profile too`() = runTest {
        val zone = java.time.ZoneId.of("Asia/Jerusalem")
        val yesterday = java.time.LocalDate.now(zone).minusDays(1)
        val tenHours = Shift(
            id = "s-recent",
            userId = "u1",
            startTime = yesterday.atTime(9, 0).atZone(zone).toInstant(),
            endTime = yesterday.atTime(19, 0).atZone(zone).toInstant(),
            breakMinutes = 0,
        )
        settingsDao.insertSettings(settings.toEntity())
        shiftDao.insertShift(tenHours.toEntity())
        profileDao.insert(ilProfile.toEntity())

        val weeks = repository.observeWeeklyTotals("u1").first()

        assertEquals(600, weeks.sumOf { it.totalMinutes })
        assertEquals(84, weeks.sumOf { it.overtimeMinutes })
    }
}
