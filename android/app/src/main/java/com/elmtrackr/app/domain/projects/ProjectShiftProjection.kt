package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.ShiftDurationCalculator
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.MoneyPolicy
import java.math.BigDecimal
import java.time.Instant

/**
 * What an in-progress project shift is doing to the project's numbers.
 *
 * All figures derive from the project's fee *before tax*: tax is collected on the
 * client's behalf and is not the user's earnings. None of this is employee pay —
 * a project shift earns no wage, no overtime and no premium — so nothing here
 * reads a compensation profile or an hourly wage.
 *
 * Every rate is nullable rather than zero. A project with no tracked time has no
 * effective rate at all, and showing 0 would read as "you are earning nothing".
 */
data class ProjectShiftProjection(
    /** Completed project minutes, excluding the shift in progress. */
    val trackedMinutes: Int,
    /** Elapsed minutes of the shift in progress, gross of its break. */
    val activeMinutes: Int,
    /**
     * Effective rate on the hours already banked. Null until some completed time
     * exists, which is the case during a project's very first shift.
     */
    val currentHourlyRate: Money?,
    /**
     * Effective rate the project would have if the user clocked out now. Null
     * only when the projected total is still zero minutes.
     */
    val projectedHourlyRate: Money?,
    /**
     * Budget minutes left after the projected total. Negative once the projection
     * runs past the budget; null when the project has no hour budget.
     */
    val budgetRemainingMinutes: Int?,
    /**
     * Minutes of further work before the effective rate would fall below the
     * project's target rate. Negative when the projection is already below it.
     *
     * Null when no target rate is configured — there is nothing to count down to,
     * and inventing a target would put a number on the screen the user never set.
     */
    val minutesUntilTargetRate: Int?,
) {
    val projectedMinutes: Int get() = trackedMinutes + activeMinutes

    /** True once the projected total exceeds a configured hour budget. */
    val isOverBudget: Boolean get() = budgetRemainingMinutes?.let { it < 0 } == true

    /** True once the projected rate has dropped below a configured target rate. */
    val isBelowTargetRate: Boolean get() = minutesUntilTargetRate?.let { it <= 0 } == true
}

object ProjectShiftProjectionCalculator {

    private val MINUTES_PER_HOUR = BigDecimal("60")

    /**
     * Projects [project]'s figures forward to [now], counting [activeShift] as
     * time in progress.
     *
     * [shifts] may contain anything; only completed shifts linked to this project
     * and tracked as project time are counted, and [activeShift] is excluded from
     * the banked total even if it appears in the list.
     */
    fun project(
        project: Project,
        activeShift: Shift?,
        shifts: List<Shift>,
        now: Instant = Instant.now(),
    ): ProjectShiftProjection {
        val banked = shifts.asSequence()
            .filter { it.projectId == project.id && it.isProjectTime }
            .filter { it.id != activeShift?.id }
            .sumOf { ShiftDurationCalculator.netMinutes(it) ?: 0 }

        val active = activeShift
            ?.takeIf { it.projectId == project.id && it.isProjectTime }
            ?.let { ShiftDurationCalculator.elapsedMinutes(it, now) }
            ?: 0

        val projected = banked + active
        val targetRate = project.targetHourlyRate?.takeIf { it.amount.signum() > 0 }

        return ProjectShiftProjection(
            trackedMinutes = banked,
            activeMinutes = active,
            currentHourlyRate = ProjectMetrics.effectiveHourlyRate(project.fee, banked),
            projectedHourlyRate = ProjectMetrics.effectiveHourlyRate(project.fee, projected),
            budgetRemainingMinutes = project.hourBudgetMinutes
                ?.takeIf { it > 0 }
                ?.let { it - projected },
            minutesUntilTargetRate = targetRate?.let { target ->
                minutesAtRate(project.fee.base, target)?.let { it - projected }
            },
        )
    }

    /**
     * How many minutes of work bring the effective rate exactly down to [rate].
     * The rate falls as hours accumulate, so this is the point the rate crosses
     * the target, not a point it grows towards.
     *
     * Null when the amounts cannot be compared — a zero fee, or a target quoted
     * in another currency, where any answer would be invented rather than
     * converted.
     */
    internal fun minutesAtRate(base: Money, rate: Money): Int? {
        if (base.currencyCode != rate.currencyCode) return null
        if (base.amount.signum() <= 0 || rate.amount.signum() <= 0) return null
        val hours = base.amount.divide(rate.amount, MoneyPolicy.CALCULATION_CONTEXT)
        val minutes = hours.multiply(MINUTES_PER_HOUR, MoneyPolicy.CALCULATION_CONTEXT)
        // Clamp rather than overflow: a tiny target rate against a large fee
        // produces more minutes than an Int holds.
        return minutes.min(BigDecimal(Int.MAX_VALUE))
            .setScale(0, MoneyPolicy.DISPLAY_ROUNDING)
            .toInt()
    }
}
