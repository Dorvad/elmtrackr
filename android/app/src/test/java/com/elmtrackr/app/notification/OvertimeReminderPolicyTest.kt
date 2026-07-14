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

    // startTime (2024-01-08) is a Monday in UTC. ISO day-of-week: Mon=1 .. Sun=7.

    @Test
    fun `at-time rule fires today when today is a selected day and the time is ahead`() {
        val rule = ReminderRule(
            "r1",
            ReminderTriggerKind.AT_TIME,
            timeMinuteOfDay = 17 * 60 + 30,
            daysOfWeek = setOf(1), // Monday
        )
        // Monday 09:00 -> Monday 17:30 is 8.5 hours away.
        assertEquals(8 * 60L + 30, delayFor(rule, startTime))
    }

    @Test
    fun `at-time rule skips forward to the next selected day`() {
        val rule = ReminderRule(
            "r1",
            ReminderTriggerKind.AT_TIME,
            timeMinuteOfDay = 17 * 60 + 30,
            daysOfWeek = setOf(3), // Wednesday
        )
        // Monday 09:00 -> Wednesday 17:30 is 2 days + 8.5 hours.
        assertEquals(2 * 24 * 60L + 8 * 60 + 30, delayFor(rule, startTime))
    }

    @Test
    fun `at-time rule wraps to next week when only today is selected but the time passed`() {
        val rule = ReminderRule(
            "r1",
            ReminderTriggerKind.AT_TIME,
            timeMinuteOfDay = 8 * 60,
            daysOfWeek = setOf(1), // Monday, but 08:00 already passed at 09:00
        )
        // Next Monday 08:00 is 6 days + 23 hours away.
        assertEquals(6 * 24 * 60L + 23 * 60, delayFor(rule, startTime))
    }

    @Test
    fun `at-time rule with empty days behaves like every day`() {
        val everyDay = ReminderRule("r1", ReminderTriggerKind.AT_TIME, timeMinuteOfDay = 17 * 60 + 30)
        val allDays = everyDay.copy(daysOfWeek = (1..7).toSet())
        assertEquals(delayFor(everyDay, startTime), delayFor(allDays, startTime))
    }

    @Test
    fun `firesOn respects selected days and treats empty as every day`() {
        val monOnly = ReminderRule("r1", ReminderTriggerKind.AT_TIME, daysOfWeek = setOf(1))
        assertEquals(true, monOnly.firesOn(java.time.DayOfWeek.MONDAY))
        assertEquals(false, monOnly.firesOn(java.time.DayOfWeek.TUESDAY))
        val everyDay = ReminderRule("r2", ReminderTriggerKind.AT_TIME)
        assertEquals(true, everyDay.firesOn(java.time.DayOfWeek.SATURDAY))
    }

    // --- codec ---

    @Test
    fun `codec round-trips rules`() {
        val rules = listOf(
            ReminderRule("a", ReminderTriggerKind.BEFORE_OVERTIME, offsetMinutes = 15),
            ReminderRule("b", ReminderTriggerKind.AFTER_OVERTIME, offsetMinutes = 120),
            ReminderRule("c", ReminderTriggerKind.AT_TIME, timeMinuteOfDay = 17 * 60, daysOfWeek = setOf(1, 3, 5)),
        )
        assertEquals(rules, ReminderRulesCodec.decode(ReminderRulesCodec.encode(rules)))
    }

    @Test
    fun `codec decodes rules saved before daysOfWeek existed as every day`() {
        // Payload written by an older app version, without the daysOfWeek field.
        val legacy = """[{"id":"c","kind":"AT_TIME","timeMinuteOfDay":1050}]"""
        val decoded = ReminderRulesCodec.decode(legacy)
        assertNotNull(decoded)
        assertEquals(emptySet<Int>(), decoded!!.single().daysOfWeek)
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
