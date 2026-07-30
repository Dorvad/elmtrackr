package com.elmtrackr.app.ui.projects

import com.elmtrackr.app.domain.model.PaymentMethod
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import com.elmtrackr.app.domain.projects.BillingFormError
import com.elmtrackr.app.domain.projects.PaymentRejection
import com.elmtrackr.app.domain.projects.ProjectBillingCorrection
import com.elmtrackr.app.domain.projects.ProjectBillingFormValidator
import com.elmtrackr.app.domain.projects.ProjectPaymentFormInput
import com.elmtrackr.app.fake.FakeCurrentUserProvider
import com.elmtrackr.app.fake.FakeProjectsRepository
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Recording billing details and payments through the ViewModel.
 *
 * The point of testing at this level rather than only in the domain is the
 * write path: what actually lands in the repository, and that a rejected write
 * lands nothing at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectBillingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val projectsRepo = FakeProjectsRepository()
    private val settingsRepo = FakeSettingsRepository()
    private val shiftsRepo = FakeShiftsRepository()
    private val currentUser = FakeCurrentUserProvider()

    private fun buildVm() = ProjectsViewModel(projectsRepo, settingsRepo, shiftsRepo, currentUser)

    private fun seed() {
        currentUser.setUserId("u1")
        settingsRepo.setSettings(
            UserSettings(
                id = "settings",
                userId = "u1",
                timezone = "Asia/Jerusalem",
                featuresPaidProjects = true,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
    }

    private fun project() = Project(
        id = "p1",
        userId = "u1",
        name = "Website rebuild",
        clientName = "Acme Ltd",
        workStatus = ProjectWorkStatus.ACTIVE,
        fee = ProjectFee.from(BigDecimal("10000"), "USD", TaxMode.EXCLUSIVE, BigDecimal("18")),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun record(id: String = "b1", dueOn: LocalDate? = null) = ProjectBillingRecord(
        id = id,
        userId = "u1",
        projectId = "p1",
        fee = ProjectFee.from(BigDecimal("10000"), "USD", TaxMode.EXCLUSIVE, BigDecimal("18")),
        billedOn = LocalDate.of(2026, 7, 1),
        dueOn = dueOn,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    // ── Recording billing ─────────────────────────────────────────────────────

    @Test
    fun `recording billing stores a snapshot of the fee as billed`() = runTest {
        seed()
        projectsRepo.setProjects(project())
        val vm = buildVm()

        val input = vm.newBillingInput(project())
        var saved = false
        vm.recordBilling(input) { saved = true }
        advanceUntilIdle()

        assertTrue(saved)
        val stored = projectsRepo.currentBillingRecords.single()
        assertEquals("p1", stored.projectId)
        assertEquals(Money.of("10000.00", "USD"), stored.fee.base)
        assertEquals(Money.of("1800.00", "USD"), stored.fee.tax)
        assertEquals(Money.of("11800.00", "USD"), stored.fee.clientTotal)
    }

    @Test
    fun `the reference and notes are stored, and blanks become null`() = runTest {
        seed()
        projectsRepo.setProjects(project())
        val vm = buildVm()

        vm.recordBilling(
            vm.newBillingInput(project()).copy(externalReference = " INV-42 ", notes = "   "),
        )
        advanceUntilIdle()

        val stored = projectsRepo.currentBillingRecords.single()
        assertEquals("INV-42", stored.externalReference)
        assertNull(stored.notes)
    }

    @Test
    fun `an invalid amount is reported and nothing is written`() = runTest {
        seed()
        projectsRepo.setProjects(project())
        val vm = buildVm()

        var saved = false
        vm.recordBilling(vm.newBillingInput(project()).copy(baseAmount = "0")) { saved = true }
        advanceUntilIdle()

        assertEquals(false, saved)
        assertTrue(projectsRepo.currentBillingRecords.isEmpty())
        assertEquals(
            BillingFormError.AMOUNT_NOT_POSITIVE,
            vm.billingError.value[ProjectBillingFormValidator.FIELD_AMOUNT],
        )

        vm.clearBillingError()
        assertTrue(vm.billingError.value.isEmpty())
    }

    @Test
    fun `editing the project after billing leaves the snapshot alone`() = runTest {
        seed()
        projectsRepo.setProjects(project())
        val vm = buildVm()
        vm.recordBilling(vm.newBillingInput(project()))
        advanceUntilIdle()

        // Double the project's fee afterwards.
        projectsRepo.upsertProject(
            project().copy(
                fee = ProjectFee.from(BigDecimal("20000"), "USD", TaxMode.EXCLUSIVE, BigDecimal("18")),
            ),
        )
        advanceUntilIdle()

        assertEquals(
            Money.of("11800.00", "USD"),
            projectsRepo.currentBillingRecords.single().fee.clientTotal,
        )
    }

    // ── Correction ────────────────────────────────────────────────────────────

    @Test
    fun `cancelling an unpaid billing record is allowed`() = runTest {
        seed()
        projectsRepo.setProjects(project())
        projectsRepo.setBillingRecords(record())
        val vm = buildVm()

        var blocked: ProjectBillingCorrection.Block? = null
        vm.cancelBilling(record(), emptyList()) { blocked = it }
        advanceUntilIdle()

        assertNull(blocked)
        assertTrue(projectsRepo.currentBillingRecords.single().isCancelled)
    }

    @Test
    fun `cancelling a paid billing record is refused and nothing changes`() = runTest {
        seed()
        projectsRepo.setProjects(project())
        projectsRepo.setBillingRecords(record())
        val payment = ProjectPayment(
            id = "pay1",
            userId = "u1",
            projectId = "p1",
            billingRecordId = "b1",
            paidOn = LocalDate.of(2026, 7, 10),
            amount = Money.of("1000.00", "USD"),
        )
        projectsRepo.setPayments(payment)
        val vm = buildVm()

        var blocked: ProjectBillingCorrection.Block? = null
        vm.cancelBilling(record(), listOf(payment)) { blocked = it }
        advanceUntilIdle()

        assertEquals(ProjectBillingCorrection.Block.HAS_PAYMENTS, blocked)
        // The record survives, and so does the payment history.
        assertEquals(false, projectsRepo.currentBillingRecords.single().isCancelled)
        assertEquals(1, projectsRepo.currentPayments.size)
    }

    @Test
    fun `a cancelled record can be restored`() = runTest {
        seed()
        projectsRepo.setBillingRecords(record().copy(cancelledAt = Instant.EPOCH))
        val vm = buildVm()

        vm.restoreBilling("b1")
        advanceUntilIdle()

        assertEquals(false, projectsRepo.currentBillingRecords.single().isCancelled)
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    @Test
    fun `a partial payment is stored with its method and reference`() = runTest {
        seed()
        projectsRepo.setProjects(project())
        projectsRepo.setBillingRecords(record())
        val vm = buildVm()

        val input = vm.newPaymentInput(record(), emptyList())
            .copy(amount = "5000", method = PaymentMethod.BANK_TRANSFER, externalReference = "TRX-1")
        var saved = false
        vm.savePayment(input, record(), emptyList()) { saved = true }
        advanceUntilIdle()

        assertTrue(saved)
        val stored = projectsRepo.currentPayments.single()
        assertEquals(Money.of("5000.00", "USD"), stored.amount)
        assertEquals("BANK_TRANSFER", stored.method)
        assertEquals("TRX-1", stored.externalReference)
        assertNull(vm.paymentRejection.value)
    }

    @Test
    fun `the payment form defaults to the outstanding balance`() = runTest {
        seed()
        projectsRepo.setBillingRecords(record())
        val vm = buildVm()

        val existing = ProjectPayment(
            id = "pay1",
            userId = "u1",
            projectId = "p1",
            billingRecordId = "b1",
            paidOn = LocalDate.of(2026, 7, 10),
            amount = Money.of("1800.00", "USD"),
        )
        assertEquals("10000.00", vm.newPaymentInput(record(), listOf(existing)).amount)
    }

    @Test
    fun `an overpayment is refused and nothing is written`() = runTest {
        seed()
        projectsRepo.setProjects(project())
        projectsRepo.setBillingRecords(record())
        val vm = buildVm()

        var saved = false
        vm.savePayment(
            vm.newPaymentInput(record(), emptyList()).copy(amount = "99999"),
            record(),
            emptyList(),
        ) { saved = true }
        advanceUntilIdle()

        assertEquals(false, saved)
        assertTrue(projectsRepo.currentPayments.isEmpty())
        assertTrue(vm.paymentRejection.value is PaymentRejection.Overpayment)

        vm.clearPaymentRejection()
        assertNull(vm.paymentRejection.value)
    }

    @Test
    fun `a payment in the wrong currency is refused`() = runTest {
        seed()
        projectsRepo.setBillingRecords(record())
        val vm = buildVm()

        vm.savePayment(
            ProjectPaymentFormInput(
                projectId = "p1",
                billingRecordId = "b1",
                amount = "100",
                currencyCode = "EUR",
                paidOn = LocalDate.of(2026, 7, 10),
            ),
            record(),
            emptyList(),
        )
        advanceUntilIdle()

        assertTrue(vm.paymentRejection.value is PaymentRejection.CurrencyMismatch)
        assertTrue(projectsRepo.currentPayments.isEmpty())
    }

    @Test
    fun `a payment without a billing record is refused`() = runTest {
        seed()
        val vm = buildVm()

        vm.savePayment(
            ProjectPaymentFormInput(
                projectId = "p1",
                billingRecordId = "missing",
                amount = "100",
                currencyCode = "USD",
                paidOn = LocalDate.of(2026, 7, 10),
            ),
            billingRecord = null,
            existingPayments = emptyList(),
        )
        advanceUntilIdle()

        assertEquals(PaymentRejection.BillingRecordUnavailable, vm.paymentRejection.value)
        assertTrue(projectsRepo.currentPayments.isEmpty())
    }

    @Test
    fun `deleting a payment removes it so the balance recalculates`() = runTest {
        seed()
        projectsRepo.setBillingRecords(record())
        val payment = ProjectPayment(
            id = "pay1",
            userId = "u1",
            projectId = "p1",
            billingRecordId = "b1",
            paidOn = LocalDate.of(2026, 7, 10),
            amount = Money.of("5000.00", "USD"),
        )
        projectsRepo.setPayments(payment)
        val vm = buildVm()

        vm.deletePayment("pay1")
        advanceUntilIdle()

        assertTrue(projectsRepo.currentPayments.isEmpty())
    }

    @Test
    fun `several payments settle the bill and the last one is not an overpayment`() = runTest {
        seed()
        projectsRepo.setProjects(project())
        projectsRepo.setBillingRecords(record())
        val vm = buildVm()

        vm.savePayment(
            vm.newPaymentInput(record(), emptyList()).copy(amount = "5000"),
            record(),
            emptyList(),
        )
        advanceUntilIdle()
        val first = projectsRepo.currentPayments
        vm.savePayment(
            vm.newPaymentInput(record(), first).copy(amount = "6800"),
            record(),
            first,
        )
        advanceUntilIdle()

        assertNull(vm.paymentRejection.value)
        assertEquals(2, projectsRepo.currentPayments.size)
        assertEquals(
            Money.of("11800.00", "USD"),
            Money.sum(projectsRepo.currentPayments.map { it.amount }, "USD"),
        )
    }

    @Test
    fun `the newly recorded payment is what makes the project read paid`() = runTest {
        seed()
        projectsRepo.setProjects(project())
        projectsRepo.setBillingRecords(record())
        val vm = buildVm()

        vm.savePayment(
            vm.newPaymentInput(record(), emptyList()),
            record(),
            emptyList(),
        )
        advanceUntilIdle()

        // The form defaulted to the full outstanding balance, so the bill is settled.
        assertNotNull(projectsRepo.currentPayments.singleOrNull())
        assertEquals(Money.of("11800.00", "USD"), projectsRepo.currentPayments.single().amount)
    }
}
