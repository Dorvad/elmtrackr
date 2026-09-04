package com.elmtrackr.app.domain.compensation

import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.PayCategory
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.RoundingRules
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.ShiftPayBreakdown
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.time.ZoneMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Characterisation suite for the Israeli minute classifier.
 *
 * The engine is ~590 lines of per-minute classification and, until this file, was
 * exercised only through [PayrollCalculator] and `Wave1PayRegressionTest`. That
 * indirection is what let three report-vs-pay divergences ship: every one of them
 * lived in a parameter those suites never varied.
 *
 * The point of this file is not to assert that the numbers are *right* — several
 * are pinned here precisely because they are questionable, and each such case says
 * so. It is to make the numbers *fixed*, so a later refactor that moves one has to
 * move it on purpose.
 *
 * All times are Asia/Jerusalem. Israel is UTC+2 in winter; DST 2024 began Friday
 * 29 March at 02:00 and ended Sunday 27 October at 02:00.
 */
class IsraeliCompensationEngineTest {

    private val zone: ZoneId = ZoneId.of("Asia/Jerusalem")

    /** ₪60/h is one shekel per minute, so every money assertion below is readable. */
    private fun ilProfile(
        rate: Double = 60.0,
        rules: CompensationRules = RegionPresets.forRegion(RegionCode.IL).rules,
        id: String = "il",
    ) = CompensationProfile(
        id = id,
        userId = "u1",
        name = "Test",
        regionCode = RegionCode.IL,
        currencyCode = "ILS",
        timezone = "Asia/Jerusalem",
        baseHourlyRate = rate,
        rules = rules,
        stackingPolicy = RegionPresets.forRegion(RegionCode.IL).stackingPolicy,
        isDefault = true,
    )

    private fun settingsFor(profile: CompensationProfile) = UserSettings(
        id = "cfg1",
        userId = "u1",
        timezone = "Asia/Jerusalem",
        weekendDays = listOf(5, 6),
        hourlyRate = profile.baseHourlyRate,
        defaultCompensationProfileId = profile.id,
    )

    private fun shift(
        start: String,
        end: String,
        break_: Int = 0,
        special: Boolean = false,
        id: String = "s1",
    ) = Shift(
        id = id,
        userId = "u1",
        startTime = Instant.parse(start),
        endTime = Instant.parse(end),
        breakMinutes = break_,
        isSpecialDay = special,
    )

    private fun classify(
        shift: Shift,
        profile: CompensationProfile,
        weeklyRegularBefore: Int = 0,
        weeklyOvertimeBefore: Int = 0,
    ): List<IsraeliCompensationEngine.ClassifiedPaySegment> {
        val resolved = CompensationResolver.resolveShiftCompensation(
            shift, settingsFor(profile), listOf(profile),
        )
        return IsraeliCompensationEngine.classifyShiftSegments(
            shift = shift,
            weeklyRegularMinutesBefore = weeklyRegularBefore,
            weeklyOvertimeMinutesBefore = weeklyOvertimeBefore,
            resolved = resolved,
            zone = zone,
        )
    }

    private fun pay(shift: Shift, profile: CompensationProfile, week: List<Shift> = listOf(shift)) =
        IsraeliCompensationEngine.calculateIsraeliShiftPay(
            shift, week, settingsFor(profile), listOf(profile),
        )

    /** Compact shape of a classification: `"420@1.0 | 120@1.25"`. */
    private fun List<IsraeliCompensationEngine.ClassifiedPaySegment>.shape(): String =
        joinToString(" | ") { "${it.minutes}@${it.multiplier}" }

    private fun assertBuckets(
        bd: ShiftPayBreakdown,
        regular: Double = 0.0,
        overtime: Double = 0.0,
        weekend: Double = 0.0,
        holiday: Double = 0.0,
        night: Double = 0.0,
    ) {
        assertEquals("regularGross", regular, bd.regularGross, 0.01)
        assertEquals("overtimeGross", overtime, bd.overtimeGross, 0.01)
        assertEquals("weekendGross", weekend, bd.weekendGross, 0.01)
        assertEquals("holidayGross", holiday, bd.holidayGross, 0.01)
        assertEquals("nightGross", night, bd.nightGross, 0.01)
        // The category split must reconstruct the total, or a bucket has gone
        // missing or been counted twice.
        assertEquals(
            "buckets must sum to totalGross",
            bd.totalGross,
            bd.regularGross + bd.overtimeGross + bd.weekendGross + bd.holidayGross + bd.nightGross,
            0.01,
        )
    }

