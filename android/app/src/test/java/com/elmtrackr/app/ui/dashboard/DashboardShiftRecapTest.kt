package com.elmtrackr.app.ui.dashboard

import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.fake.FakeAppPreferencesStore
import com.elmtrackr.app.fake.FakeAuthRepository
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakePremiumProfilesRepository
import com.elmtrackr.app.fake.FakeReportsRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeSetupChecklistPreferences
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.fake.FakeTasksRepository
import com.elmtrackr.app.fake.FakeWidgetPinInspector
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * The clock-out recap must be the payroll engine's answer, not a second estimate.
 *
 * [FakeShiftsRepository.clockOut] ends every shift at 2024-01-08 17:00 UTC (a
 * Monday), so a shift started at 08:00 the same day is a fixed nine hours.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardShiftRecapTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val shiftsRepo = FakeShiftsRepository()
    private val settingsRepo = FakeSettingsRepository()
    private val compensationRepo = FakeCompensationProfilesRepository()
    private val premiumRepo = FakePremiumProfilesRepository()
    private val authRepo = FakeAuthRepository().apply {
        setProfile(Profile("u1", "test@test.com", null, Instant.EPOCH, Instant.EPOCH))
    }

    private fun buildVm() = DashboardViewModel(
        shiftsRepo, settingsRepo, FakeReportsRepository(), authRepo, compensationRepo, FakeTasksRepository(),
        FakeAppPreferencesStore(), premiumRepo, FakeSetupChecklistPreferences(), FakeWidgetPinInspector(),
        com.elmtrackr.app.fake.FakeProjectsRepository(),
        com.elmtrackr.app.fake.FakeFeatureDiscoveryPreferences(),
        com.elmtrackr.app.fake.FakeReviewPromptRecorder(),
        computationDispatcher = mainDispatcherRule.dispatcher,
    )

    private fun settings(rate: Double?) = UserSettings(
        "cfg", "u1", hourlyRate = rate, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private val start = Instant.parse("2024-01-08T08:00:00Z")

    private fun activeShift(
        breakMinutes: Int = 0,
        source: CompensationSource = CompensationSource.EMPLOYEE,
    ) = Shift(
        id = "s1",
        userId = "u1",
        startTime = start,
        endTime = null,
        breakMinutes = breakMinutes,
        compensationSource = source,
        projectId = if (source.isProjectTime) "proj" else null,
    )

    @Test
    fun `the recap shows the engine's pay for the shift that just ended`() = runTest {
        settingsRepo.setSettings(settings(rate = 100.0))
        shiftsRepo.setShifts(activeShift(breakMinutes = 30))
        val vm = buildVm()

        vm.clockOut("s1")
        advanceUntilIdle()

        val recap = vm.shiftRecap.value
        assertNotNull(recap)
        val saved = shiftsRepo.observeShifts("u1").first().single()
        val expected = PayrollCalculator.calculateShiftPayInContext(
            saved, listOf(saved), settings(100.0),
        )!!
        assertEquals(expected.totalGross, recap!!.pay!!.totalGross, 0.0001)
        assertEquals(expected.overtimeGross, recap.pay!!.overtimeGross, 0.0001)
        assertEquals(expected.currencyCode, recap.pay!!.currencyCode)
    }

    @Test
    fun `the recap separates shift length, break and worked time`() = runTest {
        settingsRepo.setSettings(settings(rate = 100.0))
        shiftsRepo.setShifts(activeShift(breakMinutes = 30))
        val vm = buildVm()

        vm.clockOut("s1")
        advanceUntilIdle()

        val time = vm.shiftRecap.value!!.time!!
        assertEquals(540, time.elapsedMinutes)
        assertEquals(30, time.unpaidBreakMinutes)
        assertEquals(510, time.workedMinutes)
    }

    @Test
    fun `without a rate the recap has time but no invented pay`() = runTest {
        settingsRepo.setSettings(settings(rate = null))
        shiftsRepo.setShifts(activeShift())
        val vm = buildVm()

        vm.clockOut("s1")
        advanceUntilIdle()

        val recap = vm.shiftRecap.value!!
        assertNull(recap.pay)
        assertEquals(540, recap.time!!.workedMinutes)
    }

    @Test
    fun `a project shift gets the project summary, not a wage recap`() = runTest {
        settingsRepo.setSettings(settings(rate = 100.0))
        shiftsRepo.setShifts(activeShift(source = CompensationSource.PROJECT))
        val vm = buildVm()

        vm.clockOut("s1")
        advanceUntilIdle()

        assertNull(vm.shiftRecap.value)
    }

    @Test
    fun `dismissing and clocking in again both clear the recap`() = runTest {
        settingsRepo.setSettings(settings(rate = 100.0))
        shiftsRepo.setShifts(activeShift())
        val vm = buildVm()

        vm.clockOut("s1")
        advanceUntilIdle()
        assertNotNull(vm.shiftRecap.value)
        vm.dismissShiftRecap()
        assertNull(vm.shiftRecap.value)

        shiftsRepo.setShifts(activeShift().copy(id = "s2"))
        vm.clockOut("s2")
        advanceUntilIdle()
        assertNotNull(vm.shiftRecap.value)
        vm.clockIn()
        advanceUntilIdle()
        assertNull(vm.shiftRecap.value)
    }
}
