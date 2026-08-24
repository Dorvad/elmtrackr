package com.elmtrackr.app.ui.dashboard

import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectWorkStatus
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
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Clocking in to a project from the dashboard.
 *
 * The behaviours worth locking down are the fallbacks: with the feature off, or
 * with no clockable project, a punch must still be an ordinary hourly-work punch
 * rather than being lost or becoming project time with nothing behind it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardProjectTimeViewModelTest {

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
        // The production flowOn dispatcher would run the payroll transform off the
        // test scheduler, so advanceUntilIdle could not await its emissions.
        computationDispatcher = mainDispatcherRule.dispatcher,
    )

    private fun settings(paidProjects: Boolean = true) = UserSettings(
        id = "cfg",
        userId = "u1",
        featuresPaidProjects = paidProjects,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun project(
        id: String = "p1",
        name: String = "Website rebuild",
        status: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
        budgetMinutes: Int? = null,
        targetRate: String? = null,
    ) = Project(
        id = id,
        userId = "u1",
        name = name,
        clientName = "Acme Ltd",
        workStatus = status,
        fee = ProjectFee.from(BigDecimal("10000"), "USD", TaxMode.EXCLUSIVE, BigDecimal("18")),
        hourBudgetMinutes = budgetMinutes,
        targetHourlyRate = targetRate?.let { Money.of(it, "USD") },
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun ready(vm: DashboardViewModel) = vm.uiState.value as DashboardUiState.Ready

    @Test
    fun `no project options are offered while Paid Projects is off`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = false))
        projectsRepo.setProjects(project())
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(ready(vm).projectOptions.isEmpty())
        assertFalse(ready(vm).canSelectProjectTime)
        job.cancel()
    }

    @Test
    fun `only clockable projects are offered`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(
            project("p1", "Open work"),
            project("p2", "Finished", status = ProjectWorkStatus.COMPLETED),
            project("p3", "Draft", status = ProjectWorkStatus.DRAFT),
        )
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("p1"), ready(vm).projectOptions.map { it.projectId })
        job.cancel()
    }

    @Test
    fun `clocking in as project time records the project and its name`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project())
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.selectCompensationSource(CompensationSource.PROJECT)
        advanceUntilIdle()
        vm.clockIn()
        advanceUntilIdle()

        val shift = shiftsRepo.currentShifts.single()
        assertEquals(CompensationSource.PROJECT, shift.compensationSource)
        assertEquals("p1", shift.projectId)
        assertEquals("Website rebuild", shift.projectNameSnapshot)
        job.cancel()
    }

    @Test
    fun `selecting project time picks the first project automatically`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project())
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.selectCompensationSource(CompensationSource.PROJECT)
        advanceUntilIdle()

        assertEquals("p1", ready(vm).selectedProjectId)
        job.cancel()
    }

    @Test
    fun `switching back to hourly work drops the project selection and note`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project())
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.selectClockInProject("p1")
        vm.setProjectClockInNote("Homepage")
        vm.selectCompensationSource(CompensationSource.EMPLOYEE)
        advanceUntilIdle()

        assertNull(ready(vm).selectedProjectId)
        assertEquals("", ready(vm).projectClockInNote)
        job.cancel()
    }

    @Test
    fun `clocking in falls back to hourly work when the feature was switched off`() = runTest {
        // The selection was made while the feature was on; disabling it must not
        // produce a project shift, and must not swallow the punch either.
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project())
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.selectCompensationSource(CompensationSource.PROJECT)
        advanceUntilIdle()

        settingsRepo.setSettings(settings(paidProjects = false))
        advanceUntilIdle()
        vm.clockIn()
        advanceUntilIdle()

        val shift = shiftsRepo.currentShifts.single()
        assertEquals(CompensationSource.EMPLOYEE, shift.compensationSource)
        assertNull(shift.projectId)
        job.cancel()
    }

    @Test
    fun `clocking in falls back to hourly work when the project stopped being clockable`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project())
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.selectCompensationSource(CompensationSource.PROJECT)
        advanceUntilIdle()

        projectsRepo.setProjects(project(status = ProjectWorkStatus.COMPLETED))
        advanceUntilIdle()
        vm.clockIn()
        advanceUntilIdle()

        val shift = shiftsRepo.currentShifts.single()
        assertEquals(CompensationSource.EMPLOYEE, shift.compensationSource)
        assertNull(shift.projectId)
        job.cancel()
    }

    @Test
    fun `a clock-in note is saved on the project shift`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project())
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.selectCompensationSource(CompensationSource.PROJECT)
        vm.setProjectClockInNote("  Homepage rework  ")
        advanceUntilIdle()
        vm.clockIn()
        advanceUntilIdle()

        assertEquals("Homepage rework", shiftsRepo.currentShifts.single().notes)
        // Consumed, so the next punch does not inherit it.
        assertEquals("", ready(vm).projectClockInNote)
        job.cancel()
    }

    @Test
    fun `an active project shift exposes its project and that project's shifts`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project(budgetMinutes = 600, targetRate = "100"))
        val active = Shift(
            id = "active",
            userId = "u1",
            startTime = Instant.now().minus(120, ChronoUnit.MINUTES),
            endTime = null,
            compensationSource = CompensationSource.PROJECT,
            projectId = "p1",
            projectNameSnapshot = "Website rebuild",
        )
        val banked = Shift(
            id = "banked",
            userId = "u1",
            startTime = Instant.now().minus(400, ChronoUnit.MINUTES),
            endTime = Instant.now().minus(280, ChronoUnit.MINUTES),
            compensationSource = CompensationSource.PROJECT,
            projectId = "p1",
        )
        shiftsRepo.setShifts(banked, active)
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = ready(vm)
        assertEquals("p1", state.activeProject?.id)
        assertEquals("Website rebuild", state.activeProject?.name)
        assertEquals("USD", state.activeProject?.currencyCode)
        assertEquals(Money.of("100", "USD"), state.activeProject?.targetHourlyRate)
        assertEquals(setOf("banked", "active"), state.activeProjectShifts.map { it.id }.toSet())
        assertTrue(state.activeShiftIsProjectTime)
        job.cancel()
    }

    @Test
    fun `an active hourly shift exposes no project`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project())
        shiftsRepo.setShifts(
            Shift(
                id = "active",
                userId = "u1",
                startTime = Instant.now().minus(60, ChronoUnit.MINUTES),
                endTime = null,
            ),
        )
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertNull(ready(vm).activeProject)
        assertTrue(ready(vm).activeProjectShifts.isEmpty())
        assertFalse(ready(vm).activeShiftIsProjectTime)
        job.cancel()
    }

    @Test
    fun `an active project shift survives the feature being switched off`() = runTest {
        // Nothing converts it and nothing discards it: the shift stays project
        // time and the dashboard still knows which project it belongs to.
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project())
        shiftsRepo.setShifts(
            Shift(
                id = "active",
                userId = "u1",
                startTime = Instant.now().minus(60, ChronoUnit.MINUTES),
                endTime = null,
                compensationSource = CompensationSource.PROJECT,
                projectId = "p1",
                projectNameSnapshot = "Website rebuild",
            ),
        )
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        settingsRepo.setSettings(settings(paidProjects = false))
        advanceUntilIdle()

        val state = ready(vm)
        assertEquals(CompensationSource.PROJECT, state.activeShift?.compensationSource)
        assertEquals("p1", state.activeShift?.projectId)
        assertTrue(state.activeShiftIsProjectTime)
        job.cancel()
    }

    @Test
    fun `clocking out a project shift takes no wage snapshot and reports a summary`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project())
        shiftsRepo.setShifts(
            Shift(
                id = "active",
                userId = "u1",
                // The fake clocks out at a fixed 2024-01-08T17:00Z, so the start
                // has to precede it for the shift to have any duration.
                startTime = Instant.parse("2024-01-08T15:00:00Z"),
                endTime = null,
                compensationSource = CompensationSource.PROJECT,
                projectId = "p1",
                projectNameSnapshot = "Website rebuild",
            ),
        )
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.clockOut("active")
        advanceUntilIdle()

        val shift = shiftsRepo.currentShifts.single()
        assertTrue(shift.isCompleted)
        assertEquals(CompensationSource.PROJECT, shift.compensationSource)
        // No wage snapshot: this time is paid by the project's fee.
        assertNull(shift.compensationSnapshot)

        val summary = vm.projectShiftSummary.value
        assertNotNull(summary)
        assertEquals("p1", summary!!.projectId)
        assertEquals("Website rebuild", summary.projectName)
        assertTrue(summary.addedMinutes > 0)
        assertNotNull(summary.effectiveHourlyRate)

        vm.dismissProjectShiftSummary()
        assertNull(vm.projectShiftSummary.value)
        job.cancel()
    }

    @Test
    fun `re-enabling the feature restores the selector without creating anything`() = runTest {
        // Disabling hides the controls but keeps the projects; re-enabling must
        // bring the same projects back and must not invent a sample project.
        settingsRepo.setSettings(settings(paidProjects = true))
        projectsRepo.setProjects(project())
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf("p1"), ready(vm).projectOptions.map { it.projectId })

        settingsRepo.setSettings(settings(paidProjects = false))
        advanceUntilIdle()
        assertTrue(ready(vm).projectOptions.isEmpty())
        assertFalse(ready(vm).canSelectProjectTime)
        // The project itself is untouched while the module is off.
        assertEquals(listOf("p1"), projectsRepo.currentProjects.map { it.id })

        settingsRepo.setSettings(settings(paidProjects = true))
        advanceUntilIdle()
        assertEquals(listOf("p1"), ready(vm).projectOptions.map { it.projectId })
        assertTrue(ready(vm).canSelectProjectTime)
        assertEquals(1, projectsRepo.currentProjects.size)
        job.cancel()
    }

    @Test
    fun `a project shift restored after process death can still be clocked out`() = runTest {
        // Nothing re-derives the compensation source at clock-out: it comes from
        // the row that was reloaded, so a restart cannot turn project time into
        // paid hours.
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project())
        shiftsRepo.setShifts(
            Shift(
                id = "restored",
                userId = "u1",
                startTime = Instant.parse("2024-01-08T15:00:00Z"),
                endTime = null,
                compensationSource = CompensationSource.PROJECT,
                projectId = "p1",
                projectNameSnapshot = "Website rebuild",
            ),
        )
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("p1", ready(vm).activeProject?.id)

        vm.clockOut("restored")
        advanceUntilIdle()

        val shift = shiftsRepo.currentShifts.single()
        assertTrue(shift.isCompleted)
        assertEquals(CompensationSource.PROJECT, shift.compensationSource)
        assertEquals("p1", shift.projectId)
        assertNull(shift.compensationSnapshot)
        job.cancel()
    }

    @Test
    fun `project hours do not count towards the daily overtime ring`() = runTest {
        // The ring turns over at the daily overtime threshold, which is a pay
        // classification. Project hours are still visible on the project and in
        // the shift list; they must not make the clock read as overtime.
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project())
        val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val startOfDay = today.atTime(6, 0).toInstant(java.time.ZoneOffset.UTC)
        shiftsRepo.setShifts(
            Shift(
                id = "wages",
                userId = "u1",
                startTime = startOfDay,
                endTime = startOfDay.plus(4, ChronoUnit.HOURS),
            ),
            Shift(
                id = "project",
                userId = "u1",
                startTime = startOfDay.plus(5, ChronoUnit.HOURS),
                endTime = startOfDay.plus(13, ChronoUnit.HOURS),
                compensationSource = CompensationSource.PROJECT,
                projectId = "p1",
            ),
        )
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Four hours of wages, not twelve.
        assertEquals(240, ready(vm).todayCompletedMinutes)
        job.cancel()
    }

    @Test
    fun `clocking out an hourly shift reports no project summary`() = runTest {
        settingsRepo.setSettings(settings())
        shiftsRepo.setShifts(
            Shift(
                id = "active",
                userId = "u1",
                startTime = Instant.now().minus(60, ChronoUnit.MINUTES),
                endTime = null,
            ),
        )
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.clockOut("active")
        advanceUntilIdle()

        assertNull(vm.projectShiftSummary.value)
        job.cancel()
    }
}
