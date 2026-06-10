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

/** All shifts in an ISO Monday-anchored week. */
data class WeeklyTotals(
    val weekStart: String,     // YYYY-MM-DD (always a Monday)
    val totalMinutes: Int,
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
