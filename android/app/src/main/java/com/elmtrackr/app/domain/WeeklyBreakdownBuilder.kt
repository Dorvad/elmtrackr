package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.WeeklyTotals
import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Groups shifts into ISO Monday-anchored weeks and computes per-week totals.
 */
object WeeklyBreakdownBuilder {

    /** Group a list of shifts into weeks, sorted chronologically. */
    fun groupByWeek(shifts: List<Shift>): List<WeeklyTotals> {
        val map = mutableMapOf<String, MutableList<Shift>>()

        for (shift in shifts) {
            val monday = getMondayUtc(shift.startTime)
            map.getOrPut(monday) { mutableListOf() } += shift
        }

        return map.entries
            .sortedBy { it.key }
            .map { (weekStart, weekShifts) ->
                WeeklyTotals(
                    weekStart = weekStart,
                    totalMinutes = weekShifts.sumOf {
                        ShiftDurationCalculator.netMinutes(it) ?: 0
                    },
                    shifts = weekShifts,
                )
            }
    }

    /**
     * Returns the ISO date string (YYYY-MM-DD) of the Monday that starts the
     * week containing [instant] (UTC).
     */
    internal fun getMondayUtc(instant: java.time.Instant): String {
        val date = instant.atOffset(ZoneOffset.UTC).toLocalDate()
        // (dayOfWeek.value - 1) gives distance from Monday; +7 mod 7 handles Sunday (value=7)
        val daysBack = ((date.dayOfWeek.value - DayOfWeek.MONDAY.value) + 7) % 7
        return date.minusDays(daysBack.toLong())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
}
