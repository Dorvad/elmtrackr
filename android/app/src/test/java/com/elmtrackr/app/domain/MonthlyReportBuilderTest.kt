package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.PayBucket
import com.elmtrackr.app.domain.model.RegionCode
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

    /**
     * A Friday day shift is not weekend work, and never was in the money.
     *
     * This asserted 480 weekend minutes, because the report classified weekend
     * minutes with the whole-day `WeekendRules.isWeekendDate`. The pay engine has
     * always used `isWeeklyRestAt`, which honours the Israeli preset's
     * `weeklyRestStartTime` of 17:00 — and these settings resolve to that preset,
     * because `legacySettingsToResolved` copies it whenever no region is chosen.
     *
     * So the same shift was paid as seven hours of ordinary time plus an hour of
     * overtime and reported as eight hours of weekend work. It now reports what it
     * pays: the pre-rest daily standard is 420 minutes, so 420 regular and 60 of
     * daily overtime, and not one weekend minute — the shift ends exactly as rest
     * begins.
     *
     * `segments` still marks both as a calendar weekend day, which is what it is
     * for: an overnight shift's day split for the UI. Nothing derives money from
     * it any more.
     */
    @Test
    fun `buildShiftBreakdown - a Friday day shift is ordinary time, not weekend`() {
        // 2024-01-05 = Friday, 09:00-17:00 UTC, entirely before the 17:00 boundary.
        val s = shift("s1", "2024-01-05T09:00:00Z", "2024-01-05T17:00:00Z")
        val bd = MonthlyReportBuilder.buildShiftBreakdown(s, settings)
        assertEquals(480, bd.totalMinutes)
        assertEquals(0,   bd.weekendMinutes)
        assertEquals(420, bd.regularMinutes)
        assertEquals(60,  bd.overtimeMinutes)
        assertTrue("the calendar flag is unchanged", bd.segments.all { it.isWeekend })
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
    fun `buildMonthlyReport - a Friday day shift adds no weekend minutes`() {
        val shifts = listOf(
            shift("s1", "2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z"),  // Mon: 480 regular
            // Friday, but wholly before the 17:00 rest boundary — so 420 regular
            // plus 60 of overtime against the shorter pre-rest standard, which is
            // what the pay engine has always charged for it.
            shift("s2", "2024-01-05T09:00:00Z", "2024-01-05T17:00:00Z"),
        )
        val report = MonthlyReportBuilder.buildMonthlyReport(2024, 1, shifts, settings)
        assertEquals(960, report.totalMinutes)
        assertEquals(0,   report.weekendMinutes)
        assertEquals(900, report.regularMinutes)
        assertEquals(60,  report.overtimeMinutes)
    }

    @Test
    fun `buildMonthlyReport - weekly threshold spans real weeks in a month that does not start on the week boundary`() {
        // February 2024 starts on a Thursday. Seven 6h40m weekday-equivalent shifts on
        // Feb 1-7 total 2800 minutes with no daily overtime (each is under 480).
        //
        // The old day-of-month bucketer put all seven in "days 1-7" and reported
        // 2800 - 2400 = 400 weekly OT. Anchored to real Sunday-start weeks they split
        // across Jan 28-Feb 3 and Feb 4-10, neither of which reaches 2400, so the
        // correct answer is 0. Every pre-existing case used January 2024, which starts
        // on a Monday and hid this.
        val shifts = (1..7).map { day ->
            val date = LocalDate.of(2024, 2, day)
            // 08:00-14:40 = 400 minutes, below the 480 daily threshold.
            shift("s$day", "${date}T08:00:00Z", "${date}T14:40:00Z")
        }
        val report = MonthlyReportBuilder.buildMonthlyReport(2024, 2, shifts, settings)

        assertEquals(2800, report.totalMinutes)
        assertEquals(0, report.overtimeMinutes)
    }

    @Test
    fun `buildMonthlyReport - weekly threshold still applies within a single pay week`() {
        // Sun 4 Feb - Thu 8 Feb 2024, one Sunday-start week: 5 x 500 min = 2500.
        // Daily OT: 5 x (500-480) = 100. Weekly OT: 2500-2400 = 100. max = 100.
        val shifts = (4..8).map { day ->
            val date = LocalDate.of(2024, 2, day)
            shift("s$day", "${date}T08:00:00Z", "${date}T16:20:00Z")
        }
        val report = MonthlyReportBuilder.buildMonthlyReport(2024, 2, shifts, settings)

        assertEquals(2500, report.totalMinutes)
        assertEquals(100, report.overtimeMinutes)
    }

    @Test
    fun `defaultWeekStartDay is Sunday when no region is configured`() {
        assertEquals(0, MonthlyReportBuilder.defaultWeekStartDay(settings))
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

    @Test
    fun `groupByWeek - zone override buckets by the work timezone day`() {
        // 2024-01-07T22:30Z is already Jan 8, 00:30 in Jerusalem: the shift
        // belongs to the days 8-14 bucket in the work timezone, not days 1-7.
        val shifts = listOf(
            shift("s1", "2024-01-07T22:30:00Z", "2024-01-08T06:30:00Z"),
        )

        val utcWeeks = WeeklyBreakdownBuilder.groupByWeek(shifts)
        val workWeeks = WeeklyBreakdownBuilder.groupByWeek(
            shifts,
            zoneOverride = java.time.ZoneId.of("Asia/Jerusalem"),
        )

        assertEquals(480, utcWeeks[0].totalMinutes)
        assertEquals(0, utcWeeks[1].totalMinutes)
        assertEquals(0, workWeeks[0].totalMinutes)
        assertEquals(480, workWeeks[1].totalMinutes)
    }

    @Test
    fun `buildMonthlyReport - weekly overtime uses the work timezone weeks`() {
        val jerusalem = settings.copy(timezone = "Asia/Jerusalem")
        // Six 8h shifts that all fall in the Sun 14 - Sat 20 Jan pay week by Jerusalem
        // time (UTC+2), so 6 x 480 = 2880 crosses the 2400 weekly threshold by 480. None
        // produces daily overtime on its own (each is exactly the 480 threshold), so the
        // 480 can only come from the weekly threshold.
        //
        // The first shift is the point of the test: 13 Jan 22:30 UTC is 14 Jan 00:30 in
        // Jerusalem — Sunday, the first day of this pay week — but Saturday in UTC, the
        // last day of the previous one. Grouping in UTC would split it off, leaving two
        // weeks of 2400 and 480 and reporting no overtime at all.
        val shifts = listOf(
            shift("s1", "2024-01-13T22:30:00Z", "2024-01-14T06:30:00Z"),
            shift("s2", "2024-01-14T22:30:00Z", "2024-01-15T06:30:00Z"),
            shift("s3", "2024-01-15T22:30:00Z", "2024-01-16T06:30:00Z"),
            shift("s4", "2024-01-16T22:30:00Z", "2024-01-17T06:30:00Z"),
            shift("s5", "2024-01-17T22:30:00Z", "2024-01-18T06:30:00Z"),
            shift("s6", "2024-01-18T09:00:00Z", "2024-01-18T17:00:00Z"),
        )

        val report = MonthlyReportBuilder.buildMonthlyReport(2024, 1, shifts, jerusalem)

        assertEquals(2880, report.totalMinutes)
        assertEquals(480, report.overtimeMinutes)
    }

    @Test
    fun `buildMonthlyReport - a Mon-to-Sun stretch is two pay weeks, not one`() {
        val jerusalem = settings.copy(timezone = "Asia/Jerusalem")
        // The same six 8h shifts this test used to assert 480 OT for, back when weekly
        // overtime was applied over day-of-month buckets. By Jerusalem time they land on
        // Mon 8 - Thu 11 and Sun 14 Jan: 1920 minutes in the Sun 7 - Sat 13 pay week and
        // 960 in the next. Neither reaches 2400, so the correct answer is no overtime —
        // "days 8-14" is not a week under a Sunday week start.
        val shifts = listOf(
            shift("s1", "2024-01-07T22:30:00Z", "2024-01-08T06:30:00Z"),
            shift("s2", "2024-01-08T22:30:00Z", "2024-01-09T06:30:00Z"),
            shift("s3", "2024-01-09T22:30:00Z", "2024-01-10T06:30:00Z"),
            shift("s4", "2024-01-10T22:30:00Z", "2024-01-11T06:30:00Z"),
            shift("s5", "2024-01-13T22:30:00Z", "2024-01-14T06:30:00Z"),
            shift("s6", "2024-01-14T09:00:00Z", "2024-01-14T17:00:00Z"),
        )

        val report = MonthlyReportBuilder.buildMonthlyReport(2024, 1, shifts, jerusalem)

        assertEquals(2880, report.totalMinutes)
        // 300, not 0, and the weekly threshold is not why.
        //
        // 22:30Z is 00:30 in Jerusalem, so five of these six shifts run 00:30-08:30
        // local and put 330 minutes inside the 22:00-06:00 night window. Two hours
        // is enough to make the whole workday a night workday under the Israeli
        // preset, which shortens the daily standard to 420 — so each of those five
        // shifts earns 60 minutes of daily overtime, and always has been paid for
        // it. The report used the raw standard and reported none.
        //
        // The point the test was written to make still holds: no *weekly* overtime,
        // because neither pay week reaches 2400 minutes.
        assertEquals(300, report.overtimeMinutes)
    }

    // ── The invariant this all exists for ────────────────────────────────────

    /**
     * Reported hours and paid brackets must describe the same shift.
     *
     * This is the assertion the whole wave was for. Every defect it replaces was
     * the same shape — the report classifying minutes with one rule while the
     * money used another — so the guard is not "these numbers are 420 and 60" but
     * "whatever they are, the two sides agree".
     *
     * Run across every shipped preset and over the cases that used to diverge: a
     * Friday either side of the rest boundary, a night shift, an overnight shift,
     * and a plain weekday.
     */
    private fun assertHoursMatchMoney(
        label: String,
        shift: Shift,
        profile: CompensationProfile,
        context: List<Shift> = listOf(shift),
    ) {
        val settings = settingsFor(profile)
        val profiles = listOf(profile)
        val bd = MonthlyReportBuilder.buildShiftBreakdown(shift, settings, profiles, emptyList(), context)
        val pay = PayrollCalculator.calculateShiftPayInContext(shift, context, settings, profiles)
            ?: return  // no rate configured for this preset; nothing to compare

        val paidOvertime = pay.brackets
            .filter { it.category.bucket == PayBucket.OVERTIME }
            .sumOf { it.minutes }
        val paidSpecial = pay.brackets
            .filter { it.category.bucket == PayBucket.WEEKEND || it.category.bucket == PayBucket.HOLIDAY }
            .sumOf { it.minutes }
        val paidRegular = pay.brackets
            .filter { it.category.bucket == PayBucket.REGULAR }
            .sumOf { it.minutes }

        assertEquals("$label: overtime minutes", paidOvertime, bd.overtimeMinutes)
        assertEquals("$label: weekend/holiday minutes", paidSpecial, bd.weekendMinutes)
        assertEquals("$label: regular minutes", paidRegular, bd.regularMinutes)
        assertEquals(
            "$label: the parts must sum to the total",
            bd.totalMinutes,
            bd.regularMinutes + bd.overtimeMinutes + bd.weekendMinutes,
        )
    }

    @Test
    fun `reported hours match paid brackets across every preset`() {
        val cases = listOf(
            "weekday 8h" to shift("s1", "2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z"),
            "weekday 10h" to shift("s2", "2024-01-08T08:00:00Z", "2024-01-08T18:00:00Z"),
            "Friday before rest" to shift("s3", "2024-01-05T09:00:00Z", "2024-01-05T17:00:00Z"),
            "Friday across rest" to shift("s4", "2024-01-05T14:00:00Z", "2024-01-05T20:00:00Z"),
            "Saturday" to shift("s5", "2024-01-06T09:00:00Z", "2024-01-06T17:00:00Z"),
            "overnight into Saturday" to shift("s6", "2024-01-05T22:00:00Z", "2024-01-06T06:00:00Z"),
            "night shift" to shift("s7", "2024-01-03T22:00:00Z", "2024-01-04T06:00:00Z"),
        )

        listOf(RegionCode.IL, RegionCode.US, RegionCode.US_CA, RegionCode.GB, RegionCode.EU)
            .forEach { region ->
                val p = presetProfile(region, "p-$region")
                cases.forEach { (name, s) -> assertHoursMatchMoney("$region / $name", s, p) }
            }
    }

    @Test
    fun `reported hours match paid brackets for a week that straddles the month`() {
        val p = presetProfile(RegionCode.US, "p-us-straddle")
        // Sun 28 Jan starts the pay week; the reported shift is on 1 Feb, with four
        // January days of context. Weekly overtime is only attributable with them.
        val january = (28..31).map { d ->
            shift("jan$d", "2024-01-${d}T08:00:00Z", "2024-01-${d}T18:00:00Z")
        }
        val february = shift("feb1", "2024-02-01T08:00:00Z", "2024-02-01T18:00:00Z")

        assertHoursMatchMoney(
            "US / straddling week", february, p, context = january + february,
        )
    }

    // ── Thresholds agree with the pay path ───────────────────────────────────

    /**
     * An Israeli profile on preset rules, with the stale legacy field still holding
     * 480. This is the shape every IL user on defaults has: `UserSettings`
     * initialises `dailyOvertimeThresholdMinutes` to 480 while the IL preset's daily
     * standard is 516.
     */
    private val ilProfile = CompensationProfile(
        id = "p-il",
        userId = "u1",
        name = "Main job",
        regionCode = RegionCode.IL,
        currencyCode = "ILS",
        timezone = "Asia/Jerusalem",
        baseHourlyRate = 60.0,
        rules = RegionPresets.forRegion(RegionCode.IL).rules,
        stackingPolicy = RegionPresets.forRegion(RegionCode.IL).stackingPolicy,
        isDefault = true,
    )

    private val ilSettings = settings.copy(
        timezone = "Asia/Jerusalem",
        regionCode = RegionCode.IL,
        defaultCompensationProfileId = "p-il",
        // Deliberately left stale, which is the whole point.
        dailyOvertimeThresholdMinutes = 480,
        weeklyOvertimeThresholdMinutes = 2400,
    )

    /**
     * The defect this closes. The report counted overtime hours from 8:00 while pay
     * treated the same minutes as regular until 8:36, so the report card, the CSV and
     * the PDF disagreed with the money beside them for every IL user on defaults.
     *
     * 540 worked minutes: 60 over the stale 480, 24 over the profile's real 516.
     */
    @Test
    fun `report overtime uses the profile threshold, not the stale settings field`() {
        // Sunday 14 Jan, 09:00-18:00 Jerusalem — a weekday under IL weekend rules.
        val nineHours = shift("s1", "2024-01-14T07:00:00Z", "2024-01-14T16:00:00Z")

        val bd = MonthlyReportBuilder.buildShiftBreakdown(nineHours, ilSettings, listOf(ilProfile))

        assertEquals(540, bd.totalMinutes)
        assertEquals(24, bd.overtimeMinutes)
        assertEquals(516, bd.regularMinutes)
    }

    /**
     * The invariant the review asked for: report overtime minutes and payroll
     * overtime brackets must describe the same minutes. Asserted against the pay
     * path's own output rather than a hardcoded number, so the two cannot drift
     * apart again without this failing.
     */
    @Test
    fun `report overtime minutes match the payroll overtime brackets`() {
        val nineHours = shift("s1", "2024-01-14T07:00:00Z", "2024-01-14T16:00:00Z")
        val shifts = listOf(nineHours)

        val report = MonthlyReportBuilder.buildMonthlyReport(
            2024, 1, shifts, ilSettings, listOf(ilProfile),
        )
        val pay = PayrollCalculator.calculateShiftPayInContext(
            nineHours, shifts, ilSettings, listOf(ilProfile),
        )
        val paidOvertimeMinutes = pay!!.brackets.filter { it.rate > 1.0 }.sumOf { it.minutes }

        assertEquals(paidOvertimeMinutes, report.overtimeMinutes)
    }

    /** With no profiles the resolver mirrors the settings fields, so nothing moves. */
    @Test
    fun `an unprofiled user keeps the settings thresholds`() {
        val nineHours = shift("s1", "2024-01-08T09:00:00Z", "2024-01-08T18:00:00Z")

        val bd = MonthlyReportBuilder.buildShiftBreakdown(nineHours, settings)

        assertEquals(540, bd.totalMinutes)
        assertEquals(60, bd.overtimeMinutes)
    }

    // ── Daily overtime is only reported where a daily ladder pays it ─────────

    private fun presetProfile(region: RegionCode, id: String) = CompensationProfile(
        id = id,
        userId = "u1",
        name = "Main job",
        regionCode = region,
        currencyCode = RegionPresets.forRegion(region).currencyCode,
        // UTC so the calendar in these fixtures needs no zone arithmetic to read.
        timezone = "UTC",
        baseHourlyRate = 60.0,
        rules = RegionPresets.forRegion(region).rules,
        stackingPolicy = RegionPresets.forRegion(region).stackingPolicy,
        isDefault = true,
    )

    private fun settingsFor(profile: CompensationProfile) = settings.copy(
        timezone = "UTC",
        regionCode = profile.regionCode,
        defaultCompensationProfileId = profile.id,
    )

    /**
     * The US federal preset is weekly-only by design — `dailyOvertimeTiers` is empty —
     * so nothing pays a premium for the 9th and 10th hour of a single day. The report
     * counted them anyway, because it measured against the daily *standard*: a 10-hour
     * Monday showed 120 overtime minutes that no pay figure paid, on the shift row
     * badge, the per-shift rows in Reports, the CSV, the PDF and the per-task totals.
     */
    @Test
    fun `a weekly-only profile reports no daily overtime for a long single day`() {
        val p = presetProfile(RegionCode.US, "p-us")
        val tenHours = shift("s1", "2024-01-08T08:00:00Z", "2024-01-08T18:00:00Z")

        val bd = MonthlyReportBuilder.buildShiftBreakdown(tenHours, settingsFor(p), listOf(p))

        assertEquals(600, bd.totalMinutes)
        assertEquals(0, bd.overtimeMinutes)
        assertEquals(600, bd.regularMinutes)
    }

    /**
     * The invariant, asserted against the pay path's own output rather than a literal,
     * so the two cannot drift apart again without this failing. This is the same
     * assertion the IL profile already had; it did not hold for a weekly-only one.
     */
    @Test
    fun `weekly-only profile - report overtime minutes match the payroll brackets`() {
        val p = presetProfile(RegionCode.US, "p-us")
        val settingsUs = settingsFor(p)
        val tenHours = shift("s1", "2024-01-08T08:00:00Z", "2024-01-08T18:00:00Z")
        val shifts = listOf(tenHours)

        val bd = MonthlyReportBuilder.buildShiftBreakdown(tenHours, settingsUs, listOf(p))
        val pay = PayrollCalculator.calculateShiftPayInContext(tenHours, shifts, settingsUs, listOf(p))!!
        val paidOvertimeMinutes = pay.brackets.filter { it.rate > 1.0 }.sumOf { it.minutes }

        assertEquals(paidOvertimeMinutes, bd.overtimeMinutes)
    }

    /**
     * And the gate does not swallow the overtime that preset *does* pay: six 8-hour
     * days is 48 hours, so the week is 8 hours past the FLSA 40-hour threshold. That
     * belongs to the week, which is why it is reported at month level and not
     * attributed to any one day.
     */
    @Test
    fun `a weekly-only profile still reports weekly overtime`() {
        val p = presetProfile(RegionCode.US, "p-us")
        // Mon 8 Jan – Sat 13 Jan 2024, all inside the pay week starting Sunday the 7th.
        val shifts = (8..13).map { day ->
            val d = "%02d".format(day)
            shift("s$day", "2024-01-${d}T08:00:00Z", "2024-01-${d}T16:00:00Z")
        }

        val report = MonthlyReportBuilder.buildMonthlyReport(
            2024, 1, shifts, settingsFor(p), listOf(p),
        )

        assertEquals(2880, report.totalMinutes)
        assertEquals(480, report.overtimeMinutes)
        // The per-shift breakdowns are no longer all zero, and that is the point of
        // the change rather than a side effect. Weekly overtime is attributed to the
        // shifts that actually crossed the threshold — the last day and a half of
        // the week — instead of existing only as a month-level figure no row could
        // account for. The money has always been attributed this way, which is why
        // the rows and the money could disagree before.
        assertEquals(480, report.shifts.sumOf { it.overtimeMinutes })
        assertTrue("early shifts in the week are still all regular", report.shifts.any { it.overtimeMinutes == 0 })
    }

    /** The UK preset ships with overtime switched off entirely. */
    @Test
    fun `a profile with overtime disabled reports no overtime`() {
        val p = presetProfile(RegionCode.GB, "p-gb")
        val tenHours = shift("s1", "2024-01-08T08:00:00Z", "2024-01-08T18:00:00Z")
        val settingsGb = settingsFor(p)

        val bd = MonthlyReportBuilder.buildShiftBreakdown(tenHours, settingsGb, listOf(p))
        val report = MonthlyReportBuilder.buildMonthlyReport(
            2024, 1, listOf(tenHours), settingsGb, listOf(p),
        )

        assertEquals(0, bd.overtimeMinutes)
        assertEquals(0, report.overtimeMinutes)
    }

    /** California does pay a daily ladder, so its daily overtime is still reported. */
    @Test
    fun `a profile with a daily ladder still reports daily overtime`() {
        val p = presetProfile(RegionCode.US_CA, "p-ca")
        val tenHours = shift("s1", "2024-01-08T08:00:00Z", "2024-01-08T18:00:00Z")

        val bd = MonthlyReportBuilder.buildShiftBreakdown(tenHours, settingsFor(p), listOf(p))

        assertEquals(600, bd.totalMinutes)
        assertEquals(120, bd.overtimeMinutes)
        assertEquals(480, bd.regularMinutes)
    }

    /** The weekly threshold moved with the daily one; 2400 vs the IL preset's 2520. */
    @Test
    fun `weekly report overtime uses the profile weekly standard`() {
        // Five 8h40 weekdays from Sunday 14 Jan: 2600 minutes, no single day over 516.
        val shifts = (0..4).map { i ->
            val day = 14 + i
            shift("s$i", "2024-01-${day}T07:00:00Z", "2024-01-${day}T15:40:00Z")
        }

        val report = MonthlyReportBuilder.buildMonthlyReport(
            2024, 1, shifts, ilSettings, listOf(ilProfile),
        )

        assertEquals(2600, report.totalMinutes)
        // 2600 - 2520, not 2600 - 2400.
        assertEquals(80, report.overtimeMinutes)
    }
}
