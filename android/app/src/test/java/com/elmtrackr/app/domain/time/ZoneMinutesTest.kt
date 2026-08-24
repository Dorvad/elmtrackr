package com.elmtrackr.app.domain.time

import com.elmtrackr.app.domain.toJsDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * [ZoneMinutes] replaces an `Instant.atZone` per payable minute in the pay engines,
 * so it is checked against `java.time` itself rather than against expected literals:
 * every minute of several days, in zones with and without DST, on both sides of a
 * transition and in the southern hemisphere.
 */
class ZoneMinutesTest {

    private val jerusalem = ZoneId.of("Asia/Jerusalem")
    private val newYork = ZoneId.of("America/New_York")
    private val sydney = ZoneId.of("Australia/Sydney")
    private val kolkata = ZoneId.of("Asia/Kolkata") // +05:30 — a half-hour offset
    private val utc = ZoneId.of("UTC")

    private fun assertMatchesJavaTime(zone: ZoneId, fromIso: String, minutes: Int) {
        val startMs = Instant.parse(fromIso).toEpochMilli()
        val endMs = startMs + (minutes - 1) * 60_000L
        assertTrue(
            "no transition expected in $fromIso +$minutes min ($zone)",
            ZoneMinutes.hasFixedOffset(zone, startMs, endMs),
        )
        val offsetSeconds = ZoneMinutes.offsetSecondsAt(zone, startMs)
        for (i in 0 until minutes) {
            val ms = startMs + i * 60_000L
            val zdt = Instant.ofEpochMilli(ms).atZone(zone)
            assertEquals(
                "minuteOfDay at $zone +$i from $fromIso",
                zdt.hour * 60 + zdt.minute,
                ZoneMinutes.minuteOfDay(ms, offsetSeconds),
            )
            assertEquals(
                "jsDayOfWeek at $zone +$i from $fromIso",
                zdt.dayOfWeek.toJsDay(),
                ZoneMinutes.jsDayOfWeek(ms, offsetSeconds),
            )
        }
    }

    @Test
    fun `matches java time across three days of winter in Jerusalem`() {
        assertMatchesJavaTime(jerusalem, "2024-01-14T00:00:00Z", 3 * 24 * 60)
    }

    @Test
    fun `matches java time across three days of summer in Jerusalem`() {
        assertMatchesJavaTime(jerusalem, "2024-07-14T00:00:00Z", 3 * 24 * 60)
    }

    /** A negative offset, where the local day is behind UTC and floor division matters. */
    @Test
    fun `matches java time across a day in New York`() {
        assertMatchesJavaTime(newYork, "2024-01-15T00:00:00Z", 24 * 60)
    }

    /** Southern hemisphere: DST runs the other way round through the year. */
    @Test
    fun `matches java time across a day in Sydney`() {
        assertMatchesJavaTime(sydney, "2024-01-15T00:00:00Z", 24 * 60)
    }

    /** A 30-minute offset, which a whole-hour shortcut would get wrong. */
    @Test
    fun `matches java time across a day in Kolkata`() {
        assertMatchesJavaTime(kolkata, "2024-01-15T00:00:00Z", 24 * 60)
    }

    @Test
    fun `matches java time across a day in UTC`() {
        assertMatchesJavaTime(utc, "2024-01-15T00:00:00Z", 24 * 60)
    }

    /** The whole point of the epoch-day anchor: 1970-01-01 was a Thursday. */
    @Test
    fun `the epoch itself is a Thursday`() {
        assertEquals(4, ZoneMinutes.jsDayOfWeek(0L, 0L))
        assertEquals(0, ZoneMinutes.minuteOfDay(0L, 0L))
    }

    /** Instants before the epoch: floor division, not truncation toward zero. */
    @Test
    fun `matches java time before the epoch`() {
        assertMatchesJavaTime(utc, "1969-12-30T00:00:00Z", 24 * 60)
        assertMatchesJavaTime(newYork, "1969-12-30T00:00:00Z", 24 * 60)
    }

    // ── The guard that decides when the arithmetic may be used ────────────────

    @Test
    fun `hasFixedOffset is false across the Jerusalem spring transition`() {
        // Asia/Jerusalem moves to DST on 2024-03-29 at 02:00 local (00:00 UTC).
        val before = Instant.parse("2024-03-28T22:00:00Z").toEpochMilli()
        val after = Instant.parse("2024-03-29T06:00:00Z").toEpochMilli()
        assertFalse(ZoneMinutes.hasFixedOffset(jerusalem, before, after))
    }

    @Test
    fun `hasFixedOffset is false across the Jerusalem autumn transition`() {
        // And back on 2024-10-27 at 02:00 local (2024-10-26T23:00Z).
        val before = Instant.parse("2024-10-26T20:00:00Z").toEpochMilli()
        val after = Instant.parse("2024-10-27T04:00:00Z").toEpochMilli()
        assertFalse(ZoneMinutes.hasFixedOffset(jerusalem, before, after))
    }

    @Test
    fun `hasFixedOffset is true for a shift that ends before the transition`() {
        val start = Instant.parse("2024-03-28T10:00:00Z").toEpochMilli()
        val end = Instant.parse("2024-03-28T18:00:00Z").toEpochMilli()
        assertTrue(ZoneMinutes.hasFixedOffset(jerusalem, start, end))
    }

    @Test
    fun `hasFixedOffset is always true for a zone with no DST`() {
        val start = Instant.parse("2024-03-28T10:00:00Z").toEpochMilli()
        val end = Instant.parse("2025-03-28T10:00:00Z").toEpochMilli()
        assertTrue(ZoneMinutes.hasFixedOffset(kolkata, start, end))
        assertTrue(ZoneMinutes.hasFixedOffset(utc, start, end))
    }

    /**
     * The interval is inclusive of its end, so a transition landing exactly on it
     * still sends the caller down the `atZone` path. Cheaper to be conservative here
     * than to reason about which side of the boundary the last minute lands on.
     */
    @Test
    fun `hasFixedOffset is false when a transition lands exactly on the end`() {
        val transition = Instant.parse("2024-03-29T00:00:00Z").toEpochMilli()
        val start = transition - 60 * 60_000L
        assertFalse(ZoneMinutes.hasFixedOffset(jerusalem, start, transition))
        assertTrue(ZoneMinutes.hasFixedOffset(jerusalem, start, transition - 1))
    }
}
