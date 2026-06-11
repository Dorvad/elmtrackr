package com.elmtrackr.app.ui.shifts

import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ShiftsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val shiftsRepo = FakeShiftsRepository()
    private val settingsRepo = FakeSettingsRepository()

    private fun buildVm() = ShiftsViewModel(shiftsRepo, settingsRepo)

    // ── UI state ────────────────────────────────────────────────────────────

    @Test
    fun `initial state is Loading`() {
        assertEquals(ShiftsUiState.Loading, buildVm().uiState.value)
    }

    @Test
    fun `empty state when no shifts`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ShiftsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        shiftsRepo.setShifts()
        advanceUntilIdle()

        assertTrue(states.any { it is ShiftsUiState.Empty })
        job.cancel()
    }

    @Test
    fun `list state when shifts exist`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ShiftsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        shiftsRepo.setShifts(
            Shift("s1", "u1", Instant.parse("2024-01-08T09:00:00Z"), Instant.parse("2024-01-08T17:00:00Z")),
        )
        advanceUntilIdle()

        val ready = states.filterIsInstance<ShiftsUiState.Ready>().last()
        assertEquals(1, ready.shifts.size)
        job.cancel()
    }

    @Test
    fun `active shift exposed in Ready state`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<ShiftsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        shiftsRepo.setShifts(Shift("s1", "u1", Instant.parse("2024-01-08T09:00:00Z"), null))
        advanceUntilIdle()

        val ready = states.filterIsInstance<ShiftsUiState.Ready>().last()
        assertEquals("s1", ready.activeShift?.id)
        job.cancel()
    }

    // ── create manual shift ─────────────────────────────────────────────────

    @Test
    fun `create manual shift writes locally and marks pending sync`() = runTest {
        val vm = buildVm()
        val prevSync = shiftsRepo.syncScheduledCount

        val input = ShiftFormInput(
            startTime = Instant.parse("2024-01-08T09:00:00Z"),
            endTime = Instant.parse("2024-01-08T17:00:00Z"),
            breakMinutes = 30,
            notes = "Test shift",
            isSpecialDay = false,
            refundAction = null,
        )
        vm.createShift(input)
        advanceUntilIdle()

        assertEquals(1, shiftsRepo.syncScheduledCount - prevSync)
        assertEquals(1, shiftsRepo.currentShifts.size)
    }

    @Test
    fun `create shift closes form on success`() = runTest {
        val vm = buildVm()
        vm.showCreateForm()
        advanceUntilIdle()

        val input = ShiftFormInput(
            startTime = Instant.parse("2024-01-08T09:00:00Z"),
            endTime = Instant.parse("2024-01-08T17:00:00Z"),
            breakMinutes = 0,
            notes = "",
            isSpecialDay = false,
            refundAction = null,
        )
        vm.createShift(input)
        advanceUntilIdle()

        assertNull(vm.formTarget.value)
    }

    // ── edit shift ──────────────────────────────────────────────────────────

    @Test
    fun `edit shift updates locally and marks pending sync`() = runTest {
        val original = Shift("s1", "u1", Instant.parse("2024-01-08T09:00:00Z"), Instant.parse("2024-01-08T17:00:00Z"), breakMinutes = 0)
        shiftsRepo.setShifts(original)
        val vm = buildVm()
        val prevSync = shiftsRepo.syncScheduledCount

        val input = ShiftFormInput(
            startTime = original.startTime,
            endTime = original.endTime,
            breakMinutes = 45,
            notes = "Updated",
            isSpecialDay = true,
            refundAction = null,
        )
        vm.saveEditedShift("s1", input)
        advanceUntilIdle()

        assertEquals(1, shiftsRepo.syncScheduledCount - prevSync)
        val saved = shiftsRepo.getShiftById("s1")!!
        assertEquals(45, saved.breakMinutes)
        assertEquals("Updated", saved.notes)
        assertTrue(saved.isSpecialDay)
    }

    @Test
    fun `active shift edit preserves null end time`() = runTest {
        val active = Shift("s1", "u1", Instant.parse("2024-01-08T09:00:00Z"), endTime = null)
        shiftsRepo.setShifts(active)
        val vm = buildVm()

        val input = ShiftFormInput(
            startTime = active.startTime,
            endTime = null,
            breakMinutes = 0,
            notes = "",
            isSpecialDay = false,
            refundAction = null,
        )
        vm.saveEditedShift("s1", input)
        advanceUntilIdle()

        assertNull(shiftsRepo.getShiftById("s1")?.endTime)
    }

    // ── delete shift ────────────────────────────────────────────────────────

    @Test
    fun `delete shift removes shift and marks pending sync`() = runTest {
        shiftsRepo.setShifts(
            Shift("s1", "u1", Instant.parse("2024-01-08T09:00:00Z"), Instant.parse("2024-01-08T17:00:00Z")),
        )
        val vm = buildVm()
        val states = mutableListOf<ShiftsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        val prevSync = shiftsRepo.syncScheduledCount

        advanceUntilIdle()
        vm.deleteShift("s1")
        advanceUntilIdle()

        assertEquals(1, shiftsRepo.syncScheduledCount - prevSync)
        assertTrue(states.any { it is ShiftsUiState.Empty })
        job.cancel()
    }

    // ── validation ──────────────────────────────────────────────────────────

    @Test
    fun `validation rejects end before start`() {
        val vm = buildVm()
        val input = ShiftFormInput(
            startTime = Instant.parse("2024-01-08T17:00:00Z"),
            endTime = Instant.parse("2024-01-08T09:00:00Z"),
            breakMinutes = 0,
            notes = "",
            isSpecialDay = false,
            refundAction = null,
        )
        val errors = vm.validate(input)
        assertTrue(errors.containsKey("endTime"))
    }

    @Test
    fun `validation rejects equal start and end time`() {
        val vm = buildVm()
        val t = Instant.parse("2024-01-08T09:00:00Z")
        val input = ShiftFormInput(t, t, 0, "", false, null)
        val errors = vm.validate(input)
        assertTrue(errors.containsKey("endTime"))
    }

    @Test
    fun `overnight shift is allowed`() {
        val vm = buildVm()
        val input = ShiftFormInput(
            startTime = Instant.parse("2024-01-08T22:00:00Z"),
            endTime = Instant.parse("2024-01-09T06:00:00Z"),
            breakMinutes = 0,
            notes = "",
            isSpecialDay = false,
            refundAction = null,
        )
        val errors = vm.validate(input)
        assertFalse(errors.containsKey("endTime"))
    }

    @Test
    fun `validation rejects negative break minutes`() {
        val vm = buildVm()
        val input = ShiftFormInput(
            startTime = Instant.parse("2024-01-08T09:00:00Z"),
            endTime = Instant.parse("2024-01-08T17:00:00Z"),
            breakMinutes = -1,
            notes = "",
            isSpecialDay = false,
            refundAction = null,
        )
        val errors = vm.validate(input)
        assertTrue(errors.containsKey("breakMinutes"))
    }

    @Test
    fun `validation passes with null end time`() {
        val vm = buildVm()
        val input = ShiftFormInput(
            startTime = Instant.parse("2024-01-08T09:00:00Z"),
            endTime = null,
            breakMinutes = 0,
            notes = "",
            isSpecialDay = false,
            refundAction = null,
        )
        val errors = vm.validate(input)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validation errors set on invalid create`() = runTest {
        val vm = buildVm()
        vm.showCreateForm()

        val input = ShiftFormInput(
            startTime = Instant.parse("2024-01-08T17:00:00Z"),
            endTime = Instant.parse("2024-01-08T09:00:00Z"),
            breakMinutes = 0,
            notes = "",
            isSpecialDay = false,
            refundAction = null,
        )
        vm.createShift(input)
        advanceUntilIdle()

        assertTrue(vm.formErrors.value.containsKey("endTime"))
        assertTrue(vm.formTarget.value is ShiftFormNavState.Create)
    }

    // ── sync scheduler ──────────────────────────────────────────────────────

    @Test
    fun `sync scheduler called after create, update, and delete`() = runTest {
        val vm = buildVm()
        val shift = Shift("s1", "u1", Instant.parse("2024-01-08T09:00:00Z"), Instant.parse("2024-01-08T17:00:00Z"))
        val start = shiftsRepo.syncScheduledCount

        // create
        vm.createShift(ShiftFormInput(shift.startTime, shift.endTime, 0, "", false, null))
        advanceUntilIdle()

        // get created shift id
        val createdId = shiftsRepo.currentShifts.first().id

        shiftsRepo.setShifts(Shift(createdId, "u1", shift.startTime, shift.endTime))

        // update
        vm.saveEditedShift(createdId, ShiftFormInput(shift.startTime, shift.endTime, 15, "", false, null))
        advanceUntilIdle()

        // delete
        vm.deleteShift(createdId)
        advanceUntilIdle()

        assertEquals(3, shiftsRepo.syncScheduledCount - start)
    }

    // ── form navigation ─────────────────────────────────────────────────────

    @Test
    fun `showCreateForm sets Create state`() {
        val vm = buildVm()
        vm.showCreateForm()
        assertTrue(vm.formTarget.value is ShiftFormNavState.Create)
    }

    @Test
    fun `showEditForm sets Edit state with shift`() = runTest {
        val shift = Shift("s1", "u1", Instant.parse("2024-01-08T09:00:00Z"), Instant.parse("2024-01-08T17:00:00Z"))
        shiftsRepo.setShifts(shift)
        val vm = buildVm()

        vm.showEditForm("s1")
        advanceUntilIdle()

        val state = vm.formTarget.value
        assertTrue(state is ShiftFormNavState.Edit)
        assertEquals("s1", (state as ShiftFormNavState.Edit).shift.id)
    }

    @Test
    fun `closeForm clears formTarget and errors`() = runTest {
        val vm = buildVm()
        vm.showCreateForm()
        vm.closeForm()
        advanceUntilIdle()

        assertNull(vm.formTarget.value)
        assertTrue(vm.formErrors.value.isEmpty())
    }
}
