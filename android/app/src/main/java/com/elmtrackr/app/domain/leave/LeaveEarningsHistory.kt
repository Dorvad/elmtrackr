package com.elmtrackr.app.domain.leave

import java.time.LocalDate
import java.time.YearMonth

/**
 * One month of the earnings base a leave estimate may average over.
 *
 * A deliberate type rather than a call into the payroll summer. The base for
 * valuing a leave day is not simply "what the reports show for that month": it
 * must exclude deductions, exclude project time, and exclude leave already
 * estimated — otherwise a month with paid leave in it raises the average used to
 * value the next leave day, which compounds an estimate into a bigger estimate.
 * Building this list is the caller's job (see LeaveEarningsBase), and the
 * calculators here take it as data so they stay testable without a database.
 */
data class LeaveEarningsMonth(
    val yearMonth: YearMonth,
    /** Gross wages before deductions, worked time only. */
    val eligibleGross: Double,
    /** Distinct calendar dates with at least one completed employee-paid shift. */
    val daysWorked: Int,
    val minutesWorked: Int,
) {
    /**
     * A month counts towards an average only if it has real earned work in it. A
     * month with no work is not a low month to average in, it is a month with
     * nothing to say.
     */
    val hasEarnings: Boolean get() = eligibleGross > 0.0 && daysWorked > 0
}

data class LeaveEarningsHistory(val months: List<LeaveEarningsMonth>) {

    private val byMonth: Map<YearMonth, LeaveEarningsMonth> = months.associateBy { it.yearMonth }

    fun month(yearMonth: YearMonth): LeaveEarningsMonth? = byMonth[yearMonth]

    /**
     * The three whole months before [reference]'s month, most recent last.
     * [reference]'s own month is excluded: it is still in progress, and averaging
     * a part-month in drags the figure down for no reason the user would accept.
     */
    fun precedingThreeMonths(reference: LocalDate): List<YearMonth> {
        val lastWhole = YearMonth.from(reference).minusMonths(1)
        return listOf(lastWhole.minusMonths(2), lastWhole.minusMonths(1), lastWhole)
    }

    /**
     * Every run of three consecutive months inside the twelve whole months before
     * [reference], oldest first.
     */
    fun candidateWindows(reference: LocalDate): List<List<YearMonth>> {
        val lastWhole = YearMonth.from(reference).minusMonths(1)
        val earliest = lastWhole.minusMonths(11)
        val all = buildList {
            var cursor = earliest
            while (!cursor.isAfter(lastWhole)) {
                add(cursor)
                cursor = cursor.plusMonths(1)
            }
        }
        return all.windowed(size = 3, step = 1, partialWindows = false)
    }

    fun isComplete(window: List<YearMonth>): Boolean =
        window.isNotEmpty() && window.all { month(it)?.hasEarnings == true }

    fun grossIn(window: List<YearMonth>): Double =
        window.sumOf { month(it)?.eligibleGross ?: 0.0 }

    fun daysWorkedIn(window: List<YearMonth>): Int =
        window.sumOf { month(it)?.daysWorked ?: 0 }

    companion object {
        val EMPTY = LeaveEarningsHistory(emptyList())
    }
}

/** The period an average was taken over, so the UI can show its working. */
data class LeaveAverage(
    val amountPerDay: Double,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val grossIncluded: Double,
    val divisor: Double,
    /**
     * True when the three months immediately before the absence were not usable
     * and another period was selected. Always surfaced: quietly averaging a
     * different period produces a number the user cannot reconcile against their
     * payslip, and they would have no way to know why.
     */
    val usedFallbackPeriod: Boolean,
)
