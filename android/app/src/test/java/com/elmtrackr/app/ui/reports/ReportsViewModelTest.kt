package com.elmtrackr.app.ui.reports

import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakePremiumProfilesRepository
import com.elmtrackr.app.fake.FakeReportsRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeCurrentUserProvider
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.fake.FakeRefundsRepository
import com.elmtrackr.app.fake.FakeRefundReceiptStorage
import com.elmtrackr.app.fake.FakeTasksRepository
import com.elmtrackr.app.domain.leave.AbsenceListEntry
import com.elmtrackr.app.domain.model.AbsenceAllocation
import com.elmtrackr.app.domain.model.AbsenceEvent
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.model.Workplace
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val reportsRepo = FakeReportsRepository()
    private val shiftsRepo = FakeShiftsRepository()
    private val settingsRepo = FakeSettingsRepository()

    private val tasksRepo = FakeTasksRepository()
    private val leaveRepo = com.elmtrackr.app.fake.FakeLeaveRepository()
    private val workplacesRepo = com.elmtrackr.app.fake.FakeWorkplacesRepository()

    private fun buildVm(ensureSettings: Boolean = true): ReportsViewModel {
        if (ensureSettings) {
            settingsRepo.setSettings(UserSettings(id = "s", userId = "u1"))
        }
        return ReportsViewModel(
            reportsRepo,
            shiftsRepo,
            tasksRepo,
            settingsRepo,
            FakeCurrentUserProvider(),
            FakeRefundsRepository(),
            FakeCompensationProfilesRepository(),
            FakePremiumProfilesRepository(),
            FakeRefundReceiptStorage(),
            com.elmtrackr.app.fake.FakeProjectsRepository(),
            com.elmtrackr.app.fake.FakeReviewPromptRecorder(),
            leaveRepo,
            workplacesRepo,
            // The production flowOn dispatcher would run the payroll transform off the
            // test scheduler, so advanceUntilIdle could not await its emissions.
            computationDispatcher = mainDispatcherRule.dispatcher,
        )
    }

    private fun reportWith(shiftCount: Int, totalMinutes: Int = 0) =
        MonthlyReport(2024, 1, totalMinutes, totalMinutes, 0, 0, shiftCount, emptyList())

    private fun completedShift(id: String = "s1") = Shift(
        id = id,
        userId = "u1",
        startTime = Instant.parse("2024-01-08T09:00:00Z"),
        endTime = Instant.parse("2024-01-08T17:00:00Z"),
        breakMinutes = 0,
    )

    // ── UI state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state is Loading`() = runTest {
        settingsRepo.setSettings(null)
        assertEquals(ReportsUiState.Loading, buildVm(ensureSettings = false).uiState.value)
    }

    @Test
    fun `stays loading until settings are available`() = runTest {
        settingsRepo.setSettings(null)
        val vm = buildVm(ensureSettings = false)
        val states = mutableListOf<ReportsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        reportsRepo.setReport(reportWith(shiftCount = 1))
        advanceUntilIdle()

        assertTrue(states.all { it is ReportsUiState.Loading })
        job.cancel()
    }

    @Test
    fun `ready state remains available for refunds when report has no shifts`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ReportsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        reportsRepo.setReport(reportWith(shiftCount = 0))
        advanceUntilIdle()

        assertTrue(states.any { it is ReportsUiState.Ready && it.report.shiftCount == 0 })
        job.cancel()
    }

    @Test
    fun `monthly summary from local shifts`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ReportsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        shiftsRepo.setShifts(completedShift("s1"), completedShift("s2"))
        reportsRepo.setReport(reportWith(shiftCount = 2, totalMinutes = 960))
        advanceUntilIdle()

        val ready = states.filterIsInstance<ReportsUiState.Ready>().last()
        assertEquals(2, ready.report.shiftCount)
        assertEquals(960, ready.report.totalMinutes)
        job.cancel()
    }

    @Test
    fun `ready state includes raw shifts`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ReportsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        shiftsRepo.setShifts(completedShift())
        reportsRepo.setReport(reportWith(shiftCount = 1))
        advanceUntilIdle()

        val ready = states.filterIsInstance<ReportsUiState.Ready>().last()
        assertEquals(1, ready.rawShifts.size)
        job.cancel()
    }

    @Test
    fun `active shift is excluded from rawShifts`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ReportsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        val active = Shift("active", "u1", Instant.parse("2024-01-08T09:00:00Z"), endTime = null)
        shiftsRepo.setShifts(completedShift(), active)
        reportsRepo.setReport(reportWith(shiftCount = 1))
        advanceUntilIdle()

        val ready = states.filterIsInstance<ReportsUiState.Ready>().last()
        assertTrue(ready.rawShifts.none { it.isActive })
        job.cancel()
    }

    // ── Timezone ──────────────────────────────────────────────────────────

    @Test
    fun `initial selected month uses the device timezone not UTC`() = runTest {
        val vm = buildVm()
        val expected = YearMonth.now(ZoneId.systemDefault())
        assertEquals(expected.year to expected.monthValue, vm.selectedYearMonth.value)
    }

    @Test
    fun `ready state carries the work timezone from settings`() = runTest {
        settingsRepo.setSettings(
            UserSettings(id = "s", userId = "u1", timezone = "Asia/Jerusalem"),
        )
        val vm = buildVm(ensureSettings = false)
        val states = mutableListOf<ReportsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        shiftsRepo.setShifts(completedShift())
        reportsRepo.setReport(reportWith(shiftCount = 1))
        advanceUntilIdle()

        val ready = states.filterIsInstance<ReportsUiState.Ready>().last()
        assertEquals(ZoneId.of("Asia/Jerusalem"), ready.zone)
        job.cancel()
    }

    // ── Month navigation ──────────────────────────────────────────────────

    @Test
    fun `previousMonth navigates to prior month`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ReportsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        // selectedYearMonth is WhileSubscribed-derived; keep it collected like the UI does.
        val monthJob = launch { vm.selectedYearMonth.collect { } }

        reportsRepo.setReport(reportWith(shiftCount = 1))
        advanceUntilIdle()

        vm.previousMonth()
        advanceUntilIdle()

        // The transient Loading between months is conflated away under the
        // unconfined test dispatcher; assert the resulting month pairing instead.
        val ready = states.filterIsInstance<ReportsUiState.Ready>().lastOrNull()
        assertNotNull(ready)
        assertEquals(vm.selectedYearMonth.value.first, ready!!.year)
        assertEquals(vm.selectedYearMonth.value.second, ready.month)
        assertTrue(states.size > 1)
        job.cancel()
        monthJob.cancel()
    }

    @Test
    fun `nextMonth does not advance past current month`() = runTest {
        val vm = buildVm()
        val initial = vm.selectedYearMonth.value

        reportsRepo.setReport(reportWith(shiftCount = 1))
        advanceUntilIdle()

        vm.nextMonth()
        advanceUntilIdle()

        // Should remain at same month since we start at current month
        assertEquals(initial, vm.selectedYearMonth.value)
    }

    @Test
    fun `canGoNext is false for current month`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()
        assertFalse(vm.canGoNext.value)
    }

    @Test
    fun `canGoNext is true after going back one month`() = runTest {
        val vm = buildVm()
        reportsRepo.setReport(reportWith(shiftCount = 1))
        advanceUntilIdle()

        vm.previousMonth()
        advanceUntilIdle()

        assertTrue(vm.canGoNext.value)
    }

    // ── Payroll ───────────────────────────────────────────────────────────

    @Test
    fun `gross pay calculated when hourly rate exists`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ReportsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        settingsRepo.setSettings(
            UserSettings(
                id = "s",
                userId = "u1",
                hourlyRate = 50.0,
            )
        )
        shiftsRepo.setShifts(completedShift())
        reportsRepo.setReport(reportWith(shiftCount = 1, totalMinutes = 480))
        advanceUntilIdle()

        val ready = states.filterIsInstance<ReportsUiState.Ready>().last()
        assertNotNull(ready.paySummary)
        assertTrue((ready.paySummary?.totalGross ?: 0.0) > 0.0)
        job.cancel()
    }

    @Test
    fun `pay section absent when hourly rate is null`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ReportsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        settingsRepo.setSettings(UserSettings(id = "s", userId = "u1", hourlyRate = null))
        shiftsRepo.setShifts(completedShift())
        reportsRepo.setReport(reportWith(shiftCount = 1))
        advanceUntilIdle()

        val ready = states.filterIsInstance<ReportsUiState.Ready>().last()
        assertNull(ready.paySummary)
        job.cancel()
    }

    @Test
    fun `pay section absent when hourly rate is zero`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ReportsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        settingsRepo.setSettings(UserSettings(id = "s", userId = "u1", hourlyRate = 0.0))
        shiftsRepo.setShifts(completedShift())
        reportsRepo.setReport(reportWith(shiftCount = 1))
        advanceUntilIdle()

        val ready = states.filterIsInstance<ReportsUiState.Ready>().last()
        assertNull(ready.paySummary)
        job.cancel()
    }

    // ── CSV ───────────────────────────────────────────────────────────────

    @Test
    fun `csv content includes header row`() {
        val vm = buildVm()
        val csv = vm.buildCsvContent(listOf(completedShift()), settings = null)
        assertTrue(csv.lines().first().contains("Date"))
        assertTrue(csv.lines().first().contains("Start Time"))
        assertTrue(csv.lines().first().contains("Total Hours"))
    }

    @Test
    fun `csv matches web columns regardless of hourly rate`() {
        val vm = buildVm()
        val settings = UserSettings(id = "s", userId = "u1", hourlyRate = 50.0)
        val csv = vm.buildCsvContent(listOf(completedShift()), settings)
        assertTrue(csv.lines().first().contains("Regular Hours"))
        assertFalse(csv.lines().first().contains("Gross Pay"))
    }

    @Test
    fun `csv includes overnight and notes columns`() {
        val vm = buildVm()
        val csv = vm.buildCsvContent(listOf(completedShift()), settings = null)
        assertTrue(csv.lines().first().contains("Overnight"))
        assertTrue(csv.lines().first().contains("Notes"))
    }

    @Test
    fun `csv has one data row per shift`() {
        val vm = buildVm()
        val shifts = listOf(completedShift("s1"), completedShift("s2"))
        val csv = vm.buildCsvContent(shifts, settings = null)
        val shiftRows = csv.lines().drop(1).takeWhile { it.isNotBlank() }
        assertEquals(2, shiftRows.size)
    }

    @Test
    fun `csv is empty except header when shifts list is empty`() {
        val vm = buildVm()
        val csv = vm.buildCsvContent(emptyList(), settings = null)
        val shiftRows = csv.lines().drop(1).takeWhile { it.isNotBlank() }
        assertEquals(0, shiftRows.size)
    }

    @Test
    fun `csv filename format is elmtrackr-YYYY-MM`() {
        val vm = buildVm()
        assertEquals("elmtrackr-2024-01.csv", vm.csvFilename(2024, 1))
        assertEquals("elmtrackr-2024-12.csv", vm.csvFilename(2024, 12))
    }

    @Test
    fun `csv filename pads single-digit month`() {
        val vm = buildVm()
        assertEquals("elmtrackr-2024-03.csv", vm.csvFilename(2024, 3))
    }

    @Test
    fun `csv escapes commas in notes field`() {
        val vm = buildVm()
        val result = vm.csvEscape("hello, world")
        assertEquals("\"hello, world\"", result)
    }

    @Test
    fun `csv escapes double quotes in notes field`() {
        val vm = buildVm()
        val result = vm.csvEscape("say \"hi\"")
        assertEquals("\"say \"\"hi\"\"\"", result)
    }

    @Test
    fun `csv neutralises spreadsheet formula prefixes in notes`() {
        val vm = buildVm()
        // A note that Excel/Sheets would execute on open; the leading apostrophe
        // keeps the cell as text. No comma or quote is present, so the old
        // escape returned it verbatim.
        assertEquals(
            "\"'=HYPERLINK(\"\"http://x\"\")\"",
            vm.csvEscape("=HYPERLINK(\"http://x\")"),
        )
        assertEquals("\"'+1234\"", vm.csvEscape("+1234"))
        assertEquals("\"'-1+2\"", vm.csvEscape("-1+2"))
        assertEquals("\"'@SUM(A1)\"", vm.csvEscape("@SUM(A1)"))
    }

    @Test
    fun `csv quotes plain notes and leaves inner text intact`() {
        val vm = buildVm()
        assertEquals("\"late ride\"", vm.csvEscape("late ride"))
        // A formula character that is not leading is harmless and must not be touched.
        assertEquals("\"7 = seven\"", vm.csvEscape("7 = seven"))
    }

    @Test
    fun `csv escapes bare carriage return in notes`() {
        val vm = buildVm()
        // A bare CR broke row alignment because it was not an escape trigger.
        assertEquals("\"'\rsecond line\"", vm.csvEscape("\rsecond line"))
        assertEquals("\"first\rsecond\"", vm.csvEscape("first\rsecond"))
    }

    @Test
    fun `csv data row contains date and times`() {
        val vm = buildVm()
        val shift = completedShift()
        val csv = vm.buildCsvContent(listOf(shift), settings = null)
        val dataRow = csv.lines().drop(1).first { it.isNotBlank() }
        assertTrue("Row should contain date: $dataRow", dataRow.contains("2024-01-08"))
        assertTrue("Row should contain start time: $dataRow", dataRow.contains("09:00"))
        assertTrue("Row should contain end time: $dataRow", dataRow.contains("17:00"))
    }

    @Test
    fun `retry bumps refresh nonce without changing selected month`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()
        val before = vm.selectedYearMonth.value
        vm.retry()
        advanceUntilIdle()
        assertEquals(before, vm.selectedYearMonth.value)
    }

    @Test
    fun `csv totals row matches the on-screen weekly-overtime report`() {
        val vm = buildVm()
        val settings = UserSettings(
            id = "s", userId = "u1",
            dailyOvertimeThresholdMinutes = 480,
            weeklyOvertimeThresholdMinutes = 2400,
            weekendDays = listOf(5, 6),
        )
        // One pay week: Sun 14 - Thu 18 Jan 2024 (Fri 19 / Sat 20 are the configured
        // weekend and are avoided). Five 8h shifts hit the 2400 weekly threshold exactly,
        // and a sixth 4h shift on the Sunday pushes the week to 2640 — 4h of weekly
        // overtime that the per-shift rows, each within the 480 daily threshold, miss.
        val shifts = (14..18).map { day ->
            Shift(
                id = "d$day", userId = "u1",
                startTime = Instant.parse("2024-01-%02dT09:00:00Z".format(day)),
                endTime = Instant.parse("2024-01-%02dT17:00:00Z".format(day)),
                breakMinutes = 0,
            )
        } + listOf(
            Shift(
                id = "d14b", userId = "u1",
                startTime = Instant.parse("2024-01-14T18:00:00Z"),
                endTime = Instant.parse("2024-01-14T22:00:00Z"),
                breakMinutes = 0,
            ),
        )

        val csv = vm.buildCsvContent(shifts, settings, year = 2024, month = 1)

        val totalRow = csv.lines().last()
        assertTrue("Row should be the totals row: $totalRow", totalRow.startsWith("TOTAL"))
        val cells = totalRow.split(",")
        assertEquals("44.00", cells[4]) // total hours
        assertEquals("4.00", cells[6])  // overtime hours — weekly-driven, was 0.00
    }

    @Test
    fun `csv names a vacation day after the hours total without adding it to worked hours`() {
        val vm = buildVm()
        val withoutLeave = vm.buildCsvContent(listOf(completedShift()), settings = null, year = 2024, month = 1)
        val csv = vm.buildCsvContent(
            listOf(completedShift()),
            settings = null,
            year = 2024,
            month = 1,
            leaveEntries = listOf(vacationEntry(notes = "=HYPERLINK(\"http://x\")", workplace = "Cafe, North")),
        )

        val lines = csv.lines()
        val totalIndex = lines.indexOfFirst { it.startsWith("TOTAL") }
        val leaveIndex = lines.indexOf("PAID LEAVE")
        assertTrue("Leave block follows the hours total: $csv", leaveIndex > totalIndex)
        val dayRow = lines.first { it.startsWith("2026-09-15") }
        assertTrue(dayRow.contains("Vacation"))
        assertTrue(dayRow.contains("\"Cafe, North\""))
        assertTrue(dayRow.contains("1.00"))
        assertTrue(dayRow.contains("\"'=HYPERLINK(\"\"http://x\"\")\""))
        assertEquals(
            withoutLeave.lines().first { it.startsWith("TOTAL") },
            lines.first { it.startsWith("TOTAL") },
        )
        assertTrue(lines.last().startsWith("LEAVE TOTAL"))
    }

    @Test
    fun `ready state lists a vacation day in the selected month and ignores another month`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ReportsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()
        val (year, month) = vm.selectedYearMonth.value
        val inMonth = LocalDate.of(year, month, 15)
        val otherMonth = inMonth.minusMonths(1)

        workplacesRepo.setWorkplaces(
            Workplace(
                id = "wp",
                userId = "local-user",
                name = "Cafe North",
                regionCode = RegionCode.IL,
                currencyCode = "ILS",
                timezone = "UTC",
            ),
        )
        leaveRepo.setEvents(
            AbsenceEvent(
                id = "e1",
                userId = "local-user",
                type = AbsenceType.VACATION,
                startDate = inMonth,
                endDate = inMonth,
            ),
            AbsenceEvent(
                id = "e2",
                userId = "local-user",
                type = AbsenceType.VACATION,
                startDate = otherMonth,
                endDate = otherMonth,
            ),
        )
        leaveRepo.setAllocations(
            AbsenceAllocation(
                id = "a1",
                userId = "local-user",
                absenceEventId = "e1",
                workplaceId = "wp",
                affectedDate = inMonth,
                entitlementUnits = 1.0,
                unit = LeaveBalanceUnit.DAYS,
                estimatedGrossPay = 320.0,
            ),
            AbsenceAllocation(
                id = "a2",
                userId = "local-user",
                absenceEventId = "e2",
                workplaceId = "wp",
                affectedDate = otherMonth,
                entitlementUnits = 1.0,
                unit = LeaveBalanceUnit.DAYS,
                estimatedGrossPay = 320.0,
            ),
        )
        advanceUntilIdle()

        val ready = states.filterIsInstance<ReportsUiState.Ready>().last()
        assertEquals(inMonth, ready.leaveEntries.single().date)
        assertEquals(AbsenceType.VACATION, ready.leaveEntries.single().type)
        assertEquals("Cafe North", ready.leaveEntries.single().workplaceName)
        assertEquals(1.0, ready.paidLeave!!.vacationDays, 0.0001)
        assertEquals(0, ready.report.totalMinutes)
        assertEquals(
            ready.report.totalMinutes,
            ready.report.regularMinutes + ready.report.overtimeMinutes + ready.report.weekendMinutes,
        )
        job.cancel()
    }

    @Test
    fun `csv leave totals are per currency and never add two currencies together`() {
        val vm = buildVm()
        val csv = vm.buildCsvContent(
            emptyList(),
            settings = null,
            year = 2026,
            month = 9,
            leaveEntries = listOf(
                vacationEntry(id = "a1", workplace = "Cafe North", currency = "ILS", gross = 4000.0),
                vacationEntry(id = "a2", workplace = "Remote Inc", currency = "USD", gross = 1000.0),
            ),
        )

        val totals = csv.lines().filter { it.startsWith("LEAVE TOTAL") }
        assertEquals("one total row per currency: $csv", 2, totals.size)
        assertEquals("LEAVE TOTAL,,,1.00,0.00,4000.00,ILS,", totals[0])
        assertEquals("LEAVE TOTAL,,,1.00,0.00,1000.00,USD,", totals[1])
        assertFalse("no combined figure anywhere: $csv", csv.contains("5000.00"))
        val usdRow = csv.lines().first { it.contains("Remote Inc") }
        assertEquals("USD", usdRow.split(",")[6])
    }

    @Test
    fun `csv for a month with leave and no completed shifts keeps a zero hours total`() {
        val vm = buildVm()
        val csv = vm.buildCsvContent(
            emptyList(),
            settings = null,
            year = 2026,
            month = 9,
            leaveEntries = listOf(vacationEntry()),
        )

        val total = csv.lines().first { it.startsWith("TOTAL") }.split(",")
        assertEquals("0.00", total[4])
        assertEquals("0 shifts", total[9])
        assertTrue(csv.lines().any { it.startsWith("2026-09-15") && it.contains("Vacation") })
        assertEquals("LEAVE TOTAL,,,1.00,0.00,320.00,ILS,", csv.lines().last())
    }

    @Test
    fun `screen leave totals are grouped by currency`() {
        val state = ReportsUiState.Ready(
            year = 2026,
            month = 9,
            report = MonthlyReport(2026, 9, 0, 0, 0, 0, 0, emptyList()),
            weeklyTotals = emptyList(),
            leaveEntries = listOf(
                vacationEntry(id = "a1", currency = "USD", gross = 1000.0),
                vacationEntry(id = "a2", currency = "ILS", gross = 300.0),
                vacationEntry(id = "a3", currency = "ILS", gross = 100.0),
                // An unvalued day consumes entitlement but has no money to total.
                vacationEntry(id = "a4", currency = "EUR", gross = 0.0),
            ),
        )

        assertEquals(mapOf("ILS" to 400.0, "USD" to 1000.0), state.leaveGrossByCurrency)
        assertEquals(listOf("ILS", "USD"), state.leaveGrossByCurrency.keys.toList())
    }

    private fun vacationEntry(
        notes: String? = null,
        workplace: String = "Cafe North",
        id: String = "a1",
        currency: String = "ILS",
        gross: Double = 320.0,
    ) = AbsenceListEntry(
        allocation = AbsenceAllocation(
            id = id,
            userId = "local-user",
            absenceEventId = "e1",
            workplaceId = "wp-$currency",
            affectedDate = LocalDate.parse("2026-09-15"),
            entitlementUnits = 1.0,
            unit = LeaveBalanceUnit.DAYS,
            estimatedGrossPay = gross,
        ),
        event = AbsenceEvent(
            id = "e1",
            userId = "local-user",
            type = AbsenceType.VACATION,
            startDate = LocalDate.parse("2026-09-15"),
            endDate = LocalDate.parse("2026-09-15"),
            notes = notes,
        ),
        workplaceName = workplace,
        currencyCode = currency,
    )
}
