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
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
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
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val reportsRepo = FakeReportsRepository()
    private val shiftsRepo = FakeShiftsRepository()
    private val settingsRepo = FakeSettingsRepository()

    private val tasksRepo = FakeTasksRepository()

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
}
