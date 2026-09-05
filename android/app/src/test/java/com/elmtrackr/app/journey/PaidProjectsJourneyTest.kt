package com.elmtrackr.app.journey

import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.PaymentMethod
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.TaxMode
import com.elmtrackr.app.domain.projects.ProjectAmountEntryMode
import com.elmtrackr.app.domain.projects.ProjectBillingFormInput
import com.elmtrackr.app.domain.projects.ProjectBillingStatus
import com.elmtrackr.app.domain.projects.ProjectBillingStatusResolver
import com.elmtrackr.app.domain.projects.ProjectFormInput
import com.elmtrackr.app.domain.projects.ProjectPaymentFormInput
import com.elmtrackr.app.domain.projects.ProjectWorkAction
import com.elmtrackr.app.fake.FakeAuthRepository
import com.elmtrackr.app.fake.FakeCurrentUserProvider
import com.elmtrackr.app.fake.FakeProjectsRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.navigation.BottomNavItem
import com.elmtrackr.app.ui.projects.ProjectsUiState
import com.elmtrackr.app.ui.projects.ProjectsViewModel
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
 * The release journeys, driven end to end through the real view model.
 *
 * These are not unit tests of one function. Each one walks a whole path a person
 * takes — enable the module, create a project, track time against it, bill it,
 * take a part payment, take the rest, turn the module off, turn it back on — and
 * checks the state at every step, because the defects that matter at release are
 * the ones that only appear after step four.
 *
 * The view model is real; only the repositories, settings and clock are faked.
 * A journey that passed against a mocked view model would prove nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaidProjectsJourneyTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val projectsRepo = FakeProjectsRepository()
    private val settingsRepo = FakeSettingsRepository()
    private val shiftsRepo = FakeShiftsRepository()
    private val authRepo = FakeAuthRepository().apply {
        setProfile(Profile("u1", "test@test.com", null, Instant.EPOCH, Instant.EPOCH))
    }
    private val userProvider = FakeCurrentUserProvider("u1")

    private val discovery = com.elmtrackr.app.fake.FakeFeatureDiscoveryPreferences()

    private fun buildVm() = ProjectsViewModel(projectsRepo, settingsRepo, shiftsRepo, userProvider, discovery)

    private fun settings(
        paidProjects: Boolean,
        currency: String = "USD",
        taxRateBasisPoints: Int = 0,
        taxInclusive: Boolean = false,
    ) = UserSettings(
        id = "cfg",
        userId = "u1",
        timezone = "UTC",
        hourlyRate = 100.0,
        currency = CurrencyCode.ILS,
        currencyCode = "ILS",
        regionCode = RegionCode.IL,
        featuresPaidProjects = paidProjects,
        projectsDefaultCurrencyCode = currency,
        projectsTaxRateBasisPoints = taxRateBasisPoints,
        projectsTaxInclusive = taxInclusive,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun ready(vm: ProjectsViewModel) = vm.uiState.value as ProjectsUiState.Ready

    private fun formInput(
        name: String = "Website rebuild",
        client: String = "Acme Ltd",
        currency: String = "USD",
        amount: String = "10000",
        entryMode: ProjectAmountEntryMode = ProjectAmountEntryMode.FEE_BEFORE_TAX,
        taxEnabled: Boolean = true,
        taxRate: String = "18",
        hourBudget: String = "",
    ) = ProjectFormInput(
        name = name,
        clientName = client,
        currencyCode = currency,
        amountText = amount,
        entryMode = entryMode,
        taxEnabled = taxEnabled,
        taxRateText = taxRate,
        hourBudgetText = hourBudget,
        workStatus = ProjectWorkStatus.ACTIVE,
    )

    // ── Journey 1: existing user who never enables the module ────────────────

    @Test
    fun `an existing user who does not enable the module sees four destinations and no project UI`() =
        runTest {
            // Upgrading brings the feature flag in defaulting to off.
            settingsRepo.setSettings(settings(paidProjects = false))
            // Their existing hourly data is untouched by any of this.
            shiftsRepo.setShifts(
                Shift(
                    id = "s1",
                    userId = "u1",
                    startTime = Instant.parse("2026-07-20T09:00:00Z"),
                    endTime = Instant.parse("2026-07-20T17:00:00Z"),
                ),
            )
            val vm = buildVm()
            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(ProjectsUiState.Disabled, vm.uiState.value)
            assertEquals(4, BottomNavItem.visibleItems(paidProjectsEnabled = false).size)
            // The shift is still an ordinary paid hourly shift.
            val shift = shiftsRepo.currentShifts.single()
            assertTrue(shift.isEmployeePaid)
            assertNull(shift.projectId)
            job.cancel()
        }

    @Test
    fun `the flag defaults to off, so an upgrade cannot switch the module on`() {
        val fresh = UserSettings(
            id = "cfg",
            userId = "u1",
            timezone = "UTC",
            hourlyRate = 100.0,
            currency = CurrencyCode.ILS,
            currencyCode = "ILS",
            regionCode = RegionCode.IL,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

        assertEquals(false, fresh.featuresPaidProjects)
    }

    // ── Journey 2: existing user who enables it and runs the full lifecycle ──

    @Test
    fun `the full lifecycle from enabling to final payment and back again`() = runTest {
        // 1. Enable through settings.
        settingsRepo.setSettings(settings(paidProjects = true, taxRateBasisPoints = 1800))
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // 2. Navigation gains a destination.
        assertEquals(5, BottomNavItem.visibleItems(paidProjectsEnabled = true).size)
        assertTrue(vm.uiState.value is ProjectsUiState.Ready)

        // 3. Create a project.
        var createdId: String? = null
        vm.saveProject(formInput(), existing = null) { createdId = it }
        advanceUntilIdle()
        assertNotNull(createdId)
        val project = projectsRepo.currentProjects.single()
        assertEquals(Money.of("10000.00", "USD"), project.fee.base)
        assertEquals(Money.of("1800.00", "USD"), project.fee.tax)
        assertEquals(Money.of("11800.00", "USD"), project.fee.clientTotal)

        // 4. Track project time against it. The shift earns no wage.
        shiftsRepo.setShifts(
            Shift(
                id = "ps1",
                userId = "u1",
                startTime = Instant.parse("2026-07-20T09:00:00Z"),
                endTime = Instant.parse("2026-07-20T17:00:00Z"),
                compensationSource = CompensationSource.PROJECT,
                projectId = project.id,
                projectNameSnapshot = project.name,
            ),
        )
        advanceUntilIdle()
        val projectShift = shiftsRepo.currentShifts.single()
        assertTrue(projectShift.isProjectTime)
        assertNull("project time must carry no wage snapshot", projectShift.compensationSnapshot)

        // 5. Record billing.
        vm.recordBilling(
            ProjectBillingFormInput(
                projectId = project.id,
                baseAmount = "10000",
                currencyCode = "USD",
                taxLabel = null,
                taxRatePercent = "18",
                taxMode = TaxMode.EXCLUSIVE,
                billedOn = LocalDate.of(2026, 7, 21),
                dueOn = LocalDate.of(2026, 8, 20),
                externalReference = "2026-014",
            ),
        )
        advanceUntilIdle()
        val record = projectsRepo.currentBillingRecords.single()
        assertEquals(Money.of("11800.00", "USD"), record.fee.clientTotal)

        // 6. A partial payment leaves the rest outstanding.
        vm.savePayment(
            ProjectPaymentFormInput(
                projectId = project.id,
                billingRecordId = record.id,
                amount = "5000",
                currencyCode = "USD",
                paidOn = LocalDate.of(2026, 7, 25),
                method = PaymentMethod.BANK_TRANSFER,
            ),
            billingRecord = record,
            existingPayments = projectsRepo.currentPayments,
        )
        advanceUntilIdle()
        var state = ProjectBillingStatusResolver.resolve(
            currencyCode = "USD",
            billingRecords = projectsRepo.currentBillingRecords,
            payments = projectsRepo.currentPayments,
            today = LocalDate.of(2026, 7, 26),
        )
        assertEquals(ProjectBillingStatus.PARTIALLY_PAID, state.status)
        assertEquals(Money.of("6800.00", "USD"), state.outstanding)

        // 7. The final payment settles it, and Paid is derived, never toggled.
        vm.savePayment(
            ProjectPaymentFormInput(
                projectId = project.id,
                billingRecordId = record.id,
                amount = "6800",
                currencyCode = "USD",
                paidOn = LocalDate.of(2026, 8, 1),
                method = PaymentMethod.BANK_TRANSFER,
            ),
            billingRecord = record,
            existingPayments = projectsRepo.currentPayments,
        )
        advanceUntilIdle()
        state = ProjectBillingStatusResolver.resolve(
            currencyCode = "USD",
            billingRecords = projectsRepo.currentBillingRecords,
            payments = projectsRepo.currentPayments,
            today = LocalDate.of(2026, 8, 2),
        )
        assertEquals(ProjectBillingStatus.PAID, state.status)
        assertTrue(state.outstanding.isZero)
        assertEquals(2, projectsRepo.currentPayments.size)

        // 8. Disable the module. Navigation returns to four; nothing is deleted.
        settingsRepo.setSettings(settings(paidProjects = false))
        advanceUntilIdle()
        assertEquals(ProjectsUiState.Disabled, vm.uiState.value)
        assertEquals(4, BottomNavItem.visibleItems(paidProjectsEnabled = false).size)
        assertEquals(1, projectsRepo.currentProjects.size)
        assertEquals(1, projectsRepo.currentBillingRecords.size)
        assertEquals(2, projectsRepo.currentPayments.size)

        // 9. Re-enable. Everything comes back exactly as it was.
        settingsRepo.setSettings(settings(paidProjects = true, taxRateBasisPoints = 1800))
        advanceUntilIdle()
        val restored = ready(vm).allProjects.single()
        assertEquals(project.id, restored.project.id)
        assertEquals(Money.of("11800.00", "USD"), restored.project.fee.clientTotal)
        assertEquals(ProjectBillingStatus.PAID, restored.billing.status)
        job.cancel()
    }

    // ── Journey 3: new user who skips the module ─────────────────────────────

    @Test
    fun `a new user who chooses Not now gets four destinations and can enable later`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = false))
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(ProjectsUiState.Disabled, vm.uiState.value)
        assertEquals(4, BottomNavItem.visibleItems(paidProjectsEnabled = false).size)

        // Later, from Settings.
        settingsRepo.setSettings(settings(paidProjects = true))
        advanceUntilIdle()

        assertTrue(vm.uiState.value is ProjectsUiState.Ready)
        assertEquals(5, BottomNavItem.visibleItems(paidProjectsEnabled = true).size)
        job.cancel()
    }

    // ── Journey 4: new user who enables it during onboarding, no tax ─────────

    @Test
    fun `a new user who enables the module and skips tax can bill a tax-free project`() = runTest {
        // Enabled during onboarding, default currency chosen, tax setup skipped.
        settingsRepo.setSettings(settings(paidProjects = true, currency = "EUR", taxRateBasisPoints = 0))
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // The new-project form starts from those defaults, with tax off.
        val seeded = vm.newProjectInput(settingsRepo.getSettings("u1"))
        assertEquals("EUR", seeded.currencyCode)
        assertEquals(false, seeded.taxEnabled)

        vm.saveProject(
            formInput(currency = "EUR", amount = "4000", taxEnabled = false, taxRate = ""),
            existing = null,
        )
        advanceUntilIdle()
        val project = projectsRepo.currentProjects.single()

        // With no tax, one amount rather than the same number under three names.
        assertEquals(TaxMode.NONE, project.fee.taxMode)
        assertTrue(project.fee.tax.isZero)
        assertEquals(project.fee.base, project.fee.clientTotal)

        vm.recordBilling(
            ProjectBillingFormInput(
                projectId = project.id,
                baseAmount = "4000",
                currencyCode = "EUR",
                taxLabel = null,
                taxRatePercent = "0",
                taxMode = TaxMode.NONE,
                billedOn = LocalDate.of(2026, 7, 21),
            ),
        )
        advanceUntilIdle()
        val record = projectsRepo.currentBillingRecords.single()

        vm.savePayment(
            ProjectPaymentFormInput(
                projectId = project.id,
                billingRecordId = record.id,
                amount = "4000",
                currencyCode = "EUR",
                paidOn = LocalDate.of(2026, 7, 30),
                method = PaymentMethod.CASH,
            ),
            billingRecord = record,
            existingPayments = projectsRepo.currentPayments,
        )
        advanceUntilIdle()

        val state = ProjectBillingStatusResolver.resolve(
            currencyCode = "EUR",
            billingRecords = projectsRepo.currentBillingRecords,
            payments = projectsRepo.currentPayments,
            today = LocalDate.of(2026, 7, 31),
        )
        assertEquals(ProjectBillingStatus.PAID, state.status)
        job.cancel()
    }

    // ── Journey 5: tax-exclusive, and the snapshot that must not move ────────

    @Test
    fun `editing a project after billing leaves the billing snapshot untouched`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = true, taxRateBasisPoints = 1800))
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.saveProject(formInput(amount = "10000", taxRate = "18"), existing = null)
        advanceUntilIdle()
        val project = projectsRepo.currentProjects.single()

        vm.recordBilling(
            ProjectBillingFormInput(
                projectId = project.id,
                baseAmount = "10000",
                currencyCode = "USD",
                taxLabel = null,
                taxRatePercent = "18",
                taxMode = TaxMode.EXCLUSIVE,
                billedOn = LocalDate.of(2026, 7, 21),
                externalReference = "2026-014",
            ),
        )
        advanceUntilIdle()
        val snapshotBefore = projectsRepo.currentBillingRecords.single()

        // Renegotiate the fee upwards and change the tax rate.
        vm.saveProject(
            formInput(amount = "20000", taxRate = "25"),
            existing = projectsRepo.currentProjects.single(),
        )
        advanceUntilIdle()

        // The project moved; what was billed did not. A bill already sent to a
        // client is a historical fact.
        assertEquals(Money.of("20000.00", "USD"), projectsRepo.currentProjects.single().fee.base)
        val snapshotAfter = projectsRepo.currentBillingRecords.single()
        assertEquals(snapshotBefore.fee.base, snapshotAfter.fee.base)
        assertEquals(snapshotBefore.fee.tax, snapshotAfter.fee.tax)
        assertEquals(snapshotBefore.fee.clientTotal, snapshotAfter.fee.clientTotal)
        assertEquals(snapshotBefore.fee.taxRatePercent, snapshotAfter.fee.taxRatePercent)
        assertEquals(Money.of("11800.00", "USD"), snapshotAfter.fee.clientTotal)
        assertEquals("2026-014", snapshotAfter.externalReference)
        job.cancel()
    }

    // ── Journey 6: tax-inclusive, including a repeating decimal ──────────────

    @Test
    fun `a tax-inclusive project extracts a base that adds back to the exact total`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = true, taxRateBasisPoints = 1700, taxInclusive = true))
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // The user knows the client total and works backwards. 1000/1.17 does not
        // terminate, which is exactly the case that must still add up.
        vm.saveProject(
            formInput(
                amount = "1000",
                entryMode = ProjectAmountEntryMode.CLIENT_TOTAL,
                taxRate = "17",
            ),
            existing = null,
        )
        advanceUntilIdle()
        val fee = projectsRepo.currentProjects.single().fee

        assertEquals(Money.of("1000.00", "USD"), fee.clientTotal)
        assertEquals(Money.of("854.70", "USD"), fee.base)
        assertEquals(Money.of("145.30", "USD"), fee.tax)
        // The property that matters: the displayed figures add up exactly.
        assertEquals(fee.clientTotal, fee.base + fee.tax)
        job.cancel()
    }

    // ── Journey 8: an active project shift when the module is switched off ───

    @Test
    fun `disabling the module during a project shift neither loses it nor pays it`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = true))
        projectsRepo.setProjects(
            com.elmtrackr.app.domain.model.Project(
                id = "p1",
                userId = "u1",
                name = "Website rebuild",
                clientName = "Acme Ltd",
                workStatus = ProjectWorkStatus.ACTIVE,
                fee = com.elmtrackr.app.domain.money.ProjectFee.from(
                    BigDecimal("10000"), "USD", TaxMode.EXCLUSIVE, BigDecimal("18"),
                ),
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
        // An open project shift: no end time yet.
        shiftsRepo.setShifts(
            Shift(
                id = "open",
                userId = "u1",
                startTime = Instant.parse("2026-07-20T09:00:00Z"),
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

        // The shift survives, still project time, still unpaid, still linked.
        val shift = shiftsRepo.currentShifts.single()
        assertNull(shift.endTime)
        assertEquals(CompensationSource.PROJECT, shift.compensationSource)
        assertTrue(shift.isProjectTime)
        assertNull("switching the module off must not create a wage", shift.compensationSnapshot)
        assertEquals("p1", shift.projectId)
        // And the project screens are closed while it is off.
        assertEquals(ProjectsUiState.Disabled, vm.uiState.value)
        job.cancel()
    }

    @Test
    fun `re-enabling after an open project shift finds it unchanged`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = true))
        shiftsRepo.setShifts(
            Shift(
                id = "open",
                userId = "u1",
                startTime = Instant.parse("2026-07-20T09:00:00Z"),
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
        settingsRepo.setSettings(settings(paidProjects = true))
        advanceUntilIdle()

        val shift = shiftsRepo.currentShifts.single()
        assertEquals(CompensationSource.PROJECT, shift.compensationSource)
        assertEquals("p1", shift.projectId)
        assertNull(shift.compensationSnapshot)
        job.cancel()
    }

    // ── Work status and billing status never drive each other ────────────────

    @Test
    fun `completing the work does not record a payment`() = runTest {
        settingsRepo.setSettings(settings(paidProjects = true))
        val vm = buildVm()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.saveProject(formInput(), existing = null)
        advanceUntilIdle()
        vm.applyWorkAction(projectsRepo.currentProjects.single(), ProjectWorkAction.COMPLETE)
        advanceUntilIdle()

        assertEquals(ProjectWorkStatus.COMPLETED, projectsRepo.currentProjects.single().workStatus)
        assertTrue("completing work must not invent money", projectsRepo.currentPayments.isEmpty())
        assertTrue(projectsRepo.currentBillingRecords.isEmpty())
        job.cancel()
    }
}
