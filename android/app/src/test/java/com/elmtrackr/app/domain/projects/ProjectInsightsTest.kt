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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Per-project figures, and the health indicators derived from them.
 *
 * The two rules under test throughout: the effective rate comes from the fee
 * **before tax** (tax is never income), and a rate or percentage with nothing
 * behind it is null rather than zero.
 */
class ProjectInsightsTest {

    private val today = LocalDate.of(2026, 7, 29)

    private fun project(
        id: String = "p1",
        entered: String = "10000",
        mode: TaxMode = TaxMode.EXCLUSIVE,
        rate: String = "18",
        currency: String = "USD",
        status: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
        budgetMinutes: Int? = null,
        targetRate: String? = null,
        deadline: LocalDate? = null,
        archivedAt: Instant? = null,
    ) = Project(
        id = id,
        userId = "u1",
        name = "Website rebuild",
        clientName = "Acme Ltd",
        workStatus = status,
        fee = ProjectFee.from(BigDecimal(entered), currency, mode, BigDecimal(rate)),
        hourBudgetMinutes = budgetMinutes,
        targetHourlyRate = targetRate?.let { Money.of(it, currency) },
        deadline = deadline,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = archivedAt,
    )

    private fun record(
        projectId: String = "p1",
        entered: String = "10000",
        currency: String = "USD",
        dueOn: LocalDate? = null,
    ) = ProjectBillingRecord(
        id = "b-$projectId",
        userId = "u1",
        projectId = projectId,
        fee = ProjectFee.from(BigDecimal(entered), currency, TaxMode.EXCLUSIVE, BigDecimal("18")),
        billedOn = today.minusDays(10),
        dueOn = dueOn,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun payment(amount: String, projectId: String = "p1", currency: String = "USD") =
        ProjectPayment(
            id = "pay-$projectId-$amount",
            userId = "u1",
            projectId = projectId,
            billingRecordId = "b-$projectId",
            paidOn = today,
            amount = Money.of(amount, currency),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    private fun insights(
        project: Project = project(),
        trackedMinutes: Int = 0,
        records: List<ProjectBillingRecord> = emptyList(),
        payments: List<ProjectPayment> = emptyList(),
        on: LocalDate = today,
    ): ProjectInsights {
        val summary = ProjectMetrics.summarize(
            project = project,
            shifts = emptyList(),
            billingRecords = records,
            payments = payments,
            today = on,
            timeSummary = ProjectTimeSummary(trackedMinutes = trackedMinutes, shiftCount = 1),
        )
        return ProjectInsightsBuilder.build(summary, on)
    }

    // ── Value and rate ────────────────────────────────────────────────────────

    @Test
    fun `the three value figures reconcile and tax is separate from the base`() {
        val result = insights()

        assertEquals(Money.of("10000.00", "USD"), result.baseFee)
        assertEquals(Money.of("1800.00", "USD"), result.tax)
        assertEquals(Money.of("11800.00", "USD"), result.clientTotal)
        assertEquals(result.clientTotal, result.baseFee + result.tax)
    }

    @Test
    fun `the effective rate uses the base fee, not the client total`() {
        // 10 hours of a 10000 pre-tax fee is 1000/hour, not 1180.
        val result = insights(trackedMinutes = 600)
        assertEquals(Money.of("1000.00", "USD"), result.effectiveHourlyRate)
    }

    @Test
    fun `a tax-inclusive fee still rates from the base`() {
        // 11800 including 18% has a base of 10000, so 10 hours reads 1000.
        val result = insights(
            project = project(entered = "11800", mode = TaxMode.INCLUSIVE),
            trackedMinutes = 600,
        )
        assertEquals(Money.of("1000.00", "USD"), result.effectiveHourlyRate)
    }

    @Test
    fun `no tracked hours means no effective rate rather than zero`() {
        val result = insights(trackedMinutes = 0)

        assertNull(result.effectiveHourlyRate)
        assertNull(result.rateDifferenceFromTarget)
    }

    @Test
    fun `the difference from target is positive when ahead and negative when behind`() {
        val ahead = insights(project = project(targetRate = "800"), trackedMinutes = 600)
        assertEquals(Money.of("200.00", "USD"), ahead.rateDifferenceFromTarget)
        assertFalse(ahead.isBelowTargetRate)

        val behind = insights(project = project(targetRate = "1500"), trackedMinutes = 600)
        assertEquals(Money.of("-500.00", "USD"), behind.rateDifferenceFromTarget)
        assertTrue(behind.isBelowTargetRate)
    }

    @Test
    fun `a target in another currency yields no difference rather than a conversion`() {
        val crossCurrency = project().copy(targetHourlyRate = Money.of("800", "EUR"))
        val result = insights(project = crossCurrency, trackedMinutes = 600)

        assertNull(result.rateDifferenceFromTarget)
        assertFalse(result.isBelowTargetRate)
    }

    @Test
    fun `a zero target rate is treated as no target`() {
        val result = insights(project = project(targetRate = "0"), trackedMinutes = 600)
        assertNull(result.targetHourlyRate)
        assertNull(result.rateDifferenceFromTarget)
    }

    // ── Budget ────────────────────────────────────────────────────────────────

    @Test
    fun `budget usage is a fraction and over-budget minutes are counted`() {
        val result = insights(project = project(budgetMinutes = 600), trackedMinutes = 900)

        assertEquals(1.5f, result.budgetUsage!!, 0.001f)
        assertEquals(300, result.minutesOverBudget)
        assertTrue(result.isOverBudget)
    }

    @Test
    fun `inside the budget reports zero over, not a negative`() {
        val result = insights(project = project(budgetMinutes = 600), trackedMinutes = 300)

        assertEquals(0.5f, result.budgetUsage!!, 0.001f)
        assertEquals(0, result.minutesOverBudget)
        assertFalse(result.isOverBudget)
    }

    @Test
    fun `no budget means no usage figure`() {
        val result = insights(trackedMinutes = 900)
        assertNull(result.budgetUsage)
        assertNull(result.minutesOverBudget)
        assertFalse(result.isOverBudget)
    }

    // ── Dates ─────────────────────────────────────────────────────────────────

    @Test
    fun `days until deadline counts calendar days and goes negative when passed`() {
        assertEquals(
            10L,
            insights(project = project(deadline = today.plusDays(10))).daysUntilDeadline,
        )
        assertEquals(
            -3L,
            insights(project = project(deadline = today.minusDays(3))).daysUntilDeadline,
        )
        assertEquals(0L, insights(project = project(deadline = today)).daysUntilDeadline)
        assertNull(insights().daysUntilDeadline)
    }

    @Test
    fun `payment due and overdue days come from the billing state`() {
        val soon = insights(records = listOf(record(dueOn = today.plusDays(4))))
        assertEquals(4L, soon.daysUntilPaymentDue)
        assertNull(soon.daysOverdue)

        val late = insights(records = listOf(record(dueOn = today.minusDays(6))))
        assertEquals(6L, late.daysOverdue)
    }

    // ── Money in ──────────────────────────────────────────────────────────────

    @Test
    fun `billed paid and outstanding come through with the derived status`() {
        val result = insights(
            records = listOf(record()),
            payments = listOf(payment("5000.00")),
        )

        assertEquals(Money.of("11800.00", "USD"), result.billedTotal)
        assertEquals(Money.of("5000.00", "USD"), result.paidTotal)
        assertEquals(Money.of("6800.00", "USD"), result.outstandingTotal)
        assertEquals(ProjectBillingStatus.PARTIALLY_PAID, result.billingStatus)
    }

    @Test
    fun `an unbilled project has no billed total`() {
        val result = insights()
        assertNull(result.billedTotal)
        assertEquals(ProjectBillingStatus.NOT_BILLED, result.billingStatus)
    }

    @Test
    fun `completed but not billed is detected`() {
        val result = insights(project = project(status = ProjectWorkStatus.COMPLETED))
        assertTrue(result.isCompletedButNotBilled)

        val billed = insights(
            project = project(status = ProjectWorkStatus.COMPLETED),
            records = listOf(record()),
        )
        assertFalse(billed.isCompletedButNotBilled)
    }

    // ── Health indicators ─────────────────────────────────────────────────────

    @Test
    fun `a healthy project reads on track and nothing else`() {
        val flags = ProjectHealthResolver.flagsFor(insights(trackedMinutes = 60))

        assertEquals(listOf(ProjectHealthIndicator.ON_TRACK), flags.map { it.indicator })
        assertFalse(ProjectHealthResolver.needsAttention(insights(trackedMinutes = 60)))
    }

    @Test
    fun `near budget appears with the usage that triggered it`() {
        // 540 of 600 minutes is 90%, past the 85% threshold.
        val result = insights(project = project(budgetMinutes = 600), trackedMinutes = 540)
        val flag = ProjectHealthResolver.flagsFor(result)
            .single { it.indicator == ProjectHealthIndicator.NEAR_HOUR_BUDGET }

        assertEquals(0.9f, flag.budgetUsage!!, 0.001f)
    }

    @Test
    fun `over budget supersedes near budget so both are never shown`() {
        val result = insights(project = project(budgetMinutes = 600), trackedMinutes = 900)
        val indicators = ProjectHealthResolver.flagsFor(result).map { it.indicator }

        assertTrue(indicators.contains(ProjectHealthIndicator.OVER_HOUR_BUDGET))
        assertFalse(indicators.contains(ProjectHealthIndicator.NEAR_HOUR_BUDGET))
    }

    @Test
    fun `a rate below target appears with the shortfall that triggered it`() {
        val result = insights(project = project(targetRate = "1500"), trackedMinutes = 600)
        val flag = ProjectHealthResolver.flagsFor(result)
            .single { it.indicator == ProjectHealthIndicator.RATE_BELOW_TARGET }

        assertEquals(Money.of("-500.00", "USD"), flag.rateShortfall)
    }

    @Test
    fun `a rate above target raises nothing`() {
        val result = insights(project = project(targetRate = "500"), trackedMinutes = 600)
        val indicators = ProjectHealthResolver.flagsFor(result).map { it.indicator }
        assertFalse(indicators.contains(ProjectHealthIndicator.RATE_BELOW_TARGET))
    }

    @Test
    fun `payment due soon appears within the lead time with the outstanding amount`() {
        val result = insights(records = listOf(record(dueOn = today.plusDays(3))))
        val flag = ProjectHealthResolver.flagsFor(result)
            .single { it.indicator == ProjectHealthIndicator.PAYMENT_DUE_SOON }

        assertEquals(3L, flag.daysUntilDue)
        assertEquals(Money.of("11800.00", "USD"), flag.outstanding)
    }

    @Test
    fun `a due date beyond the lead time raises nothing yet`() {
        val result = insights(
            records = listOf(record(dueOn = today.plusDays(ProjectHealthResolver.DUE_SOON_DAYS + 1))),
        )
        val indicators = ProjectHealthResolver.flagsFor(result).map { it.indicator }
        assertFalse(indicators.contains(ProjectHealthIndicator.PAYMENT_DUE_SOON))
    }

    @Test
    fun `overdue supersedes due soon`() {
        val result = insights(records = listOf(record(dueOn = today.minusDays(2))))
        val indicators = ProjectHealthResolver.flagsFor(result).map { it.indicator }

        assertTrue(indicators.contains(ProjectHealthIndicator.PAYMENT_OVERDUE))
        assertFalse(indicators.contains(ProjectHealthIndicator.PAYMENT_DUE_SOON))
    }

    @Test
    fun `a settled bill raises no payment indicator, however late it was`() {
        val result = insights(
            records = listOf(record(dueOn = today.minusDays(30))),
            payments = listOf(payment("11800.00")),
        )
        val indicators = ProjectHealthResolver.flagsFor(result).map { it.indicator }

        assertFalse(indicators.contains(ProjectHealthIndicator.PAYMENT_OVERDUE))
        assertFalse(indicators.contains(ProjectHealthIndicator.PAYMENT_DUE_SOON))
    }

    @Test
    fun `completed but unbilled is flagged`() {
        val result = insights(project = project(status = ProjectWorkStatus.COMPLETED))
        val indicators = ProjectHealthResolver.flagsFor(result).map { it.indicator }
        assertTrue(indicators.contains(ProjectHealthIndicator.COMPLETED_NOT_BILLED))
    }

    @Test
    fun `several conditions can apply at once, each with its own reason`() {
        val result = insights(
            project = project(budgetMinutes = 600, targetRate = "1500"),
            trackedMinutes = 900,
            records = listOf(record(dueOn = today.minusDays(2))),
        )
        val flags = ProjectHealthResolver.flagsFor(result)

        assertEquals(
            setOf(
                ProjectHealthIndicator.OVER_HOUR_BUDGET,
                ProjectHealthIndicator.RATE_BELOW_TARGET,
                ProjectHealthIndicator.PAYMENT_OVERDUE,
            ),
            flags.map { it.indicator }.toSet(),
        )
        // Every flag carries the figure that explains it: none is a bare label.
        assertTrue(flags.all { it.budgetUsage != null || it.rateShortfall != null || it.daysOverdue != null })
        assertTrue(ProjectHealthResolver.needsAttention(result))
    }

    @Test
    fun `archived and cancelled projects raise nothing`() {
        val archived = insights(
            project = project(budgetMinutes = 60, archivedAt = Instant.EPOCH),
            trackedMinutes = 900,
        )
        assertTrue(ProjectHealthResolver.flagsFor(archived).isEmpty())

        val cancelled = insights(
            project = project(status = ProjectWorkStatus.CANCELLED, budgetMinutes = 60),
            trackedMinutes = 900,
        )
        assertTrue(ProjectHealthResolver.flagsFor(cancelled).isEmpty())
    }

    @Test
    fun `on track is never combined with another indicator`() {
        val result = insights(project = project(budgetMinutes = 600), trackedMinutes = 900)
        val indicators = ProjectHealthResolver.flagsFor(result).map { it.indicator }

        assertFalse(indicators.contains(ProjectHealthIndicator.ON_TRACK))
        assertTrue(indicators.isNotEmpty())
    }
}
