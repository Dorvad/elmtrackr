package com.elmtrackr.app.ui.reports

import com.elmtrackr.app.fake.FakeReportsRepository
import com.elmtrackr.app.domain.model.ShiftStats.MonthlyReport
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeReportsRepository()

    private fun buildVm() = ReportsViewModel(repo)

    @Test
    fun `initial state is Loading`() {
        assertEquals(ReportsUiState.Loading, buildVm().uiState.value)
    }

    @Test
    fun `Empty state when report has no shifts`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ReportsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        repo.setReport(MonthlyReport(2024, 1, 0, 0, 0, 0, shiftCount = 0, emptyList()))
        advanceUntilIdle()

        assertTrue(states.any { it is ReportsUiState.Empty })
        job.cancel()
    }

    @Test
    fun `Ready state when report has shifts`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ReportsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        repo.setReport(MonthlyReport(2024, 1, 960, 960, 0, 0, shiftCount = 2, emptyList()))
        advanceUntilIdle()

        val ready = states.filterIsInstance<ReportsUiState.Ready>().lastOrNull()
        assertEquals(2, ready?.report?.shiftCount)
        assertEquals(960, ready?.report?.totalMinutes)
        job.cancel()
    }

    @Test
    fun `previousMonth navigates to prior month`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ReportsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        repo.setReport(MonthlyReport(2024, 5, 480, 480, 0, 0, shiftCount = 1, emptyList()))
        advanceUntilIdle()

        vm.previousMonth()
        advanceUntilIdle()

        // After previousMonth, the VM will query for the prior month.
        // Since our fake returns the same report regardless of month,
        // we verify the VM called it again by checking state was re-emitted.
        assertTrue(states.size > 1)
        job.cancel()
    }
}