    // ── The Friday rest boundary ──────────────────────────────────────────────
    // The IL preset sets weeklyRestStartTime = "17:00", so Friday is two different
    // days as far as pay is concerned. `MonthlyReportBuilder` does not know this —
    // it uses the whole-day `WeekendRules.isWeekendDate` — which is the largest
    // report-vs-pay divergence in the app.

    @Test
    fun `Friday splits at the 17-00 weekly-rest boundary`() {
        val p = ilProfile()
        // Fri 5 Jan 2024, 16:00–18:00 Jerusalem (UTC+2).
        val s = shift("2024-01-05T14:00:00Z", "2024-01-05T16:00:00Z")

        val segments = classify(s, p)

        assertEquals("60@1.0 | 60@1.5", segments.shape())
        assertFalse("before 17:00 is ordinary weekday time", segments[0].isWeeklyRest)
        assertTrue("from 17:00 it is weekly rest", segments[1].isWeeklyRest)
    }

    @Test
    fun `the rest boundary is exact to the minute`() {
        val p = ilProfile()
        // Fri 5 Jan 2024, 16:59–17:01 Jerusalem.
        val s = shift("2024-01-05T14:59:00Z", "2024-01-05T15:01:00Z")

        assertEquals("1@1.0 | 1@1.5", classify(s, p).shape())
    }

    @Test
    fun `a Friday morning shift carries no weekly rest at all`() {
        val p = ilProfile()
        // Fri 5 Jan 2024, 08:00–16:00 Jerusalem: 480 minutes, all before rest.
        val s = shift("2024-01-05T06:00:00Z", "2024-01-05T14:00:00Z")

        val segments = classify(s, p)

        // 420 at the pre-rest daily standard, then 60 of daily overtime at 125%.
        assertEquals("420@1.0 | 60@1.25", segments.shape())
        assertTrue(segments.none { it.isWeeklyRest })
    }

    @Test
    fun `Saturday is weekly rest for every minute`() {
        val p = ilProfile()
        // Sat 6 Jan 2024, 09:00–17:00 Jerusalem.
        val s = shift("2024-01-06T07:00:00Z", "2024-01-06T15:00:00Z")

        val segments = classify(s, p)

        assertEquals("480@1.5", segments.shape())
        assertTrue(segments.all { it.isWeeklyRest })
    }

    @Test
    fun `Friday crossing the boundary into overtime stacks rest over overtime`() {
        val p = ilProfile(rate = 50.0)
        // Fri 15 Mar 2024, 08:00–17:06 Jerusalem: the fixture PayrollCalculatorTest
        // pins at 350 + 125 + 10. Recorded here against the classifier itself.
        val s = shift("2024-03-15T06:00:00Z", "2024-03-15T15:06:00Z")

        val segments = classify(s, p)
        assertEquals("420@1.0 | 120@1.25 | 6@2.0", segments.shape())

        val bd = pay(s, p)!!
        assertEquals(485.00, bd.totalGross, 0.01)
        // The 6 minutes of rest-time overtime are booked to overtime, not weekend:
        // `isWeeklyRest && bucket != REGULAR` lands in overtimeGross.
        assertBuckets(bd, regular = 350.0, overtime = 135.0)
    }

    // ── Night work ────────────────────────────────────────────────────────────
    // Two hours inside 22:00–06:00 makes the whole workday a seven-hour night
    // workday under the IL preset (nightDailyStandardMinutes = 420).

    @Test
    fun `a night shift is measured against the shortened night standard`() {
        val p = ilProfile()
        // Wed 3 Jan 2024 22:00 → Thu 06:00 Jerusalem: 480 minutes, all inside the
        // night window.
        val s = shift("2024-01-03T20:00:00Z", "2024-01-04T04:00:00Z")

        assertEquals("420@1.0 | 60@1.25", classify(s, p).shape())

        val bd = pay(s, p)!!
        assertEquals(495.00, bd.totalGross, 0.01)
        assertBuckets(bd, regular = 420.0, overtime = 75.0)
    }

