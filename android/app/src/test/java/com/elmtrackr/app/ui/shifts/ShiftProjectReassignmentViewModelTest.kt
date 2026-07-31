package com.elmtrackr.app.ui.shifts

import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakeCurrentUserProvider
import com.elmtrackr.app.fake.FakePremiumProfilesRepository
import com.elmtrackr.app.fake.FakeProjectsRepository
import com.elmtrackr.app.fake.FakeRefundReceiptStorage
import com.elmtrackr.app.fake.FakeRefundsRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.fake.FakeTasksRepository
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Editing a completed shift across the compensation boundary.
 *
 * Two things must hold on every save: the hours end up on exactly one side, and a
 * project's billed amounts are never rewritten. The billed figures are a snapshot
 * of what the client was asked to pay; moving hours changes the project's
 * effective rate, not its invoice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShiftProjectReassignmentViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val shiftsRepo = FakeShiftsRepository()
    private val settingsRepo = FakeSettingsRepository()
    private val compensationRepo = FakeCompensationProfilesRepository()
    private val premiumRepo = FakePremiumProfilesRepository()
    private val tasksRepo = FakeTasksRepository()
    private val currentUser = FakeCurrentUserProvider()
    private val refundsRepo = FakeRefundsRepository()
    private val receiptStorage = FakeRefundReceiptStorage()
    private val projectsRepo = FakeProjectsRepository()

    private fun buildVm() = ShiftsViewModel(
        shiftsRepo,
        settingsRepo,
        compensationRepo,
        premiumRepo,
        tasksRepo,
        currentUser,
        refundsRepo,
        receiptStorage,
        projectsRepo,
    )

    private fun seed(paidProjects: Boolean = true) {
        currentUser.setUserId("u1")
        settingsRepo.setSettings(
            UserSettings(
                id = "settings",
                userId = "u1",
                timezone = "Asia/Jerusalem",
                hourlyRate = 62.5,
                currency = CurrencyCode.ILS,
                currencyCode = "ILS",
                regionCode = RegionCode.IL,
                weekendDays = listOf(5, 6),
                featuresPaidProjects = paidProjects,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
    }

    private fun project(
        id: String = "p1",
        status: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
    ) = Project(
        id = id,
        userId = "u1",
        name = "Website rebuild",
        workStatus = status,
        fee = ProjectFee.from(BigDecimal("10000"), "USD", TaxMode.EXCLUSIVE, BigDecimal("18")),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun employeeShift() = Shift(
        id = "s1",
        userId = "u1",
        startTime = Instant.parse("2026-07-06T06:00:00Z"),
        endTime = Instant.parse("2026-07-06T14:00:00Z"),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun projectShift() = employeeShift().copy(
        compensationSource = CompensationSource.PROJECT,
        projectId = "p1",
        projectNameSnapshot = "Website rebuild",
    )

    private fun input(
        source: CompensationSource,
        projectId: String? = null,
        shift: Shift = employeeShift(),
    ) = ShiftFormInput(
        startTime = shift.startTime,
        endTime = shift.endTime,
        breakMinutes = shift.breakMinutes,
        notes = shift.notes.orEmpty(),
        refundAction = null,
        compensationSource = source,
        projectId = projectId,
    )

    private fun saved() = shiftsRepo.currentShifts.single()

    @Test
    fun `an hourly shift becomes project time and stops being paid as wages`() = runTest {
        seed()
        projectsRepo.setProjects(project())
        shiftsRepo.setShifts(employeeShift())
        val vm = buildVm()

        vm.saveEditedShift("s1", input(CompensationSource.PROJECT, projectId = "p1"))
        advanceUntilIdle()

        assertEquals(CompensationSource.PROJECT, saved().compensationSource)
        assertEquals("p1", saved().projectId)
        assertEquals("Website rebuild", saved().projectNameSnapshot)
        // No wage snapshot is written for a project shift.
        assertNull(saved().compensationSnapshot)
    }

    @Test
    fun `a project shift becomes hourly work and is recalculated as wages`() = runTest {
        seed()
        projectsRepo.setProjects(project())
        shiftsRepo.setShifts(projectShift())
        val vm = buildVm()

        vm.saveEditedShift("s1", input(CompensationSource.EMPLOYEE, shift = projectShift()))
        advanceUntilIdle()

        assertEquals(CompensationSource.EMPLOYEE, saved().compensationSource)
        assertNull(saved().projectId)
        assertNull(saved().projectNameSnapshot)
        // Recalculated on the way back, so the shift carries a fresh wage snapshot.
        assertNotNull(saved().compensationSnapshot)
    }

    @Test
    fun `a shift is reassigned between projects without becoming wages`() = runTest {
        seed()
        projectsRepo.setProjects(project("p1"), project("p2").copy(name = "Mobile app"))
        shiftsRepo.setShifts(projectShift())
        val vm = buildVm()

        vm.saveEditedShift(
            "s1",
            input(CompensationSource.PROJECT, projectId = "p2", shift = projectShift()),
        )
        advanceUntilIdle()

        assertEquals(CompensationSource.PROJECT, saved().compensationSource)
        assertEquals("p2", saved().projectId)
        assertEquals("Mobile app", saved().projectNameSnapshot)
    }

    @Test
    fun `reassigning hours leaves a billed snapshot untouched`() = runTest {
        seed()
        projectsRepo.setProjects(project())
        val billed = ProjectBillingRecord(
            id = "b1",
            userId = "u1",
            projectId = "p1",
            fee = ProjectFee.from(BigDecimal("10000"), "USD", TaxMode.EXCLUSIVE, BigDecimal("18")),
            billedOn = LocalDate.of(2026, 7, 1),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        projectsRepo.setBillingRecords(billed)
        shiftsRepo.setShifts(employeeShift())
        val vm = buildVm()

        vm.saveEditedShift("s1", input(CompensationSource.PROJECT, projectId = "p1"))
        advanceUntilIdle()

        // The billed amounts are what the client was asked to pay. Only the
        // project's tracked hours, and therefore its effective rate, move.
        assertEquals(listOf(billed), projectsRepo.currentBillingRecords)
        assertEquals(Money.of("10000", "USD"), projectsRepo.currentBillingRecords.single().fee.base)
        assertEquals(Money.of("11800", "USD"), projectsRepo.currentBillingRecords.single().fee.clientTotal)
    }

    @Test
    fun `asking for project time while the feature is off keeps the shift hourly`() = runTest {
        seed(paidProjects = false)
        projectsRepo.setProjects(project())
        shiftsRepo.setShifts(employeeShift())
        val vm = buildVm()

        vm.saveEditedShift("s1", input(CompensationSource.PROJECT, projectId = "p1"))
        advanceUntilIdle()

        assertEquals(CompensationSource.EMPLOYEE, saved().compensationSource)
        assertNull(saved().projectId)
    }

    @Test
    fun `an existing project link survives an edit made after the feature was switched off`() = runTest {
        // Disabling the module must not convert recorded project time into paid
        // hours behind the user's back.
        seed(paidProjects = false)
        projectsRepo.setProjects(project())
        shiftsRepo.setShifts(projectShift())
        val vm = buildVm()

        vm.saveEditedShift(
            "s1",
            input(CompensationSource.PROJECT, projectId = "p1", shift = projectShift())
                .copy(breakMinutes = 30),
        )
        advanceUntilIdle()

        assertEquals(CompensationSource.PROJECT, saved().compensationSource)
        assertEquals("p1", saved().projectId)
        assertEquals(30, saved().breakMinutes)
    }

    @Test
    fun `an existing link to a completed project survives an unrelated edit`() = runTest {
        // Completing a project stops new hours going in, but must not retroactively
        // reclassify the hours already recorded against it.
        seed()
        projectsRepo.setProjects(project(status = ProjectWorkStatus.COMPLETED))
        shiftsRepo.setShifts(projectShift())
        val vm = buildVm()

        vm.saveEditedShift(
            "s1",
            input(CompensationSource.PROJECT, projectId = "p1", shift = projectShift())
                .copy(breakMinutes = 15),
        )
        advanceUntilIdle()

        assertEquals(CompensationSource.PROJECT, saved().compensationSource)
        assertEquals("p1", saved().projectId)
    }

    @Test
    fun `moving a shift to a project that is not clockable falls back to hourly work`() = runTest {
        seed()
        projectsRepo.setProjects(project(status = ProjectWorkStatus.COMPLETED))
        shiftsRepo.setShifts(employeeShift())
        val vm = buildVm()

        vm.saveEditedShift("s1", input(CompensationSource.PROJECT, projectId = "p1"))
        advanceUntilIdle()

        assertEquals(CompensationSource.EMPLOYEE, saved().compensationSource)
        assertNull(saved().projectId)
    }

    @Test
    fun `creating a shift as project time attaches the project`() = runTest {
        seed()
        projectsRepo.setProjects(project())
        val vm = buildVm()

        vm.createShift(input(CompensationSource.PROJECT, projectId = "p1"))
        advanceUntilIdle()

        assertEquals(CompensationSource.PROJECT, saved().compensationSource)
        assertEquals("p1", saved().projectId)
        assertNull(saved().compensationSnapshot)
    }

    @Test
    fun `creating a shift as hourly work still takes a wage snapshot`() = runTest {
        seed()
        val vm = buildVm()

        vm.createShift(input(CompensationSource.EMPLOYEE))
        advanceUntilIdle()

        assertEquals(CompensationSource.EMPLOYEE, saved().compensationSource)
        assertNotNull(saved().compensationSnapshot)
    }

    @Test
    fun `the form offers no projects while the feature is off`() = runTest {
        seed(paidProjects = false)
        projectsRepo.setProjects(project())
        val vm = buildVm()
        val job = launch { vm.formProjects.collect {} }
        advanceUntilIdle()

        assertEquals(emptyList<Project>(), vm.formProjects.value)
        job.cancel()
    }

    @Test
    fun `the form offers every project, including ones no longer clockable`() = runTest {
        // The picker needs them all so a shift linked to a finished project can
        // still show which project that is instead of losing the link.
        seed()
        projectsRepo.setProjects(project("p1"), project("p2", status = ProjectWorkStatus.ARCHIVED))
        val vm = buildVm()
        val job = launch { vm.formProjects.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("p1", "p2"), vm.formProjects.value.map { it.id })
        job.cancel()
    }
}
