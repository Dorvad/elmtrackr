package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.compensation.ShiftClassifier
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.PremiumProfile
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
     *
     * The split comes from [ShiftClassifier], which is the same classification the
     * pay engines price. It used to be worked out here instead, with two rules that
     * did not match the ones the money used:
     *
     *  - weekend minutes came from the whole-day [WeekendRules.isWeekendDate],
     *    while the pay engine decides weekly rest minute by minute and honours
     *    `weeklyRestStartTime`. On the Israeli preset — which is also what a user
     *    who never chose a region gets, because
     *    `CompensationResolver.legacySettingsToResolved` copies it — that made the
     *    whole of Friday weekend, when only 17:00 onwards is;
     *  - overtime was measured against the raw `dailyStandardMinutes`, while every
     *    pay path measures it against the *effective* standard, which is shorter on
     *    a night shift and on the day before rest.
     *
     * So a Friday 08:00–17:06 shift was paid as seven hours of ordinary time plus
     * two hours of overtime and six minutes of rest-time overtime, and reported as
     * nine hours of weekend work with no overtime at all. The same numbers reached
     * the shift-row badge, the per-shift rows in Reports, the CSV and the PDF.
     *
     * [totalMinutes] is now payable minutes rather than clock time less breaks, so
     * `regular + overtime + weekend == total` holds even under rounding, an
     * auto-deducted break or a minimum-shift floor. None of the shipped presets
     * enable those, so for most users the figure is unchanged.
     *
     * @param contextShifts the pay week this shift belongs to, so weekly overtime
     *   is attributed. Defaults to the shift alone, which means "no prior context
     *   known" and is what a single-shift caller wants.
     */
    fun buildShiftBreakdown(
        shift: Shift,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
        premiumProfiles: List<PremiumProfile> = emptyList(),
        contextShifts: List<Shift> = listOf(shift),
    ): ShiftBreakdown {
        val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
        val zone = WorkTimezone.zoneFor(resolved, settings)
        // Day segments stay a calendar split: they are what the UI draws for an
        // overnight shift, and `isWeekend` on them is a calendar fact rather than a
        // pay classification. Nothing derives money from them any more.
        val rawSegments = OvernightShiftDetector.splitShiftByDay(shift, zone)
        val segments = if (shift.forceRegularRate || !resolved.rules.weekendEnabled) {
            rawSegments
        } else {
            WeekendRules.annotateWeekendSegments(rawSegments, resolved.rules.weekendDays)
        }

        val classification = ShiftClassifier.classify(
            shift, contextShifts, settings, profiles, premiumProfiles,
        ) ?: return ShiftBreakdown(
            // Project time and active shifts classify to nothing: project hours are
            // paid by the project's fee, so a regular/overtime/weekend split of them
            // would be a pay classification of money that is not wages. The hours
            // are still reported.
            totalMinutes = ShiftDurationCalculator.netMinutes(shift) ?: 0,
            regularMinutes = 0,
            overtimeMinutes = 0,
            weekendMinutes = 0,
            segments = segments,
        )

        return ShiftBreakdown(
            totalMinutes = classification.payableMinutes,
            regularMinutes = classification.regularMinutes,
            overtimeMinutes = classification.overtimeMinutes,
            // Weekend and holiday together, mirroring `specialGross`, which is
            // `weekendGross + holidayGross`. Keeping the same pairing is what lets
            // the hours and the money be compared directly.
            weekendMinutes = classification.specialMinutes,
            segments = segments,
        )
    }

    /**
     * Aggregate all completed shifts for a month into a [MonthlyReport].
     * [shifts] should already be filtered to the target year/month.
     *
     * The month is now the sum of its shifts. It used to re-derive overtime with a
     * per-pay-week fold that took `max(dailyOvertime, weeklyOvertime)` over each
     * week's weekday minutes — a third overtime algorithm, beside the two the
     * engines use, and one that could not reproduce a week whose shifts belong to
     * different profiles. Weekly overtime is attributed by [ShiftClassifier]
     * through [contextShifts] instead, which is how the pay path has always done
     * it.
     *
     * @param contextShifts a window wide enough to cover the pay weeks the reported
     *   [shifts] belong to — normally the month plus the tail of the week
     *   containing the 1st. Only [shifts] are reported; these supply the prior
     *   minutes that decide weekly overtime, which is why a week straddling the 1st
     *   no longer restarts its allowance at zero.
     */
    fun buildMonthlyReport(
        year: Int,
        month: Int,
        shifts: List<Shift>,
        settings: UserSettings,
        profiles: List<CompensationProfile> = emptyList(),
        premiumProfiles: List<PremiumProfile> = emptyList(),
        contextShifts: List<Shift> = shifts,
    ): MonthlyReport {
        // Employee-paid only: this report's regular/overtime/weekend split is a pay
        // classification, and project time is paid by the project fee. Project hours
        // are still visible on the project and in the shift list.
        val completed = shifts.employeePaidOnly().filter { it.isCompleted }
        val context = contextShifts.employeePaidOnly().filter { it.isCompleted }
            .ifEmpty { completed }

        val breakdowns = completed.map {
            buildShiftBreakdown(it, settings, profiles, premiumProfiles, context)
        }

        return MonthlyReport(
            year = year,
            month = month,
            totalMinutes = breakdowns.sumOf { it.totalMinutes },
            regularMinutes = breakdowns.sumOf { it.regularMinutes },
            overtimeMinutes = breakdowns.sumOf { it.overtimeMinutes },
            weekendMinutes = breakdowns.sumOf { it.weekendMinutes },
            shiftCount = completed.size,
            shifts = breakdowns,
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
