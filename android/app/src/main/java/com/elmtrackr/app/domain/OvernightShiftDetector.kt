package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.DaySegment
import com.elmtrackr.app.domain.model.Shift
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Detects overnight shifts and splits them into per-local-calendar-day segments.
 * Break minutes are distributed proportionally across segments (by gross-time share).
 */
object OvernightShiftDetector {

    // No default zone on purpose. UTC defaults let call sites silently disagree with the
    // pay engine: the PDF's "overnight" chip was decided in UTC while the CSV decided the
    // same shift in the work zone. Callers must pass the shift's work timezone
    // (WorkTimezone.zoneFor).
    fun isOvernight(shift: Shift, zone: ZoneId): Boolean {
        val end = shift.endTime ?: return false
        val startDate = shift.startTime.atZone(zone).toLocalDate()
        val endDate = end.atZone(zone).toLocalDate()
        return startDate != endDate
    }

    fun splitShiftByDay(shift: Shift, zone: ZoneId): List<DaySegment> {
        val end = shift.endTime ?: return emptyList()
        val totalNet = ShiftDurationCalculator.netMinutes(shift)
            ?.takeIf { it > 0 } ?: return emptyList()

        val startMs = shift.startTime.toEpochMilli()
        val endMs = end.toEpochMilli()
        val totalGrossMs = (endMs - startMs).toDouble()

        val segments = mutableListOf<DaySegment>()
        var cursorMs = startMs

        while (cursorMs < endMs) {
            val cursorDate = Instant.ofEpochMilli(cursorMs).atZone(zone).toLocalDate()
            val nextMidnightMs = cursorDate.plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()

            val segEndMs = minOf(nextMidnightMs, endMs)
            val proportion = if (totalGrossMs > 0) {
                (segEndMs - cursorMs).toDouble() / totalGrossMs
            } else {
                0.0
            }

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