    /**
     * A recorded break no longer changes whether a shift is night work.
     *
     * `countNightMinutes` used to scale the wall-clock night minutes by the break
     * ratio *before* the >= 120-minute test. That test asks a question about the
     * clock — did at least two hours of this shift fall between 22:00 and 06:00 —
     * and the break's position is not recorded, so scaling it first was arbitrary
     * and had a sharp edge: this shift has 130 wall-clock night minutes, a
     * 60-minute break took the count to 115, and dropping below the threshold cost
     * the shortened 420-minute night standard and every overtime minute it
     * produced. Recording an hour's break removed an hour and a half of pay.
     *
     * The classification now reads the clock and the premium still reads the paid
     * share, which are two different questions. Both shifts below are night work;
     * the break costs its own 60 minutes and nothing more.
     */
    @Test
    fun `a recorded break does not change whether a shift is night work`() {
        val p = ilProfile()
        // Tue 2 Jan 2024 15:00 → Wed 00:10 Jerusalem: 550 gross, 130 wall-clock
        // minutes inside the night window.
        val noBreak = shift("2024-01-02T13:00:00Z", "2024-01-02T22:10:00Z")
        val withBreak = shift("2024-01-02T13:00:00Z", "2024-01-02T22:10:00Z", break_ = 60)

        assertEquals("420@1.0 | 120@1.25 | 10@1.5", classify(noBreak, p).shape())
        // Still measured against the night standard, so still 420 regular — the
        // break takes its 60 minutes off the overtime tail, where they were worked.
        assertEquals("420@1.0 | 70@1.25", classify(withBreak, p).shape())

        assertEquals(585.00, pay(noBreak, p)!!.totalGross, 0.01)
        assertEquals(507.50, pay(withBreak, p)!!.totalGross, 0.01)
    }

    /**
     * The daily standard is re-decided for every minute, so it can move mid-shift.
     *
     * On the IL preset this is masked: any midnight-crossing shift that starts
     * before 22:00 already has two hours of night, and the night branch is checked
     * first. It surfaces when night pay is switched off or the window is edited.
     */
    @Test
    fun `the daily standard is resolved per minute, not per shift`() {
        val rules = RegionPresets.forRegion(RegionCode.IL).rules

        // Thursday, ordinary weekday: the full 8h36 standard.
        assertEquals(
            516,
            IsraeliCompensationEngine.dailyStandardAt(
                jsDay = 4, minuteOfDay = 20 * 60, rules = rules,
                isWeeklyRest = false, isNightShift = false,
            ),
        )
        // Friday morning, the day before rest: the shortened 7h standard.
        assertEquals(
            420,
            IsraeliCompensationEngine.dailyStandardAt(
                jsDay = 5, minuteOfDay = 10 * 60, rules = rules,
                isWeeklyRest = false, isNightShift = false,
            ),
        )
        // A night shift takes the night standard whatever day it is on.
        assertEquals(
            420,
            IsraeliCompensationEngine.dailyStandardAt(
                jsDay = 4, minuteOfDay = 20 * 60, rules = rules,
                isWeeklyRest = false, isNightShift = true,
            ),
        )
    }

    // ── The pay week ──────────────────────────────────────────────────────────

    @Test
    fun `weekly regular minutes accumulate across a week that straddles the month`() {
        val p = ilProfile()
        // The IL week starts on Sunday. 28 Jan 2024 is a Sunday, so this pay week
        // runs 28 Jan – 3 Feb: four January days, then one in February.
        val days = listOf(
            "2024-01-28", "2024-01-29", "2024-01-30", "2024-01-31",
        ).mapIndexed { i, d ->
            // 09:00–17:36 Jerusalem = 516 minutes, exactly the daily standard.
            shift("${d}T07:00:00Z", "${d}T15:36:00Z", id = "jan$i")
        }
        val february = shift("2024-02-01T07:00:00Z", "2024-02-01T15:36:00Z", id = "feb")
        val resolved = CompensationResolver.resolveShiftCompensation(
            february, settingsFor(p), listOf(p),
        )

        val withContext = IsraeliCompensationEngine.getWeeklyRegularMinutesBeforeShift(
            february, days + february, resolved, zone, settingsFor(p), listOf(p), emptyList(),
        )
        val withoutContext = IsraeliCompensationEngine.getWeeklyRegularMinutesBeforeShift(
            february, listOf(february), resolved, zone, settingsFor(p), listOf(p), emptyList(),
        )

        assertEquals("four prior days of the same pay week", 4 * 516, withContext)
        assertEquals("the January shifts are what a month-only query loses", 0, withoutContext)
    }

