package com.elmtrackr.app.ui.reports

import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ReportScope
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.AbsenceAllocation
import com.elmtrackr.app.domain.model.AbsenceEvent
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.RoundingRules
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.model.Workplace
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakeCurrentUserProvider
import com.elmtrackr.app.fake.FakePremiumProfilesRepository
import com.elmtrackr.app.fake.FakeRefundReceiptStorage
import com.elmtrackr.app.fake.FakeRefundsRepository
import com.elmtrackr.app.fake.FakeReportsRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.fake.FakeTasksRepository
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The job filter narrows what a report shows without re-pricing anything, keeps
 * currencies apart, and carries through to what is exported.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportsScopeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val shiftsRepo = FakeShiftsRepository()
    private val settingsRepo = FakeSettingsRepository()
    private val profilesRepo = FakeCompensationProfilesRepository()
    private val leaveRepo = com.elmtrackr.app.fake.FakeLeaveRepository()
    private val workplacesRepo = com.elmtrackr.app.fake.FakeWorkplacesRepository()

    private val settings = UserSettings(id = "s", userId = "local-user", defaultCompensationProfileId = "cafe")

    /** Shekels, rounding to the quarter hour. */
    private val cafe = CompensationProfile(
        id = "cafe", userId = "local-user", name = "Cafe", regionCode = RegionCode.US,
        currencyCode = "ILS", timezone = "UTC", baseHourlyRate = 50.0,
        rules = RegionPresets.forRegion(RegionCode.US).rules.copy(
            rounding = RoundingRules(enabled = true, incrementMinutes = 15, direction = "nearest"),
        ),
        stackingPolicy = RegionPresets.forRegion(RegionCode.US).stackingPolicy,
        isDefault = true, workplaceId = "wp-cafe",
    )

    /** Dollars, no rounding. */
    private val studio = CompensationProfile(
        id = "studio", userId = "local-user", name = "Studio", regionCode = RegionCode.US,
        currencyCode = "USD", timezone = "UTC", baseHourlyRate = 30.0,
        rules = RegionPresets.forRegion(RegionCode.US).rules,
        stackingPolicy = RegionPresets.forRegion(RegionCode.US).stackingPolicy,
        workplaceId = "wp-studio",
    )

    private fun buildVm() = ReportsViewModel(
        FakeReportsRepository(), shiftsRepo, FakeTasksRepository(), settingsRepo, FakeCurrentUserProvider(),
        FakeRefundsRepository(), profilesRepo, FakePremiumProfilesRepository(), FakeRefundReceiptStorage(),
        com.elmtrackr.app.fake.FakeProjectsRepository(), com.elmtrackr.app.fake.FakeReviewPromptRecorder(),
        leaveRepo, workplacesRepo,
        computationDispatcher = mainDispatcherRule.dispatcher,
    )

    private fun day(n: Int): LocalDate {
        val now = LocalDate.now(ZoneOffset.UTC)
        return now.withDayOfMonth(minOf(n, now.lengthOfMonth()))
    }

    /** One shift at each job, on different days in the current month. */
    private fun seedShifts() {
        val cafeStart = day(3).atTime(9, 0).toInstant(ZoneOffset.UTC)
        val studioStart = day(10).atTime(9, 0).toInstant(ZoneOffset.UTC)
        shiftsRepo.setShifts(
            // 7h 52m worked, rounded to 7h 45m paid.
            Shift("c1", "local-user", startTime = cafeStart, endTime = cafeStart.plusSeconds((7 * 60 + 52) * 60L), compensationProfileId = "cafe"),
            Shift("s1", "local-user", startTime = studioStart, endTime = studioStart.plusSeconds(6 * 3600L), compensationProfileId = "studio"),
        )
    }

    private suspend fun TestScope.readyState(vm: ReportsViewModel): ReportsUiState.Ready {
        val states = mutableListOf<ReportsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()
        job.cancel()
        return states.filterIsInstance<ReportsUiState.Ready>().last()
    }

    @Test
    fun `all jobs reports both, with one pay total per currency`() = runTest {
        settingsRepo.setSettings(settings)
        profilesRepo.setProfiles(cafe, studio)
        seedShifts()
        val vm = buildVm()

        val all = readyState(vm)

        assertEquals(ReportScope.AllJobs, all.scope)
        assertEquals(2, all.report.shiftCount)
        assertEquals(listOf("ILS", "USD"), all.paySummary!!.paidByCurrency.map { it.currencyCode })
        assertTrue(all.canChooseJob)
    }

    @Test
    fun `one job shows only its shifts, priced exactly as in the all-jobs report`() = runTest {
        settingsRepo.setSettings(settings)
        profilesRepo.setProfiles(cafe, studio)
        seedShifts()
        val vm = buildVm()
        val all = readyState(vm)

        vm.selectScope(ReportScope.Job("cafe"))
        val cafeOnly = readyState(vm)

        assertEquals(listOf("c1"), cafeOnly.rawShifts.map { it.id })
        assertEquals(1, cafeOnly.report.shiftCount)
        assertEquals(472, cafeOnly.report.workedMinutes)
        assertEquals(465, cafeOnly.report.totalMinutes)
        assertEquals(listOf("ILS"), cafeOnly.paySummary!!.paidByCurrency.map { it.currencyCode })
        assertEquals(
            all.paySummary!!.paidByCurrency.single { it.currencyCode == "ILS" }.totalGross,
            cafeOnly.paySummary!!.paidByCurrency.single().totalGross,
            0.0001,
        )
        // The pay-week context is never narrowed, so nothing is re-priced.
        assertEquals(all.payContextShifts.map { it.id }.toSet(), cafeOnly.payContextShifts.map { it.id }.toSet())
        assertEquals("Cafe", cafeOnly.scopedJobName)
        assertEquals(2, cafeOnly.allJobsShiftCount)
    }

    @Test
    fun `the jobs add up to the all-jobs report`() = runTest {
        settingsRepo.setSettings(settings)
        profilesRepo.setProfiles(cafe, studio)
        seedShifts()
        val vm = buildVm()
        val all = readyState(vm)
        vm.selectScope(ReportScope.Job("cafe"))
        val cafeOnly = readyState(vm)
        vm.selectScope(ReportScope.Job("studio"))
        val studioOnly = readyState(vm)

        assertEquals(all.report.workedMinutes, cafeOnly.report.workedMinutes + studioOnly.report.workedMinutes)
        assertEquals(all.report.totalMinutes, cafeOnly.report.totalMinutes + studioOnly.report.totalMinutes)
    }

    @Test
    fun `a scoped export holds that job's shifts and says so`() = runTest {
        settingsRepo.setSettings(settings)
        profilesRepo.setProfiles(cafe, studio)
        seedShifts()
        val vm = buildVm()
        vm.selectScope(ReportScope.Job("studio"))
        val studioOnly = readyState(vm)

        val csv = vm.buildCsvContent(
            shifts = studioOnly.rawShifts,
            settings = studioOnly.settings,
            profiles = studioOnly.profiles,
            contextShifts = studioOnly.payContextShifts,
            year = studioOnly.year,
            month = studioOnly.month,
            scopeJobName = studioOnly.scopedJobName,
        )
        val dataRows = csv.lines().drop(1).takeWhile { it.isNotBlank() }
        assertEquals(1, dataRows.size)
        val total = csv.lines().first { it.contains("TOTAL") }
        assertTrue(total, total.startsWith("\"TOTAL - ") && total.contains("Studio"))
        assertTrue(vm.csvFilename(2026, 10, "Studio").endsWith("-studio.csv"))
        // Worked hours, appended last, agree with the screen.
        assertEquals("6.00", total.split(",").last())
        assertEquals(360, studioOnly.report.workedMinutes)
    }

    @Test
    fun `a deleted job falls back to all jobs instead of an empty report`() = runTest {
        settingsRepo.setSettings(settings)
        profilesRepo.setProfiles(cafe, studio)
        seedShifts()
        val vm = buildVm()
        vm.selectScope(ReportScope.Job("studio"))
        readyState(vm)

        profilesRepo.setProfiles(cafe)
        val after = readyState(vm)

        assertEquals(ReportScope.AllJobs, after.scope)
    }

    @Test
    fun `leave is narrowed with the job and never added to worked hours`() = runTest {
        settingsRepo.setSettings(settings)
        profilesRepo.setProfiles(cafe, studio)
        seedShifts()
        val vacation = day(20)
        workplacesRepo.setWorkplaces(
            Workplace(id = "wp-cafe", userId = "local-user", name = "Cafe", regionCode = RegionCode.US, currencyCode = "ILS", timezone = "UTC"),
            Workplace(id = "wp-studio", userId = "local-user", name = "Studio", regionCode = RegionCode.US, currencyCode = "USD", timezone = "UTC"),
        )
        leaveRepo.setEvents(
            AbsenceEvent(id = "e1", userId = "local-user", type = AbsenceType.VACATION, startDate = vacation, endDate = vacation),
        )
        leaveRepo.setAllocations(
            AbsenceAllocation(
                id = "a1", userId = "local-user", absenceEventId = "e1", workplaceId = "wp-studio",
                affectedDate = vacation, entitlementUnits = 1.0, unit = LeaveBalanceUnit.DAYS, estimatedGrossPay = 240.0,
            ),
        )
        val vm = buildVm()

        val all = readyState(vm)
        assertEquals(1, all.leaveEntries.size)

        vm.selectScope(ReportScope.Job("cafe"))
        val cafeOnly = readyState(vm)
        assertTrue(cafeOnly.leaveEntries.isEmpty())

        vm.selectScope(ReportScope.Job("studio"))
        val studioOnly = readyState(vm)
        assertEquals(1, studioOnly.leaveEntries.size)
        // Leave sits beside the hours, never inside them.
        assertEquals(360, studioOnly.report.workedMinutes)
    }

    @Test
    fun `month over month compares worked time with worked time`() = runTest {
        settingsRepo.setSettings(settings)
        profilesRepo.setProfiles(cafe, studio)
        seedShifts()
        val vm = buildVm()

        val all = readyState(vm)

        // The fake returns the same shifts for the previous month, so both sides
        // must be the same figure: worked minutes, not payable minus net.
        val expectedPrev = all.rawShifts.sumOf {
            PayrollCalculator.shiftTime(it, settings, listOf(cafe, studio))!!.workedMinutes
        }
        assertEquals(expectedPrev, all.previousMonthMinutes)
        assertEquals(all.report.workedMinutes, all.previousMonthMinutes)
    }
}
