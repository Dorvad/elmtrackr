package com.elmtrackr.app.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class OvertimeReminderPolicyTest {

    private val threshold = 480
    private val startTime = Instant.parse("2024-01-08T09:00:00Z")

    @Test
    fun `preWarningDelayMinutes is 30 minutes before threshold`() {
        val now = startTime.plusSeconds(60)
        assertEquals(449L, OvertimeReminderPolicy.preWarningDelayMinutes(threshold, startTime, now))
    }

    @Test
    fun `preWarningDelayMinutes returns zero when warning point already passed`() {
        val now = startTime.plusSeconds(8 * 3600 - 20 * 60)
        assertEquals(0L, OvertimeReminderPolicy.preWarningDelayMinutes(threshold, startTime, now))
    }

    @Test
    fun `preWarningDelayMinutes skipped when threshold is 30 minutes or less`() {
        assertEquals(-1L, OvertimeReminderPolicy.preWarningDelayMinutes(30, startTime))
        assertEquals(-1L, OvertimeReminderPolicy.preWarningDelayMinutes(20, startTime))
    }

    @Test
    fun `atThresholdDelayMinutes reaches zero at overtime boundary`() {
        val now = startTime.plusSeconds(8 * 3600)
        assertEquals(0L, OvertimeReminderPolicy.atThresholdDelayMinutes(threshold, startTime, now))
    }

    @Test
    fun `nextHourlyDelayMinutes waits until overtime when not yet in overtime`() {
        val now = startTime.plusSeconds(7 * 3600)
        assertEquals(60L, OvertimeReminderPolicy.nextHourlyDelayMinutes(threshold, startTime, now))
    }

    @Test
    fun `nextHourlyDelayMinutes schedules first hour after overtime starts`() {
        val now = startTime.plusSeconds(8 * 3600)
        assertEquals(60L, OvertimeReminderPolicy.nextHourlyDelayMinutes(threshold, startTime, now))
    }

    @Test
    fun `nextHourlyDelayMinutes schedules remaining minutes within current overtime hour`() {
        val now = startTime.plusSeconds(8 * 3600 + 15 * 60)
        assertEquals(45L, OvertimeReminderPolicy.nextHourlyDelayMinutes(threshold, startTime, now))
    }

    @Test
    fun `overtimeHoursElapsed counts completed overtime hours`() {
        val now = startTime.plusSeconds(8 * 3600 + 90 * 60)
        assertEquals(2L, OvertimeReminderPolicy.overtimeHoursElapsed(threshold, startTime, now))
    }

    @Test
    fun `overtimeHoursElapsed is zero before threshold`() {
        val now = startTime.plusSeconds(7 * 3600)
        assertEquals(0L, OvertimeReminderPolicy.overtimeHoursElapsed(threshold, startTime, now))
    }
}