    @Test
    fun `weekly overtime begins once the weekly standard is consumed`() {
        val p = ilProfile()
        // A 516-minute day arriving with the 42-hour week already spent.
        val s = shift("2024-02-01T07:00:00Z", "2024-02-01T15:36:00Z")

        val segments = classify(s, p, weeklyRegularBefore = 2520)

        // Every minute is weekly overtime: 125% for the first two hours, 150% after.
        assertEquals("120@1.25 | 396@1.5", segments.shape())
        assertTrue(segments.all { it.isWeeklyOvertime })
    }

    // ── Daylight saving ───────────────────────────────────────────────────────

    @Test
    fun `a shift spanning the spring-forward transition keeps its payable minutes`() {
        val p = ilProfile()
        // Fri 29 Mar 2024 00:00 → 06:00 Jerusalem. The clocks jump 02:00 → 03:00,
        // so six hours of wall clock are five hours of elapsed time.
        val s = shift("2024-03-28T22:00:00Z", "2024-03-29T03:00:00Z")

        assertFalse(
            "the transition must force the per-minute conversion path",
            ZoneMinutes.hasFixedOffset(
                zone, s.startTime.toEpochMilli(), s.endTime!!.toEpochMilli(),
            ),
        )

        val segments = classify(s, p)
        assertEquals("300 elapsed minutes, not 360", 300, segments.sumOf { it.minutes })
    }

    @Test
    fun `a shift spanning the autumn fall-back keeps its payable minutes`() {
        val p = ilProfile()
        // Sun 27 Oct 2024 00:00 → 06:00 Jerusalem. The clocks fall back 02:00 →
        // 01:00, so six hours of wall clock are seven hours of elapsed time.
        val s = shift("2024-10-26T21:00:00Z", "2024-10-27T04:00:00Z")

        assertFalse(
            ZoneMinutes.hasFixedOffset(
                zone, s.startTime.toEpochMilli(), s.endTime!!.toEpochMilli(),
            ),
        )
        assertEquals(420, classify(s, p).sumOf { it.minutes })
    }

    // ── Shift-level overrides ─────────────────────────────────────────────────

    @Test
    fun `a manually marked special day is rest for every minute`() {
        val p = ilProfile()
        // Wed 3 Jan 2024, 09:00–17:00 Jerusalem, marked as a holiday.
        val s = shift("2024-01-03T07:00:00Z", "2024-01-03T15:00:00Z", special = true)

        val segments = classify(s, p)

        assertEquals("480@1.5", segments.shape())
        assertTrue(segments.all { it.isWeeklyRest })

        // Holiday money on the Israeli engine lands in weekendGross, because a
        // manual holiday is expressed as weekly rest rather than as its own
        // category. Only the combined "special" figure is displayed, so this is
        // invisible today — but it is why holidayGross is always zero here.
        assertBuckets(pay(s, p)!!, weekend = 720.0)
    }

    /**
     * `forceRegularRate` overrides the calendar, not just the special-day flag.
     *
     * The override is applied in [CompensationResolver], which clears
     * `weekendEnabled` and `holidayEnabled` on the resolved rules before the
     * classifier ever runs — "paid at the regular rate no matter what the calendar
     * or flags say". So a Saturday marked as a holiday and forced regular pays
     * neither the rest premium nor the holiday one, and the minutes are not weekly
     * rest at all.
     *
     * Worth pinning because the flag reads like it only cancels the manual holiday.
     * It does not: it also cancels Shabbat.
     */
    @Test
    fun `forceRegularRate pays a rest day at the plain regular rate`() {
        val p = ilProfile()
        val s = shift("2024-01-06T07:00:00Z", "2024-01-06T15:00:00Z", special = true)
            .copy(forceRegularRate = true)

        val segments = classify(s, p)

        assertEquals("480@1.0", segments.shape())
        assertTrue("the calendar rest day is overridden too", segments.none { it.isWeeklyRest })
        assertBuckets(pay(s, p)!!, regular = 480.0)
    }

