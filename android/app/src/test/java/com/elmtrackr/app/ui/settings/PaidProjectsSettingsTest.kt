package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.fake.FakeAppLockPreferencesStore
import com.elmtrackr.app.fake.FakeAuthRepository
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeSyncRepository
import com.elmtrackr.app.fake.FakeSyncTrigger
import com.elmtrackr.app.fake.FakeThemePreferenceStore
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Turning Paid Projects on and off through Settings. The one-time update
 * wizard existing users see instead of onboarding lives on the dashboard —
 * see PaidProjectsWizardTriggerTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaidProjectsSettingsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeSettingsRepository()
    private val authRepo = FakeAuthRepository().apply {
        setProfile(Profile("u1", "test@example.com", null, Instant.EPOCH, Instant.EPOCH))
    }
    private fun buildVm() = SettingsViewModel(
        repo,
        authRepo,
        FakeCompensationProfilesRepository(),
        FakeThemePreferenceStore(),
        FakeSyncRepository(),
        FakeSyncTrigger(),
        FakeAppLockPreferencesStore(),
        com.elmtrackr.app.fake.FakeClockFacePreferences(),
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

    private fun SettingsViewModel.saveWithPaidProjects(
        enabled: Boolean,
        base: UserSettings,
    ) = saveSettings(
        displayName = "",
        dailyOtHours = base.dailyOvertimeThresholdMinutes / 60.0,
        weeklyOtHours = base.weeklyOvertimeThresholdMinutes / 60.0,
        hourlyRate = base.hourlyRate,
        timezone = base.timezone,
        clockStyle = base.clockStyle,
        currency = base.currency,
        weekendDays = base.weekendDays,
        featureFlags = SettingsFeatureFlags(
            travelRefunds = base.featuresTravelRefunds,
            paidProjects = enabled,
            insights = base.featuresInsights,
            clockStyles = base.featuresClockStyles,
            overtimeReminders = base.featuresOvertimeReminders,
        ),
    )

    // ── Enable / disable / re-enable ──────────────────────────────────────────

    @Test
    fun `enabling through settings persists the flag`() = runTest {
        val vm = buildVm()
        val base = existingUserSettings()
        repo.setSettings(base)
        advanceUntilIdle()

        vm.saveWithPaidProjects(enabled = true, base = base)
        advanceUntilIdle()

        assertTrue(repo.getSettings("u1")!!.featuresPaidProjects)
    }

    @Test
    fun `disabling through settings persists the flag`() = runTest {
        val vm = buildVm()
        val base = existingUserSettings(paidProjects = true)
        repo.setSettings(base)
        advanceUntilIdle()

        vm.saveWithPaidProjects(enabled = false, base = base)
        advanceUntilIdle()

        assertFalse(repo.getSettings("u1")!!.featuresPaidProjects)
    }

    @Test
    fun `re-enabling after a disable works and keeps the project defaults`() = runTest {
        val vm = buildVm()
        val base = existingUserSettings(paidProjects = true).copy(
            projectsDefaultRegionCode = RegionCode.IL,
            projectsDefaultCurrencyCode = "ILS",
            projectsTaxLabel = "VAT",
            projectsTaxRateBasisPoints = 1800,
            projectsTaxInclusive = true,
        )
        repo.setSettings(base)
        advanceUntilIdle()

        vm.saveWithPaidProjects(enabled = false, base = base)
        advanceUntilIdle()
        val afterDisable = repo.getSettings("u1")!!
        assertFalse(afterDisable.featuresPaidProjects)

        vm.saveWithPaidProjects(enabled = true, base = afterDisable)
        advanceUntilIdle()

        val afterReEnable = repo.getSettings("u1")!!
        assertTrue(afterReEnable.featuresPaidProjects)
        // Disabling hides the module; it must not discard its configuration.
        assertEquals(RegionCode.IL, afterReEnable.projectsDefaultRegionCode)
        assertEquals("ILS", afterReEnable.projectsDefaultCurrencyCode)
        assertEquals("VAT", afterReEnable.projectsTaxLabel)
        assertEquals(1800, afterReEnable.projectsTaxRateBasisPoints)
        assertTrue(afterReEnable.projectsTaxInclusive)
    }

    @Test
    fun `disabling leaves existing hours and pay data untouched`() = runTest {
        val vm = buildVm()
        val base = existingUserSettings(paidProjects = true)
        repo.setSettings(base)
        advanceUntilIdle()

        vm.saveWithPaidProjects(enabled = false, base = base)
        advanceUntilIdle()

        val saved = repo.getSettings("u1")!!
        assertEquals(base.hourlyRate, saved.hourlyRate)
        assertEquals(base.currency, saved.currency)
        assertEquals(base.weekendDays, saved.weekendDays)
        assertEquals(base.dailyOvertimeThresholdMinutes, saved.dailyOvertimeThresholdMinutes)
        assertEquals(base.weeklyOvertimeThresholdMinutes, saved.weeklyOvertimeThresholdMinutes)
        assertEquals(base.clockStyle, saved.clockStyle)
        assertTrue(saved.featuresTravelRefunds)
        assertTrue(saved.onboardingCompleted)
    }

}
