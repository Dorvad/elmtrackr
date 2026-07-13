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

    /** Sentinel returned by [delayMinutesForRule] when the rule should not be scheduled. */
    const val SKIP = -1L

    /**
     * Minutes until [rule] should next fire for a shift started at [startTime],
     * or [SKIP] when the rule's moment has already passed (or can never occur
     * for this threshold). AT_TIME rules resolve against [zone].
     */
    fun delayMinutesForRule(
        rule: ReminderRule,
        thresholdMinutes: Int,
        startTime: Instant,
        now: Instant = Instant.now(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): Long {
        val elapsed = elapsedMinutes(startTime, now)
        return when (rule.kind) {
            ReminderTriggerKind.BEFORE_OVERTIME -> {
                val offset = rule.offsetMinutes.coerceAtLeast(1)
                if (thresholdMinutes <= offset) return SKIP
                if (elapsed >= thresholdMinutes) return SKIP
                (thresholdMinutes - offset - elapsed).coerceAtLeast(0)
            }
            ReminderTriggerKind.AFTER_OVERTIME -> {
                if (rule.offsetMinutes <= 0) {
                    // Fires once, when overtime starts.
                    if (elapsed >= thresholdMinutes) SKIP
                    else (thresholdMinutes - elapsed)
                } else {
                    // Repeats every offsetMinutes after the threshold.
                    val interval = rule.offsetMinutes.coerceAtLeast(MIN_REPEAT_INTERVAL_MINUTES)
                    if (elapsed < thresholdMinutes) {
                        thresholdMinutes - elapsed + interval
                    } else {
                        val intoOvertime = elapsed - thresholdMinutes
                        val untilNext = interval - (intoOvertime % interval)
                        if (untilNext == 0L) interval.toLong() else untilNext
                    }
                }
            }
            ReminderTriggerKind.AT_TIME -> {
                val nowLocal = now.atZone(zone)
                val target = nowLocal.toLocalDate()
                    .atStartOfDay(zone)
                    .plusMinutes(rule.timeMinuteOfDay.toLong())
                val resolved = if (!target.isAfter(nowLocal)) target.plusDays(1) else target
                Duration.between(nowLocal, resolved).toMinutes().coerceAtLeast(0)
            }
        }
    }

    private const val MIN_REPEAT_INTERVAL_MINUTES = 15

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

    /** Whole minutes spent past the overtime threshold (0 when not yet in overtime). */
    fun overtimeMinutesElapsed(
        thresholdMinutes: Int,
        startTime: Instant,
        now: Instant = Instant.now(),
    ): Long = (elapsedMinutes(startTime, now) - thresholdMinutes).coerceAtLeast(0)

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
