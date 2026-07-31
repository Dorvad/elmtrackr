package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
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

class ProjectBillingStatusResolverTest {

    private val today = LocalDate.of(2026, 7, 29)
    private val currency = "ILS"

    private fun record(
        id: String = "bill-1",
        total: String = "11800",
        dueOn: LocalDate? = today.plusDays(14),
        cancelled: Boolean = false,
        billedOn: LocalDate = today.minusDays(7),
        currencyCode: String = currency,
    ) = ProjectBillingRecord(
        id = id,
        userId = "u1",
        projectId = "p1",
        fee = ProjectFee.from(BigDecimal(total), currencyCode, TaxMode.NONE, BigDecimal.ZERO),
        billedOn = billedOn,
        dueOn = dueOn,
        cancelledAt = if (cancelled) Instant.EPOCH else null,
    )

    private fun payment(
        amount: String,
        id: String = "pay-1",
        recordId: String = "bill-1",
        currencyCode: String = currency,
    ) = ProjectPayment(
        id = id,
        userId = "u1",
        projectId = "p1",
        billingRecordId = recordId,
        paidOn = today,
        amount = Money.of(amount, currencyCode),
    )

    private fun resolve(
        records: List<ProjectBillingRecord>,
        payments: List<ProjectPayment> = emptyList(),
        on: LocalDate = today,
    ) = ProjectBillingStatusResolver.resolve(currency, records, payments, on)

    // ── NOT_BILLED ────────────────────────────────────────────────────────────

    @Test
    fun `no billing record means not billed`() {
        val state = resolve(emptyList())
        assertEquals(ProjectBillingStatus.NOT_BILLED, state.status)
        assertNull(state.billedTotal)
        assertTrue(state.paid.isZero)
        assertTrue(state.outstanding.isZero)
        assertNull(state.activeBillingRecordId)
    }

    @Test
    fun `only a cancelled record still means not billed`() {
        val state = resolve(listOf(record(cancelled = true)))
        assertEquals(ProjectBillingStatus.NOT_BILLED, state.status)
    }

    // ── BILLED ────────────────────────────────────────────────────────────────

    @Test
    fun `billed with no payments and not yet due`() {
        val state = resolve(listOf(record()))
        assertEquals(ProjectBillingStatus.BILLED, state.status)
        assertEquals(Money.of("11800.00", currency), state.billedTotal)
        assertEquals(Money.of("11800.00", currency), state.outstanding)
        assertFalse(state.isOverdue)
        assertEquals(14L, state.daysUntilDue)
        assertEquals("bill-1", state.activeBillingRecordId)
    }

    @Test
    fun `a record with no due date can never be overdue`() {
        val state = resolve(listOf(record(dueOn = null)), on = today.plusYears(5))
        assertEquals(ProjectBillingStatus.BILLED, state.status)
        assertFalse(state.isOverdue)
        assertNull(state.daysUntilDue)
    }

    // ── PARTIALLY_PAID ────────────────────────────────────────────────────────

    @Test
    fun `part of the total received is partially paid`() {
        val state = resolve(listOf(record()), listOf(payment("5000")))
        assertEquals(ProjectBillingStatus.PARTIALLY_PAID, state.status)
        assertEquals(Money.of("5000.00", currency), state.paid)
        assertEquals(Money.of("6800.00", currency), state.outstanding)
    }

    @Test
    fun `several payments accumulate toward the total`() {
        val payments = listOf(
            payment("4000", id = "pay-1"),
            payment("3000", id = "pay-2"),
            payment("1800", id = "pay-3"),
        )
        val state = resolve(listOf(record()), payments)
        assertEquals(Money.of("8800.00", currency), state.paid)
        assertEquals(Money.of("3000.00", currency), state.outstanding)
        assertEquals(ProjectBillingStatus.PARTIALLY_PAID, state.status)
    }

    // ── PAID ──────────────────────────────────────────────────────────────────

    @Test
    fun `payments equal to the total are paid`() {
        val state = resolve(listOf(record()), listOf(payment("11800")))
        assertEquals(ProjectBillingStatus.PAID, state.status)
        assertTrue(state.outstanding.isZero)
        assertFalse(state.isOverdue)
    }

    @Test
    fun `many partial payments summing to the total are paid`() {
        val payments = (1..3).map { payment("3933.34", id = "pay-$it") }
        // 3933.34 x 3 = 11800.02, which exceeds the total, so use exact thirds.
        val exact = listOf(
            payment("3933.33", id = "pay-1"),
            payment("3933.33", id = "pay-2"),
            payment("3933.34", id = "pay-3"),
        )
        assertEquals(3, payments.size)
        val state = resolve(listOf(record()), exact)
        assertEquals(Money.of("11800.00", currency), state.paid)
        assertEquals(ProjectBillingStatus.PAID, state.status)
    }

    @Test
    fun `paid outranks overdue`() {
        val state = resolve(
            listOf(record(dueOn = today.minusDays(30))),
            listOf(payment("11800")),
        )
        assertEquals(ProjectBillingStatus.PAID, state.status)
        assertFalse(state.isOverdue)
    }

