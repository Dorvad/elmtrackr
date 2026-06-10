package com.elmtrackr.app.ui.dashboard

import com.elmtrackr.app.fake.FakeReportsRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.ShiftStats.MonthlyReport
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val shiftsRepo = FakeShiftsRepository()
    private val settingsRepo = FakeSettingsRepository()
    private val reportsRepo = FakeReportsRepository()

    private fun buildVm() = DashboardViewModel(shiftsRepo, settingsRepo, reportsRepo)

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

        settingsRepo.setSettings(UserSettings("cfg", "u1", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))
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

        settingsRepo.setSettings(UserSettings("cfg", "u1", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))
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

        settingsRepo.setSettings(UserSettings("cfg", "u1", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))
        advanceUntilIdle()

        vm.clockIn()
        advanceUntilIdle()

        val ready = collected.filterIsInstance<DashboardUiState.Ready>().lastOrNull()
        assertTrue(ready?.activeShift?.isActive == true)
        job.cancel()
    }

    @Test
    fun `clockOut clears the active shift`() = runTest {
        val vm = buildVm()
        val collected = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { collected.add(it) } }

        settingsRepo.setSettings(UserSettings("cfg", "u1", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))
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
}
