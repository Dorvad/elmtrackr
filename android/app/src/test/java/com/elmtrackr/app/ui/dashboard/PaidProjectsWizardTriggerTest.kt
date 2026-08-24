package com.elmtrackr.app.ui.dashboard

import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.fake.FakeAppPreferencesStore
import com.elmtrackr.app.fake.FakeAuthRepository
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakeFeatureDiscoveryPreferences
import com.elmtrackr.app.fake.FakePremiumProfilesRepository
import com.elmtrackr.app.fake.FakeProjectsRepository
import com.elmtrackr.app.fake.FakeReportsRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeSetupChecklistPreferences
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.fake.FakeTasksRepository
import com.elmtrackr.app.fake.FakeWidgetPinInspector
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * When the v1.2 update wizard appears over the main screen, and what its two
 * exits do. Existing users are never sent back through onboarding, so the
 * dashboard pop-up is how they find the Paid Projects module; it appears once
 * and stays gone after either choice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaidProjectsWizardTriggerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val shiftsRepo = FakeShiftsRepository()
    private val settingsRepo = FakeSettingsRepository()
    private val reportsRepo = FakeReportsRepository()
    private val compensationRepo = FakeCompensationProfilesRepository()
    private val tasksRepo = FakeTasksRepository()
    private val projectsRepo = FakeProjectsRepository()
    private val authRepo = FakeAuthRepository().apply {
        setProfile(Profile("u1", "test@test.com", null, Instant.EPOCH, Instant.EPOCH))
    }
    private val appPrefs = FakeAppPreferencesStore()
    private val premiumRepo = FakePremiumProfilesRepository()
    private val setupPrefs = FakeSetupChecklistPreferences()
    private val widgetPin = FakeWidgetPinInspector()
    private val discoveryPrefs = FakeFeatureDiscoveryPreferences()
    private val reviewRecorder = com.elmtrackr.app.fake.FakeReviewPromptRecorder()

    private fun buildVm() = DashboardViewModel(
        shiftsRepo, settingsRepo, reportsRepo, authRepo, compensationRepo, tasksRepo, appPrefs,
        premiumRepo, setupPrefs, widgetPin, projectsRepo, discoveryPrefs, reviewRecorder,
        // The production flowOn dispatcher would run the payroll transform off the
        // test scheduler, so advanceUntilIdle could not await its emissions.
        computationDispatcher = mainDispatcherRule.dispatcher,
    )

    /** An existing user: onboarding done, hours and pay already configured. */
    private fun existingUserSettings(paidProjects: Boolean = false) = UserSettings(
        id = "s1",
        userId = "u1",
        hourlyRate = 62.5,
        currency = CurrencyCode.ILS,
        weekendDays = listOf(5, 6),
        dailyOvertimeThresholdMinutes = 516,
        weeklyOvertimeThresholdMinutes = 2520,
        onboardingCompleted = true,
        onboardingCompletedAt = Instant.EPOCH,
        featuresTravelRefunds = true,
        featuresPaidProjects = paidProjects,
        clockStyle = ClockStyle.AURORA,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun readyStates(states: List<DashboardUiState>) =
        states.filterIsInstance<DashboardUiState.Ready>()

    @Test
    fun `existing user with the feature off sees the wizard on the dashboard`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        settingsRepo.setSettings(existingUserSettings())
        advanceUntilIdle()

        assertTrue(readyStates(states).last().showPaidProjectsWizard)
        job.cancel()
    }

    @Test
    fun `a user who already has the module on never sees the wizard`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        settingsRepo.setSettings(existingUserSettings(paidProjects = true))
        advanceUntilIdle()

        assertFalse(readyStates(states).last().showPaidProjectsWizard)
        job.cancel()
    }

    @Test
    fun `a user still in onboarding is not shown the wizard`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        settingsRepo.setSettings(
            existingUserSettings().copy(onboardingCompleted = false, onboardingCompletedAt = null),
        )
        advanceUntilIdle()

        assertFalse(readyStates(states).last().showPaidProjectsWizard)
        job.cancel()
    }

    @Test
    fun `enabling from the wizard sets the flag and retires the wizard`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        settingsRepo.setSettings(existingUserSettings())
        advanceUntilIdle()

        vm.enablePaidProjectsFromWizard()
        advanceUntilIdle()

        assertTrue(settingsRepo.getSettings("u1")!!.featuresPaidProjects)
        assertTrue(discoveryPrefs.paidProjectsDiscoveryDismissed)
        assertFalse(readyStates(states).last().showPaidProjectsWizard)
        job.cancel()
    }

    @Test
    fun `dismissing the wizard does not enable the feature`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        settingsRepo.setSettings(existingUserSettings())
        advanceUntilIdle()

        vm.dismissPaidProjectsWizard()
        advanceUntilIdle()

        assertFalse(settingsRepo.getSettings("u1")!!.featuresPaidProjects)
        assertTrue(discoveryPrefs.paidProjectsDiscoveryDismissed)
        assertFalse(readyStates(states).last().showPaidProjectsWizard)
        job.cancel()
    }

    @Test
    fun `enabling from the wizard changes nothing else`() = runTest {
        val vm = buildVm()
        val base = existingUserSettings()
        settingsRepo.setSettings(base)
        advanceUntilIdle()

        vm.enablePaidProjectsFromWizard()
        advanceUntilIdle()

        val saved = settingsRepo.getSettings("u1")!!
        assertEquals(base.copy(featuresPaidProjects = true, updatedAt = saved.updatedAt), saved)
    }
}
