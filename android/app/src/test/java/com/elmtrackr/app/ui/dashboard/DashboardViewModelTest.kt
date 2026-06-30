package com.elmtrackr.app.ui.dashboard

import com.elmtrackr.app.fake.FakeAppPreferencesStore
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakeAuthRepository
import com.elmtrackr.app.fake.FakeReportsRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.fake.FakeTasksRepository
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val shiftsRepo = FakeShiftsRepository()
    private val settingsRepo = FakeSettingsRepository()
    private val reportsRepo = FakeReportsRepository()
    private val compensationRepo = FakeCompensationProfilesRepository()
    private val tasksRepo = FakeTasksRepository()
    private val authRepo = FakeAuthRepository().apply {
        setProfile(Profile("u1", "test@test.com", null, Instant.EPOCH, Instant.EPOCH))
    }

    private val appPrefs = FakeAppPreferencesStore()

    private fun buildVm() = DashboardViewModel(
        shiftsRepo, settingsRepo, reportsRepo, authRepo, compensationRepo, tasksRepo, appPrefs,
    )

    private fun defaultSettings() = UserSettings(
        "cfg", "u1", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    // ---- Existing state tests ----

    @Test
    fun `initial state is Loading`() {
        val vm = buildVm()
        assertEquals(DashboardUiState.Loading, vm.uiState.value)
    }

    @Test
    fun `Ready state emits when all repos have data`() = runTest {
        val vm = buildVm()
        val collected = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { collected.add(it) } }

        settingsRepo.setSettings(defaultSettings())
        reportsRepo.setReport(MonthlyReport(2024, 1, 0, 0, 0, 0, 0, emptyList()))
        advanceUntilIdle()

        assertTrue(collected.any { it is DashboardUiState.Ready })
        job.cancel()
    }

    @Test
    fun `active shift is null when not clocked in`() = runTest {
        val vm = buildVm()
        val collected = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { collected.add(it) } }

        settingsRepo.setSettings(defaultSettings())
        advanceUntilIdle()

        val ready = collected.filterIsInstance<DashboardUiState.Ready>().lastOrNull()
        assertNull(ready?.activeShift)
        job.cancel()
    }

    @Test
    fun `clockIn adds an active shift`() = runTest {
        val vm = buildVm()
        val collected = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { collected.add(it) } }

        settingsRepo.setSettings(defaultSettings())
        advanceUntilIdle()

        vm.clockIn()
        advanceUntilIdle()

        val ready = collected.filterIsInstance<DashboardUiState.Ready>().lastOrNull()
        assertTrue(ready?.activeShift?.isActive == true)
        job.cancel()
    }

    @Test
    fun `clockIn uses migrated default compensation profile`() = runTest {
        val defaultProfile = CompensationProfile(
            id = "profile-default",
            userId = "u1",
            name = "Default job",
            regionCode = RegionCode.IL,
            currencyCode = "ILS",
            timezone = "Asia/Jerusalem",
            baseHourlyRate = 100.0,
            rules = CompensationRules(),
            stackingPolicy = StackingPolicy.HIGHEST_ONLY,
            isDefault = true,
        )
        compensationRepo.setProfiles(defaultProfile)
        settingsRepo.setSettings(defaultSettings().copy(defaultCompensationProfileId = null))
        val vm = buildVm()
        advanceUntilIdle()

        vm.clockIn()
        advanceUntilIdle()

        assertEquals("profile-default", shiftsRepo.currentShifts.single().compensationProfileId)
    }

    @Test
    fun `clockOut clears the active shift`() = runTest {
        val vm = buildVm()
        val collected = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { collected.add(it) } }

        settingsRepo.setSettings(defaultSettings())
        vm.clockIn()
        advanceUntilIdle()

        val shiftId = (collected.filterIsInstance<DashboardUiState.Ready>()
            .lastOrNull()?.activeShift?.id) ?: return@runTest
        vm.clockOut(shiftId)
        advanceUntilIdle()

        val ready = collected.filterIsInstance<DashboardUiState.Ready>().lastOrNull()
        assertNull(ready?.activeShift)
        job.cancel()
    }

    // ---- editActiveShiftStartTime ----

    @Test
    fun `editActiveShiftStartTime updates start time of active shift`() = runTest {
        val original = Instant.parse("2024-06-10T08:00:00Z")
        val updated  = Instant.parse("2024-06-10T07:30:00Z")
        val shift = Shift("s1", "u1", startTime = original, endTime = null)
        shiftsRepo.setShifts(shift)

        val vm = buildVm()
        settingsRepo.setSettings(defaultSettings())
        advanceUntilIdle()

        vm.editActiveShiftStartTime("s1", updated)
        advanceUntilIdle()

        val storedShift = shiftsRepo.getShiftById("s1")
        assertEquals(updated, storedShift?.startTime)
    }

    @Test
    fun `editActiveShiftStartTime ignores completed shifts`() = runTest {
        val original = Instant.parse("2024-06-10T08:00:00Z")
        val completed = Shift(
            "s2", "u1",
            startTime = original,
            endTime = Instant.parse("2024-06-10T17:00:00Z"),
        )
        shiftsRepo.setShifts(completed)

        val vm = buildVm()
        settingsRepo.setSettings(defaultSettings())
        advanceUntilIdle()

        vm.editActiveShiftStartTime("s2", Instant.parse("2024-06-10T07:00:00Z"))
        advanceUntilIdle()

        val storedShift = shiftsRepo.getShiftById("s2")
        assertEquals(original, storedShift?.startTime)
    }

    // ---- recentShifts ----

    @Test
    fun `recentShifts contains only completed shifts sorted newest first`() = runTest {
        val t1 = Instant.parse("2024-06-01T08:00:00Z")
        val t2 = Instant.parse("2024-06-02T08:00:00Z")
        val end = Instant.parse("2024-06-01T17:00:00Z")
        val s1 = Shift("s1", "u1", startTime = t1, endTime = end)
        val s2 = Shift("s2", "u1", startTime = t2, endTime = end.plusSeconds(86400))
        val active = Shift("s3", "u1", startTime = Instant.now(), endTime = null)
        shiftsRepo.setShifts(s1, s2, active)

        val vm = buildVm()
        val collected = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { collected.add(it) } }
        settingsRepo.setSettings(defaultSettings())
        advanceUntilIdle()

        val ready = collected.filterIsInstance<DashboardUiState.Ready>().lastOrNull()
        val recent = ready?.recentShifts ?: emptyList()
        assertEquals(2, recent.size)
        assertTrue(recent[0].startTime >= recent[1].startTime)
        assertTrue(recent.none { it.isActive })
        job.cancel()
    }

    @Test
    fun `recentShifts is capped at 5 entries`() = runTest {
        val shifts = (1..7).map { i ->
            Shift(
                "s$i", "u1",
                startTime = Instant.parse("2024-06-${"%02d".format(i)}T08:00:00Z"),
                endTime   = Instant.parse("2024-06-${"%02d".format(i)}T17:00:00Z"),
            )
        }
        shiftsRepo.setShifts(*shifts.toTypedArray())

        val vm = buildVm()
        val collected = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { collected.add(it) } }
        settingsRepo.setSettings(defaultSettings())
        advanceUntilIdle()

        val ready = collected.filterIsInstance<DashboardUiState.Ready>().lastOrNull()
        assertEquals(5, ready?.recentShifts?.size)
        job.cancel()
    }

    // ---- displayName ----

    @Test
    fun `displayName is null when not signed in`() = runTest {
        authRepo.setProfile(null)

        val vm = buildVm()
        val collected = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { collected.add(it) } }
        settingsRepo.setSettings(defaultSettings())
        advanceUntilIdle()

        val ready = collected.filterIsInstance<DashboardUiState.Ready>().lastOrNull()
        assertNull(ready?.displayName)
        job.cancel()
    }

    @Test
    fun `displayName comes from signed-in profile`() = runTest {
        authRepo.setProfile(
            Profile("u1", "test@test.com", "Alice", Instant.EPOCH, Instant.EPOCH),
        )

        val vm = buildVm()
        val collected = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { collected.add(it) } }
        settingsRepo.setSettings(defaultSettings())
        advanceUntilIdle()

        val ready = collected.filterIsInstance<DashboardUiState.Ready>().lastOrNull()
        assertEquals("Alice", ready?.displayName)
        job.cancel()
    }

    @Test
    fun `first clock in shows celebration once`() = runTest {
        val vm = buildVm()
        settingsRepo.setSettings(defaultSettings())
        advanceUntilIdle()

        vm.clockIn()
        advanceUntilIdle()

        assertTrue(vm.showFirstClockInCelebration.value)
        vm.dismissFirstClockInCelebration()
        assertFalse(vm.showFirstClockInCelebration.value)

        vm.clockOut(shiftsRepo.currentShifts.first().id)
        advanceUntilIdle()
        vm.clockIn()
        advanceUntilIdle()

        assertFalse(vm.showFirstClockInCelebration.value)
    }

    @Test
    fun `pay summary applies overtime tiers instead of flat hourly rate`() = runTest {
        val month = YearMonth.now(ZoneOffset.UTC)
        var date = month.atDay(1)
        while (date.dayOfWeek != DayOfWeek.MONDAY) date = date.plusDays(1)
        val start = date.atTime(8, 0).toInstant(ZoneOffset.UTC)
        shiftsRepo.setShifts(
            Shift("paid", "u1", startTime = start, endTime = start.plusSeconds(10 * 3600L)),
        )
        settingsRepo.setSettings(defaultSettings().copy(hourlyRate = 100.0))

        val vm = buildVm()
        val collected = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { collected.add(it) } }
        advanceUntilIdle()

        val pay = collected.filterIsInstance<DashboardUiState.Ready>().last().paySummary
        assertEquals(1050.0, pay?.totalGross ?: 0.0, 0.001)
        assertEquals(800.0, pay?.regularGross ?: 0.0, 0.001)
        assertEquals(250.0, pay?.overtimeGross ?: 0.0, 0.001)
        job.cancel()
    }
}
