package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.money.MoneyByCurrency
import java.time.LocalDate

/**
 * The restrained project block on the dashboard.
 *
 * Counts, two balances and one cash figure. Deliberately no combined income
 * total: hourly earnings are work performed this month while project payments are
 * cash received this month, and the dashboard says which is which rather than
 * adding them.
 */
data class ProjectDashboardSummary(
    val activeProjectCount: Int,
    /** Projects with work under way and nothing billed yet. */
    val unbilledProjectCount: Int,
    val outstanding: MoneyByCurrency,
    val overdue: MoneyByCurrency,
    /** Cash received this month, including any tax it contained. */
    val receivedThisMonth: MoneyByCurrency,
    /** The tax portion of that cash, shown separately — it is not revenue. */
    val receivedTaxThisMonth: MoneyByCurrency,
    /** Projects with something the user could act on. Drives a single hint. */
    val needsAttentionCount: Int,
) {
    /** Nothing at all to show; the dashboard omits the block entirely. */
    val isEmpty: Boolean
        get() = activeProjectCount == 0 &&
            unbilledProjectCount == 0 &&
            outstanding.isEmpty &&
            overdue.isEmpty &&
            receivedThisMonth.isEmpty

    /** Project revenue before tax, for the period. Cash minus the tax it carried. */
    val receivedBeforeTaxThisMonth: MoneyByCurrency
        get() = receivedThisMonth.minusFlooredAtZero(receivedTaxThisMonth)
}

object ProjectDashboardSummaryBuilder {

    /**
     * @param today in the user's work timezone, which decides both which month
     * "this month" is and whether a payment is overdue.
     */
    fun build(
        summaries: List<ProjectSummary>,
        billingRecords: List<com.elmtrackr.app.domain.model.ProjectBillingRecord>,
        payments: List<com.elmtrackr.app.domain.model.ProjectPayment>,
        today: LocalDate,
    ): ProjectDashboardSummary {
        // Archived and cancelled projects are history and are not counted or nagged
        // about; their money still shows if a client genuinely still owes it.
        val live = summaries.filterNot {
            it.project.isArchived || it.project.workStatus == ProjectWorkStatus.CANCELLED
        }
        val insights = ProjectInsightsBuilder.buildAll(live, today)

        val monthStart = today.withDayOfMonth(1)
        val monthEnd = monthStart.plusMonths(1).minusDays(1)
        val period = ProjectPeriodTotalsBuilder.build(
            projects = live.map { it.project },
            billingRecords = billingRecords,
            payments = payments,
            from = monthStart,
            to = monthEnd,
        )

        return ProjectDashboardSummary(
            activeProjectCount = live.count { it.project.workStatus == ProjectWorkStatus.ACTIVE },
            unbilledProjectCount = insights.count {
                it.billingStatus == ProjectBillingStatus.NOT_BILLED &&
                    it.workStatus in setOf(
                        ProjectWorkStatus.ACTIVE,
                        ProjectWorkStatus.PAUSED,
                        ProjectWorkStatus.COMPLETED,
                    )
            },
            outstanding = MoneyByCurrency.from(
                insights.map { it.outstandingTotal }.filter { it.isPositive },
            ),
            overdue = MoneyByCurrency.from(
                insights.filter { it.daysOverdue != null }.map { it.outstandingTotal },
            ),
            receivedThisMonth = period.received,
            receivedTaxThisMonth = period.receivedTax,
            needsAttentionCount = insights.count { ProjectHealthResolver.needsAttention(it) },
        )
    }
}
