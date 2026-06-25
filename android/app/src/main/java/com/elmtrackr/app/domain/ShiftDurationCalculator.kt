package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift
import java.time.Instant

/**
 * Pure functions for shift duration arithmetic.
 * All methods are stateless and UI-independent.
 */
object ShiftDurationCalculator {

    /** Wall-clock minutes between start and end. Null for active shifts. */
    fun grossMinutes(shift: Shift): Int? {
        val end = shift.endTime ?: return null
        val millis = end.toEpochMilli() - shift.startTime.toEpochMilli()
        return maxOf(0, (millis / 60_000L).toInt())
    }

    /**
     * Net minutes = gross – break, clamped to 0.
     * Null for active shifts (no end time).
     */
    fun netMinutes(shift: Shift): Int? {
        val gross = grossMinutes(shift) ?: return null
        return maxOf(0, gross - shift.breakMinutes)
    }

    /**
     * Elapsed minutes for an active shift relative to [now].
     * Returns null if the shift is already completed.
     */
    fun elapsedMinutes(shift: Shift, now: Instant = Instant.now()): Int? {
        if (shift.endTime != null) return null
        val millis = now.toEpochMilli() - shift.startTime.toEpochMilli()
        return maxOf(0, (millis / 60_000L).toInt())
    }

    /** Format minutes as "Xh Ym" (e.g. "8h 30m", "45m", "0m"). */
    fun formatMinutes(minutes: Int): String {
        if (minutes <= 0) return "0m"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else   -> "${h}h ${m}m"
        }
    }

    /**
     * Validate a shift time range.
     * Returns a human-readable error string, or null if the times are valid.
     */
    fun validateShiftTimes(
        startTime: Instant,
        endTime: Instant?,
        breakMinutes: Int,
    ): String? {
        if (breakMinutes < 0) return "Break minutes cannot be negative."
        if (endTime != null) {
            if (!endTime.isAfter(startTime)) return "End time must be after start time."
            val gross = ((endTime.toEpochMilli() - startTime.toEpochMilli()) / 60_000L).toInt()
            if (breakMinutes >= gross) return "Break time cannot be equal to or longer than the shift duration."
        }
        return null
    }
}
