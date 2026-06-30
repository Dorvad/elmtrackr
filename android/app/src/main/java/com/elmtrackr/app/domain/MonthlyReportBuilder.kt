package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.ShiftBreakdown
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.time.WorkTimezone

/**
 * Builds shift-level and month-level breakdown reports.
 *
 * Category invariant (mirrors the web app):
 *   regular + overtime + weekend == total  (±1 rounding from overnight proportioning)
 *   Weekend minutes take priority; overtime is computed from the weekday-only portion only.
 */
object MonthlyReportBuilder {

    /**
     * Build a [ShiftBreakdown] for a single completed shift.
     * Uses daily overtime threshold only (weekly threshold is applied at the monthly level).
     */
    fun buildShiftBreakdown(shift: Shift, settings: UserSettings): ShiftBreakdown {
        val net = ShiftDurationCalculator.netMinutes(shift) ?: 0
        val zone = WorkTimezone.zoneFor(settings)
        val rawSegments = OvernightShiftDetector.splitShiftByDay(shift, zone)
        val segments = WeekendRules.annotateWeekendSegments(rawSegments, settings.weekendDays)
        val weekendMins = WeekendRules.totalWeekendMinutes(segments)
        val weekdayMins = maxOf(0, net - weekendMins)
        val otMins = maxOf(0, weekdayMins - settings.dailyOvertimeThresholdMinutes)
        val regularMins = maxOf(0, weekdayMins - otMins)

        return ShiftBreakdown(
            totalMinutes = net,
            regularMinutes = regularMins,
            overtimeMinutes = otMins,
            weekendMinutes = weekendMins,
            segments = segments,
        )
    }

    /**
     * Aggregate all completed shifts for a month into a [MonthlyReport].
     * [shifts] should already be filtered to the target year/month.
     *
     * Overtime strategy (matches web app):
     *   Per ISO week, compute both daily OT (sum per shift) and weekly OT (total net − threshold).
     *   Take the larger of the two so neither threshold is missed.
     */
    fun buildMonthlyReport(
        year: Int,
        month: Int,
        shifts: List<Shift>,
        settings: UserSettings,
    ): MonthlyReport {
        val completed = shifts.filter { it.isCompleted }
        val weeks = WeeklyBreakdownBuilder.groupByWeek(completed)
        val breakdownMap = completed.associateWith { buildShiftBreakdown(it, settings) }
        val breakdownList = completed.map { breakdownMap.getValue(it) }

        val totalMinutes = breakdownList.sumOf { it.totalMinutes }
        val weekendMinutes = breakdownList.sumOf { it.weekendMinutes }

        val overtimeMinutes = weeks.sumOf { week ->
            // Weekday portion per shift (strip out weekend minutes)
            val weekdayMinsPerShift = week.shifts.map { s ->
                val bd = breakdownMap[s]
                maxOf(0, (bd?.totalMinutes ?: 0) - (bd?.weekendMinutes ?: 0))
            }
            val totalWeekdayMins = weekdayMinsPerShift.sum()

            val dailyOt = weekdayMinsPerShift.sumOf { m ->
                maxOf(0, m - settings.dailyOvertimeThresholdMinutes)
            }
            val weeklyOt = maxOf(0, totalWeekdayMins - settings.weeklyOvertimeThresholdMinutes)

            // Take whichever threshold produces more overtime (they overlap, not additive)
            maxOf(dailyOt, weeklyOt)
        }

        val regularMinutes = maxOf(0, totalMinutes - overtimeMinutes - weekendMinutes)

        return MonthlyReport(
            year = year,
            month = month,
            totalMinutes = totalMinutes,
            regularMinutes = regularMinutes,
            overtimeMinutes = overtimeMinutes,
            weekendMinutes = weekendMinutes,
            shiftCount = completed.size,
            shifts = breakdownList,
        )
    }

    /** Filter shifts to those whose start time falls in [year]/[month] (work timezone). */
    fun filterByMonth(
        shifts: List<Shift>,
        year: Int,
        month: Int,
        settings: UserSettings,
    ): List<Shift> {
        val zone = WorkTimezone.zoneFor(settings)
        val (from, to) = WorkTimezone.monthRangeEpochMillis(year, month, zone)
        return shifts.filter { it.startTime.toEpochMilli() in from until to }
    }
}
