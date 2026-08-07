package com.elmtrackr.app.ui.components.motion

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class NowTickerTest {

    /**
     * Truncation is what keeps a once-a-second tick from recomposing a clock that
     * only shows minutes sixty times a minute: equal values written back to the
     * state are not a change, and they are only equal if truncated first.
     */
    @Test
    fun `readings within the same unit truncate to the same instant`() {
        val base = Instant.parse("2026-08-06T14:23:00Z")

        val atStart = truncatedNow(MINUTE_MILLIS, base)
        val partWay = truncatedNow(MINUTE_MILLIS, base.plusSeconds(37))
        val lastMoment = truncatedNow(MINUTE_MILLIS, base.plusMillis(59_999))

        assertEquals(base, atStart)
        assertEquals(base, partWay)
        assertEquals(base, lastMoment)
    }

    @Test
    fun `crossing the unit boundary produces a new instant`() {
        val base = Instant.parse("2026-08-06T14:23:00Z")

        val before = truncatedNow(MINUTE_MILLIS, base.plusMillis(59_999))
        val after = truncatedNow(MINUTE_MILLIS, base.plusSeconds(60))

        assertEquals(Instant.parse("2026-08-06T14:24:00Z"), after)
        assertEquals(60_000L, after.toEpochMilli() - before.toEpochMilli())
    }

    @Test
    fun `hour granularity truncates to the top of the hour`() {
        val reading = Instant.parse("2026-08-06T14:59:59Z")

        assertEquals(
            Instant.parse("2026-08-06T14:00:00Z"),
            truncatedNow(HOUR_MILLIS, reading),
        )
    }

    @Test
    fun `a unit of one leaves the reading alone`() {
        val reading = Instant.parse("2026-08-06T14:23:45.123Z")

        assertEquals(reading, truncatedNow(1L, reading))
    }
}
