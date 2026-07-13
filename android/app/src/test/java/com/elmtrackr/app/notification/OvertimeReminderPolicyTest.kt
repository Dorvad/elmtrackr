package com.elmtrackr.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

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

    // --- delayMinutesForRule ---

    private val utc = ZoneId.of("UTC")

    private fun delayFor(rule: ReminderRule, now: Instant): Long =
        OvertimeReminderPolicy.delayMinutesForRule(rule, threshold, startTime, now, utc)

    @Test
    fun `before-overtime rule fires offset minutes before threshold`() {
        val rule = ReminderRule("r1", ReminderTriggerKind.BEFORE_OVERTIME, offsetMinutes = 45)
        val now = startTime.plusSeconds(60)
        assertEquals(480L - 45 - 1, delayFor(rule, now))
    }

    @Test
    fun `before-overtime rule skipped once overtime has started`() {
        val rule = ReminderRule("r1", ReminderTriggerKind.BEFORE_OVERTIME, offsetMinutes = 30)
        val now = startTime.plusSeconds(9 * 3600)
        assertEquals(OvertimeReminderPolicy.SKIP, delayFor(rule, now))
    }

    @Test
    fun `before-overtime rule skipped when threshold not larger than offset`() {
        val rule = ReminderRule("r1", ReminderTriggerKind.BEFORE_OVERTIME, offsetMinutes = 45)
        assertEquals(
            OvertimeReminderPolicy.SKIP,
            OvertimeReminderPolicy.delayMinutesForRule(rule, 45, startTime, startTime, utc),
        )
    }

    @Test
    fun `after-overtime once rule fires exactly at threshold`() {
        val rule = ReminderRule("r1", ReminderTriggerKind.AFTER_OVERTIME, offsetMinutes = 0)
        val now = startTime.plusSeconds(3600)
        assertEquals(480L - 60, delayFor(rule, now))
    }

    @Test
    fun `after-overtime once rule skipped when already in overtime`() {
        val rule = ReminderRule("r1", ReminderTriggerKind.AFTER_OVERTIME, offsetMinutes = 0)
        val now = startTime.plusSeconds(9 * 3600)
        assertEquals(OvertimeReminderPolicy.SKIP, delayFor(rule, now))
    }

    @Test
    fun `after-overtime repeating rule schedules first interval after threshold`() {
        val rule = ReminderRule("r1", ReminderTriggerKind.AFTER_OVERTIME, offsetMinutes = 30)
        val now = startTime.plusSeconds(8 * 3600)
        assertEquals(30L, delayFor(rule, now))
    }

    @Test
    fun `after-overtime repeating rule schedules remainder of current interval`() {
        val rule = ReminderRule("r1", ReminderTriggerKind.AFTER_OVERTIME, offsetMinutes = 60)
        val now = startTime.plusSeconds(8 * 3600 + 15 * 60)
        assertEquals(45L, delayFor(rule, now))
    }

    @Test
    fun `after-overtime repeating rule counts down to threshold plus interval before overtime`() {
        val rule = ReminderRule("r1", ReminderTriggerKind.AFTER_OVERTIME, offsetMinutes = 60)
        val now = startTime.plusSeconds(7 * 3600)
        assertEquals(60L + 60, delayFor(rule, now))
    }

    @Test
    fun `at-time rule targets the next occurrence of the wall-clock time`() {
        // Start 09:00 UTC; reminder at 17:30 → 8.5 hours away at start.
        val rule = ReminderRule("r1", ReminderTriggerKind.AT_TIME, timeMinuteOfDay = 17 * 60 + 30)
        assertEquals(8 * 60L + 30, delayFor(rule, startTime))
    }

    @Test
    fun `at-time rule rolls to tomorrow when the time already passed`() {
        val rule = ReminderRule("r1", ReminderTriggerKind.AT_TIME, timeMinuteOfDay = 8 * 60)
        // 09:00 is past 08:00, so the next 08:00 is 23 hours away.
        assertEquals(23 * 60L, delayFor(rule, startTime))
    }

    // --- codec ---

    @Test
    fun `codec round-trips rules`() {
        val rules = listOf(
            ReminderRule("a", ReminderTriggerKind.BEFORE_OVERTIME, offsetMinutes = 15),
            ReminderRule("b", ReminderTriggerKind.AFTER_OVERTIME, offsetMinutes = 120),
            ReminderRule("c", ReminderTriggerKind.AT_TIME, timeMinuteOfDay = 17 * 60),
        )
        assertEquals(rules, ReminderRulesCodec.decode(ReminderRulesCodec.encode(rules)))
    }

    @Test
    fun `codec returns null for blank or corrupt payloads`() {
        assertNull(ReminderRulesCodec.decode(null))
        assertNull(ReminderRulesCodec.decode(""))
        assertNull(ReminderRulesCodec.decode("not json"))
        assertNull(ReminderRulesCodec.decode("""[{"id":"x","kind":"NO_SUCH_KIND"}]"""))
    }

    @Test
    fun `default rules preserve the legacy 30-before and hourly-after behavior`() {
        val defaults = ReminderRulesCodec.DEFAULT_RULES
        assertEquals(2, defaults.size)
        assertNotNull(defaults.firstOrNull { it.kind == ReminderTriggerKind.BEFORE_OVERTIME && it.offsetMinutes == 30 })
        assertNotNull(defaults.firstOrNull { it.kind == ReminderTriggerKind.AFTER_OVERTIME && it.offsetMinutes == 60 })
    }
}
