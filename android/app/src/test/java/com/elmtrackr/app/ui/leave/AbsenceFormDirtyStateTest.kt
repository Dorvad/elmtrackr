package com.elmtrackr.app.ui.leave

import com.elmtrackr.app.R
import com.elmtrackr.app.data.local.DirectTransactionRunner
import com.elmtrackr.app.data.repository.LocalLeaveRepository
import com.elmtrackr.app.domain.model.AbsenceEvent
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import com.elmtrackr.app.domain.model.LeaveManualMode
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.model.Workplace
import com.elmtrackr.app.domain.repository.AbsenceDraft
import com.elmtrackr.app.domain.repository.LeaveRepository
import com.elmtrackr.app.fake.FakeAbsenceAllocationDao
import com.elmtrackr.app.fake.FakeAbsenceEventDao
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakeCurrentUserProvider
import com.elmtrackr.app.fake.FakeLeaveBalanceSnapshotDao
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeShiftDao
import com.elmtrackr.app.fake.FakeSyncTrigger
import com.elmtrackr.app.fake.FakeWorkplaceDao
import com.elmtrackr.app.fake.FakeWorkplacesRepository
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * When closing the absence form would lose something, and when it would not.
 *
 * The form closed on any back gesture. "Dirty" is now a comparison between what
 * the user has entered and what the form opened with (or last saved), so an
 * untouched form closes without a question and a touched one asks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AbsenceFormDirtyStateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val eventDao = FakeAbsenceEventDao()
    private val workplacesRepo = FakeWorkplacesRepository()
    private val localRepository = LocalLeaveRepository(
        eventDao,
        FakeAbsenceAllocationDao(),
        FakeLeaveBalanceSnapshotDao(),
        FakeWorkplaceDao(),
        FakeShiftDao(),
        workplacesRepo,
        FakeSettingsRepository(),
        FakeCompensationProfilesRepository(),
        DirectTransactionRunner,
        FakeSyncTrigger(),
    )

    /** Saves go through [onSave] first, so a test can fail or hold one. */
    private class ControlledRepository(
        private val delegate: LeaveRepository,
    ) : LeaveRepository by delegate {
        var saveCalls = 0
        var onSave: suspend () -> Unit = {}

        override suspend fun saveAbsence(userId: String, draft: AbsenceDraft): AbsenceEvent {
            saveCalls++
            onSave()
            return delegate.saveAbsence(userId, draft)
        }
    }

    private val repository = ControlledRepository(localRepository)

    private val monday = LocalDate.parse("2026-08-10")

    private fun viewModel() = AbsenceFormViewModel(repository, workplacesRepo, FakeCurrentUserProvider())

    private fun AbsenceFormViewModel.ready() = uiState.value as AbsenceFormUiState.Ready

    private fun opened(type: AbsenceType = AbsenceType.VACATION): AbsenceFormViewModel =
        viewModel().also { it.start(type, today = monday) }

    // ── Clean and dirty ───────────────────────────────────────────────────────

    @Test
    fun `a form nobody has touched is not dirty, even after its estimates arrive`() = runTest {
        val vm = opened()

        assertFalse(vm.ready().isDirty)
    }

    @Test
    fun `each kind of edit makes the form dirty`() = runTest {
        val edits: List<Pair<String, (AbsenceFormViewModel) -> Unit>> = listOf(
            "start date" to { it.setStartDate(monday.plusDays(1)) },
            "range" to { it.setRange(true); it.setEndDate(monday.plusDays(2)) },
            "a day ticked" to { it.toggleDay(monday) },
            "part day" to { it.setFullDay(false) },
            "note" to { it.setNotes("Beach week") },
            "manual amount" to { it.setManualAmount(monday, "400") },
            "manual mode" to { it.setManualMode(monday, LeaveManualMode.FINAL_PAYABLE) },
        )
        edits.forEach { (name, edit) ->
            val vm = opened()
            edit(vm)
            assertTrue(name, vm.ready().isDirty)
        }
    }

    @Test
    fun `changing the workplace makes the form dirty`() = runTest {
        workplacesRepo.setWorkplaces(
            Workplace(id = "wp-a", userId = "local-user", name = "Cafe", regionCode = RegionCode.IL, currencyCode = "ILS", timezone = "UTC", isDefault = true),
            Workplace(id = "wp-b", userId = "local-user", name = "Bar", regionCode = RegionCode.IL, currencyCode = "ILS", timezone = "UTC"),
        )
        val vm = opened()
        assertFalse(vm.ready().isDirty)

        vm.selectWorkplace("wp-b")

        assertTrue(vm.ready().isDirty)
    }

    @Test
    fun `changing something and changing it back is not dirty`() = runTest {
        val vm = opened()
        vm.setNotes("Beach week")
        vm.setNotes("")
        vm.toggleDay(monday)
        vm.toggleDay(monday)
        vm.setFullDay(false)
        vm.setFullDay(true)

        assertFalse(vm.ready().isDirty)
    }

    @Test
    fun `whitespace alone in the note is not an edit`() = runTest {
        val vm = opened()
        vm.setNotes("   ")

        assertFalse(vm.ready().isDirty)
    }

    @Test
    fun `an existing absence opens clean and is dirty once changed`() = runTest {
        val vm = opened()
        vm.toggleDay(monday)
        vm.setNotes("Dentist")
        vm.save()
        val eventId = eventDao.rows().single().localId

        val edit = viewModel()
        edit.start(AbsenceType.VACATION, editingEventId = eventId, today = monday)
        assertFalse(edit.ready().isDirty)
        assertEquals("Dentist", edit.ready().notes)

        edit.setNotes("Dentist, then home")
        assertTrue(edit.ready().isDirty)
    }

    // ── Saving ────────────────────────────────────────────────────────────────

    @Test
    fun `a successful save leaves nothing to discard and closes the form`() = runTest {
        val vm = opened()
        vm.toggleDay(monday)
        vm.setNotes("Beach")

        vm.save()

        assertFalse(vm.ready().isDirty)
        assertTrue(vm.closed.value)
    }

    @Test
    fun `a failed save keeps the edits, says so, and lets the user try again`() = runTest {
        repository.onSave = { throw IllegalStateException("database is locked") }
        val vm = opened()
        vm.toggleDay(monday)
        vm.setNotes("Beach")

        vm.save()

        val state = vm.ready()
        assertTrue("still dirty: closing now would lose the edits", state.isDirty)
        assertFalse(state.isSaving)
        assertFalse(vm.closed.value)
        assertEquals(UiText.Res(R.string.leave_form_save_failed), state.validationError)
        assertEquals("Beach", state.notes)

        repository.onSave = {}
        vm.save()
        assertTrue(vm.closed.value)
        assertEquals(1, eventDao.rows().size)
    }

    @Test
    fun `tapping save again while a save is in flight saves once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.onSave = { gate.await() }
        val vm = opened()
        vm.toggleDay(monday)

        vm.save()
        assertTrue(vm.ready().isSaving)
        assertFalse(vm.ready().canSave)
        vm.save()
        vm.save()
        gate.complete(Unit)

        assertEquals(1, repository.saveCalls)
        assertEquals(1, eventDao.rows().size)
        assertTrue(vm.closed.value)
    }

    // ── Reopening ─────────────────────────────────────────────────────────────

    @Test
    fun `a discarded form opens fresh next time instead of bringing the edits back`() = runTest {
        val vm = opened()
        vm.toggleDay(monday)
        vm.setNotes("Discard me")

        vm.onFormClosed()
        vm.start(AbsenceType.VACATION, today = monday)

        assertEquals("", vm.ready().notes)
        assertFalse(vm.ready().isDirty)
    }

    @Test
    fun `a form reopened after a save is not closed by the previous save`() = runTest {
        val vm = opened()
        vm.toggleDay(monday)
        vm.save()
        assertTrue(vm.closed.value)

        vm.onFormClosed()
        vm.start(AbsenceType.VACATION, today = monday)

        assertFalse(vm.closed.value)
        assertFalse(vm.ready().isDirty)
    }

    @Test
    fun `a rotation resumes the form with its edits`() = runTest {
        val vm = opened()
        vm.setNotes("Keep me")

        // The screen is recreated and calls start again without having closed.
        vm.start(AbsenceType.VACATION, today = monday)

        assertEquals("Keep me", vm.ready().notes)
        assertTrue(vm.ready().isDirty)
    }

    @Test
    fun `an unreadable part day is still an edit the user would lose`() = runTest {
        val vm = opened()
        vm.setFullDay(false)
        vm.setPartialUnit(LeaveBalanceUnit.HOURS)
        vm.setPartialAmount("30")

        assertTrue(vm.ready().isDirty)
        assertTrue(vm.ready().partialAmountInvalid)
    }
}
