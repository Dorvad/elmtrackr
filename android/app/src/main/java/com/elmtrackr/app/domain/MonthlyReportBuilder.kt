package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
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
     *
     * Thresholds and weekend days are resolved through [CompensationResolver], the same
     * way every pay figure resolves them. Reading `settings.dailyOvertimeThresholdMinutes`
     * directly — as this did — put the report on a different threshold from the money:
     * the field defaults to 480 while the Israeli preset's daily standard is 516, so an
     * IL user on preset defaults saw overtime *hours* counted from 8:00 in the report
     * card, the CSV and the PDF while pay treated the same minutes as regular until 8:36.
     *
     * With no [profiles] the resolver falls back to `legacySettingsToResolved`, which
     * copies those very settings fields, so a user who has never created a second job
     * gets exactly the previous numbers.
     */
    fun buildShiftBreakdown(
        shift: Shift,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
    ): ShiftBreakdown {
        val net = ShiftDurationCalculator.netMinutes(shift) ?: 0
        val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
        val zone = WorkTimezone.zoneFor(resolved, settings)
        val rawSegments = OvernightShiftDetector.splitShiftByDay(shift, zone)
        val segments = if (shift.forceRegularRate) {
            rawSegments
        } else {
            WeekendRules.annotateWeekendSegments(rawSegments, resolved.rules.weekendDays)
        }
        val weekendMins = WeekendRules.totalWeekendMinutes(segments)
        val weekdayMins = maxOf(0, net - weekendMins)
        val otMins = maxOf(0, weekdayMins - resolved.rules.dailyStandardMinutes)
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
     *   Per pay week, compute both daily OT (sum per shift) and weekly OT (total net − threshold).
     *   Take the larger of the two so neither threshold is missed.
     *
     * The weekly threshold is applied over real pay weeks anchored to [weekStartDay].
     * It previously reused [WeeklyBreakdownBuilder.groupByWeek], whose buckets are
     * day-of-month ranges (1–7, 8–14, 15–21, 22+) built for the UI card — those only
     * line up with weeks in a month that starts on the week's first day, and bucket 4
     * spans up to ten days against a seven-day threshold, so any other month reported
     * overtime the user had not worked.
     */
    fun buildMonthlyReport(
        year: Int,
        month: Int,
        shifts: List<Shift>,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
        weekStartDay: Int = defaultWeekStartDay(settings),
    ): MonthlyReport {
        // Employee-paid only: this report's regular/overtime/weekend split is a
        // pay classification, and project time is paid by the project fee. Project
        // hours are still visible on the project and in the shift list.
        val completed = shifts.employeePaidOnly().filter { it.isCompleted }
        // Group in the work timezone — grouping in UTC while the per-shift
        // breakdowns use the work zone bucketed evening shifts into the wrong
        // week, shifting weekly-overtime minutes between weeks.
        val zone = WorkTimezone.zoneFor(settings)
        val payWeeks = completed.groupBy { PayWeekMinutes.weekStart(it, zone, weekStartDay) }
        val breakdownMap = completed.associateWith { buildShiftBreakdown(it, settings, profiles) }
        val breakdownList = completed.map { breakdownMap.getValue(it) }
        // Resolved once per shift and reused: the resolver walks the profile list and
        // this is inside a per-week fold.
        val rulesMap = completed.associateWith {
            CompensationResolver.resolveShiftCompensation(it, settings, profiles).rules
        }

        val totalMinutes = breakdownList.sumOf { it.totalMinutes }
        val weekendMinutes = breakdownList.sumOf { it.weekendMinutes }

        val overtimeMinutes = payWeeks.values.sumOf { weekShifts ->
            // Weekday portion per shift (strip out weekend minutes)
            val weekdayMinsPerShift = weekShifts.map { s ->
                val bd = breakdownMap[s]
                maxOf(0, (bd?.totalMinutes ?: 0) - (bd?.weekendMinutes ?: 0))
            }
            val totalWeekdayMins = weekdayMinsPerShift.sum()

            val dailyOt = weekShifts.zip(weekdayMinsPerShift) { s, m ->
                maxOf(0, m - (rulesMap[s]?.dailyStandardMinutes ?: settings.dailyOvertimeThresholdMinutes))
            }.sum()
            // A weekly threshold belongs to the week, not to one shift, so it is taken
            // from the week's earliest shift. Shifts in one week normally share a
            // profile; where they do not, the pay path evaluates each shift against its
            // own weekly standard and this aggregate cannot reproduce both at once.
            val weeklyStandard = weekShifts
                .minByOrNull { it.startTime }
                ?.let { rulesMap[it]?.weeklyStandardMinutes }
                ?: settings.weeklyOvertimeThresholdMinutes
            val weeklyOt = maxOf(0, totalWeekdayMins - weeklyStandard)

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

    /**
     * Pay-week anchor for the weekly overtime threshold, 0=Sun … 6=Sat.
     *
     * Taken from the region preset when known. Every shipped preset (IL, US, generic)
     * starts the week on Sunday, which is also the fallback — a month-level report has
     * no per-shift profile to consult.
     */
    internal fun defaultWeekStartDay(settings: UserSettings): Int =
        settings.regionCode
            ?.let { RegionPresets.forRegion(it).rules.weekStartDay }
            ?: FALLBACK_WEEK_START_DAY

    private const val FALLBACK_WEEK_START_DAY = 0

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
