package com.elmtrackr.app.domain.leave

import java.time.LocalDate
import java.time.YearMonth

/**
 * Historical-average bases for valuing a leave day.
 *
 * Both methods here answer "what is one day of this job worth", differing only
 * in the divisor: a fixed 90 for the Israeli-style calendar-day average, or the
 * days actually worked for the per-workday average. Neither is presented as a
 * payroll figure — the period, the gross included and the divisor all travel
 * back in [LeaveAverage] so the screen can show exactly how the number was
 * reached.
 */
object VacationPayCalculator {

    private const val STATUTORY_DIVISOR = 90.0

    /**
     * Eligible gross over a three-month period divided by 90 — the shape
     * commonly used for hourly and daily workers in Israel.
     *
     * Prefers the three whole months before the absence. When any of those has
     * no earned work, the strongest complete three-month run in the preceding
     * twelve is used instead and [LeaveAverage.usedFallbackPeriod] is set.
     * "Strongest" means highest eligible gross, which is the reading most
     * favourable to the worker; the app does not get to pick a worse period on
     * their behalf.
     *
     * Returns null when no complete three-month run exists, rather than dividing
     * a partial period by 90 and calling the result an estimate.
     */
    fun israeliStatutoryAverage(
        history: LeaveEarningsHistory,
        reference: LocalDate,
    ): LeaveAverage? = averageOver(history, reference) { window ->
        history.grossIn(window) / STATUTORY_DIVISOR to STATUTORY_DIVISOR
    }

    /**
     * Eligible gross over the period divided by the days actually worked in it —
     * what an average working day paid. Used for the per-workday vacation basis
     * and as the sick-day basis, where the value being scaled by the tier ladder
     * is what a normal working day would have earned.
     */
    fun workdayAverage(
        history: LeaveEarningsHistory,
        reference: LocalDate,
    ): LeaveAverage? = averageOver(history, reference) { window ->
        val days = history.daysWorkedIn(window)
        if (days <= 0) return@averageOver null
        history.grossIn(window) / days to days.toDouble()
    }

    private inline fun averageOver(
        history: LeaveEarningsHistory,
        reference: LocalDate,
        divide: (List<YearMonth>) -> Pair<Double, Double>?,
    ): LeaveAverage? {
        val preferred = history.precedingThreeMonths(reference)
        val window: List<YearMonth>
        val usedFallback: Boolean
        if (history.isComplete(preferred)) {
            window = preferred
            usedFallback = false
        } else {
            val best = history.candidateWindows(reference)
                .filter { history.isComplete(it) }
                .maxByOrNull { history.grossIn(it) }
                ?: return null
            window = best
            usedFallback = true
        }
        val (amount, divisor) = divide(window) ?: return null
        return LeaveAverage(
            amountPerDay = amount,
            periodStart = window.first().atDay(1),
            periodEnd = window.last().atEndOfMonth(),
            grossIncluded = history.grossIn(window),
            divisor = divisor,
            usedFallbackPeriod = usedFallback,
        )
    }
}
