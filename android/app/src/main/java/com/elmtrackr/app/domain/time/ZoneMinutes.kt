package com.elmtrackr.app.domain.time

import java.time.ZoneId

/**
 * Local wall-clock arithmetic for loops that walk a shift minute by minute.
 *
 * The pay engines classify each minute of a shift by two numbers only: which local
 * day it falls on and how far into that day it is. Getting them through
 * `Instant.atZone(zone)` costs an allocation and a zone-rules lookup per minute, and
 * the Israeli engine re-classifies every prior shift of the week for every shift, so
 * a month of work multiplied that cost by the square of the shift count.
 *
 * A zone's offset from UTC is constant between DST transitions, and inside such a
 * stretch the local calendar follows from the instant by the same integer arithmetic
 * `java.time` itself uses: `localSecond = epochSecond + offsetSeconds`, then
 * floor-divide by the length of a day. Callers ask [hasFixedOffset] whether the
 * interval they are about to walk contains a transition; when it does they must fall
 * back to `atZone`, because the arithmetic below would then be wrong.
 *
 * Every function here is verified against `java.time` in `ZoneMinutesTest`, including
 * across both of Jerusalem's transitions and in a southern-hemisphere zone.
 */
internal object ZoneMinutes {

    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_DAY = 86_400L
    private const val SECONDS_PER_MINUTE = 60L
    private const val DAYS_PER_WEEK = 7L

    /**
     * 1970-01-01 was a Thursday, which is index 4 in the 0=Sun … 6=Sat encoding the
     * compensation rules use.
     */
    private const val EPOCH_DAY_JS_OFFSET = 4L

    /**
     * True when [zone] keeps one offset across the whole interval `[fromMs, toMs]`,
     * so the arithmetic in this object applies to every instant in it.
     *
     * Inclusive of both ends: a transition landing exactly on [toMs] makes the answer
     * false, which costs a fallback on a boundary case rather than risking a wrong
     * one.
     */
    fun hasFixedOffset(zone: ZoneId, fromMs: Long, toMs: Long): Boolean {
        val next = zone.rules.nextTransition(java.time.Instant.ofEpochMilli(fromMs))
        return next == null || next.instant.toEpochMilli() > toMs
    }

    /** Offset from UTC in seconds that [zone] is on at [atMs]. */
    fun offsetSecondsAt(zone: ZoneId, atMs: Long): Long =
        zone.rules.getOffset(java.time.Instant.ofEpochMilli(atMs)).totalSeconds.toLong()

    /**
     * Minutes since local midnight for [instantMs], for a zone fixed at
     * [offsetSeconds]. Seconds are truncated, matching `zdt.hour * 60 + zdt.minute`.
     */
    fun minuteOfDay(instantMs: Long, offsetSeconds: Long): Int =
        (Math.floorMod(localSecond(instantMs, offsetSeconds), SECONDS_PER_DAY) / SECONDS_PER_MINUTE)
            .toInt()

    /**
     * Local day of the week for [instantMs] as a JS day index (0=Sun … 6=Sat), the
     * encoding [com.elmtrackr.app.domain.model.CompensationRules.weekendDays] uses.
     */
    fun jsDayOfWeek(instantMs: Long, offsetSeconds: Long): Int {
        val epochDay = Math.floorDiv(localSecond(instantMs, offsetSeconds), SECONDS_PER_DAY)
        return Math.floorMod(epochDay + EPOCH_DAY_JS_OFFSET, DAYS_PER_WEEK).toInt()
    }

    private fun localSecond(instantMs: Long, offsetSeconds: Long): Long =
        Math.floorDiv(instantMs, MILLIS_PER_SECOND) + offsetSeconds
}