    @Test
    fun `overtime disabled pays rest minutes at the rest rate and no ladder`() {
        val p = ilProfile(
            rules = RegionPresets.forRegion(RegionCode.IL).rules.copy(overtimeEnabled = false),
            id = "il-no-ot",
        )
        // Fri 5 Jan 2024, 08:00–20:00 Jerusalem: 12 hours across the boundary.
        val s = shift("2024-01-05T06:00:00Z", "2024-01-05T18:00:00Z")

        // 09:00 of weekday time, then 03:00 of rest. No overtime ladder anywhere,
        // which is the whole point of the flag.
        assertEquals("540@1.0 | 180@1.5", classify(s, p).shape())
    }

    /**
     * A minimum-shift top-up is paid, but it does not invent a premium.
     *
     * Rounding and the minimum-shift floor can make payable minutes exceed the
     * wall clock. The classifier used to map them onto a *compressed* clock —
     * `start + i/ratio` with a ratio above 1 — so a ten-minute call-out topped up
     * to four hours had its 240 payable minutes squeezed into ten minutes of real
     * time, and half of them "fell" after 17:00 and collected the rest premium.
     *
     * Two changes fix it. The ratio is clamped at 1.0, so payable minutes advance
     * at ordinary speed; and each mapped instant is clamped to the shift's last
     * worked minute, so minutes beyond the shift take that minute's
     * classification rather than whatever the clock would have said had the
     * person kept working. A guarantee pays for time not worked; it should not
     * also price it as though it had been.
     *
     * Here the ten worked minutes straddle the boundary — 16:55–16:59 ordinary,
     * 17:00–17:04 rest — so the guarantee inherits the rest rate legitimately,
     * because the shift really did run into rest.
     */
    @Test
    fun `a minimum-shift top-up takes the last worked minute's classification`() {
        val p = ilProfile(
            rules = RegionPresets.forRegion(RegionCode.IL).rules.copy(
                rounding = RoundingRules(enabled = true, incrementMinutes = 15, direction = "up"),
                minimumShiftMinutes = 240,
            ),
            id = "il-minimum",
        )
        // Fri 5 Jan 2024, 16:55–17:05 Jerusalem: ten minutes straddling the boundary.
        val s = shift("2024-01-05T14:55:00Z", "2024-01-05T15:05:00Z")

        val segments = classify(s, p)

        assertEquals(240, segments.sumOf { it.minutes })
        assertEquals("5@1.0 | 235@1.5", segments.shape())
    }

    /**
     * The same guarantee on a shift that never reaches rest stays ordinary time.
     *
     * This is the case the clamp exists for. Under the old compressed mapping the
     * top-up minutes ran on past 17:00 and collected a Shabbat premium for a
     * call-out that finished at 16:10.
     */
    @Test
    fun `a top-up on a shift that ends before rest earns no rest premium`() {
        val p = ilProfile(
            rules = RegionPresets.forRegion(RegionCode.IL).rules.copy(
                minimumShiftMinutes = 240,
            ),
            id = "il-minimum-early",
        )
        // Fri 5 Jan 2024, 16:00–16:10 Jerusalem: wholly before the boundary.
        val s = shift("2024-01-05T14:00:00Z", "2024-01-05T14:10:00Z")

        val segments = classify(s, p)

        assertEquals(240, segments.sumOf { it.minutes })
        assertEquals("240@1.0", segments.shape())
        assertTrue("nothing here is weekly rest", segments.none { it.isWeeklyRest })
    }

    // ── Week state ────────────────────────────────────────────────────────────

