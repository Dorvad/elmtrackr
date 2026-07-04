package com.elmtrackr.app.ui.shifts

import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakePremiumProfilesRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeCurrentUserProvider
import com.elmtrackr.app.fake.FakeRefundsRepository
import com.elmtrackr.app.fake.FakeRefundReceiptStorage
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.fake.FakeTasksRepository
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.ReceiptUpload
import com.elmtrackr.app.domain.model.RefundAction
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.model.RefundProvider
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.PremiumType
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class ShiftsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val shiftsRepo = FakeShiftsRepository()
    private val settingsRepo = FakeSettingsRepository()
    private val compensationRepo = FakeCompensationProfilesRepository()
    private val premiumRepo = FakePremiumProfilesRepository()
    private val tasksRepo = FakeTasksRepository()
    private val currentUser = FakeCurrentUserProvider()
    private val refundsRepo = FakeRefundsRepository()
    private val receiptStorage = FakeRefundReceiptStorage()

    private fun buildVm() = ShiftsViewModel(
        shiftsRepo,
        settingsRepo,
        compensationRepo,
        premiumRepo,
        tasksRepo,
        currentUser,
        refundsRepo,
        receiptStorage,
    )

    private fun seedSettings(userId: String = "u1") {
        currentUser.setUserId(userId)
        settingsRepo.setSettings(
            UserSettings(
                id = "settings",
                userId = userId,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
    }

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
    fun `empty state includes selected month`() = runTest {
        val vm = buildVm()
        // WhileSubscribed state flows need an active collector to leave Loading.
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        val empty = vm.uiState.value as ShiftsUiState.Empty
        assertEquals(vm.selectedMonth.value, empty.month)
        job.cancel()
    }

    @Test
    fun `ready state includes selected month`() = runTest {
        shiftsRepo.setShifts(
            Shift("s1", "u1", Instant.parse("2024-01-08T09:00:00Z"), Instant.parse("2024-01-08T17:00:00Z")),
        )
        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        val ready = vm.uiState.value as ShiftsUiState.Ready
        assertEquals(vm.selectedMonth.value, ready.month)
        job.cancel()
    }

    @Test
    fun `month navigation lands on the new month's state`() = runTest {
        // The transient Loading between months is conflated away under the
        // unconfined test dispatcher, so assert the regression guard directly:
        // the emitted state must carry the newly selected month, never stale data.
        seedSettings()
        shiftsRepo.setShifts(
            Shift("s1", "u1", Instant.parse("2024-01-08T09:00:00Z"), Instant.parse("2024-01-08T17:00:00Z")),
        )
        val vm = buildVm()
        val states = mutableListOf<ShiftsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()
        val monthBefore = vm.selectedMonth.value

        vm.previousMonth()
        advanceUntilIdle()

        assertEquals(monthBefore.minusMonths(1), vm.selectedMonth.value)
        val latest = states.last()
        assertTrue(latest is ShiftsUiState.Empty || latest is ShiftsUiState.Ready)
        when (latest) {
            is ShiftsUiState.Empty -> assertEquals(vm.selectedMonth.value, latest.month)
            is ShiftsUiState.Ready -> assertEquals(vm.selectedMonth.value, latest.month)
            else -> Unit
        }
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
    fun `create manual shift writes locally`() = runTest {
        seedSettings()
        val vm = buildVm()

        val input = ShiftFormInput(
            startTime = Instant.parse("2024-01-08T09:00:00Z"),
            endTime = Instant.parse("2024-01-08T17:00:00Z"),
            breakMinutes = 30,
            notes = "Test shift",
            premiumProfileId = null,
            refundAction = null,
        )
        vm.createShift(input)
        advanceUntilIdle()

        assertEquals(1, shiftsRepo.currentShifts.size)
    }

    @Test
    fun `create shift closes form on success`() = runTest {
        seedSettings()
        val vm = buildVm()
        vm.showCreateForm()
        advanceUntilIdle()

        val input = ShiftFormInput(
            startTime = Instant.parse("2024-01-08T09:00:00Z"),
            endTime = Instant.parse("2024-01-08T17:00:00Z"),
            breakMinutes = 0,
            notes = "",
            premiumProfileId = null,
            refundAction = null,
        )
        vm.createShift(input)
        advanceUntilIdle()

        assertNull(vm.formTarget.value)
    }

    // ── edit shift ──────────────────────────────────────────────────────────

    @Test
    fun `edit shift updates locally`() = runTest {
        seedSettings()
        premiumRepo.setProfiles(
            PremiumProfile(
                id = "premium-1",
                userId = "u1",
                name = "Premium",
                multiplier = 1.5,
                premiumType = PremiumType.HIGHEST_ONLY,
                isDefault = true,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
        val original = Shift("s1", "u1", Instant.parse("2024-01-08T09:00:00Z"), Instant.parse("2024-01-08T17:00:00Z"), breakMinutes = 0)
        shiftsRepo.setShifts(original)
        val vm = buildVm()

        val input = ShiftFormInput(
            startTime = original.startTime,
            endTime = original.endTime,
            breakMinutes = 45,
            notes = "Updated",
            premiumProfileId = "premium-1",
            refundAction = null,
        )
        vm.saveEditedShift("s1", input)
        advanceUntilIdle()

        val saved = shiftsRepo.getShiftById("s1")!!
        assertEquals(45, saved.breakMinutes)
        assertEquals("Updated", saved.notes)
        assertTrue(saved.isSpecialDay)
        assertEquals("premium-1", saved.premiumProfileId)
    }

    @Test
    fun `edit shift clears premium profile when no premium selected`() = runTest {
        seedSettings()
        premiumRepo.setProfiles(
            PremiumProfile(
                id = "premium-1",
                userId = "u1",
                name = "Premium",
                multiplier = 1.5,
                premiumType = PremiumType.HIGHEST_ONLY,
                isDefault = true,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
        val original = specialDayShift().copy(id = "s1")
        shiftsRepo.setShifts(original)
        val vm = buildVm()

        val input = ShiftFormInput(
            startTime = original.startTime,
            endTime = original.endTime,
            breakMinutes = original.breakMinutes,
            notes = original.notes.orEmpty(),
            premiumProfileId = null,
            refundAction = null,
        )
        vm.saveEditedShift("s1", input)
        advanceUntilIdle()

        val saved = shiftsRepo.getShiftById("s1")!!
        assertFalse(saved.isSpecialDay)
        assertNull(saved.premiumProfileId)
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
            premiumProfileId = null,
            refundAction = null,
        )
        vm.saveEditedShift("s1", input)
        advanceUntilIdle()

        assertNull(shiftsRepo.getShiftById("s1")?.endTime)
    }

    // ── delete shift ────────────────────────────────────────────────────────

    @Test
    fun `delete shift removes shift locally`() = runTest {
        shiftsRepo.setShifts(
            Shift("s1", "u1", Instant.parse("2024-01-08T09:00:00Z"), Instant.parse("2024-01-08T17:00:00Z")),
        )
        val vm = buildVm()
        val states = mutableListOf<ShiftsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        advanceUntilIdle()
        vm.deleteShift("s1")
        advanceUntilIdle()

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
            premiumProfileId = null,
            refundAction = null,
        )
        val errors = vm.validate(input)
        assertTrue(errors.containsKey("endTime"))
    }

    @Test
    fun `validation rejects equal start and end time`() {
        val vm = buildVm()
        val t = Instant.parse("2024-01-08T09:00:00Z")
        val input = ShiftFormInput(t, t, 0, "", null, null)
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
            premiumProfileId = null,
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
            premiumProfileId = null,
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
            premiumProfileId = null,
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
            premiumProfileId = null,
            refundAction = null,
        )
        vm.createShift(input)
        advanceUntilIdle()

        assertTrue(vm.formErrors.value.containsKey("endTime"))
        assertTrue(vm.formTarget.value is ShiftFormNavState.Create)
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

    @Test
    fun `refund claim stores ride details and receipt then marks shift submitted`() = runTest {
        val shift = specialDayShift()
        shiftsRepo.setShifts(shift)
        val vm = buildVm()
        vm.showEditForm(shift.id)
        val collection = launch { vm.refundClaims.collect {} }
        advanceUntilIdle()
        val rideAt = Instant.parse("2024-01-08T18:15:00Z")

        vm.saveRefundClaim(
            shiftId = shift.id,
            direction = RefundDirection.FROM_WORK,
            provider = RefundProvider.TAXI,
            amount = 42.5,
            rideAt = rideAt,
            notes = "Late ride",
            receipt = ReceiptUpload("receipt.jpg", byteArrayOf(1, 2, 3)),
        )
        advanceUntilIdle()

        val claim = refundsRepo.observeClaimsForShift(shift.id).first().single()
        assertEquals(rideAt, claim.rideAt)
        assertEquals("Late ride", claim.notes)
        assertTrue(claim.receiptPath?.endsWith("receipt.jpg") == true)
        assertEquals(RefundAction.SUBMITTED, shiftsRepo.getShiftById(shift.id)?.refundAction)
        collection.cancel()
    }

    @Test
    fun `deleting final refund removes receipt and clears shift action`() = runTest {
        val shift = specialDayShift()
        shiftsRepo.setShifts(shift)
        val vm = buildVm()
        vm.showEditForm(shift.id)
        val collection = launch { vm.refundClaims.collect {} }
        advanceUntilIdle()
        vm.saveRefundClaim(
            shift.id,
            RefundDirection.TO_WORK,
            RefundProvider.TAXI,
            20.0,
            shift.startTime,
            "",
            ReceiptUpload("ride.png", byteArrayOf(7)),
        )
        advanceUntilIdle()
        val claim = refundsRepo.observeClaimsForShift(shift.id).first().single()

        vm.deleteRefundClaim(claim.id)
        advanceUntilIdle()

        assertTrue(refundsRepo.observeClaimsForShift(shift.id).first().isEmpty())
        assertNull(shiftsRepo.getShiftById(shift.id)?.refundAction)
        assertEquals(listOf(claim.receiptPath), receiptStorage.deletedPaths)
        collection.cancel()
    }

    @Test
    fun `ineligible refund direction is rejected`() = runTest {
        val shift = Shift(
            "ordinary",
            "local-user",
            Instant.parse("2024-01-08T12:00:00Z"),
            Instant.parse("2024-01-08T17:00:00Z"),
        )
        shiftsRepo.setShifts(shift)
        val vm = buildVm()

        vm.saveRefundClaim(
            shift.id,
            RefundDirection.FROM_WORK,
            RefundProvider.TAXI,
            20.0,
            shift.endTime!!,
            "",
            null,
        )
        advanceUntilIdle()

        assertTrue(vm.formErrors.value.containsKey("refund"))
        assertTrue(refundsRepo.observeClaimsForShift(shift.id).first().isEmpty())
    }

    @Test
    fun `claim is saved without receipt when storage upload fails`() = runTest {
        val shift = specialDayShift()
        shiftsRepo.setShifts(shift)
        receiptStorage.failUploads = true
        val vm = buildVm()
        var completed: Boolean? = null

        vm.saveRefundClaim(
            shift.id,
            RefundDirection.FROM_WORK,
            RefundProvider.TAXI,
            35.0,
            shift.endTime!!,
            "",
            ReceiptUpload("receipt.jpg", byteArrayOf(1)),
        ) { completed = it }
        advanceUntilIdle()

        val claim = refundsRepo.observeClaimsForShift(shift.id).first().single()
        assertNull(claim.receiptPath)
        assertEquals(true, completed)
        assertTrue(vm.refundNotice.value?.contains("saved without") == true)
    }

    @Test
    fun `create shift uses selected compensation profile`() = runTest {
        seedSettings()
        val ilPreset = RegionPresets.forRegion(RegionCode.IL)
        val profile = CompensationProfile(
            id = "profile-b",
            userId = "u1",
            name = "Night rate",
            regionCode = RegionCode.IL,
            currencyCode = "ILS",
            timezone = "Asia/Jerusalem",
            baseHourlyRate = 55.0,
            rules = ilPreset.rules,
            stackingPolicy = ilPreset.stackingPolicy,
            isDefault = false,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        compensationRepo.setProfiles(profile)
        val vm = buildVm()

        vm.createShift(
            ShiftFormInput(
                startTime = Instant.parse("2024-01-08T09:00:00Z"),
                endTime = Instant.parse("2024-01-08T17:00:00Z"),
                breakMinutes = 0,
                notes = "",
                premiumProfileId = null,
                refundAction = null,
                compensationProfileId = "profile-b",
            ),
        )
        advanceUntilIdle()

        assertEquals("profile-b", shiftsRepo.currentShifts.single().compensationProfileId)
    }

    private fun specialDayShift() = Shift(
        id = "special",
        userId = "local-user",
        startTime = Instant.parse("2024-01-08T12:00:00Z"),
        endTime = Instant.parse("2024-01-08T17:00:00Z"),
        premiumProfileId = "premium-1",
        isSpecialDay = true,
    )
}
