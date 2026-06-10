package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.DaySegment
import com.elmtrackr.app.domain.model.Shift
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Detects overnight shifts and splits them into per-UTC-calendar-day segments.
 * Break minutes are distributed proportionally across segments (by gross-time share).
 */
object OvernightShiftDetector {

    /** True if the shift crosses at least one UTC midnight boundary. */
    fun isOvernight(shift: Shift): Boolean {
        val end = shift.endTime ?: return false
        val startDate = shift.startTime.atOffset(ZoneOffset.UTC).toLocalDate()
        val endDate = end.atOffset(ZoneOffset.UTC).toLocalDate()
        return startDate != endDate
    }

    /**
     * Split a completed shift into per-UTC-day [DaySegment] list.
     * Returns an empty list for active shifts or zero-duration shifts.
     * isWeekend is always false here; call [WeekendRules.annotateWeekendSegments] afterwards.
     */
    fun splitShiftByDay(shift: Shift): List<DaySegment> {
        val end = shift.endTime ?: return emptyList()
        val totalNet = ShiftDurationCalculator.netMinutes(shift)
            ?.takeIf { it > 0 } ?: return emptyList()

        val startMs = shift.startTime.toEpochMilli()
        val endMs = end.toEpochMilli()
        val totalGrossMs = (endMs - startMs).toDouble()

        val segments = mutableListOf<DaySegment>()
        var cursorMs = startMs

        while (cursorMs < endMs) {
            val cursorDate = Instant.ofEpochMilli(cursorMs)
                .atOffset(ZoneOffset.UTC)
                .toLocalDate()

            val nextMidnightMs = cursorDate.plusDays(1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()

            val segEndMs = minOf(nextMidnightMs, endMs)
            val proportion = if (totalGrossMs > 0)
                (segEndMs - cursorMs).toDouble() / totalGrossMs
            else 0.0

            segments += DaySegment(
                date = cursorDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                minutes = (totalNet * proportion).roundToInt(),
                isWeekend = false,
            )
            cursorMs = segEndMs
        }

        return segments
    }
}