    @Test
    fun `advanceWeekState counts regular minutes and weekly overtime separately`() {
        val regular = IsraeliCompensationEngine.ClassifiedPaySegment(
            minutes = 100, multiplier = 1.0, label = "100% — Regular",
            isWeeklyRest = false, isDailyOvertime = false, isWeeklyOvertime = false,
            bucket = IsraeliCompensationEngine.OvertimeBucket.REGULAR,
            category = PayCategory.REGULAR,
        )
        val weeklyOt = regular.copy(
            minutes = 30, multiplier = 1.25, isWeeklyOvertime = true,
            bucket = IsraeliCompensationEngine.OvertimeBucket.OT_FIRST_TWO,
            category = PayCategory.WEEKLY_OVERTIME,
        )
        val dailyOt = regular.copy(
            minutes = 40, multiplier = 1.25, isDailyOvertime = true,
            bucket = IsraeliCompensationEngine.OvertimeBucket.OT_FIRST_TWO,
            category = PayCategory.DAILY_OVERTIME,
        )

        val state = IsraeliCompensationEngine.advanceWeekState(
            IsraeliCompensationEngine.WeekPayState(),
            listOf(regular, weeklyOt, dailyOt),
        )

        assertEquals("only REGULAR minutes fill the weekly allowance", 100, state.weeklyRegularMinutes)
        assertEquals("daily overtime is not weekly overtime", 30, state.weeklyOvertimeMinutes)
    }

    // ── The forward pass ─────────────────────────────────────────────────────

    /**
     * `classifyWeek` must return exactly what the per-shift path returns.
     *
     * That equivalence is the entire safety argument for it. `weekStateBeforeShift`
     * re-derives the week from scratch for every shift, so a week of n shifts
     * classifies the first one n times — each walk being minute by minute. The
     * forward pass does the same work once by carrying the accumulated state,
     * which is the only thing a shift needs from the ones before it.
     *
     * Asserted over a week that actually exercises the accumulation: five ordinary
     * days that cross the 2,520-minute weekly standard, so the later shifts are
     * classified differently from the earlier ones and a pass that lost the state
     * would visibly disagree.
     */
    @Test
    fun `the forward pass agrees with the per-shift path`() {
        val p = ilProfile()
        val settings = settingsFor(p)
        val week = listOf(
            "2024-01-07", "2024-01-08", "2024-01-09", "2024-01-10", "2024-01-11",
        ).mapIndexed { i, d ->
            // 09:00–18:00 Jerusalem: 540 minutes each, so the week passes 2,520
            // during the fifth shift.
            shift("${d}T07:00:00Z", "${d}T16:00:00Z", id = "s$i")
        }

        val batch = IsraeliCompensationEngine.classifyWeek(week, zone, settings, listOf(p), emptyList())

        assertEquals("every shift is classified", week.size, batch.size)
        week.forEach { s ->
            val resolved = CompensationResolver.resolveShiftCompensation(s, settings, listOf(p))
            val state = IsraeliCompensationEngine.weekStateBeforeShift(
                s, week, resolved, zone, settings, listOf(p), emptyList(),
            )
            val oneAtATime = IsraeliCompensationEngine.classifyShiftSegments(
                shift = s,
                weeklyRegularMinutesBefore = state.weeklyRegularMinutes,
                weeklyOvertimeMinutesBefore = state.weeklyOvertimeMinutes,
                resolved = resolved,
                zone = zone,
            )
            assertEquals("shift ${s.id}", oneAtATime, batch[s.id])
        }
    }

    @Test
    fun `the forward pass carries weekly overtime into the later shifts`() {
        // Guards the equivalence test above from passing vacuously: if the state
        // were not carried, every shift would classify as pure regular time and the
        // two paths would agree on the wrong answer.
        val p = ilProfile()
        val week = listOf(
            "2024-01-07", "2024-01-08", "2024-01-09", "2024-01-10", "2024-01-11",
        ).mapIndexed { i, d -> shift("${d}T07:00:00Z", "${d}T16:00:00Z", id = "s$i") }

        val batch = IsraeliCompensationEngine.classifyWeek(
            week, zone, settingsFor(p), listOf(p), emptyList(),
        )

        assertTrue(
            "the last shift of the week must carry weekly overtime",
            batch.getValue("s4").any { it.isWeeklyOvertime },
        )
        assertTrue(
            "the first must not",
            batch.getValue("s0").none { it.isWeeklyOvertime },
        )
    }

    @Test
    fun `an active shift classifies to nothing`() {
        val p = ilProfile()
        val active = Shift(
            id = "s1", userId = "u1",
            startTime = Instant.parse("2024-01-03T07:00:00Z"),
            endTime = null,
        )
        val resolved = CompensationResolver.resolveShiftCompensation(
            active, settingsFor(p), listOf(p),
        )

        assertTrue(
            IsraeliCompensationEngine.classifyShiftSegments(
                active, 0, 0, resolved, zone,
            ).isEmpty(),
        )
    }
}
