package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.MoneyByCurrency
import com.elmtrackr.app.domain.money.MoneyPolicy
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Project value per hour, per currency.
 *
 * **Not profit.** Expenses are outside the MVP, so nothing here is netted off:
 * this is contracted base fee divided by hours worked, which measures how well the
 * price matched the effort. The naming throughout — "revenue efficiency",
 * "project value per hour" — is chosen so it can never be read as net profit.
 */
data class RevenueEfficiency(
    val currencyCode: String,
    /** Agreed fees before tax for the projects in scope. */
    val baseRevenue: Money,
    /** All-time minutes behind [valuePerHour], not the reported period's. */
    val trackedMinutes: Int,
    /** Base revenue ÷ all-time hours. Null when no hours were tracked against it. */
    val valuePerHour: Money?,
)

/**
 * The project report.
 *
 * Money is grouped by currency at every level, and the four accounting bases stay
 * apart — see [ProjectPeriodTotals] for why adding them would be meaningless.
 * Time totals *are* summed across projects and currencies, because an hour is an
 * hour whatever it was priced in.
 */
data class ProjectReport(
    val filter: ProjectReportFilter,
    val from: LocalDate,
    val to: LocalDate,

    /** Per-project detail, for the rows and the CSV. */
    val projects: List<ProjectInsights>,

    // ── Time ──────────────────────────────────────────────────────────────────
    val trackedMinutes: Int,
    val activeDays: Int,
    /** Mean minutes per project in scope. Zero when there are none. */
    val averageMinutesPerProject: Int,
    /** Minutes past budget, summed over projects that have one. */
    val minutesOverBudget: Int,

    // ── Money, per basis, per currency ────────────────────────────────────────
    val totals: ProjectPeriodTotals,

    /** One entry per currency; never a single cross-currency figure. */
    val efficiency: List<RevenueEfficiency>,
) {
    val currencies: List<String> get() = totals.currencies

    val hasMultipleCurrencies: Boolean get() = totals.hasMultipleCurrencies

    val isEmpty: Boolean get() = projects.isEmpty()

    /** Projects with at least one health flag other than on-track. */
    fun needingAttention(): List<ProjectInsights> =
        projects.filter { ProjectHealthResolver.needsAttention(it) }
}

object ProjectReportBuilder {

    private val MINUTES_PER_HOUR = BigDecimal("60")

    /**
     * @param projectMinutes tracked minutes per project id, already restricted to
     * the report's date range by the caller — it owns the shift query.
     * @param activeDays distinct days with project time in range.
     */
    fun build(
        summaries: List<ProjectSummary>,
        billingRecords: List<ProjectBillingRecord>,
        payments: List<ProjectPayment>,
        filter: ProjectReportFilter,
        projectMinutes: Map<String, Int>,
        activeDays: Int,
        today: LocalDate,
    ): ProjectReport {
        val from = filter.from ?: today.withDayOfMonth(1)
        val to = filter.to ?: from.plusMonths(1).minusDays(1)

        val selected = ProjectReportFilterEngine.apply(summaries, filter)
        val selectedIds = selected.map { it.project.id }.toSet()

        val scopedMinutes = projectMinutes.filterKeys { it in selectedIds }
        val scopedRecords = billingRecords.filter { it.projectId in selectedIds }
        val scopedPayments = payments.filter { it.projectId in selectedIds }

        // Each project's own period figures, on the same basis as the report-wide
        // totals below — same builder, same dates, one project's records at a time.
        // Without these the rows carried all-time billing while the TOTAL row was
        // period-filtered, so a project billed in June and viewed in July showed its
        // amount on its row and zero in TOTAL.
        val periodTotalsByProject = selected.associate { summary ->
            val id = summary.project.id
            id to ProjectPeriodTotalsBuilder.build(
                projects = listOf(summary.project),
                billingRecords = scopedRecords.filter { it.projectId == id },
                payments = scopedPayments.filter { it.projectId == id },
                projectMinutesByProject = scopedMinutes.filterKeys { it == id },
                from = from,
                to = to,
            )
        }
        val insights = ProjectInsightsBuilder.buildAll(selected, today, periodTotalsByProject)

        val totals = ProjectPeriodTotalsBuilder.build(
            projects = selected.map { it.project },
            billingRecords = scopedRecords,
            payments = scopedPayments,
            projectMinutesByProject = scopedMinutes,
            activeDays = activeDays,
            from = from,
            to = to,
        )

        val trackedMinutes = scopedMinutes.values.sum()
        return ProjectReport(
            filter = filter,
            from = from,
            to = to,
            projects = insights,
            trackedMinutes = trackedMinutes,
            activeDays = activeDays,
            averageMinutesPerProject = if (selected.isEmpty()) 0 else trackedMinutes / selected.size,
            minutesOverBudget = insights.sumOf { it.minutesOverBudget ?: 0 },
            totals = totals,
            efficiency = efficiencyByCurrency(insights),
        )
    }

    /**
     * Revenue efficiency per currency.
     *
     * Grouped rather than summed: dividing a mixed-currency revenue by a shared
     * hour count would produce a number in no currency at all.
     *
     * Hours are all-time, matching the revenue. [ProjectInsights.baseFee] is the whole
     * contracted fee, so dividing it by one period's minutes reported a value per hour
     * several times the real one — the same error as the per-project effective rate,
     * one level up.
     */
    internal fun efficiencyByCurrency(
        insights: List<ProjectInsights>,
    ): List<RevenueEfficiency> = insights
        .groupBy { it.currencyCode }
        .toSortedMap()
        .map { (currency, group) ->
            val revenue = Money.sum(group.map { it.baseFee }, currency)
            val minutes = group.sumOf { it.allTimeMinutes }
            RevenueEfficiency(
                currencyCode = currency,
                baseRevenue = revenue,
                trackedMinutes = minutes,
                valuePerHour = valuePerHour(revenue, minutes),
            )
        }

    /** Zero-safe: no hours means no rate, not a division by zero. */
    internal fun valuePerHour(revenue: Money, minutes: Int): Money? {
        if (minutes <= 0) return null
        val hours = BigDecimal(minutes).divide(MINUTES_PER_HOUR, MoneyPolicy.CALCULATION_CONTEXT)
        if (hours.signum() == 0) return null
        return Money.of(
            revenue.amount.divide(hours, MoneyPolicy.CALCULATION_CONTEXT),
            revenue.currencyCode,
        )
    }

    /** Empty report for a filter that matched nothing, so the screen has a shape to render. */
    fun empty(filter: ProjectReportFilter, today: LocalDate): ProjectReport {
        val from = filter.from ?: today.withDayOfMonth(1)
        return ProjectReport(
            filter = filter,
            from = from,
            to = filter.to ?: from.plusMonths(1).minusDays(1),
            projects = emptyList(),
            trackedMinutes = 0,
            activeDays = 0,
            averageMinutesPerProject = 0,
            minutesOverBudget = 0,
            totals = ProjectPeriodTotals(),
            efficiency = emptyList(),
        )
    }
}

/** Money grouped for display, so a screen can render one block per currency. */
fun MoneyByCurrency.blocks(): List<Money> = entries()
