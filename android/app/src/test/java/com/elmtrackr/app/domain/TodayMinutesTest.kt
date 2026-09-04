package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One definition of "how long today", where there were three.
 *
 * The widget and the watch counted completed shifts net of break and added the
 * running shift **gross**; the Shifts screen's week cards did the same. So a
 * running shift with a break recorded on it counted more while it was running than
 * the moment it ended, and the day ring stepped backwards at clock-out.
 */
class TodayMinutesTest {

    private val zone: ZoneId = ZoneId.of("Asia/Jerusalem")
    private val now: Instant = Instant.parse("2024-01-08T14:00:00Z")

    private fun shift(
        id: String,
        start: String,
        end: String? = null,
        break_: Int = 0,
    ) = Shift(
        id = id,
        userId = "u1",
        startTime = Instant.parse(start),
        endTime = end?.let { Instant.parse(it) },
        breakMinutes = break_,
    )

    @Test
    fun `a running shift counts net of its break`() {
        // Started 09:00Z, now 14:00Z: five hours elapsed, half an hour of it break.
        val running = shift("s1", "2024-01-08T09:00:00Z", break_ = 30)

        assertEquals(270, TodayMinutes.activeNetMinutes(running, now))
    }

    /**
     * The regression this exists to prevent: the total must not fall when a shift
     * ends.
     */
    @Test
    fun `the total does not drop when a shift with a break ends`() {
        val running = shift("s1", "2024-01-08T09:00:00Z", break_ = 30)
        val ended = running.copy(endTime = now)

        val whileRunning = TodayMinutes.forDay(listOf(running), zone, LocalDate.of(2024, 1, 8), now)
        val afterEnding = TodayMinutes.forDay(listOf(ended), zone, LocalDate.of(2024, 1, 8), now)

        assertEquals(whileRunning, afterEnding)
    }

    @Test
    fun `a completed shift counts net of its break`() {
        val done = shift("s1", "2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z", break_ = 45)

        assertEquals(435, TodayMinutes.netMinutes(done, now))
    }

    @Test
    fun `a break longer than the shift cannot make it negative`() {
        val odd = shift("s1", "2024-01-08T13:30:00Z", break_ = 600)

        assertEquals(0, TodayMinutes.activeNetMinutes(odd, now))
    }

    @Test
    fun `shifts are attributed to the day they began`() {
        // 2024-01-07 22:00Z is 2024-01-08 00:00 in Jerusalem, so it belongs to the
        // 8th; 2024-01-07 20:00Z is the 7th, and must not be counted.
        val startedOnThe8th = shift("s1", "2024-01-07T22:00:00Z", "2024-01-08T02:00:00Z")
        val startedOnThe7th = shift("s2", "2024-01-07T20:00:00Z", "2024-01-07T21:00:00Z")

        val total = TodayMinutes.forDay(
            listOf(startedOnThe8th, startedOnThe7th), zone, LocalDate.of(2024, 1, 8), now,
        )

        assertEquals(240, total)
    }

    @Test
    fun `a finished shift is never counted as running`() {
        val done = shift("s1", "2024-01-08T09:00:00Z", "2024-01-08T10:00:00Z")

        assertEquals(0, TodayMinutes.activeNetMinutes(done, now))
        assertEquals(60, TodayMinutes.netMinutes(done, now))
    }
}
