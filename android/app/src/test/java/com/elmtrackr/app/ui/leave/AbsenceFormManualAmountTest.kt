package com.elmtrackr.app.ui.leave

import com.elmtrackr.app.R
import com.elmtrackr.app.data.local.DirectTransactionRunner
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.remote.RemoteAbsenceAllocationRow
import com.elmtrackr.app.data.remote.toLocalEntity
import com.elmtrackr.app.data.remote.toRemoteInsert
import com.elmtrackr.app.data.repository.LocalLeaveRepository
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.leave.LeaveEstimateGap
import com.elmtrackr.app.domain.leave.LeavePolicyCodec
import com.elmtrackr.app.domain.model.AbsenceAllocation
import com.elmtrackr.app.domain.model.AbsenceEvent
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import com.elmtrackr.app.domain.model.LeaveCalculationSnapshot
import com.elmtrackr.app.domain.model.LeaveManualMode
import com.elmtrackr.app.domain.model.LeaveManualOverride
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.model.VacationPayBasis
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * A manual leave amount, from the field to the stored record and back.
 *
 * Driven through the real [LocalLeaveRepository] over in-memory DAOs, so the
 * preview, the save, the reload and the edit all take the path production takes.
 * There are no settings and no shifts here, so the Israeli default (a 90-day
 * average) has no history to average: every day starts as "needs a value", which
 * is exactly when a user reaches for the amount field.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AbsenceFormManualAmountTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val eventDao = FakeAbsenceEventDao()
    private val allocationDao = FakeAbsenceAllocationDao()
    private val workplacesRepo = FakeWorkplacesRepository()
    private val profilesRepo = FakeCompensationProfilesRepository()
    private val repository = LocalLeaveRepository(
        eventDao,
        allocationDao,
        FakeLeaveBalanceSnapshotDao(),
        FakeWorkplaceDao(),
        FakeShiftDao(),
        workplacesRepo,
        FakeSettingsRepository(),
        profilesRepo,
        DirectTransactionRunner,
        FakeSyncTrigger(),
    )

    private val monday = LocalDate.parse("2026-08-10")
    private val tuesday = monday.plusDays(1)
    private val wednesday = monday.plusDays(2)

    private fun viewModel() = AbsenceFormViewModel(repository, workplacesRepo, FakeCurrentUserProvider())

    private fun AbsenceFormViewModel.ready() = uiState.value as AbsenceFormUiState.Ready

    private fun AbsenceFormViewModel.day(date: LocalDate) = ready().days.first { it.date == date }

    private fun storedAllocations(): List<AbsenceAllocation> =
        allocationDao.rows().filter { it.deletedAt == null }.map { it.toDomain() }.sortedBy { it.affectedDate }

    /** A form over [days], each ticked. */
    private fun openForm(type: AbsenceType, vararg days: LocalDate): AbsenceFormViewModel {
        val vm = viewModel()
        vm.start(type, today = days.first())
        if (days.size > 1) {
            vm.setRange(true)
            vm.setEndDate(days.last())
        }
        days.forEach { vm.toggleDay(it) }
        return vm
    }

    // ── The amount reaches the estimate ───────────────────────────────────────

    @Test
    fun `without history the day asks for a value, and a typed amount answers it`() = runTest {
        val vm = openForm(AbsenceType.VACATION, monday)
        assertEquals(LeaveEstimateGap.NO_PAY_HISTORY, vm.day(monday).gap)

        vm.setManualAmount(monday, "400")

        val day = vm.day(monday)
        assertNull(day.gap)
        assertEquals(400.0, day.estimatedGross!!, 0.0)
        assertEquals(LeaveManualMode.BASE_DAILY, day.manualMode)
        assertEquals(400.0, vm.ready().estimatedTotal, 0.0)
    }

    @Test
    fun `a daily amount on a sick day takes the day's percentage`() = runTest {
        val vm = openForm(AbsenceType.SICK, monday, tuesday)
        vm.setManualAmount(monday, "400")
        vm.setManualAmount(tuesday, "400")

        // Israeli ladder: day one 0%, day two 50%.
        assertEquals(0.0, vm.day(monday).estimatedGross!!, 0.0)
        assertEquals(200.0, vm.day(tuesday).estimatedGross!!, 0.0)
    }

    @Test
    fun `a final amount is used as entered on a sick day`() = runTest {
        val vm = openForm(AbsenceType.SICK, monday, tuesday)
        vm.setManualMode(tuesday, LeaveManualMode.FINAL_PAYABLE)
        vm.setManualAmount(tuesday, "333")

        assertEquals(333.0, vm.day(tuesday).estimatedGross!!, 0.0)
        assertEquals(LeaveManualMode.FINAL_PAYABLE, vm.day(tuesday).manualMode)
    }

    @Test
    fun `a part day scales a daily amount and an unreadable one is refused, not saved as a full day`() = runTest {
        val vm = openForm(AbsenceType.VACATION, monday)
        vm.setManualAmount(monday, "400")
        vm.setFullDay(false)
        vm.setPartialAmount("0.5")
        assertEquals(200.0, vm.day(monday).estimatedGross!!, 0.0)

        vm.setPartialAmount("1.5")
        assertTrue(vm.ready().partialAmountInvalid)
        assertNull("no estimate for a part day the field does not describe", vm.day(monday).estimatedGross)
        vm.save()
        assertEquals(UiText.Res(R.string.leave_form_partial_invalid_days), vm.ready().validationError)
        assertTrue("nothing saved", storedAllocations().isEmpty())

        vm.setPartialAmount("0,5")
        vm.save()
        assertEquals(0.5, storedAllocations().single().entitlementUnits, 0.0)
        assertEquals(200.0, storedAllocations().single().estimatedGrossPay, 0.0)
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    fun `a comma decimal is read as a decimal`() = runTest {
        val vm = openForm(AbsenceType.VACATION, monday)
        vm.setManualAmount(monday, "412,50")

        assertEquals(412.5, vm.day(monday).estimatedGross!!, 0.0)
    }

    @Test
    fun `a negative or malformed amount blocks the save and says why`() = runTest {
        listOf("-5", "abc", "1e3", "NaN", ".").forEach { typed ->
            val vm = openForm(AbsenceType.VACATION, monday)
            vm.setManualAmount(monday, typed)

            assertTrue(typed, vm.day(monday).manualAmountInvalid)
            vm.save()
            assertEquals(typed, UiText.Res(R.string.leave_manual_invalid), vm.ready().validationError)
            assertFalse(typed, vm.closed.value)
        }
        assertTrue(storedAllocations().isEmpty())
    }

    /** A fixed eight-hour vacation day at 50/h: an automatic estimate of 400 exists. */
    private suspend fun giveAutomaticEstimate() {
        val workplace = workplacesRepo.ensureDefaultWorkplace("local-user")
        val current = workplacesRepo.ensurePolicy("local-user", workplace.id)
        workplacesRepo.updatePolicyRules(
            "local-user",
            workplace.id,
            current.rules.copy(
                vacation = current.rules.vacation.copy(
                    payBasis = VacationPayBasis.FIXED_DAILY_HOURS,
                    fixedDailyMinutes = 480,
                ),
            ),
        )
        profilesRepo.setProfiles(
            CompensationProfile(
                id = "p-main",
                userId = "local-user",
                name = "Main job",
                regionCode = RegionCode.IL,
                currencyCode = "ILS",
                timezone = "UTC",
                baseHourlyRate = 50.0,
                rules = RegionPresets.forRegion(RegionCode.IL).rules,
                stackingPolicy = RegionPresets.forRegion(RegionCode.IL).stackingPolicy,
                isDefault = true,
            ),
        )
    }

    @Test
    fun `with enough to go on the automatic estimate is used and nothing is marked manual`() = runTest {
        giveAutomaticEstimate()
        val vm = openForm(AbsenceType.VACATION, monday)

        assertEquals(400.0, vm.day(monday).estimatedGross!!, 0.0)
        assertFalse(vm.day(monday).showsManualEntry)
        vm.save()
        assertNull(storedAllocations().single().calculationSnapshot?.manualOverride)
    }

    @Test
    fun `choosing a manual amount and leaving it empty does not quietly save the automatic one`() = runTest {
        giveAutomaticEstimate()
        val vm = openForm(AbsenceType.VACATION, monday)
        vm.setManualMode(monday, LeaveManualMode.BASE_DAILY)
        assertTrue(vm.day(monday).showsManualEntry)

        vm.save()

        assertEquals(UiText.Res(R.string.leave_manual_missing), vm.ready().validationError)
        assertTrue(storedAllocations().isEmpty())

        vm.setManualAmount(monday, "500")
        vm.save()
        assertEquals(500.0, storedAllocations().single().estimatedGrossPay, 0.0)
        assertEquals(LeaveManualMode.BASE_DAILY, storedAllocations().single().calculationSnapshot?.manualOverride?.mode)
    }

    @Test
    fun `an empty field on a day the engine could not value still saves the day`() = runTest {
        val vm = openForm(AbsenceType.VACATION, monday)

        vm.save()

        val saved = storedAllocations().single()
        assertEquals(monday, saved.affectedDate)
        assertNull(saved.calculationSnapshot)
        assertTrue(vm.closed.value)
    }

    // ── Save, reload, sync, edit ──────────────────────────────────────────────

    @Test
    fun `several days keep their own amounts and meanings through save and reload`() = runTest {
        val vm = openForm(AbsenceType.VACATION, monday, tuesday, wednesday)
        vm.setManualAmount(monday, "400")
        vm.setManualAmount(tuesday, "250.5")
        vm.setManualMode(wednesday, LeaveManualMode.FINAL_PAYABLE)
        vm.setManualAmount(wednesday, "99")
        vm.save()

        val saved = storedAllocations()
        assertEquals(listOf(400.0, 250.5, 99.0), saved.map { it.estimatedGrossPay })
        assertEquals(
            listOf(
                LeaveManualOverride(true, null, LeaveManualMode.BASE_DAILY, 400.0),
                LeaveManualOverride(true, null, LeaveManualMode.BASE_DAILY, 250.5),
                LeaveManualOverride(true, null, LeaveManualMode.FINAL_PAYABLE, 99.0),
            ),
            saved.map { it.calculationSnapshot?.manualOverride },
        )

        val eventId = eventDao.rows().single().localId
        val reopened = viewModel()
        reopened.start(AbsenceType.VACATION, editingEventId = eventId, today = monday)

        assertEquals("400", reopened.day(monday).manualAmount)
        assertEquals(LeaveManualMode.BASE_DAILY, reopened.day(monday).manualMode)
        assertEquals("250.5", reopened.day(tuesday).manualAmount)
        assertEquals("99", reopened.day(wednesday).manualAmount)
        assertEquals(LeaveManualMode.FINAL_PAYABLE, reopened.day(wednesday).manualMode)
        assertEquals(749.5, reopened.ready().estimatedTotal, 0.0)
    }

    @Test
    fun `editing a saved amount replaces it and leaves the other days as they were`() = runTest {
        val vm = openForm(AbsenceType.VACATION, monday, tuesday)
        vm.setManualAmount(monday, "400")
        vm.setManualAmount(tuesday, "300")
        vm.save()
        val eventId = eventDao.rows().single().localId

        val edit = viewModel()
        edit.start(AbsenceType.VACATION, editingEventId = eventId, today = monday)
        edit.setManualAmount(monday, "450")
        edit.save()

        assertEquals(listOf(450.0, 300.0), storedAllocations().map { it.estimatedGrossPay })
        assertEquals(1, eventDao.rows().size)
    }

    @Test
    fun `switching a day back to automatic clears the override`() = runTest {
        val vm = openForm(AbsenceType.VACATION, monday)
        vm.setManualAmount(monday, "400")
        vm.useAutomaticAmount(monday)

        assertNull(vm.day(monday).manualMode)
        assertEquals("", vm.day(monday).manualAmount)
        assertEquals(LeaveEstimateGap.NO_PAY_HISTORY, vm.day(monday).gap)
    }

    @Test
    fun `the override travels to Supabase and back unchanged`() = runTest {
        val vm = openForm(AbsenceType.SICK, monday, tuesday)
        vm.setManualMode(tuesday, LeaveManualMode.FINAL_PAYABLE)
        vm.setManualAmount(tuesday, "333")
        vm.save()

        val entity = allocationDao.rows().first { it.deletedAt == null && it.affectedDate == tuesday.toEpochDay() }
        val pushed = entity.toRemoteInsert(absenceEventRemoteId = "remote-event", workplaceRemoteId = "remote-wp")
        val pulled = RemoteAbsenceAllocationRow(
            id = "remote-allocation",
            userId = pushed.userId,
            absenceEventId = pushed.absenceEventId,
            workplaceId = pushed.workplaceId,
            affectedDate = pushed.affectedDate,
            entitlementUnits = pushed.entitlementUnits,
            unit = pushed.unit,
            expectedWorkMinutes = pushed.expectedWorkMinutes,
            policySnapshotJson = pushed.policySnapshotJson,
            calculationSnapshotJson = pushed.calculationSnapshotJson,
            estimatedGrossPay = pushed.estimatedGrossPay,
            createdAt = "2026-08-10T10:00:00Z",
            updatedAt = "2026-08-10T10:00:00Z",
        ).toLocalEntity(absenceEventLocalId = entity.absenceEventLocalId, workplaceLocalId = entity.workplaceLocalId)

        val override = LeavePolicyCodec.decodeCalculationSnapshot(pulled.calculationSnapshotJson)?.manualOverride
        assertEquals(LeaveManualOverride(true, null, LeaveManualMode.FINAL_PAYABLE, 333.0), override)
        assertEquals(333.0, pulled.estimatedGrossPay, 0.0)
    }

    // ── Existing records ──────────────────────────────────────────────────────

    private suspend fun seedLegacy(payBasis: String, baseAmount: Double, gross: Double): String {
        val event = AbsenceEvent(
            id = "legacy-event",
            userId = "local-user",
            type = AbsenceType.VACATION,
            startDate = monday,
            endDate = monday,
        )
        eventDao.upsertRow(event.toEntity(syncStatus = SyncStatus.SYNCED))
        val workplaceId = workplacesRepo.ensureDefaultWorkplace("local-user").id
        val allocation = AbsenceAllocation(
            id = "legacy-allocation",
            userId = "local-user",
            absenceEventId = event.id,
            workplaceId = workplaceId,
            affectedDate = monday,
            entitlementUnits = 1.0,
            unit = LeaveBalanceUnit.DAYS,
            calculationSnapshot = LeaveCalculationSnapshot(
                calculatedAt = Instant.parse("2026-08-01T00:00:00Z"),
                absenceType = AbsenceType.VACATION,
                workplaceId = workplaceId,
                payBasis = payBasis,
                currencyCode = "ILS",
                baseAmount = baseAmount,
                estimatedGrossPay = gross,
                // Written by the old build: "enabled" and nothing else.
                manualOverride = LeaveManualOverride(enabled = true),
            ),
            estimatedGrossPay = gross,
        )
        allocationDao.upsertRow(allocation.toEntity(syncStatus = SyncStatus.SYNCED))
        return event.id
    }

    @Test
    fun `an old record whose typed amount was ignored is not turned into an override`() = runTest {
        val eventId = seedLegacy(payBasis = "israel_statutory_average_90", baseAmount = 266.67, gross = 266.67)
        val before = allocationDao.rows()

        val vm = viewModel()
        vm.start(AbsenceType.VACATION, editingEventId = eventId, today = monday)

        assertNull(vm.day(monday).manualMode)
        assertEquals("", vm.day(monday).manualAmount)
        assertEquals("opening a record writes nothing", before, allocationDao.rows())
    }

    @Test
    fun `an old record priced from a manual basis comes back as the daily amount it was`() = runTest {
        val eventId = seedLegacy(payBasis = "manual", baseAmount = 375.0, gross = 375.0)

        val vm = viewModel()
        vm.start(AbsenceType.VACATION, editingEventId = eventId, today = monday)

        assertEquals(LeaveManualMode.BASE_DAILY, vm.day(monday).manualMode)
        assertEquals("375", vm.day(monday).manualAmount)
        assertEquals(375.0, vm.day(monday).estimatedGross!!, 0.0)
    }

    @Test
    fun `typed amounts survive moving the end of the range`() = runTest {
        val vm = openForm(AbsenceType.VACATION, monday, tuesday)
        vm.setManualAmount(monday, "400")

        vm.setEndDate(wednesday)

        assertEquals("400", vm.day(monday).manualAmount)
        assertEquals(400.0, vm.day(monday).estimatedGross!!, 0.0)
    }
}
