package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.MoneyByCurrency
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * The financial edges, asserted as arithmetic rather than as screens.
 *
 * The single invariant behind almost all of it: whatever the currency, the tax
 * mode or the size of the number, `base + tax == clientTotal` exactly, and the
 * outstanding balance walks to exactly zero and stops there. A cent that appears
 * or disappears in a rounding step is a defect a user will eventually find while
 * reconciling against their bank.
 */
class ProjectFinancialEdgeCaseTest {

    private fun fee(
        amount: String,
        currency: String = "USD",
        mode: TaxMode = TaxMode.EXCLUSIVE,
        rate: String = "18",
    ) = ProjectFee.from(BigDecimal(amount), currency, mode, BigDecimal(rate))

    private fun record(fee: ProjectFee, id: String = "b1") = ProjectBillingRecord(
        id = id,
        userId = "u1",
        projectId = "p1",
        fee = fee,
        billedOn = LocalDate.of(2026, 7, 1),
        dueOn = LocalDate.of(2026, 8, 1),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun payment(amount: String, currency: String = "USD", id: String = "pay") = ProjectPayment(
        id = id,
        userId = "u1",
        projectId = "p1",
        billingRecordId = "b1",
        paidOn = LocalDate.of(2026, 7, 10),
        amount = Money.of(amount, currency),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun resolve(
        fee: ProjectFee,
        payments: List<ProjectPayment>,
        today: LocalDate = LocalDate.of(2026, 7, 15),
    ) = ProjectBillingStatusResolver.resolve(
        currencyCode = fee.currencyCode,
        billingRecords = listOf(record(fee)),
        payments = payments,
        today = today,
    )

    // ── Magnitude ────────────────────────────────────────────────────────────

    @Test
    fun `a very large fee keeps every digit and still adds up`() {
        // Nine hundred billion. Beyond Double's exact integer range, which is
        // why the money layer never sees a Double.
        val large = fee("987654321098765.43")

        assertEquals(BigDecimal("987654321098765.43"), large.base.amount)
        assertEquals(large.clientTotal, large.base + large.tax)
        assertEquals(BigDecimal("177777777797777.78"), large.tax.amount)
    }

    @Test
    fun `a very large amount survives a full payment to exactly zero`() {
        val large = fee("987654321098765.43")
        val state = resolve(large, listOf(payment(large.clientTotal.amount.toPlainString())))

        assertTrue(state.outstanding.isZero)
        assertEquals(ProjectBillingStatus.PAID, state.status)
    }

    @Test
    fun `the smallest representable amount is still a real fee`() {
        val tiny = fee("0.01", mode = TaxMode.NONE, rate = "0")

        assertEquals(BigDecimal("0.01"), tiny.clientTotal.amount)
        assertFalse(tiny.clientTotal.isZero)
    }

    @Test
    fun `a one-cent fee with tax does not round the tax away into nothing`() {
        val tiny = fee("0.01")

        // 18% of a cent is less than half a cent, so the tax rounds to zero and
        // the total must still equal the base rather than gaining a phantom cent.
        assertEquals(tiny.clientTotal, tiny.base + tiny.tax)
        assertEquals(BigDecimal("0.01"), tiny.clientTotal.amount)
    }

    @Test
    fun `a zero fee is coherent rather than an error`() {
        val zero = fee("0", mode = TaxMode.NONE, rate = "0")

        assertTrue(zero.base.isZero)
        assertTrue(zero.tax.isZero)
        assertTrue(zero.clientTotal.isZero)
    }

    // ── Tax modes ────────────────────────────────────────────────────────────

    @Test
    fun `zero tax leaves the base and the total identical`() {
        val none = fee("5000", mode = TaxMode.NONE, rate = "0")

        assertTrue(none.tax.isZero)
        assertEquals(none.base, none.clientTotal)
    }

    @Test
    fun `a zero rate with tax enabled behaves like no tax`() {
        val zeroRate = fee("5000", mode = TaxMode.EXCLUSIVE, rate = "0")

        assertTrue(zeroRate.tax.isZero)
        assertEquals(zeroRate.base, zeroRate.clientTotal)
    }

    @Test
    fun `exclusive tax adds to the fee the user quoted`() {
        val exclusive = fee("10000", mode = TaxMode.EXCLUSIVE, rate = "18")

        assertEquals(Money.of("10000.00", "USD"), exclusive.base)
        assertEquals(Money.of("1800.00", "USD"), exclusive.tax)
        assertEquals(Money.of("11800.00", "USD"), exclusive.clientTotal)
    }

    @Test
    fun `inclusive tax is extracted from the total the client pays`() {
        val inclusive = fee("11800", mode = TaxMode.INCLUSIVE, rate = "18")

        assertEquals(Money.of("11800.00", "USD"), inclusive.clientTotal)
        assertEquals(Money.of("10000.00", "USD"), inclusive.base)
        assertEquals(Money.of("1800.00", "USD"), inclusive.tax)
    }

    @Test
    fun `the two modes agree when given each other's figures`() {
        val exclusive = fee("10000", mode = TaxMode.EXCLUSIVE, rate = "18")
        val inclusive = fee("11800", mode = TaxMode.INCLUSIVE, rate = "18")

        assertEquals(exclusive.base, inclusive.base)
        assertEquals(exclusive.tax, inclusive.tax)
        assertEquals(exclusive.clientTotal, inclusive.clientTotal)
    }

    // ── Repeating decimals ───────────────────────────────────────────────────

    @Test
    fun `an inclusive division that never terminates still adds up exactly`() {
        // 1000 / 1.17 = 854.7008547008547008547... forever.
        val fee = fee("1000", mode = TaxMode.INCLUSIVE, rate = "17")

        assertEquals(Money.of("854.70", "USD"), fee.base)
        assertEquals(Money.of("145.30", "USD"), fee.tax)
        assertEquals(fee.clientTotal, fee.base + fee.tax)
    }

    @Test
    fun `repeating decimals add up at every rate and every amount tried`() {
        // The residual is what guarantees this: tax is the total minus the base,
        // not an independently rounded percentage.
        listOf("17", "18", "19", "7.5", "12.345", "33.333").forEach { rate ->
            listOf("1000", "999.99", "1", "0.03", "123456.78").forEach { amount ->
                val inclusive = fee(amount, mode = TaxMode.INCLUSIVE, rate = rate)
                assertEquals(
                    "inclusive $amount at $rate% did not add up",
                    inclusive.clientTotal,
                    inclusive.base + inclusive.tax,
                )
                val exclusive = fee(amount, mode = TaxMode.EXCLUSIVE, rate = rate)
                assertEquals(
                    "exclusive $amount at $rate% did not add up",
                    exclusive.clientTotal,
                    exclusive.base + exclusive.tax,
                )
            }
        }
    }

    @Test
    fun `a one-third tax rate does not lose a cent`() {
        val third = fee("100", mode = TaxMode.INCLUSIVE, rate = "33.333333")

        assertEquals(third.clientTotal, third.base + third.tax)
        assertEquals(Money.of("100.00", "USD"), third.clientTotal)
    }

    // ── Payments ─────────────────────────────────────────────────────────────

    @Test
    fun `a partial payment leaves the exact remainder outstanding`() {
        val state = resolve(fee("10000"), listOf(payment("5000")))

        assertEquals(ProjectBillingStatus.PARTIALLY_PAID, state.status)
        assertEquals(Money.of("6800.00", "USD"), state.outstanding)
    }

    @Test
    fun `many small payments walk the balance to exactly zero`() {
        // 11,800 in 118 payments of 100. Every intermediate balance must be
        // exact, or the last payment does not settle it.
        val payments = (1..118).map { payment("100", id = "pay-$it") }
        val state = resolve(fee("10000"), payments)

        assertTrue(state.outstanding.isZero)
        assertEquals(ProjectBillingStatus.PAID, state.status)
    }

    @Test
    fun `an awkward split of a repeating-decimal total still settles`() {
        val awkward = fee("1000", mode = TaxMode.INCLUSIVE, rate = "17")
        val payments = listOf(
            payment("333.33", id = "a"),
            payment("333.33", id = "b"),
            payment("333.34", id = "c"),
        )
        val state = resolve(awkward, payments)

        assertTrue(state.outstanding.isZero)
        assertEquals(ProjectBillingStatus.PAID, state.status)
    }

    @Test
    fun `the outstanding balance never goes negative`() {
        // Even if an overpayment were somehow stored, the balance floors at zero
        // rather than showing the user a negative debt.
        val state = resolve(fee("10000"), listOf(payment("99999")))

        assertFalse(state.outstanding.isNegative)
        assertTrue(state.outstanding.isZero)
    }

    @Test
    fun `an overpayment is refused at the point of entry`() {
        val billed = record(fee("10000"))
        val rejection = ProjectPaymentFormValidator.validate(
            ProjectPaymentFormInput(
                projectId = "p1",
                billingRecordId = "b1",
                amount = "11800.01",
                currencyCode = "USD",
                paidOn = LocalDate.of(2026, 7, 10),
            ),
            billingRecord = billed,
            existingPayments = emptyList(),
        )

        assertTrue(rejection is PaymentFormResult.Rejected)
    }

    @Test
    fun `a payment that exactly settles the balance is accepted`() {
        val billed = record(fee("10000"))
        val result = ProjectPaymentFormValidator.validate(
            ProjectPaymentFormInput(
                projectId = "p1",
                billingRecordId = "b1",
                amount = "11800.00",
                currencyCode = "USD",
                paidOn = LocalDate.of(2026, 7, 10),
            ),
            billingRecord = billed,
            existingPayments = emptyList(),
        )

        assertTrue(result is PaymentFormResult.Valid)
    }

    @Test
    fun `a second payment cannot exceed what is left`() {
        val billed = record(fee("10000"))
        val result = ProjectPaymentFormValidator.validate(
            ProjectPaymentFormInput(
                projectId = "p1",
                billingRecordId = "b1",
                amount = "6800.01",
                currencyCode = "USD",
                paidOn = LocalDate.of(2026, 7, 10),
            ),
            billingRecord = billed,
            existingPayments = listOf(payment("5000")),
        )

        assertTrue(result is PaymentFormResult.Rejected)
    }

    @Test
    fun `a zero payment is refused`() {
        val result = ProjectPaymentFormValidator.validate(
            ProjectPaymentFormInput(
                projectId = "p1",
                billingRecordId = "b1",
                amount = "0",
                currencyCode = "USD",
                paidOn = LocalDate.of(2026, 7, 10),
            ),
            billingRecord = record(fee("10000")),
            existingPayments = emptyList(),
        )

        assertTrue(result is PaymentFormResult.Rejected)
    }

    @Test
    fun `a payment in the wrong currency is refused rather than converted`() {
        val result = ProjectPaymentFormValidator.validate(
            ProjectPaymentFormInput(
                projectId = "p1",
                billingRecordId = "b1",
                amount = "100",
                currencyCode = "ILS",
                paidOn = LocalDate.of(2026, 7, 10),
            ),
            billingRecord = record(fee("10000", currency = "USD")),
            existingPayments = emptyList(),
        )

        assertTrue(result is PaymentFormResult.Rejected)
    }

    // ── Multiple currencies ──────────────────────────────────────────────────

    @Test
    fun `totals in three currencies stay in three buckets`() {
        val totals = MoneyByCurrency.from(
            listOf(Money.of("100", "USD"), Money.of("200", "ILS"), Money.of("300", "EUR")),
        )

        assertEquals(listOf("EUR", "ILS", "USD"), totals.currencies)
        assertNull("there must be no single combined figure", totals.singleOrNull())
        assertEquals(Money.of("200", "ILS"), totals["ILS"])
    }

    @Test
    fun `a currency with no activity reads as zero rather than missing`() {
        val totals = MoneyByCurrency.from(listOf(Money.of("100", "USD")))

        assertTrue(totals["JPY"].isZero)
        assertEquals("JPY", totals["JPY"].currencyCode)
    }

    // ── Hours ────────────────────────────────────────────────────────────────

    @Test
    fun `zero tracked hours yields no rate rather than zero or infinity`() {
        val summary = ProjectMetrics.summarize(
            project = project(),
            shifts = emptyList(),
            billingRecords = emptyList(),
            payments = emptyList(),
            today = LocalDate.of(2026, 7, 15),
        )

        assertEquals(0, summary.time.trackedMinutes)
        assertNull("a project with no hours has no hourly rate", summary.effectiveHourlyRate)
    }

    @Test
    fun `one minute of work produces a very large but finite rate`() {
        val summary = ProjectMetrics.summarize(
            project = project(),
            shifts = listOf(projectShift(minutes = 1)),
            billingRecords = emptyList(),
            payments = emptyList(),
            today = LocalDate.of(2026, 7, 15),
        )

        assertNotNull(summary.effectiveHourlyRate)
        assertEquals(Money.of("600000.00", "USD"), summary.effectiveHourlyRate)
    }

    @Test
    fun `the effective rate uses the fee before tax, never the client total`() {
        val summary = ProjectMetrics.summarize(
            project = project(),
            shifts = listOf(projectShift(minutes = 600)),
            billingRecords = emptyList(),
            payments = emptyList(),
            today = LocalDate.of(2026, 7, 15),
        )

        // 10,000 base over 10 hours. Not 11,800 — tax collected for the state is
        // not the user's earnings.
        assertEquals(Money.of("1000.00", "USD"), summary.effectiveHourlyRate)
    }

    @Test
    fun `hundreds of project shifts sum without drift`() {
        // 500 shifts of 37 minutes: 18,500 minutes. Integer minutes throughout,
        // so there is nothing to drift, which is the point of asserting it.
        val shifts = (1..500).map { projectShift(minutes = 37, id = "s$it") }
        val summary = ProjectMetrics.summarize(
            project = project(),
            shifts = shifts,
            billingRecords = emptyList(),
            payments = emptyList(),
            today = LocalDate.of(2026, 7, 15),
        )

        assertEquals(500 * 37, summary.time.trackedMinutes)
        assertNotNull(summary.effectiveHourlyRate)
    }

    @Test
    fun `employee-paid shifts never count towards project hours`() {
        val summary = ProjectMetrics.summarize(
            project = project(),
            shifts = listOf(
                projectShift(minutes = 60, id = "project"),
                Shift(
                    id = "hourly",
                    userId = "u1",
                    startTime = Instant.parse("2026-07-10T09:00:00Z"),
                    endTime = Instant.parse("2026-07-10T17:00:00Z"),
                    projectId = "p1",
                    compensationSource = CompensationSource.EMPLOYEE,
                ),
            ),
            billingRecords = emptyList(),
            payments = emptyList(),
            today = LocalDate.of(2026, 7, 15),
        )

        // Only the project-compensated hour. The hourly shift is the employer's
        // to pay for and is not part of this project's cost of delivery.
        assertEquals(60, summary.time.trackedMinutes)
    }

    private fun project(
        currency: String = "USD",
        amount: String = "10000",
        budgetMinutes: Int? = null,
    ) = Project(
        id = "p1",
        userId = "u1",
        name = "Website rebuild",
        clientName = "Acme Ltd",
        workStatus = ProjectWorkStatus.ACTIVE,
        fee = fee(amount, currency),
        hourBudgetMinutes = budgetMinutes,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun projectShift(minutes: Int, id: String = "s1") = Shift(
        id = id,
        userId = "u1",
        startTime = Instant.parse("2026-07-10T09:00:00Z"),
        endTime = Instant.parse("2026-07-10T09:00:00Z").plusSeconds(minutes * 60L),
        projectId = "p1",
        compensationSource = CompensationSource.PROJECT,
    )
}
