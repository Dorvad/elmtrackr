package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.MoneyPolicy
import com.elmtrackr.app.domain.money.ProjectFee
import java.math.BigDecimal
import java.time.LocalDate

/** Time tracked against a project. */
data class ProjectTimeSummary(
    val trackedMinutes: Int = 0,
    val shiftCount: Int = 0,
) {
    val hasTime: Boolean get() = trackedMinutes > 0
}

/**
 * Everything a project card or detail screen needs, assembled once.
 *
 * Money here is the *client* side of the project — the agreed fee and what has
 * been paid. It is entirely separate from the employee pay estimate: nothing in
 * this file reads a compensation profile, an hourly wage or an overtime rule,
 * and [PayrollProjectIsolationTest] holds that line.
 */
data class ProjectSummary(
    val project: Project,
    val time: ProjectTimeSummary,
    val billing: ProjectBillingState,
    /**
     * Fee before tax divided by hours tracked. Null when no time is tracked —
     * never zero and never infinity.
     *
     * Based on the *pre-tax* fee: tax collected on the client's behalf is not
     * the user's earnings. It is still gross of income tax and expenses, which
     * is why the UI labels the base "your fee before tax" and never "you keep".
     */
    val effectiveHourlyRate: Money?,
    /** Tracked minutes over the hour budget, or null when no budget is set. */
    val budgetUtilization: Float?,
    val hasBillingRecords: Boolean,
    val hasPayments: Boolean,
) {
    val currencyCode: String get() = project.currencyCode
    val fee: ProjectFee get() = project.fee

    /** True once a billing record exists, which freezes the billed snapshot. */
    val isBilled: Boolean get() = hasBillingRecords
}

object ProjectMetrics {

    private val MINUTES_PER_HOUR = BigDecimal("60")

    /** Minutes tracked against [projectId], from completed shifts only. */
    fun timeSummary(shifts: List<Shift>, projectId: String): ProjectTimeSummary {
        val relevant = shifts.filter { it.projectId == projectId }
        val minutes = relevant.sumOf { shift ->
            // Active shifts have no end time yet, so they contribute nothing
            // until clock-out — consistent with how reports count hours.
            ShiftDurationCalculator.netMinutes(shift) ?: 0
        }
        return ProjectTimeSummary(trackedMinutes = minutes, shiftCount = relevant.size)
    }

    /** All project time summaries in one pass, for the list screen. */
    fun timeSummaries(shifts: List<Shift>): Map<String, ProjectTimeSummary> =
        shifts.asSequence()
            .mapNotNull { shift -> shift.projectId?.let { it to shift } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, projectShifts) ->
                ProjectTimeSummary(
                    trackedMinutes = projectShifts.sumOf { ShiftDurationCalculator.netMinutes(it) ?: 0 },
                    shiftCount = projectShifts.size,
                )
            }

    /**
     * Pre-tax fee per hour tracked. Null when there is no time yet, so callers
     * must render "not enough data" rather than a misleading 0 or a division by
     * zero.
     */
    fun effectiveHourlyRate(fee: ProjectFee, trackedMinutes: Int): Money? {
        if (trackedMinutes <= 0) return null
        val hours = BigDecimal(trackedMinutes).divide(MINUTES_PER_HOUR, MoneyPolicy.CALCULATION_CONTEXT)
        if (hours.signum() == 0) return null
        return Money.of(
            fee.base.amount.divide(hours, MoneyPolicy.CALCULATION_CONTEXT),
            fee.currencyCode,
        )
    }

    fun budgetUtilization(trackedMinutes: Int, hourBudgetMinutes: Int?): Float? {
        val budget = hourBudgetMinutes?.takeIf { it > 0 } ?: return null
        return trackedMinutes.toFloat() / budget.toFloat()
    }

    fun summarize(
        project: Project,
        shifts: List<Shift>,
        billingRecords: List<ProjectBillingRecord>,
        payments: List<ProjectPayment>,
        today: LocalDate,
        timeSummary: ProjectTimeSummary = timeSummary(shifts, project.id),
    ): ProjectSummary {
        val ownRecords = billingRecords.filter { it.projectId == project.id }
        val ownPayments = payments.filter { it.projectId == project.id }
        return ProjectSummary(
            project = project,
            time = timeSummary,
            billing = ProjectBillingStatusResolver.resolve(
                currencyCode = project.currencyCode,
                billingRecords = ownRecords,
                payments = ownPayments,
                today = today,
            ),
            effectiveHourlyRate = effectiveHourlyRate(project.fee, timeSummary.trackedMinutes),
            budgetUtilization = budgetUtilization(timeSummary.trackedMinutes, project.hourBudgetMinutes),
            hasBillingRecords = ownRecords.isNotEmpty(),
            hasPayments = ownPayments.isNotEmpty(),
        )
    }
}
