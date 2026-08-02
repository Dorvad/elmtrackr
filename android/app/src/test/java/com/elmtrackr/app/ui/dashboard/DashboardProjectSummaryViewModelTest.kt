package com.elmtrackr.app.ui.dashboard

import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import com.elmtrackr.app.fake.FakeAppPreferencesStore
import com.elmtrackr.app.fake.FakeAuthRepository
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakePremiumProfilesRepository
import com.elmtrackr.app.fake.FakeProjectsRepository
import com.elmtrackr.app.fake.FakeReportsRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeSetupChecklistPreferences
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.fake.FakeTasksRepository
import com.elmtrackr.app.fake.FakeWidgetPinInspector
import com.elmtrackr.app.navigation.BottomNavItem
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * The project block on the dashboard, and the guarantee that an hourly-only user's
 * dashboard is unchanged.
 *
 * The pay summary and the project figures are checked to coexist without either
 * absorbing the other: hourly earnings are work performed this month, project
 * payments are cash received, and the state keeps them in separate fields.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardProjectSummaryViewModelTest {

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

    private val discoveryPrefs = com.elmtrackr.app.fake.FakeFeatureDiscoveryPreferences()
    private val reviewRecorder = com.elmtrackr.app.fake.FakeReviewPromptRecorder()

    private fun buildVm() = DashboardViewModel(
        shiftsRepo, settingsRepo, reportsRepo, authRepo, compensationRepo, tasksRepo, appPrefs,
        premiumRepo, setupPrefs, widgetPin, projectsRepo, discoveryPrefs, reviewRecorder,
    )

    private val today: LocalDate = LocalDate.now(java.time.ZoneOffset.UTC)

    private fun settings(paidProjects: Boolean) = UserSettings(
        id = "cfg",
        userId = "u1",
        timezone = "UTC",
        hourlyRate = 100.0,
        currency = CurrencyCode.ILS,
        currencyCode = "ILS",
        regionCode = RegionCode.IL,
        featuresPaidProjects = paidProjects,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun project(
        id: String = "p1",
        status: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
        currency: String = "USD",
        entered: String = "10000",
    ) = Project(
        id = id,
        userId = "u1",
        name = "Project $id",
        clientName = "Acme Ltd",
        workStatus = status,
        fee = ProjectFee.from(BigDecimal(entered), currency, TaxMode.EXCLUSIVE, BigDecimal("18")),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun record(projectId: String = "p1", currency: String = "USD") = ProjectBillingRecord(
        id = "b-$projectId",
        userId = "u1",
        projectId = projectId,
        fee = ProjectFee.from(BigDecimal("10000"), currency, TaxMode.EXCLUSIVE, BigDecimal("18")),
        billedOn = today.withDayOfMonth(1),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun payment(projectId: String = "p1", amount: String, currency: String = "USD") =
        ProjectPayment(
            id = "pay-$projectId-$amount",
            userId = "u1",
            projectId = projectId,
            billingRecordId = "b-$projectId",
            paidOn = today,
            amount = Money.of(amount, currency),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    private fun ready(vm: DashboardViewModel) = vm.uiState.value as DashboardUiState.Ready

    // ── Feature disabled ──────────────────────────────────────────────────────

    @Test
    fun `with the feature off there is no project block at all`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = false))
        projectsRepo.setProjects(project())
        projectsRepo.setBillingRecords(record())
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertNull(ready(vm).projectSummary)
        job.cancel()
    }

    @Test
    fun `with the feature off the dashboard keeps four navigation destinations`() {
        val items = BottomNavItem.visibleItems(paidProjectsEnabled = false)

        assertEquals(4, items.size)
        assertTrue(items.none { it == BottomNavItem.PROJECTS })
    }

    @Test
    fun `with the feature off the hourly pay summary is still produced`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = false))
        val start = today.atTime(9, 0).toInstant(java.time.ZoneOffset.UTC)
        shiftsRepo.setShifts(
            Shift(id = "s1", userId = "u1", startTime = start, endTime = start.plusSeconds(8 * 3600)),
        )
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Untouched by any of this work.
        assertNotNull(ready(vm).paySummary)
        assertNull(ready(vm).projectSummary)
        job.cancel()
    }

    // ── Feature enabled ───────────────────────────────────────────────────────

    @Test
    fun `with the feature on the block appears alongside the hourly content`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = true))
        projectsRepo.setProjects(project())
        projectsRepo.setBillingRecords(record())
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = ready(vm)
        assertNotNull(state.projectSummary)
        assertEquals(1, state.projectSummary!!.activeProjectCount)
        // The hourly monthly report is still there and is not merged into it.
        assertNotNull(state.monthlyReport)
        job.cancel()
    }

    @Test
    fun `with the feature on there are five navigation destinations, Settings last`() {
        val items = BottomNavItem.visibleItems(paidProjectsEnabled = true)

        assertEquals(5, items.size)
        assertTrue(items.contains(BottomNavItem.PROJECTS))
        assertEquals(BottomNavItem.SETTINGS, items.last())
        assertTrue(items.size <= BottomNavItem.MAX_PRIMARY_DESTINATIONS)
    }

    @Test
    fun `hourly earnings and project cash are separate fields on the state`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = true))
        projectsRepo.setProjects(project())
        projectsRepo.setBillingRecords(record())
        projectsRepo.setPayments(payment(amount = "11800.00"))
        val start = today.atTime(9, 0).toInstant(java.time.ZoneOffset.UTC)
        shiftsRepo.setShifts(
            Shift(id = "s1", userId = "u1", startTime = start, endTime = start.plusSeconds(8 * 3600)),
        )
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = ready(vm)
        // Hourly: value of work performed. In the user's own display currency.
        assertNotNull(state.paySummary)
        assertTrue(state.paySummary!!.totalGross > 0.0)
        // Project: cash received, in the project's currency, in its own field.
        assertEquals(
            Money.of("11800.00", "USD"),
            state.projectSummary!!.receivedThisMonth["USD"],
        )
        job.cancel()
    }

    @Test
    fun `tax collected is reported apart from project revenue`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = true))
        projectsRepo.setProjects(project())
        projectsRepo.setBillingRecords(record())
        projectsRepo.setPayments(payment(amount = "11800.00"))
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val summary = ready(vm).projectSummary!!
        assertEquals(Money.of("1800.00", "USD"), summary.receivedTaxThisMonth["USD"])
        assertEquals(Money.of("10000.00", "USD"), summary.receivedBeforeTaxThisMonth["USD"])
        job.cancel()
    }

    @Test
    fun `two currencies are shown in separate buckets`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = true))
        projectsRepo.setProjects(
            project("p1", currency = "USD"),
            project("p2", currency = "ILS", entered = "20000"),
        )
        projectsRepo.setBillingRecords(record("p1", "USD"), record("p2", "ILS"))
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val outstanding = ready(vm).projectSummary!!.outstanding
        assertEquals(listOf("ILS", "USD"), outstanding.currencies)
        assertNull(outstanding.singleOrNull())
        job.cancel()
    }

    @Test
    fun `project hours feed the project block and not the hourly report`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = true))
        projectsRepo.setProjects(project())
        val start = today.atTime(9, 0).toInstant(java.time.ZoneOffset.UTC)
        shiftsRepo.setShifts(
            Shift(
                id = "proj",
                userId = "u1",
                startTime = start,
                endTime = start.plusSeconds(4 * 3600),
                compensationSource = CompensationSource.PROJECT,
                projectId = "p1",
            ),
        )
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = ready(vm)
        // Project time earns no wage, so the hourly ring counts none of it.
        assertEquals(0, state.todayCompletedMinutes)
        assertNotNull(state.projectSummary)
        job.cancel()
    }

    @Test
    fun `re-enabling the feature brings the block back unchanged`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = true))
        projectsRepo.setProjects(project())
        projectsRepo.setBillingRecords(record())
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val before = ready(vm).projectSummary

        settingsRepo.setSettings(settings(paidProjects = false))
        advanceUntilIdle()
        assertNull(ready(vm).projectSummary)

        settingsRepo.setSettings(settings(paidProjects = true))
        advanceUntilIdle()

        assertEquals(before, ready(vm).projectSummary)
        // And the project data was never touched while the module was off.
        assertEquals(1, projectsRepo.currentProjects.size)
        assertEquals(1, projectsRepo.currentBillingRecords.size)
        job.cancel()
    }
}
