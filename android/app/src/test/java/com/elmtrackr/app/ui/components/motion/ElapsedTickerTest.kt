package com.elmtrackr.app.ui.components.motion

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * The arithmetic behind the three live-duration displays (the dashboard clock,
 * the shifts hero total, the active-row duration). It used to be written out
 * three times; these cases are the ones each copy had to get right.
 */
class ElapsedTickerTest {

    private val start = Instant.parse("2026-08-03T08:00:00Z")

    @Test
    fun `seconds count whole seconds since start`() {
        assertEquals(
            0L,
            elapsedUnits(start, 1_000L, Instant.parse("2026-08-03T08:00:00.999Z")),
        )
        assertEquals(
            90L,
            elapsedUnits(start, 1_000L, Instant.parse("2026-08-03T08:01:30Z")),
        )
    }

    /**
     * The whole point of the minute unit: the value must not change on every
     * tick, or the display recomposes sixty times per visible change.
     */
    @Test
    fun `minute units hold steady within a minute`() {
        val oneMinute = 60_000L
        assertEquals(1L, elapsedUnits(start, oneMinute, Instant.parse("2026-08-03T08:01:00Z")))
        assertEquals(1L, elapsedUnits(start, oneMinute, Instant.parse("2026-08-03T08:01:59Z")))
        assertEquals(2L, elapsedUnits(start, oneMinute, Instant.parse("2026-08-03T08:02:00Z")))
    }

    /**
     * A start time in the future is reachable: the user can edit a shift's start
     * time, and device clocks drift against the server's. It must read zero, not
     * a negative duration.
     */
    @Test
    fun `a start time in the future reads zero`() {
        assertEquals(
            0L,
            elapsedUnits(start, 1_000L, Instant.parse("2026-08-03T07:45:00Z")),
        )
    }

    @Test
    fun `no start time reads zero`() {
        assertEquals(0L, elapsedUnits(null, 1_000L, Instant.parse("2026-08-03T09:00:00Z")))
    }
}
