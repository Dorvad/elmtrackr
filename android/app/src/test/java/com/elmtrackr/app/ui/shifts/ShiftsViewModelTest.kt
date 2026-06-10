package com.elmtrackr.app.ui.shifts

import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ShiftsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeShiftsRepository()

    private fun buildVm() = ShiftsViewModel(repo)

    @Test
    fun `initial state is Loading`() {
        assertEquals(ShiftsUiState.Loading, buildVm().uiState.value)
    }

    @Test
    fun `Empty state when no shifts`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ShiftsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        repo.setShifts()
        advanceUntilIdle()

        assertTrue(states.any { it is ShiftsUiState.Empty })
        job.cancel()
    }

    @Test
    fun `Ready state when shifts exist`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ShiftsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        repo.setShifts(
            Shift("s1", "u1", Instant.parse("2024-01-08T09:00:00Z"), Instant.parse("2024-01-08T17:00:00Z")),
        )
        advanceUntilIdle()

        val ready = states.filterIsInstance<ShiftsUiState.Ready>().lastOrNull()
        assertEquals(1, ready?.shifts?.size)
        job.cancel()
    }

    @Test
    fun `active shift exposed in Ready state`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ShiftsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        repo.setShifts(
            Shift("s1", "u1", Instant.parse("2024-01-08T09:00:00Z"), null),
        )
        advanceUntilIdle()

        val ready = states.filterIsInstance<ShiftsUiState.Ready>().lastOrNull()
        assertEquals("s1", ready?.activeShift?.id)
        job.cancel()
    }

    @Test
    fun `deleteShift removes shift`() = runTest {
        val vm = buildVm()
        repo.setShifts(
            Shift("s1", "u1", Instant.parse("2024-01-08T09:00:00Z"), Instant.parse("2024-01-08T17:00:00Z")),
        )
        val states = mutableListOf<ShiftsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        advanceUntilIdle()
        vm.deleteShift("s1")
        advanceUntilIdle()

        assertTrue(states.any { it is ShiftsUiState.Empty })
        job.cancel()
    }
}
