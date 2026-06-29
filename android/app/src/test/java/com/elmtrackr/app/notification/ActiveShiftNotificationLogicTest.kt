package com.elmtrackr.app.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ActiveShiftNotificationLogicTest {

    // ── isShiftOverThreshold ──────────────────────────────────────────────────

    @Test
    fun `isShiftOverThreshold returns false when shift just started`() {
        val startTime = Instant.now().minusSeconds(60) // 1 minute ago
        assertFalse(ActiveShiftNotificationManager.isShiftOverThreshold(startTime, thresholdMinutes = 480))
    }

    @Test
    fun `isShiftOverThreshold returns false when just under threshold`() {
        val startTime = Instant.now().minusSeconds(8 * 3600 - 1) // 1 second short of 8h
        assertFalse(ActiveShiftNotificationManager.isShiftOverThreshold(startTime, thresholdMinutes = 480))
    }

    @Test
    fun `isShiftOverThreshold returns true when elapsed equals threshold`() {
        val startTime = Instant.now().minusSeconds(8 * 3600) // exactly 8h
        assertTrue(ActiveShiftNotificationManager.isShiftOverThreshold(startTime, thresholdMinutes = 480))
    }

    @Test
    fun `isShiftOverThreshold returns true when elapsed exceeds threshold`() {
        val startTime = Instant.now().minusSeconds(10 * 3600) // 10h elapsed, 8h threshold
        assertTrue(ActiveShiftNotificationManager.isShiftOverThreshold(startTime, thresholdMinutes = 480))
    }

    @Test
    fun `isShiftOverThreshold respects custom threshold`() {
        val startTime = Instant.now().minusSeconds(6 * 3600) // 6h elapsed
        assertFalse(ActiveShiftNotificationManager.isShiftOverThreshold(startTime, thresholdMinutes = 480))
        assertTrue(ActiveShiftNotificationManager.isShiftOverThreshold(startTime, thresholdMinutes = 300))
    }

    @Test
    fun `isShiftOverThreshold uses fallback threshold correctly`() {
        val startTime = Instant.now().minusSeconds(9 * 3600) // 9h elapsed
        assertTrue(
            ActiveShiftNotificationManager.isShiftOverThreshold(
                startTime,
                OvertimeReminderPolicy.FALLBACK_THRESHOLD_MINUTES,
            ),
        )
    }

    @Test
    fun `isShiftOverThreshold false for future start time`() {
        val startTime = Instant.now().plusSeconds(3600) // start 1h in the future (edge case)
        assertFalse(ActiveShiftNotificationManager.isShiftOverThreshold(startTime, thresholdMinutes = 480))
    }
}
