package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.model.WeeklyTotals
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
        prevMonthShifts: List<Shift> = emptyList(),
    ): List<WeeklyTotals> {
        val hasPay = settings?.hourlyRate?.let { it > 0 } == true

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
            val wi = weekBucket(shift)
            val mins = min(ShiftDurationCalculator.netMinutes(shift) ?: 0, MAX_SHIFT_MIN)
            val b = buckets[wi]
            b.shiftList += shift
            b.totalMin += mins
            if (settings != null) {
                b.overtimeMin += maxOf(0, mins - settings.dailyOvertimeThresholdMinutes)
                if (hasPay) {
                    b.pay += PayrollCalculator.calculateShiftPay(shift, settings)?.totalGross ?: 0.0
                }
            }
            val date = shift.startTime.atOffset(ZoneOffset.UTC).toLocalDate().toString()
            if (b.firstDate.isEmpty() || date < b.firstDate) b.firstDate = date
        }

        for (shift in prevMonthShifts) {
            val wi = weekBucket(shift)
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

    /** 0-based bucket index by UTC day-of-month. */
    private fun weekBucket(shift: Shift): Int {
        val day = shift.startTime.atOffset(ZoneOffset.UTC).dayOfMonth
        return when {
            day <= 7  -> 0
            day <= 14 -> 1
            day <= 21 -> 2
            else      -> 3
        }
    }
}
