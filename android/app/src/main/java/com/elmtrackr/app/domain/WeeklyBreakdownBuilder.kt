package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.model.WeeklyTotals
import com.elmtrackr.app.domain.time.WorkTimezone
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.min

/**
 * Groups shifts into fixed day-of-month buckets — matching the web app:
 *   Week 1: days  1– 7  ("1–7")
 *   Week 2: days  8–14  ("8–14")
 *   Week 3: days 15–21  ("15–21")
 *   Week 4: days 22+    ("22+")
 *
 * A shift can contribute at most 24 h to a bucket (guards corrupted data).
 *
 * These are **display** buckets for the Hours breakdown card, not pay weeks: they ignore
 * the configured week start and bucket 4 spans up to ten days. Never use them to apply a
 * weekly overtime threshold — see [MonthlyReportBuilder.buildMonthlyReport], which groups
 * by [PayWeekMinutes.weekStart] instead. The per-bucket `overtimeMinutes` here is a
 * daily-threshold sum only, which is why it is safe.
 */
object WeeklyBreakdownBuilder {

    private const val MAX_SHIFT_MIN = 24 * 60

    private val LABELS    = listOf("Week 1", "Week 2", "Week 3", "Week 4")
    private val DAY_RANGE = listOf("1–7", "8–14", "15–21", "22+")

    /**
     * Builds four weekly buckets for [shifts].
     *
     * @param shifts          Completed shifts for the selected month.
     * @param settings        Needed for overtime threshold and hourly rate.
     * @param prevMonthShifts Completed shifts for the previous month —
     *                        used to compute the per-week delta in the UI.
     */
    fun groupByWeek(
        shifts: List<Shift>,
        settings: UserSettings? = null,
        profiles: List<CompensationProfile> = emptyList(),
        premiumProfiles: List<PremiumProfile> = emptyList(),
        prevMonthShifts: List<Shift> = emptyList(),
        zoneOverride: java.time.ZoneId? = null,
    ): List<WeeklyTotals> {
        // Employee-paid only. Each bucket reports overtime minutes and pay, both of
        // which are employee-compensation figures; project time is paid by the
        // project's fee and is reported on the project instead. Applied to the
        // previous month too so the delta compares like with like.
        @Suppress("NAME_SHADOWING") val shifts = shifts.employeePaidOnly()
        @Suppress("NAME_SHADOWING") val prevMonthShifts = prevMonthShifts.employeePaidOnly()
        val hasPay = settings?.let { s ->
            (s.hourlyRate ?: 0.0) > 0.0 ||
                profiles.any { (it.baseHourlyRate ?: 0.0) > 0.0 }
        } == true

        val zone = zoneOverride ?: settings?.let { WorkTimezone.zoneFor(it) } ?: ZoneOffset.UTC

        data class Bucket(
            val shiftList: MutableList<Shift> = mutableListOf(),
            var totalMin: Int = 0,
            var overtimeMin: Int = 0,
            var pay: Double = 0.0,
            var prevMin: Int = 0,
            var firstDate: String = "",
        )
        val buckets = Array(4) { Bucket() }

        for (shift in shifts) {
            val wi = weekBucket(shift, zone)
            val mins = min(ShiftDurationCalculator.netMinutes(shift) ?: 0, MAX_SHIFT_MIN)
            val b = buckets[wi]
            b.shiftList += shift
            b.totalMin += mins
            if (settings != null) {
                val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
                val threshold = resolved.rules.dailyStandardMinutes
                b.overtimeMin += maxOf(0, mins - threshold)
                if (hasPay) {
                    b.pay += PayrollCalculator.calculateShiftPayInContext(
                        shift, shifts, settings, profiles, premiumProfiles,
                    )?.totalGross ?: 0.0
                }
            }
            val date = WorkTimezone.shiftLocalDate(shift, zone).toString()
            if (b.firstDate.isEmpty() || date < b.firstDate) b.firstDate = date
        }

        for (shift in prevMonthShifts) {
            val wi = weekBucket(shift, zone)
            buckets[wi].prevMin += min(ShiftDurationCalculator.netMinutes(shift) ?: 0, MAX_SHIFT_MIN)
        }

        return buckets.mapIndexed { i, b ->
            WeeklyTotals(
                weekStart        = b.firstDate.ifEmpty { "week-$i" },
                label            = LABELS[i],
                dayRange         = DAY_RANGE[i],
                totalMinutes     = b.totalMin,
                overtimeMinutes  = b.overtimeMin,
                pay              = if (hasPay) b.pay else null,
                prevMonthMinutes = b.prevMin,
                shifts           = b.shiftList,
            )
        }
    }

    /** 0-based bucket index by local day-of-month in the work timezone. */
    private fun weekBucket(shift: Shift, zone: ZoneId): Int {
        val day = shift.startTime.atZone(zone).dayOfMonth
        return when {
            day <= 7  -> 0
            day <= 14 -> 1
            day <= 21 -> 2
            else      -> 3
        }
    }
}
