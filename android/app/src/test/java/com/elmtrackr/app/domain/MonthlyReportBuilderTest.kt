package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MonthlyReportBuilderTest {

    private val settings = UserSettings(
        id = "cfg1", userId = "u1",
        dailyOvertimeThresholdMinutes = 480,
        weeklyOvertimeThresholdMinutes = 2400,
        weekendDays = listOf(5, 6),  // Fri + Sat
        hourlyRate = 60.0,
    )

    private fun shift(id: String, start: String, end: String?, break_: Int = 0, special: Boolean = false) =
        Shift(
            id = id, userId = "u1",
            startTime = Instant.parse(start),
            endTime = end?.let { Instant.parse(it) },
            breakMinutes = break_,
            isSpecialDay = special,
        )

    // ── buildShiftBreakdown ───────────────────────────────────────────────────

    @Test
    fun `buildShiftBreakdown - regular 8h weekday shift`() {
        // 9:00→17:00 Mon, 0 break = 480 min net, no overtime, no weekend
        val s = shift("s1", "2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z")
        val bd = MonthlyReportBuilder.buildShiftBreakdown(s, settings)
        assertEquals(480, bd.totalMinutes)
        assertEquals(480, bd.regularMinutes)
        assertEquals(0,   bd.overtimeMinutes)
        assertEquals(0,   bd.weekendMinutes)
        assertEquals(1,   bd.segments.size)
    }

    @Test
    fun `buildShiftBreakdown - overtime shift`() {
        // 8:00→19:00 Mon = 660 min net; threshold 480 → 180 min OT
        val s = shift("s1", "2024-01-08T08:00:00Z", "2024-01-08T19:00:00Z")
        val bd = MonthlyReportBuilder.buildShiftBreakdown(s, settings)
        assertEquals(660, bd.totalMinutes)
        assertEquals(480, bd.regularMinutes)
        assertEquals(180, bd.overtimeMinutes)
        assertEquals(0,   bd.weekendMinutes)
    }

    // ── Test 4: weekend shift ─────────────────────────────────────────────────

    @Test
    fun `buildShiftBreakdown - Friday shift is all weekend minutes`() {
        // 2024-01-05 = Friday; weekend days = [5,6]
        val s = shift("s1", "2024-01-05T09:00:00Z", "2024-01-05T17:00:00Z")
        val bd = MonthlyReportBuilder.buildShiftBreakdown(s, settings)
        assertEquals(480, bd.totalMinutes)
        assertEquals(480, bd.weekendMinutes)
        assertEquals(0,   bd.regularMinutes)
        assertEquals(0,   bd.overtimeMinutes)
        assertTrue(bd.segments.all { it.isWeekend })
    }

    @Test
    fun `buildShiftBreakdown - overnight Friday to Saturday is all weekend`() {
        // Both Friday and Saturday are in weekendDays
        val s = shift("s1", "2024-01-05T22:00:00Z", "2024-01-06T02:00:00Z")
        val bd = MonthlyReportBuilder.buildShiftBreakdown(s, settings)
        assertEquals(bd.totalMinutes, bd.weekendMinutes)
        assertEquals(0, bd.regularMinutes)
        assertEquals(0, bd.overtimeMinutes)
        assertEquals(2, bd.segments.size)
        assertTrue(bd.segments.all { it.isWeekend })
    }

    @Test
    fun `buildShiftBreakdown - overnight Mon to Tue is all weekday`() {
        val s = shift("s1", "2024-01-08T22:00:00Z", "2024-01-09T02:00:00Z")
        val bd = MonthlyReportBuilder.buildShiftBreakdown(s, settings)
        assertEquals(0, bd.weekendMinutes)
        assertEquals(2, bd.segments.size)
        assertTrue(bd.segments.none { it.isWeekend })
    }

    // ── Test 6: monthly report aggregation ───────────────────────────────────

    @Test
    fun `buildMonthlyReport - two normal shifts in same week`() {
        // Mon 9:00→17:00 (480 min) + Tue 9:00→17:00 (480 min) → total 960, no OT, no weekend
        val shifts = listOf(
            shift("s1", "2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z"),
            shift("s2", "2024-01-09T09:00:00Z", "2024-01-09T17:00:00Z"),
        )
        val report = MonthlyReportBuilder.buildMonthlyReport(2024, 1, shifts, settings)
        assertEquals(2,   report.shiftCount)
        assertEquals(960, report.totalMinutes)
        assertEquals(960, report.regularMinutes)
        assertEquals(0,   report.overtimeMinutes)
        assertEquals(0,   report.weekendMinutes)
    }

    @Test
    fun `buildMonthlyReport - active shift is excluded`() {
        val shifts = listOf(
            shift("s1", "2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z"),  // completed
            shift("s2", "2024-01-09T09:00:00Z", null),                     // active
        )
        val report = MonthlyReportBuilder.buildMonthlyReport(2024, 1, shifts, settings)
        assertEquals(1, report.shiftCount)
        assertEquals(480, report.totalMinutes)
    }

    @Test
    fun `buildMonthlyReport - overtime via daily threshold`() {
        // Two 11h shifts in the same week: each has 180 min daily OT
        val shifts = listOf(
            shift("s1", "2024-01-08T08:00:00Z", "2024-01-08T19:00:00Z"),  // 660 min
            shift("s2", "2024-01-09T08:00:00Z", "2024-01-09T19:00:00Z"),  // 660 min
        )
        val report = MonthlyReportBuilder.buildMonthlyReport(2024, 1, shifts, settings)
        // totalWeekdayMins = 1320; dailyOT = 180+180 = 360; weeklyOT = max(0, 1320-2400) = 0
        // overtimeMinutes = max(360, 0) = 360
        assertEquals(1320, report.totalMinutes)
        assertEquals(360,  report.overtimeMinutes)
        assertEquals(960,  report.regularMinutes)
    }

    @Test
    fun `buildMonthlyReport - overtime via weekly threshold`() {
        // 5 shifts × 10h = 3000 min; weekly threshold = 2400 → weekly OT = 600
        // Each shift: 600 min net, daily threshold 480 → dailyOT = 120 per shift × 5 = 600
        // max(daily=600, weekly=600) = 600 (same in this case)
        val shifts = (1..5).map { i ->
            shift("s$i", "2024-01-0${7 + i}T08:00:00Z", "2024-01-0${7 + i}T18:00:00Z")
        }
        val report = MonthlyReportBuilder.buildMonthlyReport(2024, 1, shifts, settings)
        assertEquals(3000, report.totalMinutes)
        assertEquals(600,  report.overtimeMinutes)
    }

    @Test
    fun `buildMonthlyReport - weekend + weekday in same month`() {
        val shifts = listOf(
            shift("s1", "2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z"),  // Mon, 480 regular
            shift("s2", "2024-01-05T09:00:00Z", "2024-01-05T17:00:00Z"),  // Fri, 480 weekend
        )
        val report = MonthlyReportBuilder.buildMonthlyReport(2024, 1, shifts, settings)
        assertEquals(960, report.totalMinutes)
        assertEquals(480, report.weekendMinutes)
        assertEquals(480, report.regularMinutes)
        assertEquals(0,   report.overtimeMinutes)
    }

    // ── filterByMonth ─────────────────────────────────────────────────────────

    @Test
    fun `filterByMonth keeps only shifts starting in target month`() {
        val shifts = listOf(
            shift("jan", "2024-01-15T09:00:00Z", "2024-01-15T17:00:00Z"),
            shift("feb", "2024-02-01T09:00:00Z", "2024-02-01T17:00:00Z"),
        )
        val jan = MonthlyReportBuilder.filterByMonth(shifts, 2024, 1)
        assertEquals(1, jan.size)
        assertEquals("jan", jan[0].id)
    }

    // ── WeeklyBreakdownBuilder ────────────────────────────────────────────────

    @Test
    fun `groupByWeek - two shifts in same week share one entry`() {
        val shifts = listOf(
            shift("s1", "2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z"),  // Mon
            shift("s2", "2024-01-09T09:00:00Z", "2024-01-09T17:00:00Z"),  // Tue
        )
        val weeks = WeeklyBreakdownBuilder.groupByWeek(shifts)
        assertEquals(1, weeks.size)
        assertEquals("2024-01-08", weeks[0].weekStart)
        assertEquals(960, weeks[0].totalMinutes)
    }

    @Test
    fun `getMondayUtc - Sunday rolls back to previous Monday`() {
        // 2024-01-07 is a Sunday; Monday of that week is 2024-01-01
        val sunday = Instant.parse("2024-01-07T12:00:00Z")
        assertEquals("2024-01-01", WeeklyBreakdownBuilder.getMondayUtc(sunday))
    }

    @Test
    fun `getMondayUtc - Monday stays on same day`() {
        val monday = Instant.parse("2024-01-08T12:00:00Z")
        assertEquals("2024-01-08", WeeklyBreakdownBuilder.getMondayUtc(monday))
    }
}
