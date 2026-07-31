package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.PaymentMethod
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * The billing and payment workflow end to end, at the domain level.
 *
 * Two invariants run through every case: the displayed amounts reconcile exactly
 * (base + tax == total, and billed − paid == outstanding), and a billing snapshot
 * is never restated by anything that happens afterwards.
 */
class ProjectBillingWorkflowTest {

    private val today = LocalDate.of(2026, 7, 29)

    private fun project(
        entered: String = "10000",
        mode: TaxMode = TaxMode.EXCLUSIVE,
        rate: String = "18",
        currency: String = "USD",
        deadline: LocalDate? = null,
    ) = Project(
        id = "p1",
        userId = "u1",
        name = "Website rebuild",
        clientName = "Acme Ltd",
        workStatus = ProjectWorkStatus.ACTIVE,
        fee = ProjectFee.from(BigDecimal(entered), currency, mode, BigDecimal(rate)),
        deadline = deadline,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun record(
        id: String = "b1",
        entered: String = "10000",
        mode: TaxMode = TaxMode.EXCLUSIVE,
        rate: String = "18",
        currency: String = "USD",
        billedOn: LocalDate = today.minusDays(10),
        dueOn: LocalDate? = today.plusDays(10),
        cancelledAt: Instant? = null,
    ) = ProjectBillingRecord(
        id = id,
        userId = "u1",
        projectId = "p1",
        fee = ProjectFee.from(BigDecimal(entered), currency, mode, BigDecimal(rate)),
        billedOn = billedOn,
        dueOn = dueOn,
        cancelledAt = cancelledAt,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun payment(
        id: String,
        amount: String,
        currency: String = "USD",
        recordId: String = "b1",
        paidOn: LocalDate = today,
    ) = ProjectPayment(
        id = id,
        userId = "u1",
        projectId = "p1",
        billingRecordId = recordId,
        paidOn = paidOn,
        amount = Money.of(amount, currency),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun resolve(
        records: List<ProjectBillingRecord>,
        payments: List<ProjectPayment> = emptyList(),
        on: LocalDate = today,
        currency: String = "USD",
    ) = ProjectBillingStatusResolver.resolve(currency, records, payments, on)

    // ── Marking a project billed ──────────────────────────────────────────────

    @Test
    fun `the billing form defaults to the project's agreed fee`() {
        val input = ProjectBillingFormInput.from(project(), today)

        assertEquals("10000.00", input.baseAmount)
        assertEquals("USD", input.currencyCode)
        assertEquals("18", input.taxRatePercent)
        assertEquals(TaxMode.EXCLUSIVE, input.taxMode)
        assertEquals(today, input.billedOn)
    }

    @Test
    fun `the due date defaults to the project deadline when it has one`() {
        val deadline = today.plusDays(30)
        assertEquals(deadline, ProjectBillingFormInput.from(project(deadline = deadline), today).dueOn)
    }

    @Test
    fun `a valid form produces a fee whose parts reconcile exactly`() {
        val result = ProjectBillingFormValidator.validate(
            ProjectBillingFormInput.from(project(), today),
        )

        val fee = (result as BillingFormResult.Valid).fee
        assertEquals(Money.of("10000.00", "USD"), fee.base)
        assertEquals(Money.of("1800.00", "USD"), fee.tax)
        assertEquals(Money.of("11800.00", "USD"), fee.clientTotal)
        assertEquals(fee.clientTotal, fee.base + fee.tax)
    }

    @Test
    fun `the user may bill a different amount than the project says`() {
        val input = ProjectBillingFormInput.from(project(), today).copy(baseAmount = "8000")
        val fee = (ProjectBillingFormValidator.validate(input) as BillingFormResult.Valid).fee

        assertEquals(Money.of("8000.00", "USD"), fee.base)
        assertEquals(Money.of("9440.00", "USD"), fee.clientTotal)
    }

    @Test
    fun `an empty, non-numeric, zero or negative amount is refused`() {
        val base = ProjectBillingFormInput.from(project(), today)
        listOf("", "abc", "0", "-5").forEach { text ->
            val result = ProjectBillingFormValidator.validate(base.copy(baseAmount = text))
            assertTrue(text, result is BillingFormResult.Invalid)
            assertTrue(
                text,
                (result as BillingFormResult.Invalid)
                    .errors
                    .containsKey(ProjectBillingFormValidator.FIELD_AMOUNT),
            )
        }
    }

    @Test
    fun `a due date before the billing date is refused, but the same day is fine`() {
        val base = ProjectBillingFormInput.from(project(), today).copy(billedOn = today)

        val before = ProjectBillingFormValidator.validate(base.copy(dueOn = today.minusDays(1)))
        assertEquals(
            BillingFormError.DUE_BEFORE_BILLED,
            (before as BillingFormResult.Invalid).errors[ProjectBillingFormValidator.FIELD_DUE_ON],
        )

        // Due on receipt is a normal term.
        assertTrue(ProjectBillingFormValidator.validate(base.copy(dueOn = today)) is BillingFormResult.Valid)
    }

    @Test
    fun `a tax rate outside zero to one hundred is refused`() {
        val base = ProjectBillingFormInput.from(project(), today)
        listOf("-1", "101", "abc").forEach { rate ->
            val result = ProjectBillingFormValidator.validate(base.copy(taxRatePercent = rate))
            assertTrue(rate, result is BillingFormResult.Invalid)
        }
        // Blank means no tax rather than an error.
        val blank = ProjectBillingFormValidator.validate(base.copy(taxRatePercent = ""))
        assertEquals(Money.zero("USD"), (blank as BillingFormResult.Valid).fee.tax)
    }

    // ── The snapshot ──────────────────────────────────────────────────────────

    @Test
    fun `editing the project afterwards does not restate the billed amounts`() {
        val billed = record()

        // The project's price doubles after billing.
        val repriced = project(entered = "20000")

        // The record is unchanged: it is a snapshot, not a view of the project.
        assertEquals(Money.of("10000.00", "USD"), billed.fee.base)
        assertEquals(Money.of("11800.00", "USD"), billed.fee.clientTotal)
        assertEquals(Money.of("20000.00", "USD"), repriced.fee.base)
        assertEquals(billed.fee.clientTotal, resolve(listOf(billed)).billedTotal)
    }

    @Test
    fun `changing the project tax rate afterwards does not restate the billed tax`() {
        val billed = record(rate = "18")
        val retaxed = project(rate = "25")

        assertEquals(Money.of("1800.00", "USD"), billed.fee.tax)
        assertEquals(BigDecimal("18"), billed.fee.taxRatePercent)
        assertEquals(BigDecimal("25"), retaxed.fee.taxRatePercent)
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    @Test
    fun `a partial payment leaves the project partially paid`() {
        val state = resolve(listOf(record()), listOf(payment("pay1", "5000.00")))

        assertEquals(ProjectBillingStatus.PARTIALLY_PAID, state.status)
        assertEquals(Money.of("5000.00", "USD"), state.paid)
        assertEquals(Money.of("6800.00", "USD"), state.outstanding)
        // The figures add up exactly.
        assertEquals(state.billedTotal, state.paid + state.outstanding)
    }

    @Test
    fun `multiple payments accumulate`() {
        val state = resolve(
            listOf(record()),
            listOf(payment("p1", "5000.00"), payment("p2", "3000.00"), payment("p3", "1000.00")),
        )

        assertEquals(Money.of("9000.00", "USD"), state.paid)
        assertEquals(Money.of("2800.00", "USD"), state.outstanding)
        assertEquals(ProjectBillingStatus.PARTIALLY_PAID, state.status)
    }

    @Test
    fun `paying the full billed total flips the status to paid automatically`() {
        val state = resolve(listOf(record()), listOf(payment("p1", "11800.00")))

        assertEquals(ProjectBillingStatus.PAID, state.status)
        assertEquals(Money.zero("USD"), state.outstanding)
        assertFalse(state.isOverdue)
    }

    @Test
    fun `a late but fully paid project reads paid, not overdue`() {
        val state = resolve(
            listOf(record(dueOn = today.minusDays(30))),
            listOf(payment("p1", "11800.00")),
        )
        assertEquals(ProjectBillingStatus.PAID, state.status)
        assertFalse(state.isOverdue)
        assertNull(state.daysOverdue)
    }

    @Test
    fun `an overpayment is refused rather than silently recorded`() {
        val result = ProjectPaymentValidator.validate(
            amount = Money.of("11800.01", "USD"),
            billingRecord = record(),
            existingPayments = emptyList(),
        )

        val rejection = result.rejection as PaymentRejection.Overpayment
        assertEquals(Money.of("11800.00", "USD"), rejection.maximumAllowed)
    }

    @Test
    fun `a payment that would exceed the remaining balance is refused`() {
        val result = ProjectPaymentValidator.validate(
            amount = Money.of("7000.00", "USD"),
            billingRecord = record(),
            existingPayments = listOf(payment("p1", "5000.00")),
        )

        val rejection = result.rejection as PaymentRejection.Overpayment
        assertEquals(Money.of("6800.00", "USD"), rejection.maximumAllowed)
        assertEquals(Money.of("5000.00", "USD"), rejection.alreadyPaid)
    }

    @Test
    fun `paying exactly the remaining balance is allowed`() {
        val result = ProjectPaymentValidator.validate(
            amount = Money.of("6800.00", "USD"),
            billingRecord = record(),
            existingPayments = listOf(payment("p1", "5000.00")),
        )
        assertTrue(result.isValid)
    }

    @Test
    fun `zero and negative payments are refused`() {
        assertEquals(
            PaymentRejection.ZeroAmount,
            ProjectPaymentValidator.validate(Money.zero("USD"), record(), emptyList()).rejection,
        )
        assertEquals(
            PaymentRejection.NegativeAmount,
            ProjectPaymentValidator.validate(Money.of("-1", "USD"), record(), emptyList()).rejection,
        )
    }

    @Test
    fun `a payment in another currency is refused rather than converted`() {
        val rejection = ProjectPaymentValidator
            .validate(Money.of("100", "EUR"), record(currency = "USD"), emptyList())
            .rejection as PaymentRejection.CurrencyMismatch

        assertEquals("EUR", rejection.paymentCurrency)
        assertEquals("USD", rejection.billingCurrency)
    }

    @Test
    fun `a payment without a billing record is refused`() {
        assertEquals(
            PaymentRejection.BillingRecordUnavailable,
            ProjectPaymentValidator.validate(Money.of("100", "USD"), null, emptyList()).rejection,
        )
    }

    @Test
    fun `deleting a payment recalculates the balance and the status`() {
        val payments = listOf(payment("p1", "5000.00"), payment("p2", "6800.00"))
        assertEquals(ProjectBillingStatus.PAID, resolve(listOf(record()), payments).status)

        val afterDelete = resolve(listOf(record()), payments.filterNot { it.id == "p2" })
        assertEquals(ProjectBillingStatus.PARTIALLY_PAID, afterDelete.status)
        assertEquals(Money.of("6800.00", "USD"), afterDelete.outstanding)
    }

    @Test
    fun `the payment form defaults to the outstanding balance`() {
        val input = ProjectPaymentFormInput.forRecord(
            record(),
            listOf(payment("p1", "5000.00")),
            today,
        )
        assertEquals("6800.00", input.amount)
        assertEquals("USD", input.currencyCode)
    }

    @Test
    fun `re-saving an existing payment unchanged is not an overpayment against itself`() {
        val existing = payment("p1", "11800.00")
        val result = ProjectPaymentFormValidator.validate(
            ProjectPaymentFormInput.from(existing),
            record(),
            listOf(existing),
        )
        assertTrue(result is PaymentFormResult.Valid)
    }

    @Test
    fun `a non-numeric payment amount is reported rather than parsed as zero`() {
        val result = ProjectPaymentFormValidator.validate(
            ProjectPaymentFormInput.forRecord(record(), emptyList(), today).copy(amount = "abc"),
            record(),
            emptyList(),
        )
        assertEquals(PaymentFormResult.NotANumber, result)
    }

    // ── Outstanding amount ────────────────────────────────────────────────────

    @Test
    fun `outstanding never goes negative`() {
        // Payments beyond the billed total can only exist in data that predates
        // validation; the balance still must not read as a negative debt.
        val state = resolve(listOf(record()), listOf(payment("p1", "20000.00")))

        assertEquals(Money.zero("USD"), state.outstanding)
        assertEquals(Money.of("8200.00", "USD"), state.credit)
        assertEquals(ProjectBillingStatus.PAID, state.status)
    }

    @Test
    fun `a repeating decimal keeps the three amounts reconciled`() {
        // 10000 including 3% has no exact base; the tax is the residual so that
        // base + tax == total to the cent.
        val inclusive = record(entered = "10000", mode = TaxMode.INCLUSIVE, rate = "3")
        val fee = inclusive.fee

        assertEquals(Money.of("10000.00", "USD"), fee.clientTotal)
        assertEquals(fee.clientTotal, fee.base + fee.tax)

        val state = resolve(listOf(inclusive), listOf(payment("p1", "3333.33")))
        assertEquals(state.billedTotal, state.paid + state.outstanding)
    }

    @Test
    fun `tax stays separate from the base project revenue`() {
        val fee = record().fee
        // The user's revenue is the base; the tax is collected for the authority.
        assertEquals(Money.of("10000.00", "USD"), fee.base)
        assertEquals(Money.of("1800.00", "USD"), fee.tax)
        assertTrue(fee.clientTotal > fee.base)
    }

    // ── Overdue ───────────────────────────────────────────────────────────────

    @Test
    fun `due today is not overdue`() {
        val state = resolve(listOf(record(dueOn = today)))

        assertFalse(state.isOverdue)
        assertEquals(ProjectBillingStatus.BILLED, state.status)
        assertEquals(0L, state.daysUntilDue)
        assertNull(state.daysOverdue)
    }

    @Test
    fun `a record due yesterday is one day overdue`() {
        val state = resolve(listOf(record(dueOn = today.minusDays(1))))

        assertTrue(state.isOverdue)
        assertEquals(ProjectBillingStatus.OVERDUE, state.status)
        assertEquals(1L, state.daysOverdue)
    }

    @Test
    fun `days overdue counts calendar days across a month boundary`() {
        val state = resolve(
            listOf(record(billedOn = LocalDate.of(2026, 6, 1), dueOn = LocalDate.of(2026, 6, 30))),
            on = LocalDate.of(2026, 7, 29),
        )
        assertEquals(29L, state.daysOverdue)
    }

    @Test
    fun `overdue outranks partially paid but the partial figures stay available`() {
        val state = resolve(
            listOf(record(dueOn = today.minusDays(5))),
            listOf(payment("p1", "5000.00")),
        )

        assertEquals(ProjectBillingStatus.OVERDUE, state.status)
        assertEquals(Money.of("5000.00", "USD"), state.paid)
        assertEquals(Money.of("6800.00", "USD"), state.outstanding)
    }

    @Test
    fun `a record with no due date never goes overdue`() {
        val state = resolve(listOf(record(dueOn = null)))

        assertFalse(state.isOverdue)
        assertNull(state.daysUntilDue)
        assertEquals(ProjectBillingStatus.BILLED, state.status)
    }

    @Test
    fun `the overdue boundary is date-only, so it does not shift with the clock`() {
        // Whatever time of day it is, "overdue" flips exactly once, at the
        // calendar boundary in the user's work timezone.
        val due = LocalDate.of(2026, 7, 29)
        assertFalse(resolve(listOf(record(dueOn = due)), on = due).isOverdue)
        assertTrue(resolve(listOf(record(dueOn = due)), on = due.plusDays(1)).isOverdue)
    }

    @Test
    fun `a cancelled record produces neither a balance nor an overdue state`() {
        val cancelled = record(dueOn = today.minusDays(30), cancelledAt = Instant.EPOCH)
        val state = resolve(listOf(cancelled))

        assertEquals(ProjectBillingStatus.NOT_BILLED, state.status)
        assertNull(state.billedTotal)
        assertFalse(state.isOverdue)
        assertNull(state.activeBillingRecordId)
    }

    @Test
    fun `payments against a cancelled record do not offset a live one`() {
        val state = resolve(
            listOf(
                record(id = "old", cancelledAt = Instant.EPOCH),
                record(id = "new", entered = "5000"),
            ),
            listOf(payment("p1", "1000.00", recordId = "old")),
        )

        assertEquals(Money.of("5900.00", "USD"), state.billedTotal)
        assertEquals(Money.zero("USD"), state.paid)
        assertEquals(Money.of("5900.00", "USD"), state.outstanding)
    }

    // ── Correction ────────────────────────────────────────────────────────────

    @Test
    fun `a record with no payments can be cancelled and re-recorded`() {
        assertTrue(ProjectBillingCorrection.canCancel(record(), emptyList()).isAllowed)
    }

    @Test
    fun `a record with payments cannot be replaced`() {
        val verdict = ProjectBillingCorrection.canCancel(record(), listOf(payment("p1", "100.00")))

        assertEquals(ProjectBillingCorrection.Block.HAS_PAYMENTS, verdict.blockedReason)
    }

    @Test
    fun `a payment against another record does not block the correction`() {
        val verdict = ProjectBillingCorrection.canCancel(
            record(id = "b1"),
            listOf(payment("p1", "100.00", recordId = "b2")),
        )
        assertTrue(verdict.isAllowed)
    }

    @Test
    fun `an already cancelled record reports that rather than allowing it twice`() {
        val verdict = ProjectBillingCorrection.canCancel(
            record(cancelledAt = Instant.EPOCH),
            emptyList(),
        )
        assertEquals(ProjectBillingCorrection.Block.ALREADY_CANCELLED, verdict.blockedReason)
    }

    @Test
    fun `deleting a record follows the same rule as cancelling it`() {
        val withPayments = listOf(payment("p1", "100.00"))
        assertEquals(
            ProjectBillingCorrection.canCancel(record(), withPayments).blockedReason,
            ProjectBillingCorrection.canDelete(record(), withPayments).blockedReason,
        )
    }

    @Test
    fun `billing is offered only while no active record exists`() {
        assertTrue(ProjectBillingCorrection.canRecordBilling(emptyList()))
        assertFalse(ProjectBillingCorrection.canRecordBilling(listOf(record())))
        // A cancelled record does not block a corrected one.
        assertTrue(
            ProjectBillingCorrection.canRecordBilling(listOf(record(cancelledAt = Instant.EPOCH))),
        )
    }

    // ── Payment methods ───────────────────────────────────────────────────────

    @Test
    fun `every payment method round-trips and unknown values become other`() {
        PaymentMethod.entries.forEach { method ->
            assertEquals(method, PaymentMethod.fromPersisted(method.name))
            assertEquals(method, PaymentMethod.fromPersisted(method.toWire()))
        }
        assertEquals(PaymentMethod.OTHER, PaymentMethod.fromPersisted("CRYPTO"))
        // Absent stays absent: the method is optional.
        assertNull(PaymentMethod.fromPersisted(null))
        assertNull(PaymentMethod.fromPersisted(""))
    }

    @Test
    fun `all seven methods are offered`() {
        assertEquals(7, PaymentMethod.selectable.size)
        assertTrue(PaymentMethod.selectable.contains(PaymentMethod.BANK_TRANSFER))
        assertTrue(PaymentMethod.selectable.contains(PaymentMethod.OTHER))
    }

    // ── Project completion ────────────────────────────────────────────────────

    @Test
    fun `completing a project records no payment and does not change billing status`() {
        val active = project()
        val completed = ProjectWorkStatusActions.apply(
            active,
            ProjectWorkAction.COMPLETE,
            Instant.EPOCH,
            today,
        )

        assertEquals(ProjectWorkStatus.COMPLETED, completed.workStatus)
        // Same billing inputs, so the same derived status: completion is work, not money.
        assertEquals(
            resolve(listOf(record())).status,
            resolve(listOf(record())).status,
        )
        assertEquals(ProjectBillingStatus.BILLED, resolve(listOf(record())).status)
    }
}
