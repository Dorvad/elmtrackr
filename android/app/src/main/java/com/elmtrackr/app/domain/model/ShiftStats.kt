package com.elmtrackr.app.domain.model

/** One calendar day's slice of a (potentially overnight) shift. */
data class DaySegment(
    val date: String,          // YYYY-MM-DD (UTC)
    val minutes: Int,
    val isWeekend: Boolean = false,
)

/** Shift enriched with computed statistics (UI-facing, never persisted). */
data class ShiftWithStats(
    val shift: Shift,
    val grossMinutes: Int,
    val netMinutes: Int,
    val isOvernight: Boolean,
    val spansWeekend: Boolean,
)

/**
 * Breakdown of a single shift into mutually-exclusive buckets.
 * regular + overtime + weekend == total (may differ by ±1 due to rounding on overnight splits).
 */
data class ShiftBreakdown(
    val totalMinutes: Int,
    val regularMinutes: Int,
    val overtimeMinutes: Int,
    val weekendMinutes: Int,
    val segments: List<DaySegment>,
    /**
     * Time actually worked: clock time less breaks, before rounding or a minimum-shift
     * floor. [totalMinutes] is the *payable* figure the categories add up to; the two
     * are equal unless the profile rounds, guarantees a minimum, pays breaks or
     * auto-deducts one.
     */
    val workedMinutes: Int = totalMinutes,
    /**
     * How the shift's clock time became [totalMinutes]. Null for project time, whose
     * hours are tracked but not paid as wages.
     */
    val paidTime: PaidTime? = null,
)

/**
 * Totals for a calendar-week bucket (days 1–7, 8–14, 15–21, 22+).
 * weekStart is still the ISO date of the Monday but is kept for compatibility.
 */
data class WeeklyTotals(
    val weekStart: String,          // YYYY-MM-DD label (first shift day in bucket)
    val label: String,              // "Week 1" … "Week 4"
    val dayRange: String,           // "1–7", "8–14", "15–21", "22+"
    val totalMinutes: Int,
    val overtimeMinutes: Int = 0,
    /** Gross per currency; null when no rate is configured. Never one figure across currencies. */
    val pay: PayByCurrency? = null,
    val prevMonthMinutes: Int = 0,
    val shifts: List<Shift>,
)

/** Aggregated monthly stats for the Reports screen. */
data class MonthlyReport(
    val year: Int,
    val month: Int,            // 1–12
    /** Payable minutes — what the pay figures are derived from. See [workedMinutes]. */
    val totalMinutes: Int,
    val regularMinutes: Int,
    val overtimeMinutes: Int,
    val weekendMinutes: Int,
    val shiftCount: Int,
    val shifts: List<ShiftBreakdown>,
    /**
     * Minutes actually worked across the month, before rounding or minimum-shift
     * floors. Shown as "worked"; [totalMinutes] is shown as "paid" only where the two
     * differ, so rounded time is never presented as time physically worked.
     */
    val workedMinutes: Int = totalMinutes,
) {
    /** True when the profile's time rules made the paid hours differ from the worked hours. */
    val payableDiffersFromWorked: Boolean get() = totalMinutes != workedMinutes

    /** Rounding across the month, signed. */
    val roundingMinutes: Int get() = shifts.sumOf { it.paidTime?.roundingMinutes ?: 0 }

    /** Minutes added by minimum-shift guarantees across the month. */
    val minimumTopUpMinutes: Int get() = shifts.sumOf { it.paidTime?.minimumTopUpMinutes ?: 0 }

    /** Recorded break minutes the profiles paid for across the month. */
    val paidBreakMinutes: Int get() = shifts.sumOf { it.paidTime?.paidBreakMinutes ?: 0 }

    /** Unpaid break minutes across the month, recorded and automatically deducted. */
    val unpaidBreakMinutes: Int get() = shifts.sumOf { it.paidTime?.unpaidBreakMinutes ?: 0 }

    /** Which rules made paid time differ from worked time this month. */
    val paidTimeReasons: Set<PaidTimeReason>
        get() = if (payableDiffersFromWorked) {
            PaidTimeReason.of(roundingMinutes, minimumTopUpMinutes, paidBreakMinutes)
        } else {
            emptySet()
        }
}
