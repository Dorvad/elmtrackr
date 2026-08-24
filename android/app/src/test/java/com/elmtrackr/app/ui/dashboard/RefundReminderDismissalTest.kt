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
import com.elmtrackr.app.data.local.preferences.AppPreferenceValues
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
import java.time.YearMonth
import java.time.ZoneId

/**
 * The refund reminder's dismissal used to be a `rememberSaveable` on the
 * dashboard, so "not now" lasted until the process died and the nudge came back
 * on the next launch — the same month, the same claims, nothing changed.
 *
 * It is now a stored month, which has to satisfy two things at once: it silences
 * the reminder for the month it was dismissed in, and it lets the reminder come
 * back when the next month's claims arrive.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RefundReminderDismissalTest {

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
    private val reviewRecorder = com.elmtrackr.app.fake.FakeReviewPromptRecorder()

    private fun buildVm(discoveryPrefs: FakeFeatureDiscoveryPreferences) = DashboardViewModel(
        shiftsRepo, settingsRepo, reportsRepo, authRepo, compensationRepo, tasksRepo, appPrefs,
        premiumRepo, setupPrefs, widgetPin, projectsRepo, discoveryPrefs, reviewRecorder,
        // The production flowOn dispatcher would run the payroll transform off the
        // test scheduler, so advanceUntilIdle could not await its emissions.
        computationDispatcher = mainDispatcherRule.dispatcher,
    )

    private val workZone = ZoneId.of("Asia/Jerusalem")

    private fun settings() = UserSettings(
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
        timezone = workZone.id,
        clockStyle = ClockStyle.AURORA,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun readyStates(states: List<DashboardUiState>) =
        states.filterIsInstance<DashboardUiState.Ready>()

    private fun vmWith(storedMonth: String?): Pair<DashboardViewModel, FakeFeatureDiscoveryPreferences> {
        val prefs = FakeFeatureDiscoveryPreferences(
            AppPreferenceValues(refundReminderDismissedMonth = storedMonth),
        )
        return buildVm(prefs) to prefs
    }

    @Test
    fun `a fresh install has not dismissed the reminder`() = runTest {
        val (vm, _) = vmWith(null)
        val states = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        settingsRepo.setSettings(settings())
        advanceUntilIdle()

        assertFalse(readyStates(states).last().refundReminderDismissed)
        job.cancel()
    }

    @Test
    fun `dismissing records the current work month`() = runTest {
        val (vm, prefs) = vmWith(null)
        val states = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        settingsRepo.setSettings(settings())
        advanceUntilIdle()

        vm.dismissRefundReminder()
        advanceUntilIdle()

        assertEquals(YearMonth.now(workZone).toString(), prefs.refundReminderDismissedMonth)
        assertTrue(readyStates(states).last().refundReminderDismissed)
        job.cancel()
    }

    /**
     * The behaviour the old in-memory flag could not express. A dismissal made
     * last month says nothing about this month's unresolved claims.
     */
    @Test
    fun `last month's dismissal does not silence this month`() = runTest {
        val (vm, _) = vmWith(YearMonth.now(workZone).minusMonths(1).toString())
        val states = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        settingsRepo.setSettings(settings())
        advanceUntilIdle()

        assertFalse(readyStates(states).last().refundReminderDismissed)
        job.cancel()
    }

    /** The state a relaunch produces: the store still holds this month. */
    @Test
    fun `a dismissal made this month survives a rebuild of the view model`() = runTest {
        val (vm, _) = vmWith(YearMonth.now(workZone).toString())
        val states = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        settingsRepo.setSettings(settings())
        advanceUntilIdle()

        assertTrue(readyStates(states).last().refundReminderDismissed)
        job.cancel()
    }

    /**
     * Nothing here may throw on the dashboard's hot path — the whole screen is one
     * flow, so a parse failure would surface as [DashboardUiState.Error].
     */
    @Test
    fun `an unreadable stored month leaves the reminder armed rather than erroring`() = runTest {
        val (vm, _) = vmWith("not-a-month")
        val states = mutableListOf<DashboardUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        settingsRepo.setSettings(settings())
        advanceUntilIdle()

        assertTrue(states.none { it is DashboardUiState.Error })
        assertFalse(readyStates(states).last().refundReminderDismissed)
        job.cancel()
    }
}
