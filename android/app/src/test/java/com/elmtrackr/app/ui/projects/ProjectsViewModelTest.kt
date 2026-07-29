package com.elmtrackr.app.ui.projects

import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import com.elmtrackr.app.domain.projects.ProjectAmountEntryMode
import com.elmtrackr.app.domain.projects.ProjectBillingStatus
import com.elmtrackr.app.domain.projects.ProjectFormInput
import com.elmtrackr.app.domain.projects.ProjectStatusFilter
import com.elmtrackr.app.domain.projects.ProjectWorkAction
import com.elmtrackr.app.fake.FakeCurrentUserProvider
import com.elmtrackr.app.fake.FakeProjectsRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeShiftsRepository
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
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val projectsRepo = FakeProjectsRepository()
    private val settingsRepo = FakeSettingsRepository()
    private val shiftsRepo = FakeShiftsRepository()
    private val userProvider = FakeCurrentUserProvider("u1")

    private fun buildVm() = ProjectsViewModel(projectsRepo, settingsRepo, shiftsRepo, userProvider)

    private fun settings(paidProjects: Boolean = true) = UserSettings(
        id = "s1",
        userId = "u1",
        timezone = "Asia/Jerusalem",
        currencyCode = "ILS",
        featuresPaidProjects = paidProjects,
        onboardingCompleted = true,
        projectsDefaultCurrencyCode = "ILS",
        projectsTaxLabel = "VAT",
        projectsTaxRateBasisPoints = 1800,
    )

    private fun project(
        id: String,
        name: String = "Project $id",
        status: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
        entered: String = "10000",
        currency: String = "ILS",
    ) = Project(
        id = id,
        userId = "u1",
        name = name,
        clientName = "Acme",
        workStatus = status,
        fee = ProjectFee.from(BigDecimal(entered), currency, TaxMode.NONE, BigDecimal.ZERO),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    /** Collects uiState so the WhileSubscribed flow actually runs. */
    private suspend fun kotlinx.coroutines.test.TestScope.ready(
        vm: ProjectsViewModel,
    ): ProjectsUiState.Ready {
        val states = mutableListOf<ProjectsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()
        job.cancel()
        return states.filterIsInstance<ProjectsUiState.Ready>().last()
    }

    // ── Feature gate ──────────────────────────────────────────────────────────

    @Test
    fun `no project data is emitted while the feature is disabled`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = false))
        projectsRepo.setProjects(project("p1"))
        val vm = buildVm()

        val states = mutableListOf<ProjectsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(ProjectsUiState.Disabled, states.last())
        assertTrue(states.none { it is ProjectsUiState.Ready })
    }

    @Test
    fun `enabling the feature reveals the existing projects`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = false))
        projectsRepo.setProjects(project("p1"))
        val vm = buildVm()
        val states = mutableListOf<ProjectsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }
        advanceUntilIdle()
        assertEquals(ProjectsUiState.Disabled, states.last())

        // Disabling never deleted the project, so turning the module back on
        // restores access to it.
        settingsRepo.setSettings(settings(paidProjects = true))
        advanceUntilIdle()
        job.cancel()

        val readyState = states.filterIsInstance<ProjectsUiState.Ready>().last()
        assertEquals(listOf("p1"), readyState.allProjects.map { it.project.id })
    }

    // ── Empty state ───────────────────────────────────────────────────────────

    @Test
    fun `an empty list is distinguished from no matches`() = runTest {
        settingsRepo.setSettings(settings())
        val vm = buildVm()

        val empty = ready(vm)
        assertTrue(empty.hasNoProjects)
        assertFalse(empty.hasNoMatches)
    }

    @Test
    fun `a search matching nothing reports no matches, not an empty account`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project("p1", name = "Website"))
        val vm = buildVm()
        vm.onQueryChange("nothing")

        val state = ready(vm)
        assertFalse(state.hasNoProjects)
        assertTrue(state.hasNoMatches)
    }

    // ── Search and filters ────────────────────────────────────────────────────

    @Test
    fun `search narrows the visible list but not the full list`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project("p1", name = "Website"), project("p2", name = "Brochure"))
        val vm = buildVm()
        vm.onQueryChange("website")

        val state = ready(vm)
        assertEquals(listOf("p1"), state.visibleProjects.map { it.project.id })
        assertEquals(2, state.allProjects.size)
    }

    @Test
    fun `the status filter narrows the visible list`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(
            project("active", status = ProjectWorkStatus.ACTIVE),
            project("draft", status = ProjectWorkStatus.DRAFT),
        )
        val vm = buildVm()
        vm.onFilterChange(ProjectStatusFilter.DRAFT)

        assertEquals(listOf("draft"), ready(vm).visibleProjects.map { it.project.id })
    }

    @Test
    fun `archived projects are hidden by default and counted separately`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(
            project("live", status = ProjectWorkStatus.ACTIVE),
            project("old", status = ProjectWorkStatus.ARCHIVED),
        )
        val vm = buildVm()

        val state = ready(vm)
        assertEquals(listOf("live"), state.visibleProjects.map { it.project.id })
        assertEquals(1, state.counts[ProjectStatusFilter.ARCHIVED])
    }

    // ── Creation and editing ──────────────────────────────────────────────────

    @Test
    fun `saving a new project persists it and reports the new id`() = runTest {
        settingsRepo.setSettings(settings())
        val vm = buildVm()
        var savedId: String? = null

        vm.saveProject(
            ProjectFormInput(
                name = "Website rebuild",
                clientName = "Acme",
                currencyCode = "ILS",
                amountText = "10000",
            ),
            existing = null,
        ) { savedId = it }
        advanceUntilIdle()

        assertEquals(1, projectsRepo.currentProjects.size)
        assertEquals("Website rebuild", projectsRepo.currentProjects.single().name)
        assertEquals(projectsRepo.currentProjects.single().id, savedId)
    }

    @Test
    fun `an invalid form is not persisted`() = runTest {
        settingsRepo.setSettings(settings())
        val vm = buildVm()

        vm.saveProject(ProjectFormInput(name = "", clientName = "", amountText = ""), existing = null)
        advanceUntilIdle()

        assertTrue(projectsRepo.currentProjects.isEmpty())
    }

    @Test
    fun `a tax-inclusive project stores the entered total and the derived base`() = runTest {
        settingsRepo.setSettings(settings())
        val vm = buildVm()

        vm.saveProject(
            ProjectFormInput(
                name = "P",
                clientName = "C",
                currencyCode = "ILS",
                amountText = "11800",
                entryMode = ProjectAmountEntryMode.CLIENT_TOTAL,
                taxEnabled = true,
                taxRateText = "18",
                taxLabel = "VAT",
            ),
            existing = null,
        )
        advanceUntilIdle()

        val fee = projectsRepo.currentProjects.single().fee
        assertEquals(Money.of("11800.00", "ILS"), fee.clientTotal)
        assertEquals(Money.of("10000.00", "ILS"), fee.base)
        assertEquals(Money.of("1800.00", "ILS"), fee.tax)
    }

    @Test
    fun `editing a project updates it in place`() = runTest {
        settingsRepo.setSettings(settings())
        val original = project("p1", name = "Old name")
        projectsRepo.setProjects(original)
        val vm = buildVm()

        vm.saveProject(
            ProjectFormInput.forEdit(original).copy(name = "New name"),
            existing = original,
        )
        advanceUntilIdle()

        assertEquals(1, projectsRepo.currentProjects.size)
        assertEquals("New name", projectsRepo.currentProjects.single().name)
        assertEquals("p1", projectsRepo.currentProjects.single().id)
    }

    // ── Editing after a billing snapshot ──────────────────────────────────────

    @Test
    fun `editing the amount after billing leaves the billing record untouched`() = runTest {
        settingsRepo.setSettings(settings())
        val original = project("p1", entered = "10000")
        val record = ProjectBillingRecord(
            id = "bill-1",
            userId = "u1",
            projectId = "p1",
            fee = ProjectFee.from(BigDecimal("10000"), "ILS", TaxMode.NONE, BigDecimal.ZERO),
            externalReference = "2026-014",
            billedOn = LocalDate.of(2026, 7, 20),
            dueOn = LocalDate.of(2026, 8, 19),
        )
        projectsRepo.setProjects(original)
        projectsRepo.setBillingRecords(record)
        val vm = buildVm()

        vm.saveProject(
            ProjectFormInput.forEdit(original).copy(amountText = "25000"),
            existing = original,
        )
        advanceUntilIdle()

        // The project's own price moved…
        assertEquals(
            Money.of("25000.00", "ILS"),
            projectsRepo.currentProjects.single().fee.clientTotal,
        )
        // …and the recorded billing request did not.
        val storedRecord = projectsRepo.currentBillingRecords.single()
        assertEquals(Money.of("10000.00", "ILS"), storedRecord.fee.clientTotal)
        assertEquals("2026-014", storedRecord.externalReference)
        assertEquals(LocalDate.of(2026, 7, 20), storedRecord.billedOn)
        assertEquals(record, storedRecord)
    }

    @Test
    fun `a billed project is flagged so the form can explain the snapshot`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project("p1"))
        projectsRepo.setBillingRecords(
            ProjectBillingRecord(
                id = "bill-1",
                userId = "u1",
                projectId = "p1",
                fee = ProjectFee.from(BigDecimal("10000"), "ILS", TaxMode.NONE, BigDecimal.ZERO),
                billedOn = LocalDate.of(2026, 7, 20),
            ),
        )
        val vm = buildVm()

        assertTrue(ready(vm).summary("p1")!!.isBilled)
    }

    // ── Work status ───────────────────────────────────────────────────────────

    @Test
    fun `a work action moves the status without touching the fee`() = runTest {
        settingsRepo.setSettings(settings())
        val original = project("p1", status = ProjectWorkStatus.ACTIVE)
        projectsRepo.setProjects(original)
        val vm = buildVm()

        vm.applyWorkAction(original, ProjectWorkAction.PAUSE)
        advanceUntilIdle()

        val saved = projectsRepo.currentProjects.single()
        assertEquals(ProjectWorkStatus.PAUSED, saved.workStatus)
        assertEquals(original.fee, saved.fee)
    }

    @Test
    fun `completing a project does not make it paid`() = runTest {
        settingsRepo.setSettings(settings())
        val original = project("p1")
        projectsRepo.setProjects(original)
        projectsRepo.setBillingRecords(
            ProjectBillingRecord(
                id = "bill-1",
                userId = "u1",
                projectId = "p1",
                fee = ProjectFee.from(BigDecimal("10000"), "ILS", TaxMode.NONE, BigDecimal.ZERO),
                billedOn = LocalDate.of(2026, 7, 1),
            ),
        )
        val vm = buildVm()

        vm.applyWorkAction(original, ProjectWorkAction.COMPLETE)
        advanceUntilIdle()

        val state = ready(vm)
        val summary = state.summary("p1")!!
        assertEquals(ProjectWorkStatus.COMPLETED, summary.project.workStatus)
        assertEquals(ProjectBillingStatus.BILLED, summary.billing.status)
    }

    @Test
    fun `paying a project does not complete it`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project("p1"))
        projectsRepo.setBillingRecords(
            ProjectBillingRecord(
                id = "bill-1",
                userId = "u1",
                projectId = "p1",
                fee = ProjectFee.from(BigDecimal("10000"), "ILS", TaxMode.NONE, BigDecimal.ZERO),
                billedOn = LocalDate.of(2026, 7, 1),
            ),
        )
        projectsRepo.setPayments(
            ProjectPayment(
                id = "pay-1",
                userId = "u1",
                projectId = "p1",
                billingRecordId = "bill-1",
                paidOn = LocalDate.of(2026, 7, 10),
                amount = Money.of("10000", "ILS"),
            ),
        )
        val vm = buildVm()

        val summary = ready(vm).summary("p1")!!
        assertEquals(ProjectBillingStatus.PAID, summary.billing.status)
        assertEquals(ProjectWorkStatus.ACTIVE, summary.project.workStatus)
    }

    @Test
    fun `archiving keeps the project and its data`() = runTest {
        settingsRepo.setSettings(settings())
        val original = project("p1")
        projectsRepo.setProjects(original)
        val vm = buildVm()

        vm.applyWorkAction(original, ProjectWorkAction.ARCHIVE)
        advanceUntilIdle()

        assertEquals(1, projectsRepo.currentProjects.size)
        assertEquals(ProjectWorkStatus.ARCHIVED, projectsRepo.currentProjects.single().workStatus)
    }

    // ── Deletion ──────────────────────────────────────────────────────────────

    @Test
    fun `an unused draft is deleted permanently`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project("p1", status = ProjectWorkStatus.DRAFT))
        val vm = buildVm()
        var deleted = false

        val summary = ready(vm).summary("p1")!!
        vm.deleteProject(summary) { deleted = true }
        advanceUntilIdle()

        assertTrue(deleted)
        assertTrue(projectsRepo.currentProjects.isEmpty())
    }

    @Test
    fun `a project with tracked time is not deleted`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project("p1", status = ProjectWorkStatus.DRAFT))
        shiftsRepo.setShifts(
            Shift(
                id = "s1",
                userId = "u1",
                startTime = Instant.parse("2026-07-20T08:00:00Z"),
                endTime = Instant.parse("2026-07-20T08:00:00Z").plus(3, ChronoUnit.HOURS),
                projectId = "p1",
            ),
        )
        val vm = buildVm()

        val summary = ready(vm).summary("p1")!!
        assertFalse(summary.canDeletePermanently)

        vm.deleteProject(summary)
        advanceUntilIdle()
        assertEquals(1, projectsRepo.currentProjects.size)
    }

    @Test
    fun `a billed project is not deleted even if the caller asks`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project("p1", status = ProjectWorkStatus.DRAFT))
        projectsRepo.setBillingRecords(
            ProjectBillingRecord(
                id = "bill-1",
                userId = "u1",
                projectId = "p1",
                fee = ProjectFee.from(BigDecimal("10000"), "ILS", TaxMode.NONE, BigDecimal.ZERO),
                billedOn = LocalDate.of(2026, 7, 1),
            ),
        )
        val vm = buildVm()

        vm.deleteProject(ready(vm).summary("p1")!!)
        advanceUntilIdle()

        assertEquals(1, projectsRepo.currentProjects.size)
    }

    // ── Metrics in state ──────────────────────────────────────────────────────

    @Test
    fun `tracked time and the effective rate reach the summary`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(project("p1", entered = "10000"))
        shiftsRepo.setShifts(
            Shift(
                id = "s1",
                userId = "u1",
                startTime = Instant.parse("2026-07-20T08:00:00Z"),
                endTime = Instant.parse("2026-07-20T08:00:00Z").plus(40, ChronoUnit.HOURS),
                projectId = "p1",
            ),
        )
        val vm = buildVm()

        val summary = ready(vm).summary("p1")!!
        assertEquals(40 * 60, summary.time.trackedMinutes)
        assertEquals(Money.of("250.00", "ILS"), summary.effectiveHourlyRate)
    }

    @Test
    fun `projects in different currencies keep their own`() = runTest {
        settingsRepo.setSettings(settings())
        projectsRepo.setProjects(
            project("ils", currency = "ILS", entered = "10000"),
            project("usd", currency = "USD", entered = "2500"),
        )
        val vm = buildVm()

        val state = ready(vm)
        assertEquals("ILS", state.summary("ils")!!.currencyCode)
        assertEquals("USD", state.summary("usd")!!.currencyCode)
    }
}
