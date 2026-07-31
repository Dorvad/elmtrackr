package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class ProjectPaymentValidatorTest {

    private val today = LocalDate.of(2026, 7, 29)

    private fun record(
        total: String = "1000",
        currency: String = "ILS",
        cancelled: Boolean = false,
    ) = ProjectBillingRecord(
        id = "bill-1",
        userId = "u1",
        projectId = "p1",
        fee = ProjectFee.from(BigDecimal(total), currency, TaxMode.NONE, BigDecimal.ZERO),
        billedOn = today,
        cancelledAt = if (cancelled) Instant.EPOCH else null,
    )

    private fun payment(amount: String, currency: String = "ILS", id: String = "pay-1") =
        ProjectPayment(
            id = id,
            userId = "u1",
            projectId = "p1",
            billingRecordId = "bill-1",
            paidOn = today,
            amount = Money.of(amount, currency),
        )

    @Test
    fun `a payment within the balance is accepted`() {
        val result = ProjectPaymentValidator.validate(Money.of("400", "ILS"), record(), emptyList())
        assertTrue(result.isValid)
    }

    @Test
    fun `a payment settling the exact balance is accepted`() {
        val result = ProjectPaymentValidator.validate(Money.of("1000", "ILS"), record(), emptyList())
        assertTrue(result.isValid)
    }

    @Test
    fun `a payment completing a partial balance is accepted`() {
        val result = ProjectPaymentValidator.validate(
            Money.of("600", "ILS"),
            record(),
            listOf(payment("400")),
        )
        assertTrue(result.isValid)
    }

    // ── Zero and negative ─────────────────────────────────────────────────────

    @Test
    fun `a zero payment is refused`() {
        val result = ProjectPaymentValidator.validate(Money.of("0", "ILS"), record(), emptyList())
        assertEquals(PaymentRejection.ZeroAmount, result.rejection)
    }

    @Test
    fun `a negative payment is refused`() {
        val result = ProjectPaymentValidator.validate(Money.of("-100", "ILS"), record(), emptyList())
        assertEquals(PaymentRejection.NegativeAmount, result.rejection)
    }

    // ── Currency ──────────────────────────────────────────────────────────────

    @Test
    fun `a payment in a different currency is refused`() {
        val result = ProjectPaymentValidator.validate(
            Money.of("100", "USD"),
            record(currency = "ILS"),
            emptyList(),
        )
        assertEquals(
            PaymentRejection.CurrencyMismatch("USD", "ILS"),
            result.rejection,
        )
    }

    @Test
    fun `an existing payment in a foreign currency is reported rather than summed`() {
        val result = ProjectPaymentValidator.validate(
            Money.of("100", "ILS"),
            record(),
            listOf(payment("500", currency = "USD")),
        )
        assertTrue(result.rejection is PaymentRejection.CurrencyMismatch)
    }

    // ── Overpayment ───────────────────────────────────────────────────────────

    @Test
    fun `a payment above the billed total is refused`() {
        val result = ProjectPaymentValidator.validate(
            Money.of("1000.01", "ILS"),
            record(total = "1000"),
            emptyList(),
        )
        val rejection = result.rejection as PaymentRejection.Overpayment
        assertEquals(Money.of("1000.00", "ILS"), rejection.billedTotal)
        assertEquals(Money.zero("ILS"), rejection.alreadyPaid)
        assertEquals(Money.of("1000.00", "ILS"), rejection.maximumAllowed)
    }

    @Test
    fun `a payment above the remaining balance is refused with the allowance shown`() {
        val result = ProjectPaymentValidator.validate(
            Money.of("700", "ILS"),
            record(total = "1000"),
            listOf(payment("400")),
        )
        val rejection = result.rejection as PaymentRejection.Overpayment
        assertEquals(Money.of("400.00", "ILS"), rejection.alreadyPaid)
        assertEquals(Money.of("600.00", "ILS"), rejection.maximumAllowed)
        assertEquals(Money.of("700.00", "ILS"), rejection.attempted)
    }

    @Test
    fun `any payment on a fully settled record is refused`() {
        val result = ProjectPaymentValidator.validate(
            Money.of("0.01", "ILS"),
            record(total = "1000"),
            listOf(payment("1000")),
        )
        assertTrue(result.rejection is PaymentRejection.Overpayment)
    }

    @Test
    fun `payments against other records do not consume this balance`() {
        val other = payment("1000", id = "pay-other").copy(billingRecordId = "bill-2")
        val result = ProjectPaymentValidator.validate(Money.of("1000", "ILS"), record(), listOf(other))
        assertTrue(result.isValid)
    }

    // ── Missing or cancelled record ───────────────────────────────────────────

    @Test
    fun `a payment with no billing record is refused`() {
        val result = ProjectPaymentValidator.validate(Money.of("100", "ILS"), null, emptyList())
        assertEquals(PaymentRejection.BillingRecordUnavailable, result.rejection)
    }

    @Test
    fun `a payment against a cancelled record is refused`() {
        val result = ProjectPaymentValidator.validate(
            Money.of("100", "ILS"),
            record(cancelled = true),
            emptyList(),
        )
        assertEquals(PaymentRejection.BillingRecordUnavailable, result.rejection)
    }

    // ── Remaining payable ─────────────────────────────────────────────────────

    @Test
    fun `remaining payable reflects recorded payments`() {
        assertEquals(
            Money.of("600.00", "ILS"),
            ProjectPaymentValidator.remainingPayable(record(total = "1000"), listOf(payment("400"))),
        )
    }

    @Test
    fun `remaining payable never goes negative`() {
        assertEquals(
            Money.zero("ILS"),
            ProjectPaymentValidator.remainingPayable(record(total = "1000"), listOf(payment("1500"))),
        )
    }

    @Test
    fun `validation result exposes validity plainly`() {
        assertTrue(PaymentValidationResult.Valid.isValid)
        assertFalse(PaymentValidationResult.Invalid(PaymentRejection.ZeroAmount).isValid)
    }
}
