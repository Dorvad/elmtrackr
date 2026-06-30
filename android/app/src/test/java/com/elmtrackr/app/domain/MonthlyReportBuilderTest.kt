package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

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
        val workDays = listOf(8, 9, 10, 11, 14) // Mon-Thu + Sun in one ISO week
        val shifts = workDays.mapIndexed { index, day ->
            val date = LocalDate.of(2024, 1, day)
            val i = index + 1
            shift("s$i", "${date}T08:00:00Z", "${date}T18:00:00Z")
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
        val jan = MonthlyReportBuilder.filterByMonth(shifts, 2024, 1, settings)
        assertEquals(1, jan.size)
        assertEquals("jan", jan[0].id)
    }

    // ── WeeklyBreakdownBuilder ────────────────────────────────────────────────

    @Test
    fun `groupByWeek - always returns 4 buckets`() {
        val shifts = listOf(
            shift("s1", "2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z"),  // day 8 → bucket 1
            shift("s2", "2024-01-09T09:00:00Z", "2024-01-09T17:00:00Z"),  // day 9 → bucket 1
        )
        val weeks = WeeklyBreakdownBuilder.groupByWeek(shifts)
        assertEquals(4, weeks.size)
    }

    @Test
    fun `groupByWeek - two shifts on days 8-14 accumulate in bucket 1`() {
        val shifts = listOf(
            shift("s1", "2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z"),  // day 8
            shift("s2", "2024-01-09T09:00:00Z", "2024-01-09T17:00:00Z"),  // day 9
        )
        val weeks = WeeklyBreakdownBuilder.groupByWeek(shifts)
        assertEquals(960, weeks[1].totalMinutes)  // bucket 1 = days 8–14
        assertEquals(0, weeks[0].totalMinutes)     // bucket 0 = days 1–7, empty
    }

    @Test
    fun `groupByWeek - shift on day 1 lands in bucket 0`() {
        val shifts = listOf(
            shift("s1", "2024-01-01T09:00:00Z", "2024-01-01T17:00:00Z"),  // day 1
        )
        val weeks = WeeklyBreakdownBuilder.groupByWeek(shifts)
        assertEquals(480, weeks[0].totalMinutes)
        assertEquals("1–7", weeks[0].dayRange)
    }

    @Test
    fun `groupByWeek - shift on day 25 lands in bucket 3`() {
        val shifts = listOf(
            shift("s1", "2024-01-25T09:00:00Z", "2024-01-25T17:00:00Z"),  // day 25
        )
        val weeks = WeeklyBreakdownBuilder.groupByWeek(shifts)
        assertEquals(480, weeks[3].totalMinutes)
        assertEquals("22+", weeks[3].dayRange)
    }
}
