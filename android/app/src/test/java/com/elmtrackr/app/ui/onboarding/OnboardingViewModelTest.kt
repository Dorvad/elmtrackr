package com.elmtrackr.app.ui.onboarding

import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.fake.FakeAuthRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settingsRepo = FakeSettingsRepository()
    private val authRepo = FakeAuthRepository()
    private var completionCalled = false

    private fun buildVm() = OnboardingViewModel(
        settingsRepository = settingsRepo,
        markOnboardingCompleted = { completionCalled = true },
        authRepository = authRepo,
    )

    private fun validInput(
        dailyOT: Int = 8,
        weeklyOT: Int = 40,
        hourlyRate: Double? = null,
        displayName: String = "",
    ) = OnboardingInput(
        displayName = displayName,
        dailyOvertimeHours = dailyOT,
        weeklyOvertimeHours = weeklyOT,
        hourlyRate = hourlyRate,
    )

    // ---- Initial state ----

    @Test
    fun `initial state is Welcome`() {
        assertEquals(OnboardingUiState.Welcome, buildVm().uiState.value)
    }

    // ---- Validation errors ----

    @Test
    fun `zero daily OT hours → ValidationError`() {
        val vm = buildVm()
        vm.completeOnboarding(validInput(dailyOT = 0))

        val state = vm.uiState.value
        assertTrue(state is OnboardingUiState.ValidationError)
        assertNotNull((state as OnboardingUiState.ValidationError).errors["dailyOT"])
    }

    @Test
    fun `zero weekly OT hours → ValidationError`() {
        val vm = buildVm()
        vm.completeOnboarding(validInput(weeklyOT = 0))

        val state = vm.uiState.value
        assertTrue(state is OnboardingUiState.ValidationError)
        assertNotNull((state as OnboardingUiState.ValidationError).errors["weeklyOT"])
    }

    @Test
    fun `weekly OT less than daily OT → ValidationError`() {
        val vm = buildVm()
        vm.completeOnboarding(validInput(dailyOT = 10, weeklyOT = 8))

        val state = vm.uiState.value
        assertTrue(state is OnboardingUiState.ValidationError)
        assertNotNull((state as OnboardingUiState.ValidationError).errors["weeklyOT"])
    }

    @Test
    fun `negative hourly rate → ValidationError`() {
        val vm = buildVm()
        vm.completeOnboarding(validInput(hourlyRate = -5.0))

        val state = vm.uiState.value
        assertTrue(state is OnboardingUiState.ValidationError)
        assertNotNull((state as OnboardingUiState.ValidationError).errors["hourlyRate"])
    }

    // ---- Save flow ----

    @Test
    fun `valid input → Completed state and onboarding marked`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<OnboardingUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.completeOnboarding(validInput())
        advanceUntilIdle()

        assertEquals(OnboardingUiState.Completed, states.last())
        assertTrue(completionCalled)
        job.cancel()
    }

    @Test
    fun `valid input saves settings with correct OT thresholds`() = runTest {
        val vm = buildVm()
        vm.completeOnboarding(validInput(dailyOT = 9, weeklyOT = 45))
        advanceUntilIdle()

        val saved = settingsRepo.getSettings("any")
        assertNotNull(saved)
        assertEquals(9 * 60, saved!!.dailyOvertimeThresholdMinutes)
        assertEquals(45 * 60, saved.weeklyOvertimeThresholdMinutes)
    }

    @Test
    fun `valid input saves hourly rate and weekend days`() = runTest {
        val vm = buildVm()
        vm.completeOnboarding(
            validInput(hourlyRate = 55.0).copy(weekendDays = listOf(5, 6))
        )
        advanceUntilIdle()

        val saved = settingsRepo.getSettings("any")
        assertNotNull(saved)
        assertEquals(55.0, saved!!.hourlyRate)
        assertEquals(listOf(5, 6), saved.weekendDays)
    }

    @Test
    fun `valid input saves feature toggles`() = runTest {
        val vm = buildVm()
        vm.completeOnboarding(
            validInput().copy(
                featuresTravelRefunds = true,
                featuresPaidProjects = false,
                featuresInsights = false,
                featuresClockStyles = true,
            )
        )
        advanceUntilIdle()

        val saved = settingsRepo.getSettings("any")
        assertNotNull(saved)
        assertTrue(saved!!.featuresTravelRefunds)
        assertEquals(false, saved.featuresPaidProjects)
        assertEquals(false, saved.featuresInsights)
        assertTrue(saved.featuresClockStyles)
    }

    @Test
    fun `display name saved to profile when signed in`() = runTest {
        authRepo.setProfile(
            Profile("u1", "test@test.com", null, Instant.EPOCH, Instant.EPOCH)
        )
        val vm = buildVm()
        vm.completeOnboarding(validInput(displayName = "Alice"))
        advanceUntilIdle()

        // FakeAuthRepository.saveProfile updates the stored profile
        assertEquals("Alice", authRepo.getCurrentProfile()?.fullName)
    }

    @Test
    fun `display name not required — completes without profile if not signed in`() = runTest {
        authRepo.setProfile(null)
        val vm = buildVm()
        vm.completeOnboarding(validInput(displayName = "Alice"))
        advanceUntilIdle()

        assertEquals(OnboardingUiState.Completed, vm.uiState.value)
    }
}