    // ── OVERDUE ───────────────────────────────────────────────────────────────

    @Test
    fun `unpaid past the due date is overdue`() {
        val state = resolve(listOf(record(dueOn = today.minusDays(1))))
        assertEquals(ProjectBillingStatus.OVERDUE, state.status)
        assertTrue(state.isOverdue)
        assertEquals(-1L, state.daysUntilDue)
    }

    @Test
    fun `due today is not yet overdue`() {
        val state = resolve(listOf(record(dueOn = today)))
        assertEquals(ProjectBillingStatus.BILLED, state.status)
        assertFalse(state.isOverdue)
        assertEquals(0L, state.daysUntilDue)
    }

    @Test
    fun `overdue outranks partially paid but the figures stay available`() {
        val state = resolve(listOf(record(dueOn = today.minusDays(3))), listOf(payment("5000")))
        assertEquals(ProjectBillingStatus.OVERDUE, state.status)
        assertTrue(state.isOverdue)
        assertEquals(Money.of("5000.00", currency), state.paid)
        assertEquals(Money.of("6800.00", currency), state.outstanding)
    }

    @Test
    fun `a cancelled record never produces overdue`() {
        val state = resolve(listOf(record(dueOn = today.minusDays(90), cancelled = true)))
        assertEquals(ProjectBillingStatus.NOT_BILLED, state.status)
        assertFalse(state.isOverdue)
    }

    @Test
    fun `cancelling one of two records leaves the other governing status`() {
        val records = listOf(
            record(id = "cancelled", total = "5000", dueOn = today.minusDays(60), cancelled = true),
            record(id = "live", total = "1000", dueOn = today.plusDays(10)),
        )
        val state = resolve(records)
        assertEquals(ProjectBillingStatus.BILLED, state.status)
        assertEquals(Money.of("1000.00", currency), state.billedTotal)
        assertFalse(state.isOverdue)
    }

    @Test
    fun `payments against a cancelled record do not offset live billing`() {
        val records = listOf(
            record(id = "cancelled", total = "5000", cancelled = true),
            record(id = "live", total = "1000"),
        )
        val payments = listOf(payment("5000", id = "pay-1", recordId = "cancelled"))
        val state = resolve(records, payments)
        assertEquals(Money.of("1000.00", currency), state.outstanding)
        assertEquals(ProjectBillingStatus.BILLED, state.status)
    }

    // ── Multiple records ──────────────────────────────────────────────────────

    @Test
    fun `multiple active records sum into one billed total`() {
        val records = listOf(
            record(id = "bill-1", total = "1000", billedOn = today.minusDays(20)),
            record(id = "bill-2", total = "2000", billedOn = today.minusDays(5)),
        )
        val state = resolve(records)
        assertEquals(Money.of("3000.00", currency), state.billedTotal)
        // Oldest record is the primary one.
        assertEquals("bill-1", state.activeBillingRecordId)
    }

    @Test
    fun `the earliest due date across records drives overdue`() {
        val records = listOf(
            record(id = "bill-1", total = "1000", dueOn = today.minusDays(2)),
            record(id = "bill-2", total = "2000", dueOn = today.plusDays(30)),
        )
        val state = resolve(records)
        assertEquals(ProjectBillingStatus.OVERDUE, state.status)
        assertEquals(today.minusDays(2), state.dueOn)
    }

    // ── Balances and credit ───────────────────────────────────────────────────

    @Test
    fun `outstanding is never negative and excess shows as credit`() {
        // Reachable only for data written before validation, or an imported backup.
        val state = resolve(listOf(record(total = "1000")), listOf(payment("1500")))
        assertTrue(state.outstanding.isZero)
        assertEquals(Money.of("500.00", currency), state.credit)
        assertEquals(ProjectBillingStatus.PAID, state.status)
    }

    @Test
    fun `no credit in the ordinary case`() {
        val state = resolve(listOf(record()), listOf(payment("5000")))
        assertTrue(state.credit.isZero)
    }

    // ── Currency ──────────────────────────────────────────────────────────────

    @Test
    fun `state carries the project currency`() {
        val state = ProjectBillingStatusResolver.resolve(
            "USD",
            listOf(record(currencyCode = "USD")),
            listOf(payment("100", currencyCode = "USD")),
            today,
        )
        assertEquals("USD", state.currencyCode)
        assertEquals("USD", state.paid.currencyCode)
        assertEquals("USD", state.outstanding.currencyCode)
    }

    @Test
    fun `active records are returned oldest first and exclude cancelled`() {
        val records = listOf(
            record(id = "new", billedOn = today),
            record(id = "cancelled", billedOn = today.minusDays(50), cancelled = true),
            record(id = "old", billedOn = today.minusDays(10)),
        )
        val active = ProjectBillingStatusResolver.activeRecords(records)
        assertEquals(listOf("old", "new"), active.map { it.id })
    }
}
