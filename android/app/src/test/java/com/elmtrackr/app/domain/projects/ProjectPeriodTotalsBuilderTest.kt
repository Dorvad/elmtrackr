package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
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

/**
 * [ProjectPeriodTotalsBuilder] exercised directly.
 *
 * It had no test file of its own: every assertion about it came through
 * `ProjectReportTest`, which pins its due dates in the past and so never varies
 * the one parameter that matters here. That is why the July fix for "overdue is
 * measured against the period end rather than against today" could land on one of
 * the builder's two call sites and stay broken on the other — `ProjectReport`
 * passes `today` for each project's own totals and omits it for the report-wide
 * TOTAL row, where it falls back to the end of the period.
 *
 * These tests fix the semantics of `today` so the remaining half of that fix can
 * be made without guessing at them.
 */
class ProjectPeriodTotalsBuilderTest {

    private val julyStart = LocalDate.of(2026, 7, 1)
    private val julyEnd = LocalDate.of(2026, 7, 31)

    private fun project(id: String = "p1", currency: String = "USD") = Project(
        id = id,
        userId = "u1",
        name = "Project $id",
        clientName = "Acme Ltd",
        workStatus = ProjectWorkStatus.ACTIVE,
        fee = ProjectFee.from(BigDecimal("10000"), currency, TaxMode.EXCLUSIVE, BigDecimal("18")),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun record(
        id: String = "r1",
        projectId: String = "p1",
        entered: String = "10000",
        currency: String = "USD",
        billedOn: LocalDate = julyStart.plusDays(4),
        dueOn: LocalDate? = null,
    ) = ProjectBillingRecord(
        id = id,
        userId = "u1",
        projectId = projectId,
        fee = ProjectFee.from(BigDecimal(entered), currency, TaxMode.EXCLUSIVE, BigDecimal("18")),
        billedOn = billedOn,
        dueOn = dueOn,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun payment(
        id: String = "pay1",
        recordId: String = "r1",
        amount: String,
        currency: String = "USD",
        paidOn: LocalDate = julyStart.plusDays(10),
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

    private fun build(
        records: List<ProjectBillingRecord>,
        payments: List<ProjectPayment> = emptyList(),
        today: LocalDate,
        to: LocalDate = julyEnd,
    ) = ProjectPeriodTotalsBuilder.build(
        projects = listOf(project()),
        billingRecords = records,
        payments = payments,
        from = julyStart,
        to = to,
        today = today,
    )

    // ── overdue is a statement about today, not about the period ──────────────

    @Test
    fun `an invoice due later this month is not overdue yet`() {
        // Viewing July on the 5th. The invoice falls due on the 25th — three weeks
        // away, and inside the period being viewed.
        val totals = build(
            records = listOf(record(dueOn = LocalDate.of(2026, 7, 25))),
            today = LocalDate.of(2026, 7, 5),
        )

        assertTrue("nothing is overdue on the 5th", totals.overdue.isEmpty)
        assertFalse("but it is certainly outstanding", totals.outstanding.isEmpty)
    }

    @Test
    fun `an invoice whose due date has passed is overdue`() {
        val totals = build(
            records = listOf(record(dueOn = LocalDate.of(2026, 7, 5))),
            today = LocalDate.of(2026, 7, 25),
        )

        assertEquals(Money.of("11800", "USD"), totals.overdue.singleOrNull())
    }

    @Test
    fun `a due date reached today is not yet overdue`() {
        // `isBefore` is strict, so the invoice becomes overdue tomorrow.
        val totals = build(
            records = listOf(record(dueOn = LocalDate.of(2026, 7, 20))),
            today = LocalDate.of(2026, 7, 20),
        )

        assertTrue(totals.overdue.isEmpty)
    }

    @Test
    fun `a past month keeps end-of-period semantics`() {
        // Looking back at July from September: everything that fell due inside
        // July is overdue, and `today` must not extend the window past `to`.
        val totals = build(
            records = listOf(record(dueOn = LocalDate.of(2026, 7, 25))),
            today = LocalDate.of(2026, 9, 1),
        )

        assertEquals(Money.of("11800", "USD"), totals.overdue.singleOrNull())
    }

    @Test
    fun `the default today is the end of the period`() {
        // This default is what the report-wide call relies on, and why that call
        // reports an invoice as overdue while the project's own row does not.
        // Pinned so the difference is visible rather than incidental.
        val defaulted = ProjectPeriodTotalsBuilder.build(
            projects = listOf(project()),
            billingRecords = listOf(record(dueOn = LocalDate.of(2026, 7, 25))),
            payments = emptyList(),
            from = julyStart,
            to = julyEnd,
        )

        assertEquals(
            "with no `today`, an invoice due on the 25th is overdue as at the 31st",
            Money.of("11800", "USD"),
            defaulted.overdue.singleOrNull(),
        )
    }

    @Test
    fun `a settled invoice is never overdue`() {
        val totals = build(
            records = listOf(record(dueOn = LocalDate.of(2026, 7, 5))),
            payments = listOf(payment(amount = "11800")),
            today = LocalDate.of(2026, 7, 25),
        )

        assertTrue("paid in full, so no balance to be overdue", totals.overdue.isEmpty)
        assertTrue(totals.outstanding.isEmpty)
    }

    @Test
    fun `a partly settled invoice is overdue for the remainder only`() {
        val totals = build(
            records = listOf(record(dueOn = LocalDate.of(2026, 7, 5))),
            payments = listOf(payment(amount = "3800")),
            today = LocalDate.of(2026, 7, 25),
        )

        assertEquals(Money.of("8000", "USD"), totals.overdue.singleOrNull())
    }

    // ── the bases stay apart ──────────────────────────────────────────────────

    @Test
    fun `billing and cash are recognised on their own dates`() {
        // Billed in July, paid in August: July sees the bill and none of the cash.
        val totals = build(
            records = listOf(record(billedOn = LocalDate.of(2026, 7, 4))),
            payments = listOf(payment(amount = "11800", paidOn = LocalDate.of(2026, 8, 3))),
            today = LocalDate.of(2026, 7, 31),
        )

        assertEquals(Money.of("11800", "USD"), totals.billed.singleOrNull())
        assertTrue("the cash belongs to August", totals.received.isEmpty)
        assertEquals(
            "and the balance is still owed as at the end of July",
            Money.of("11800", "USD"),
            totals.outstanding.singleOrNull(),
        )
    }

    @Test
    fun `outstanding carries billing from before the period`() {
        // A June invoice, unpaid. It is not July *billing*, but it is still a debt
        // in July — balances are cumulative where flows are not.
        val totals = build(
            records = listOf(record(billedOn = LocalDate.of(2026, 6, 10))),
            today = LocalDate.of(2026, 7, 15),
        )

        assertTrue("not billed in this period", totals.billed.isEmpty)
        assertEquals(Money.of("11800", "USD"), totals.outstanding.singleOrNull())
    }

    @Test
    fun `two currencies are kept apart rather than summed`() {
        val totals = ProjectPeriodTotalsBuilder.build(
            projects = listOf(project()),
            billingRecords = listOf(
                record(id = "r-usd", currency = "USD", dueOn = LocalDate.of(2026, 7, 5)),
                record(id = "r-ils", currency = "ILS", dueOn = LocalDate.of(2026, 7, 5)),
            ),
            payments = emptyList(),
            from = julyStart,
            to = julyEnd,
            today = LocalDate.of(2026, 7, 25),
        )

        assertEquals(listOf("ILS", "USD"), totals.overdue.currencies)
        assertTrue(totals.hasMultipleCurrencies)
    }
}
