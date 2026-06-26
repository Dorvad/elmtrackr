package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.DaySegment
import java.time.LocalDate

/**
 * Weekend-day detection.
 * weekendDays values use JS encoding (0=Sun…6=Sat) to match the Supabase schema.
 */
object WeekendRules {

    /**
     * Returns true if [dateStr] (YYYY-MM-DD, interpreted as UTC) falls on a
     * configured weekend day.
     */
    fun isWeekendDate(dateStr: String, weekendDays: List<Int>): Boolean {
        val date = LocalDate.parse(dateStr)
        return date.dayOfWeek.toJsDay() in weekendDays
    }

    /** Annotate each DaySegment with whether its date is a weekend. */
    fun annotateWeekendSegments(
        segments: List<DaySegment>,
        weekendDays: List<Int>,
    ): List<DaySegment> = segments.map { seg ->
        seg.copy(isWeekend = isWeekendDate(seg.date, weekendDays))
    }

    /** Total minutes belonging to weekend segments. */
    fun totalWeekendMinutes(segments: List<DaySegment>): Int =
        segments.filter { it.isWeekend }.sumOf { it.minutes }
}
