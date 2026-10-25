package com.elmtrackr.app.domain.dashboard

import com.elmtrackr.app.domain.model.Shift
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class LongShiftNoticeTest {

    private val now = Instant.parse("2026-10-06T10:00:00Z")

    private fun runningFor(duration: Duration) =
        Shift(id = "s", userId = "u", startTime = now.minus(duration), endTime = null)

    @Test
    fun `an ordinary long shift does not trigger the notice`() {
        assertFalse(LongShiftNotice.shouldShow(runningFor(Duration.ofHours(12)), now))
        assertFalse(LongShiftNotice.shouldShow(runningFor(Duration.ofMinutes(959)), now))
    }

    @Test
    fun `sixteen hours running asks whether the clock-out was forgotten`() {
        assertTrue(LongShiftNotice.shouldShow(runningFor(Duration.ofHours(16)), now))
        assertTrue(LongShiftNotice.shouldShow(runningFor(Duration.ofHours(30)), now))
    }

    @Test
    fun `no running shift, no notice`() {
        assertFalse(LongShiftNotice.shouldShow(null, now))
        val completed = runningFor(Duration.ofHours(20)).copy(endTime = now)
        assertFalse(LongShiftNotice.shouldShow(completed, now))
    }
}
