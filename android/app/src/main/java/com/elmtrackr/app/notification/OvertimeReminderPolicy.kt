package com.elmtrackr.app.notification

import java.time.Duration
import java.time.Instant

/**
 * Pure scheduling logic for overtime reminder notifications.
 *
 * Uses gross elapsed time from [startTime], consistent with the dashboard clock
 * and the previous long-shift reminder behavior.
 */
object OvertimeReminderPolicy {
    const val PRE_WARNING_MINUTES = 30L
    const val HOURLY_INTERVAL_MINUTES = 60L
    const val FALLBACK_THRESHOLD_MINUTES = 480L

    fun preWarningDelayMinutes(
        thresholdMinutes: Int,
        startTime: Instant,
        now: Instant = Instant.now(),
    ): Long {
        if (thresholdMinutes <= PRE_WARNING_MINUTES) return -1
        val targetMinutes = thresholdMinutes - PRE_WARNING_MINUTES
        val elapsed = elapsedMinutes(startTime, now)
        return (targetMinutes - elapsed).coerceAtLeast(0)
    }

    fun atThresholdDelayMinutes(
        thresholdMinutes: Int,
        startTime: Instant,
        now: Instant = Instant.now(),
    ): Long {
        val elapsed = elapsedMinutes(startTime, now)
        return (thresholdMinutes.toLong() - elapsed).coerceAtLeast(0)
    }

    fun nextHourlyDelayMinutes(
        thresholdMinutes: Int,
        startTime: Instant,
        now: Instant = Instant.now(),
    ): Long {
        val elapsed = elapsedMinutes(startTime, now)
        if (elapsed < thresholdMinutes) {
            return (thresholdMinutes - elapsed).coerceAtLeast(0)
        }
        val overtimeMinutes = elapsed - thresholdMinutes
        val minutesUntilNextHour =
            HOURLY_INTERVAL_MINUTES - (overtimeMinutes % HOURLY_INTERVAL_MINUTES)
        return if (minutesUntilNextHour == HOURLY_INTERVAL_MINUTES) {
            HOURLY_INTERVAL_MINUTES
        } else {
            minutesUntilNextHour
        }
    }

    fun overtimeHoursElapsed(
        thresholdMinutes: Int,
        startTime: Instant,
        now: Instant = Instant.now(),
    ): Long {
        val elapsed = elapsedMinutes(startTime, now)
        if (elapsed <= thresholdMinutes) return 0
        return (elapsed - thresholdMinutes + HOURLY_INTERVAL_MINUTES - 1) / HOURLY_INTERVAL_MINUTES
    }

    private fun elapsedMinutes(startTime: Instant, now: Instant): Long =
        Duration.between(startTime, now).toMinutes().coerceAtLeast(0)
}
