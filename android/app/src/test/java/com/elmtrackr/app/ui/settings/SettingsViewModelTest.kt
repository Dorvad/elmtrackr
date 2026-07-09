package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.fake.FakeAppLockPreferencesStore
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakeAuthRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeSyncRepository
import com.elmtrackr.app.fake.FakeSyncTrigger
import com.elmtrackr.app.fake.FakeThemePreferenceStore
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
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeSettingsRepository()
    private val authRepo = FakeAuthRepository().apply {
        setProfile(Profile("u1", "test@example.com", null, Instant.EPOCH, Instant.EPOCH))
    }
    private val themeStore = FakeThemePreferenceStore()
    private val compensationRepo = FakeCompensationProfilesRepository()
    private val syncRepo = FakeSyncRepository()
    private val syncTrigger = FakeSyncTrigger()
    private val appLockPrefs = FakeAppLockPreferencesStore()

    private fun buildVm() = SettingsViewModel(
        repo,
        authRepo,
        compensationRepo,
        themeStore,
        syncRepo,
        syncTrigger,
        appLockPrefs,
    )

    private fun defaultSettings() = UserSettings(
        id = "s1",
        userId = "u1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Test
    fun `initial state is Loading`() {
        assertEquals(SettingsUiState.Loading, buildVm().uiState.value)
    }

    @Test
    fun `Ready state when settings exist`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        repo.setSettings(defaultSettings())
        advanceUntilIdle()

        val ready = states.filterIsInstance<SettingsUiState.Ready>().lastOrNull()
        assertNotNull(ready)
        assertEquals("s1", ready!!.settings.id)
        job.cancel()
    }

    @Test
    fun `ensureSettingsExist creates defaults when no settings`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.ensureSettingsExist()
        advanceUntilIdle()

        val ready = states.filterIsInstance<SettingsUiState.Ready>().lastOrNull()
        assertNotNull(ready)
        assertEquals("u1", ready!!.settings.userId)
        job.cancel()
    }

    // ── saveSettings ──────────────────────────────────────────────────────────

    @Test
    fun `saveSettings converts hours to minutes`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        advanceUntilIdle()

        vm.saveSettings("", 10.0, 50.0, null, "UTC", ClockStyle.CLASSIC, weekendDays = emptyList())
        advanceUntilIdle()

        val saved = repo.getSettings("u1")
        assertEquals(600, saved?.dailyOvertimeThresholdMinutes)
        assertEquals(3000, saved?.weeklyOvertimeThresholdMinutes)
    }

    @Test
    fun `saveSettings updates hourly rate`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        advanceUntilIdle()

        vm.saveSettings("", 8.0, 40.0, 75.5, "UTC", ClockStyle.CLASSIC, weekendDays = emptyList())
        advanceUntilIdle()

        assertEquals(75.5, repo.getSettings("u1")?.hourlyRate)
    }

    @Test
    fun `saveSettings clears hourly rate when null`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings().copy(hourlyRate = 50.0))
        advanceUntilIdle()

        vm.saveSettings("", 8.0, 40.0, null, "UTC", ClockStyle.CLASSIC, weekendDays = emptyList())
        advanceUntilIdle()

        assertEquals(null, repo.getSettings("u1")?.hourlyRate)
    }

    @Test
    fun `saveSettings updates currency`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        advanceUntilIdle()

        vm.saveSettings("", 8.0, 40.0, 50.0, "UTC", ClockStyle.CLASSIC, CurrencyCode.EUR, weekendDays = emptyList())
        advanceUntilIdle()

        assertEquals(CurrencyCode.EUR, repo.getSettings("u1")?.currency)
    }

    @Test
    fun `saveSettings persists feature flags`() = runTest {
        val vm = buildVm()
        repo.setSettings(
            defaultSettings().copy(
                featuresTravelRefunds = false,
                featuresPaidProjects = false,
                featuresInsights = false,
                featuresClockStyles = true,
            ),
        )
        advanceUntilIdle()

        vm.saveSettings(
            "",
            8.0,
            40.0,
            null,
            "UTC",
            ClockStyle.CLASSIC,
            weekendDays = emptyList(),
            featureFlags = SettingsFeatureFlags(
                travelRefunds = true,
                paidProjects = true,
                insights = true,
                clockStyles = false,
                overtimeReminders = true,
            ),
        )
        advanceUntilIdle()

        val saved = repo.getSettings("u1")
        assertEquals(true, saved?.featuresTravelRefunds)
        assertEquals(true, saved?.featuresPaidProjects)
        assertEquals(true, saved?.featuresInsights)
        assertEquals(false, saved?.featuresClockStyles)
    }

    @Test
    fun `saveSettings updates display name in profile`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        authRepo.setProfile(Profile("u1", "test@example.com", null, Instant.EPOCH, Instant.EPOCH))
        advanceUntilIdle()

        vm.saveSettings("Alice", 8.0, 40.0, null, "UTC", ClockStyle.CLASSIC, weekendDays = emptyList())
        advanceUntilIdle()

        assertEquals("Alice", authRepo.getCurrentProfile()?.fullName)
    }

    @Test
    fun `saveSettings does not update name when unchanged`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        authRepo.setProfile(Profile("u1", "test@example.com", "Alice", Instant.EPOCH, Instant.EPOCH))
        advanceUntilIdle()

        vm.saveSettings("Alice", 8.0, 40.0, null, "UTC", ClockStyle.CLASSIC, weekendDays = emptyList())
        advanceUntilIdle()

        assertEquals("Alice", authRepo.getCurrentProfile()?.fullName)
    }

    // ── validate ──────────────────────────────────────────────────────────────

    @Test
    fun `validate zero daily OT returns error`() {
        assertTrue("dailyOt" in buildVm().validate(0.0, 40.0, null))
    }

    @Test
    fun `validate daily OT over 24 returns error`() {
        assertTrue("dailyOt" in buildVm().validate(25.0, 40.0, null))
    }

    @Test
    fun `validate zero weekly OT returns error`() {
        assertTrue("weeklyOt" in buildVm().validate(8.0, 0.0, null))
    }

    @Test
    fun `validate weekly OT over 168 returns error`() {
        assertTrue("weeklyOt" in buildVm().validate(8.0, 200.0, null))
    }

    @Test
    fun `validate weekly OT less than daily returns error`() {
        assertTrue("weeklyOt" in buildVm().validate(10.0, 9.0, null))
    }

    @Test
    fun `validate negative hourly rate returns error`() {
        assertTrue("hourlyRate" in buildVm().validate(8.0, 40.0, -1.0))
    }

    @Test
    fun `validate with valid inputs returns no errors`() {
        assertTrue(buildVm().validate(8.0, 40.0, 50.0).isEmpty())
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    @Test
    fun `saveTheme reflected in Ready state`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        vm.saveTheme("dark")
        advanceUntilIdle()

        val ready = states.filterIsInstance<SettingsUiState.Ready>().lastOrNull()
        assertEquals("dark", ready?.selectedTheme)
        job.cancel()
    }

    // ── Save feedback ─────────────────────────────────────────────────────────

    @Test
    fun `saveSettings emits success feedback`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        vm.saveSettings("", 8.0, 40.0, null, "UTC", ClockStyle.CLASSIC, weekendDays = emptyList())
        advanceUntilIdle()

        val ready = states.filterIsInstance<SettingsUiState.Ready>().lastOrNull()
        assertNotNull(ready)
        assertEquals(UiText.Res(R.string.settings_feedback_saved), ready!!.saveFeedback?.message)
        assertEquals(false, ready.saveFeedback?.isError)
        job.cancel()
    }

    @Test
    fun `saveSettings emits validation feedback`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        vm.saveSettings("", 0.0, 40.0, null, "UTC", ClockStyle.CLASSIC, weekendDays = emptyList())
        advanceUntilIdle()

        val ready = states.filterIsInstance<SettingsUiState.Ready>().lastOrNull()
        assertNotNull(ready)
        assertTrue(ready!!.saveFeedback?.isError == true)
        assertTrue(ready.validationErrors.containsKey("dailyOt"))
        job.cancel()
    }

    @Test
    fun `clearSaveFeedback clears feedback`() = runTest {
        val vm = buildVm()
        repo.setSettings(defaultSettings())
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        vm.saveSettings("", 8.0, 40.0, null, "UTC", ClockStyle.CLASSIC, weekendDays = emptyList())
        advanceUntilIdle()
        vm.clearSaveFeedback()
        advanceUntilIdle()

        val ready = states.filterIsInstance<SettingsUiState.Ready>().lastOrNull()
        assertNotNull(ready)
        assertEquals(null, ready!!.saveFeedback)
        job.cancel()
    }

    // ── Account ───────────────────────────────────────────────────────────────

    @Test
    fun `saveSettings propagates currency to default compensation profile`() = runTest {
        val profile = CompensationProfile(
            id = "cp1",
            userId = "u1",
            name = "Main job",
            regionCode = RegionCode.IL,
            currencyCode = "ILS",
            timezone = "Asia/Jerusalem",
            baseHourlyRate = 50.0,
            rules = CompensationRules(),
            stackingPolicy = StackingPolicy.HIGHEST_ONLY,
            isDefault = true,
        )
        compensationRepo.setProfiles(profile)
        val vm = buildVm()
        repo.setSettings(defaultSettings().copy(hourlyRate = 50.0, currency = CurrencyCode.ILS))

        vm.saveSettings(
            displayName = "",
            dailyOtHours = 8.0,
            weeklyOtHours = 40.0,
            hourlyRate = 55.0,
            timezone = "Asia/Jerusalem",
            clockStyle = ClockStyle.CLASSIC,
            currency = CurrencyCode.USD,
            weekendDays = listOf(5, 6),
        )
        advanceUntilIdle()

        val saved = compensationRepo.getProfiles("u1").first()
        assertEquals("USD", saved.currencyCode)
        assertEquals(55.0, saved.baseHourlyRate)
    }

    @Test
    fun `resetPassword does not crash when no profile`() = runTest {
        val vm = buildVm()
        vm.resetPassword()
        advanceUntilIdle()
        // early return — no exception expected
    }
}
