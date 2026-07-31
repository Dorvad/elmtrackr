package com.elmtrackr.app.domain.projects

/**
 * A named, explainable state a project can be in.
 *
 * Deliberately **not** a score. A number like "project health: 62" tells the user
 * nothing they can act on and cannot be argued with. Each of these instead
 * corresponds to one condition, and [ProjectHealthResolver] carries the figures
 * that triggered it so the UI can say why it appeared.
 */
enum class ProjectHealthIndicator {
    /** Nothing needs attention. Only ever shown alone. */
    ON_TRACK,

    /** Tracked hours are approaching the configured budget. */
    NEAR_HOUR_BUDGET,

    /** Tracked hours have passed the configured budget. */
    OVER_HOUR_BUDGET,

    /** The effective hourly rate has fallen below the target the user set. */
    RATE_BELOW_TARGET,

    /** A payment falls due within the lead time and is still outstanding. */
    PAYMENT_DUE_SOON,

    /** A payment is past its due date and still outstanding. */
    PAYMENT_OVERDUE,

    /** The work is finished but nothing has been billed. */
    COMPLETED_NOT_BILLED,
}

/**
 * One indicator plus the numbers behind it.
 *
 * The UI renders [indicator] as the label and uses these fields to complete the
 * explanation, so no indicator ever appears without a reason attached.
 */
data class ProjectHealthFlag(
    val indicator: ProjectHealthIndicator,
    /** Budget usage as a fraction, for the two budget indicators. */
    val budgetUsage: Float? = null,
    val minutesOverBudget: Int? = null,
    val rateShortfall: com.elmtrackr.app.domain.money.Money? = null,
    val daysUntilDue: Long? = null,
    val daysOverdue: Long? = null,
    val outstanding: com.elmtrackr.app.domain.money.Money? = null,
)

object ProjectHealthResolver {

    /** Budget usage at which "near budget" starts. */
    const val NEAR_BUDGET_FRACTION = 0.85f

    /** How close a due date has to be to count as "due soon". */
    const val DUE_SOON_DAYS = 7L

    /**
     * Every indicator that applies to [insights].
     *
     * Returns [ProjectHealthIndicator.ON_TRACK] alone when nothing does, so a
     * healthy project reads as a positive statement rather than an empty space.
     *
     * Archived and cancelled projects produce nothing: they are history, and
     * nagging about a project the user deliberately closed is noise.
     */
    fun flagsFor(insights: ProjectInsights): List<ProjectHealthFlag> {
        if (insights.isArchived) return emptyList()
        if (insights.workStatus == com.elmtrackr.app.domain.model.ProjectWorkStatus.CANCELLED) {
            return emptyList()
        }

        val flags = mutableListOf<ProjectHealthFlag>()

        // Budget: over supersedes near, so the user is not told both at once.
        val usage = insights.budgetUsage
        if (usage != null) {
            when {
                insights.isOverBudget -> flags += ProjectHealthFlag(
                    indicator = ProjectHealthIndicator.OVER_HOUR_BUDGET,
                    budgetUsage = usage,
                    minutesOverBudget = insights.minutesOverBudget,
                )
                usage >= NEAR_BUDGET_FRACTION -> flags += ProjectHealthFlag(
                    indicator = ProjectHealthIndicator.NEAR_HOUR_BUDGET,
                    budgetUsage = usage,
                )
            }
        }

        insights.rateDifferenceFromTarget
            ?.takeIf { it.isNegative }
            ?.let { shortfall ->
                flags += ProjectHealthFlag(
                    indicator = ProjectHealthIndicator.RATE_BELOW_TARGET,
                    rateShortfall = shortfall,
                )
            }

        // Payment: overdue supersedes due-soon for the same reason as the budget.
        if (insights.outstandingTotal.isPositive) {
            val overdue = insights.daysOverdue
            val untilDue = insights.daysUntilPaymentDue
            when {
                overdue != null -> flags += ProjectHealthFlag(
                    indicator = ProjectHealthIndicator.PAYMENT_OVERDUE,
                    daysOverdue = overdue,
                    outstanding = insights.outstandingTotal,
                )
                untilDue != null && untilDue in 0..DUE_SOON_DAYS -> flags += ProjectHealthFlag(
                    indicator = ProjectHealthIndicator.PAYMENT_DUE_SOON,
                    daysUntilDue = untilDue,
                    outstanding = insights.outstandingTotal,
                )
            }
        }

        if (insights.isCompletedButNotBilled) {
            flags += ProjectHealthFlag(indicator = ProjectHealthIndicator.COMPLETED_NOT_BILLED)
        }

        return flags.ifEmpty { listOf(ProjectHealthFlag(ProjectHealthIndicator.ON_TRACK)) }
    }

    /** True when [insights] has anything the user might want to act on. */
    fun needsAttention(insights: ProjectInsights): Boolean =
        flagsFor(insights).none { it.indicator == ProjectHealthIndicator.ON_TRACK } &&
            flagsFor(insights).isNotEmpty()
}
