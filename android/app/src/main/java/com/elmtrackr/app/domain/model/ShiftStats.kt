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
    val pay: Double? = null,
    val prevMonthMinutes: Int = 0,
    val shifts: List<Shift>,
)

/** Aggregated monthly stats for the Reports screen. */
data class MonthlyReport(
    val year: Int,
    val month: Int,            // 1–12
    val totalMinutes: Int,
    val regularMinutes: Int,
    val overtimeMinutes: Int,
    val weekendMinutes: Int,
    val shiftCount: Int,
    val shifts: List<ShiftBreakdown>,
)
