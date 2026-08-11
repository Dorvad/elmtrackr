package com.elmtrackr.app.domain.leave

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Which dates in an absence range the app *proposes* the user would have worked.
 *
 * A proposal, never an assumption. The app does not hold a roster, so it cannot
 * know that a Tuesday in the middle of an illness was a working day. What it does
 * have is the dates the user actually worked at that job, and a weekday they have
 * worked repeatedly is a reasonable suggestion. Every proposed date arrives in
 * the UI as a togglable row, and a weekday with no history is proposed off rather
 * than omitted, so a user with an irregular pattern can still tick it.
 */
data class ProposedAbsenceDate(
    val date: LocalDate,
    /** Whether the app suggests this date is an affected working day. */
    val selected: Boolean,
    /** How many times the user worked this weekday in the sampled history. */
    val observedCount: Int,
)

object LeaveWorkdayPlanner {

    /**
     * [workedDates] are the dates the user has a completed shift on at this
     * workplace, typically the last few months. [minimumObservations] is how many
     * times a weekday must appear before it is proposed; one stray Saturday should
     * not turn every Saturday of a holiday into paid leave.
     */
    fun propose(
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        workedDates: Collection<LocalDate>,
        minimumObservations: Int = 2,
    ): List<ProposedAbsenceDate> {
        val countsByWeekday: Map<DayOfWeek, Int> = workedDates
            .groupingBy { it.dayOfWeek }
            .eachCount()

        // With no history at all, propose nothing rather than everything. Offering
        // a pre-ticked full week to someone the app knows nothing about is how a
        // two-day holiday silently becomes seven days off a balance.
        val hasHistory = workedDates.isNotEmpty()

        return buildList {
            var cursor = rangeStart
            while (!cursor.isAfter(rangeEnd)) {
                val observed = countsByWeekday[cursor.dayOfWeek] ?: 0
                add(
                    ProposedAbsenceDate(
                        date = cursor,
                        selected = hasHistory && observed >= minimumObservations,
                        observedCount = observed,
                    ),
                )
                cursor = cursor.plusDays(1)
            }
        }
    }

    /**
     * True when the app has too little history to propose anything and should ask
     * the user which days they would have worked instead of guessing.
     */
    fun needsUserInput(proposals: List<ProposedAbsenceDate>): Boolean =
        proposals.isNotEmpty() && proposals.none { it.selected }
}
