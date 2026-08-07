package com.elmtrackr.app.ui.onboarding

import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakeAuthRepository
import com.elmtrackr.app.fake.FakeAppLockPreferencesStore
import com.elmtrackr.app.fake.FakeOnboardingPreferences
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = ScreenshotTestApplication::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settingsRepo = FakeSettingsRepository()
    private val authRepo = FakeAuthRepository().apply {
        setProfile(Profile("u1", "test@test.com", null, Instant.EPOCH, Instant.EPOCH))
    }
    private val compensationRepo = FakeCompensationProfilesRepository()
    private val onboardingPrefs = FakeOnboardingPreferences()
    private val appLockPrefs = FakeAppLockPreferencesStore()
    private val discoveryPrefs = com.elmtrackr.app.fake.FakeFeatureDiscoveryPreferences()
    private val checklistPrefs = com.elmtrackr.app.fake.FakeSetupChecklistPreferences()

    private fun buildVm() = OnboardingViewModel(
        settingsRepository = settingsRepo,
        compensationProfilesRepository = compensationRepo,
        appPreferences = onboardingPrefs,
        appLockPreferences = appLockPrefs,
        authRepository = authRepo,
        featureDiscoveryPreferences = discoveryPrefs,
        setupChecklistPreferences = checklistPrefs,
    )

    private fun validInput(
        dailyOT: Int = 8,
        weeklyOT: Int = 40,
        hourlyRate: Double? = null,
        displayName: String = "Test User",
    ) = OnboardingInput(
        displayName = displayName,
        dailyOvertimeHours = dailyOT.toDouble(),
        weeklyOvertimeHours = weeklyOT.toDouble(),
        hourlyRate = hourlyRate,
    )

    @Test
    fun `initial state is Welcome`() {
        assertEquals(OnboardingUiState.Welcome, buildVm().uiState.value)
    }

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

    @Test
    /**
     * A display name feeds the dashboard greeting and nothing else, so requiring
     * one made a cosmetic field a gate on finishing setup and using the app. The
     * setup checklist asks for it afterwards instead.
     */
    fun `a blank display name does not block finishing setup`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<OnboardingUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.completeOnboarding(validInput(displayName = ""))
        advanceUntilIdle()

        assertEquals(OnboardingUiState.Completed, states.last())
        job.cancel()
    }

    @Test
    fun `valid input → Completed state and onboarding marked`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<OnboardingUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.completeOnboarding(validInput())
        advanceUntilIdle()

        assertEquals(OnboardingUiState.Completed, states.last())
        assertTrue(onboardingPrefs.onboardingCompleted)
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
            validInput(hourlyRate = 55.0).copy(weekendDays = listOf(5, 6), currency = CurrencyCode.EUR)
        )
        advanceUntilIdle()

        val saved = settingsRepo.getSettings("any")
        assertNotNull(saved)
        assertEquals(55.0, saved!!.hourlyRate)
        assertEquals(listOf(5, 6), saved.weekendDays)
        assertEquals(CurrencyCode.EUR, saved.currency)
    }

    @Test
    fun `replay onboarding updates existing compensation profile instead of duplicating`() = runTest {
        val existingProfile = com.elmtrackr.app.domain.compensation.CompensationResolver.createFromPreset(
            userId = "u1",
            regionCode = com.elmtrackr.app.domain.model.RegionCode.IL,
            baseHourlyRate = 30.0,
        ).copy(id = "profile-1")
        compensationRepo.setProfiles(existingProfile)
        settingsRepo.setSettings(
            UserSettings(
                id = "settings",
                userId = "u1",
                hourlyRate = 30.0,
                defaultCompensationProfileId = "profile-1",
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
        val vm = buildVm()

        vm.completeOnboarding(
            validInput(hourlyRate = 80.0).copy(
                regionCode = com.elmtrackr.app.domain.model.RegionCode.US,
                preserveExisting = true,
            ),
        )
        advanceUntilIdle()

        val profiles = compensationRepo.getProfiles("u1")
        assertEquals(1, profiles.size)
        assertEquals("profile-1", profiles.first().id)
        assertEquals(com.elmtrackr.app.domain.model.RegionCode.US, profiles.first().regionCode)
        assertEquals(80.0, profiles.first().baseHourlyRate)
    }

    @Test
    fun `replay onboarding preserves clock style and unshown feature flags`() = runTest {
        settingsRepo.setSettings(
            UserSettings(
                id = "settings",
                userId = "u1",
                hourlyRate = 30.0,
                clockStyle = com.elmtrackr.app.domain.model.ClockStyle.AURORA,
                featuresClockStyles = false,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
        val vm = buildVm()

        vm.completeOnboarding(validInput(hourlyRate = 80.0).copy(preserveExisting = true))
        advanceUntilIdle()

        val saved = settingsRepo.getSettings("u1")
        // The wizard still has no steps for these, so replay must not reset them.
        assertEquals(com.elmtrackr.app.domain.model.ClockStyle.AURORA, saved?.clockStyle)
        assertEquals(false, saved?.featuresClockStyles)
    }

    @Test
    fun `replay onboarding keeps paid projects on when the user answers yes again`() = runTest {
        // The wizard has an explicit Paid Projects step that the screen seeds
        // from stored settings, so on replay the input carries the answer.
        settingsRepo.setSettings(
            UserSettings(
                id = "settings",
                userId = "u1",
                featuresPaidProjects = true,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
        val vm = buildVm()

        vm.completeOnboarding(
            validInput().copy(preserveExisting = true, featuresPaidProjects = true),
        )
        advanceUntilIdle()

        assertEquals(true, settingsRepo.getSettings("u1")?.featuresPaidProjects)
    }

    @Test
    fun `replay onboarding can turn paid projects off`() = runTest {
        settingsRepo.setSettings(
            UserSettings(
                id = "settings",
                userId = "u1",
                featuresPaidProjects = true,
                projectsTaxLabel = "VAT",
                projectsTaxRateBasisPoints = 1800,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
        val vm = buildVm()

        vm.completeOnboarding(
            validInput().copy(preserveExisting = true, featuresPaidProjects = false),
        )
        advanceUntilIdle()

        val saved = settingsRepo.getSettings("u1")!!
        assertEquals(false, saved.featuresPaidProjects)
        // Turning the module off hides it; the configuration survives.
        assertEquals("VAT", saved.projectsTaxLabel)
        assertEquals(1800, saved.projectsTaxRateBasisPoints)
    }

    @Test
    fun `new user enabling paid projects stores the flag and the defaults`() = runTest {
        val vm = buildVm()

        vm.completeOnboarding(
            validInput().copy(
                featuresPaidProjects = true,
                projectsDefaultRegionCode = com.elmtrackr.app.domain.model.RegionCode.IL,
                projectsDefaultCurrencyCode = "ILS",
                projectsTaxLabel = "VAT",
                projectsTaxRateBasisPoints = 1800,
                projectsTaxInclusive = true,
            ),
        )
        advanceUntilIdle()

        val saved = settingsRepo.getSettings("u1")!!
        assertEquals(true, saved.featuresPaidProjects)
        assertEquals(com.elmtrackr.app.domain.model.RegionCode.IL, saved.projectsDefaultRegionCode)
        assertEquals("ILS", saved.projectsDefaultCurrencyCode)
        assertEquals("VAT", saved.projectsTaxLabel)
        assertEquals(1800, saved.projectsTaxRateBasisPoints)
        assertEquals(true, saved.projectsTaxInclusive)
        assertEquals(true, saved.projectsTaxEnabled)
    }

    @Test
    fun `new user enabling paid projects but skipping tax stores tax as off`() = runTest {
        val vm = buildVm()

        vm.completeOnboarding(
            validInput().copy(
                featuresPaidProjects = true,
                projectsDefaultRegionCode = com.elmtrackr.app.domain.model.RegionCode.IL,
                projectsDefaultCurrencyCode = "ILS",
                // Skipped: no label, no rate, no preference.
            ),
        )
        advanceUntilIdle()

        val saved = settingsRepo.getSettings("u1")!!
        assertEquals(true, saved.featuresPaidProjects)
        assertEquals(null, saved.projectsTaxLabel)
        assertEquals(0, saved.projectsTaxRateBasisPoints)
        assertEquals(false, saved.projectsTaxInclusive)
        assertEquals(false, saved.projectsTaxEnabled)
    }

    @Test
    fun `new user skipping paid projects leaves the module off`() = runTest {
        val vm = buildVm()

        vm.completeOnboarding(validInput().copy(featuresPaidProjects = false))
        advanceUntilIdle()

        val saved = settingsRepo.getSettings("u1")!!
        assertEquals(false, saved.featuresPaidProjects)
        assertEquals(null, saved.projectsDefaultRegionCode)
        assertEquals(0, saved.projectsTaxRateBasisPoints)
        // Declining is not a failure: onboarding still completes.
        assertEquals(true, saved.onboardingCompleted)
    }

    @Test
    fun `replay onboarding updates core work preferences`() = runTest {
        settingsRepo.setSettings(
            UserSettings(id = "settings", userId = "u1", hourlyRate = 30.0, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        )
        val vm = buildVm()

        vm.completeOnboarding(
            validInput(hourlyRate = 80.0).copy(
                weekendDays = listOf(0, 6),
                currency = CurrencyCode.USD,
                preserveExisting = true,
            )
        )
        advanceUntilIdle()

        val saved = settingsRepo.getSettings("u1")!!
        assertEquals(80.0, saved.hourlyRate)
        assertEquals(listOf(0, 6), saved.weekendDays)
        assertEquals(CurrencyCode.USD, saved.currency)
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

        assertEquals("Alice", authRepo.getCurrentProfile()?.fullName)
    }

    @Test
    fun `valid input with app lock enabled persists preference`() = runTest {
        val vm = buildVm()
        vm.completeOnboarding(validInput().copy(enableAppLock = true))
        advanceUntilIdle()

        assertTrue(appLockPrefs.preferences.first().appLockEnabled)
    }

    @Test
    fun `onboarding requires an authenticated profile`() = runTest {
        authRepo.setProfile(null)
        val vm = buildVm()
        vm.completeOnboarding(validInput(displayName = "Alice"))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is OnboardingUiState.ValidationError)
        assertNotNull((state as OnboardingUiState.ValidationError).errors["save"])
    }

    @Test
    fun `daily OT above 24 hours → ValidationError`() {
        val vm = buildVm()
        vm.completeOnboarding(validInput(dailyOT = 25, weeklyOT = 100))

        val state = vm.uiState.value
        assertTrue(state is OnboardingUiState.ValidationError)
        assertNotNull((state as OnboardingUiState.ValidationError).errors["dailyOT"])
    }

    @Test
    fun `weekly OT above 168 hours → ValidationError`() {
        val vm = buildVm()
        vm.completeOnboarding(validInput(dailyOT = 8, weeklyOT = 169))

        val state = vm.uiState.value
        assertTrue(state is OnboardingUiState.ValidationError)
        assertNotNull((state as OnboardingUiState.ValidationError).errors["weeklyOT"])
    }

    @Test
    fun `replay can turn app lock off`() = runTest {
        appLockPrefs.setAppLockEnabled(true)
        val vm = buildVm()
        vm.completeOnboarding(validInput().copy(preserveExisting = true, enableAppLock = false))
        advanceUntilIdle()

        assertFalse(appLockPrefs.preferences.first().appLockEnabled)
    }

    @Test
    fun `completing onboarding satisfies paid projects discovery`() = runTest {
        // The wizard just asked the Paid Projects question, so the dashboard
        // discovery modal must never re-ask — whatever the answer was.
        val vm = buildVm()
        vm.completeOnboarding(validInput())
        advanceUntilIdle()

        assertTrue(discoveryPrefs.paidProjectsDiscoveryDismissed)
    }

    @Test
    fun `hourly rate entered in onboarding pre-ticks the compensation checklist step`() = runTest {
        val vm = buildVm()
        vm.completeOnboarding(validInput(hourlyRate = 55.0))
        advanceUntilIdle()

        assertTrue(
            com.elmtrackr.app.domain.setup.SetupStep.COMPENSATION.key in
                checklistPrefs.current.setupChecklistVisitedSteps,
        )
    }

    @Test
    fun `skipping the rate leaves the compensation checklist step untouched`() = runTest {
        val vm = buildVm()
        vm.completeOnboarding(validInput(hourlyRate = null))
        advanceUntilIdle()

        assertTrue(checklistPrefs.current.setupChecklistVisitedSteps.isEmpty())
    }
}
