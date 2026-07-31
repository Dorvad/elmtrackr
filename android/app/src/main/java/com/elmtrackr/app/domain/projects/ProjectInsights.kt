package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.MoneyPolicy
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Every figure a project screen or report needs about one project, computed once.
 *
 * Two rules hold throughout:
 *  - the effective hourly rate is derived from the fee **before tax**. Tax is
 *    collected for the authority, so treating it as income would overstate what
 *    the work earned.
 *  - a rate or a percentage with nothing behind it is null, not zero. A project
 *    with no tracked hours has no effective rate; showing 0 would read as "this
 *    work earned nothing".
 */
data class ProjectInsights(
    val projectId: String,
    val projectName: String,
    val clientName: String?,
    val currencyCode: String,

    // ── Value ─────────────────────────────────────────────────────────────────
    /** The agreed fee before tax. This is the revenue figure. */
    val baseFee: Money,
    val tax: Money,
    val clientTotal: Money,

    // ── Time ──────────────────────────────────────────────────────────────────
    val trackedMinutes: Int,
    val hourBudgetMinutes: Int?,
    /** Tracked over budget, 1.0 being exactly on budget. Null with no budget. */
    val budgetUsage: Float?,
    /** Minutes past the budget; null with no budget, zero when inside it. */
    val minutesOverBudget: Int?,

    // ── Rate ──────────────────────────────────────────────────────────────────
    /** Base fee ÷ tracked hours. Null until hours exist. */
    val effectiveHourlyRate: Money?,
    val targetHourlyRate: Money?,
    /**
     * Effective minus target: positive is ahead, negative behind. Null unless
     * both exist in the same currency — no rate is invented across currencies.
     */
    val rateDifferenceFromTarget: Money?,

    // ── Money in ──────────────────────────────────────────────────────────────
    val billedTotal: Money?,
    val paidTotal: Money,
    val outstandingTotal: Money,
    val billingStatus: ProjectBillingStatus,

    // ── Dates ─────────────────────────────────────────────────────────────────
    /** Negative once the deadline has passed. Null with no deadline. */
    val daysUntilDeadline: Long?,
    /** Negative once payment is late. Null with no due date. */
    val daysUntilPaymentDue: Long?,
    val daysOverdue: Long?,

    val workStatus: com.elmtrackr.app.domain.model.ProjectWorkStatus,
    val isArchived: Boolean,
) {
    /** True once the effective rate has fallen below the target the user set. */
    val isBelowTargetRate: Boolean
        get() = rateDifferenceFromTarget?.isNegative == true

    val isOverBudget: Boolean get() = minutesOverBudget?.let { it > 0 } == true

    /** Work is finished but no money has been asked for. */
    val isCompletedButNotBilled: Boolean
        get() = workStatus == com.elmtrackr.app.domain.model.ProjectWorkStatus.COMPLETED &&
            billingStatus == ProjectBillingStatus.NOT_BILLED
}

object ProjectInsightsBuilder {

    private val MINUTES_PER_HOUR = java.math.BigDecimal("60")

    /**
     * @param today in the user's work timezone. Every day count here is
     * calendar-based; comparing instants would shift a deadline by a day
     * depending on where the user is.
     */
    fun build(summary: ProjectSummary, today: LocalDate): ProjectInsights {
        val project = summary.project
        val fee = project.fee
        val budget = project.hourBudgetMinutes?.takeIf { it > 0 }
        val target = project.targetHourlyRate?.takeIf { it.amount.signum() > 0 }
        val effective = summary.effectiveHourlyRate

        return ProjectInsights(
            projectId = project.id,
            projectName = project.name,
            clientName = project.clientName,
            currencyCode = project.currencyCode,
            baseFee = fee.base,
            tax = fee.tax,
            clientTotal = fee.clientTotal,
            trackedMinutes = summary.time.trackedMinutes,
            hourBudgetMinutes = budget,
            budgetUsage = budget?.let { summary.time.trackedMinutes.toFloat() / it.toFloat() },
            minutesOverBudget = budget?.let { (summary.time.trackedMinutes - it).coerceAtLeast(0) },
            effectiveHourlyRate = effective,
            targetHourlyRate = target,
            rateDifferenceFromTarget = difference(effective, target),
            billedTotal = summary.billing.billedTotal,
            paidTotal = summary.billing.paid,
            outstandingTotal = summary.billing.outstanding,
            billingStatus = summary.billing.status,
            daysUntilDeadline = project.deadline?.let { ChronoUnit.DAYS.between(today, it) },
            daysUntilPaymentDue = summary.billing.daysUntilDue,
            daysOverdue = summary.billing.daysOverdue,
            workStatus = project.workStatus,
            isArchived = project.isArchived,
        )
    }

    fun buildAll(summaries: List<ProjectSummary>, today: LocalDate): List<ProjectInsights> =
        summaries.map { build(it, today) }

    /**
     * Effective minus target, or null when they cannot be compared.
     *
     * Cross-currency returns null rather than a converted guess: the app has no
     * rates, and a "difference" between two currencies would be fiction.
     */
    private fun difference(effective: Money?, target: Money?): Money? {
        if (effective == null || target == null) return null
        if (effective.currencyCode != target.currencyCode) return null
        return effective - target
    }

    /** Hours as a decimal, for a rate or an average. Zero-safe. */
    fun hoursOf(minutes: Int): java.math.BigDecimal =
        java.math.BigDecimal(minutes).divide(MINUTES_PER_HOUR, MoneyPolicy.CALCULATION_CONTEXT)
}
