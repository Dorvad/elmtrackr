package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.OvertimeTier
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.PremiumType
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.RoundingRules
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PayrollCalculatorTest {

    private val defaultSettings = UserSettings(
        id = "cfg1", userId = "u1",
        dailyOvertimeThresholdMinutes = 480,
        weeklyOvertimeThresholdMinutes = 2400,
        weekendDays = listOf(5, 6),  // Fri + Sat
        hourlyRate = 60.0,
    )

    private fun shift(
        start: String,
        end: String?,
        break_: Int = 0,
        special: Boolean = false,
        id: String = "s1",
    ) = Shift(
        id = id, userId = "u1",
        startTime = Instant.parse(start),
        endTime = end?.let { Instant.parse(it) },
        breakMinutes = break_,
        isSpecialDay = special,
    )

    private fun profile(
        region: RegionCode,
        rate: Double = 60.0,
        rules: CompensationRules = RegionPresets.forRegion(region).rules,
        stacking: StackingPolicy = RegionPresets.forRegion(region).stackingPolicy,
        id: String = "p1",
    ) = CompensationProfile(
        id = id,
        userId = "u1",
        name = "Test",
        regionCode = region,
        currencyCode = "USD",
        timezone = "UTC",
        baseHourlyRate = rate,
        rules = rules,
        stackingPolicy = stacking,
        isDefault = true,
    )

    private fun settingsWithProfile(profile: CompensationProfile) = defaultSettings.copy(
        defaultCompensationProfileId = profile.id,
        hourlyRate = profile.baseHourlyRate,
    )

    private fun assertNear(expected: Double, actual: Double, delta: Double = 0.01) =
        assertEquals(expected, actual, delta)

    // ── Returns null for active shift or no hourly rate ───────────────────────

    @Test
    fun `calculateShiftPay returns null for active shift`() {
        assertNull(PayrollCalculator.calculateShiftPay(shift("2024-01-08T09:00:00Z", null), defaultSettings))
    }

    @Test
    fun `calculateShiftPay returns null when no hourly rate`() {
        val noRate = defaultSettings.copy(hourlyRate = null)
        assertNull(PayrollCalculator.calculateShiftPay(shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z"), noRate))
    }

    // ── Test 7a: regular weekday shift (exactly at threshold — no overtime) ───

    @Test
    fun `calculateShiftPay - 8h weekday shift uses only 100 percent tier`() {
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z")
        val bd = PayrollCalculator.calculateShiftPay(s, defaultSettings)!!
        assertEquals(1, bd.brackets.size)
        assertEquals(1.0, bd.brackets[0].rate, 0.0)
        assertNear(480.0, bd.totalGross)
        assertFalse(bd.isSpecial)
    }

    // ── Test 5 + 7b: overtime shift ───────────────────────────────────────────

    @Test
    fun `calculateShiftPay - 11h weekday shift applies 100 + 125 + 150 tiers`() {
        val s = shift("2024-01-08T08:00:00Z", "2024-01-08T19:00:00Z")
        val bd = PayrollCalculator.calculateShiftPay(s, defaultSettings)!!
        assertEquals(3, bd.brackets.size)
        assertEquals(480, bd.brackets[0].minutes); assertNear(480.0, bd.brackets[0].amount)
        assertEquals(120, bd.brackets[1].minutes); assertNear(150.0, bd.brackets[1].amount)
        assertEquals( 60, bd.brackets[2].minutes); assertNear( 90.0, bd.brackets[2].amount)
        assertNear(720.0, bd.totalGross)
        assertFalse(bd.isSpecial)
    }

    // ── Test 4: weekend / special-day shift ───────────────────────────────────

    @Test
    fun `calculateShiftPay - Saturday shift uses weekly rest rate without auto overtime`() {
        val s = shift("2024-01-06T09:00:00Z", "2024-01-06T17:00:00Z")
        val bd = PayrollCalculator.calculateShiftPay(s, defaultSettings)!!
        assertTrue(bd.isSpecial)
        assertEquals(1, bd.brackets.size)
        assertEquals(1.5, bd.brackets[0].rate, 0.0)
        assertNear(720.0, bd.totalGross)
        assertTrue(bd.brackets[0].label.contains("Weekly rest regular", ignoreCase = true))
    }

    @Test
    fun `calculateShiftPay - is_special_day flag triggers Shabbat tiers on weekday`() {
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z", special = true)
        val bd = PayrollCalculator.calculateShiftPay(s, defaultSettings)!!
        assertTrue(bd.isSpecial)
        assertEquals(1.5, bd.brackets[0].rate, 0.0)
    }

    @Test
    fun `calculateShiftPay - premium profile applies configured multiplier`() {
        val premium = PremiumProfile(
            id = "prem-1",
            userId = "u1",
            name = "Holiday",
            multiplier = 1.5,
            premiumType = PremiumType.HIGHEST_ONLY,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z").copy(
            premiumProfileId = "prem-1",
            isSpecialDay = true,
        )
        val bd = PayrollCalculator.calculateShiftPay(s, defaultSettings, premiumProfiles = listOf(premium))!!
        assertTrue(bd.isSpecial)
        assertEquals(1.5, bd.brackets[0].rate, 0.0)
        assertTrue(bd.brackets[0].label.contains("Premium"))
    }

    @Test
    fun `calculateShiftPay - 9h shift creates two weekday brackets`() {
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T18:00:00Z")
        val bd = PayrollCalculator.calculateShiftPay(s, defaultSettings)!!
        assertEquals(2, bd.brackets.size)
        assertEquals(480, bd.brackets[0].minutes)
        assertEquals(60,  bd.brackets[1].minutes)
    }

    @Test
    fun `GB weekly-only profile does not apply erroneous daily OT fallback`() {
        val gbPreset = RegionPresets.forRegion(RegionCode.GB)
        val p = profile(RegionCode.GB, rate = 15.0, rules = gbPreset.rules)
        val settings = settingsWithProfile(p)
        val s = shift("2024-01-08T08:00:00Z", "2024-01-08T18:00:00Z")
        val bd = PayrollCalculator.calculateShiftPay(s, settings, listOf(p))!!
        assertEquals(1, bd.brackets.size)
        assertEquals(1.0, bd.brackets[0].rate, 0.0)
        assertNear(150.0, bd.totalGross)
    }

    @Test
    fun `calculateShiftPay - 3h Shabbat shift stays at weekly rest regular rate`() {
        val s = shift("2024-01-06T09:00:00Z", "2024-01-06T12:00:00Z")
        val bd = PayrollCalculator.calculateShiftPay(s, defaultSettings)!!
        assertTrue(bd.isSpecial)
        assertEquals(1, bd.brackets.size)
        assertEquals(180, bd.brackets[0].minutes)
        assertEquals(1.5, bd.brackets[0].rate, 0.0)
        assertNear(270.0, bd.totalGross)
    }

    // ── Weekly overtime tiers ─────────────────────────────────────────────────

    @Test
    fun `weekly OT applies when no single day exceeds daily threshold but week total does`() {
        val il = profile(RegionCode.IL, rate = 60.0)
        val settings = settingsWithProfile(il)
        // Israeli pay week runs Sunday–Saturday: 8.5 h days (510 min) Sun–Thu.
        val week = listOf(
            shift("2024-01-07T09:00:00Z", "2024-01-07T17:30:00Z", id = "d1"),
            shift("2024-01-08T09:00:00Z", "2024-01-08T17:30:00Z", id = "d2"),
            shift("2024-01-09T09:00:00Z", "2024-01-09T17:30:00Z", id = "d3"),
            shift("2024-01-10T09:00:00Z", "2024-01-10T17:30:00Z", id = "d4"),
            shift("2024-01-11T09:00:00Z", "2024-01-11T17:30:00Z", id = "d5"),
        )
        val fifth = week.last()
        val isolated = PayrollCalculator.calculateShiftPay(fifth, settings, listOf(il))!!
        assertNear(510.0, isolated.totalGross)

        val inContext = PayrollCalculator.calculateShiftPayInContext(fifth, week, settings, listOf(il))!!
        // 480 min regular + 30 min at 125% — Sunday's hours count toward the same week.
        assertNear(480.0 + 30 * 1.25, inContext.totalGross)
        assertTrue(inContext.overtimeGross > 0.0)
    }

    @Test
    fun `federal US preset applies no daily OT on a 10h single shift`() {
        val us = profile(RegionCode.US, rate = 20.0)
        val settings = settingsWithProfile(us)
        val longDay = shift("2024-01-08T08:00:00Z", "2024-01-08T18:00:00Z")
        val bd = PayrollCalculator.calculateShiftPay(longDay, settings, listOf(us))!!
        assertEquals(1, bd.brackets.size)
        assertEquals(1.0, bd.brackets[0].rate, 0.0)
        assertNear(200.0, bd.totalGross)
    }

    @Test
    fun `federal US preset applies weekly OT after 40h in the week`() {
        val us = profile(RegionCode.US, rate = 20.0)
        val settings = settingsWithProfile(us)
        val current = shift("2024-01-09T08:00:00Z", "2024-01-09T18:00:00Z", id = "current")
        val bd = PayrollCalculator.calculateShiftPay(current, settings, listOf(us), priorWeekMinutes = 2100)!!
        // 300 min regular + 300 min at 1.5×
        assertNear(100.0 + 150.0, bd.totalGross)
    }

    @Test
    fun `California preset applies double-time after 12h`() {
        val ca = profile(RegionCode.US_CA, rate = 60.0)
        val settings = settingsWithProfile(ca)
        val longDay = shift("2024-01-08T08:00:00Z", "2024-01-08T21:00:00Z")
        val bd = PayrollCalculator.calculateShiftPay(longDay, settings, listOf(ca))!!
        assertEquals(3, bd.brackets.size)
        assertEquals(480, bd.brackets[0].minutes); assertEquals(1.0, bd.brackets[0].rate, 0.0)
        assertEquals(240, bd.brackets[1].minutes); assertEquals(1.5, bd.brackets[1].rate, 0.0)
        assertEquals(60,  bd.brackets[2].minutes); assertEquals(2.0, bd.brackets[2].rate, 0.0)
        assertNear(960.0, bd.totalGross)
    }

    @Test
    fun `California weekly OT threshold ignores hours already paid as daily OT`() {
        val ca = profile(RegionCode.US_CA, rate = 30.0)
        val settings = settingsWithProfile(ca)
        // Mon–Thu 10 h/day: 32 h straight time + 8 h daily OT before Friday.
        val week = listOf(
            shift("2024-01-08T08:00:00Z", "2024-01-08T18:00:00Z", id = "d1"),
            shift("2024-01-09T08:00:00Z", "2024-01-09T18:00:00Z", id = "d2"),
            shift("2024-01-10T08:00:00Z", "2024-01-10T18:00:00Z", id = "d3"),
            shift("2024-01-11T08:00:00Z", "2024-01-11T18:00:00Z", id = "d4"),
            shift("2024-01-12T08:00:00Z", "2024-01-12T16:00:00Z", id = "d5"),
        )
        val friday = week.last()
        val bd = PayrollCalculator.calculateShiftPayInContext(friday, week, settings, listOf(ca))!!
        // Only 32 straight-time hours precede Friday, so its 8 h stay regular.
        assertEquals(1, bd.brackets.size)
        assertEquals(1.0, bd.brackets[0].rate, 0.0)
        assertNear(240.0, bd.totalGross)
    }

    @Test
    fun `California 7th consecutive workday pays premium from the first minute`() {
        val ca = profile(RegionCode.US_CA, rate = 30.0)
        val settings = settingsWithProfile(ca)
        // US workweek runs Sunday–Saturday: Sun 2024-01-07 … Fri 2024-01-12 are
        // six 6 h days, then Saturday is the 7th consecutive workday: 10 h.
        val week = (7..12).map { day ->
            shift(
                "2024-01-%02dT08:00:00Z".format(day),
                "2024-01-%02dT14:00:00Z".format(day),
                id = "d$day",
            )
        } + shift("2024-01-13T08:00:00Z", "2024-01-13T18:00:00Z", id = "d13")
        val saturday = week.last()
        val bd = PayrollCalculator.calculateShiftPayInContext(saturday, week, settings, listOf(ca))!!
        assertEquals(2, bd.brackets.size)
        assertEquals(480, bd.brackets[0].minutes)
        assertEquals(1.5, bd.brackets[0].rate, 0.0)
        assertEquals(120, bd.brackets[1].minutes)
        assertEquals(2.0, bd.brackets[1].rate, 0.0)
        assertNear(480.0, bd.totalGross)
    }

    @Test
    fun `GB preset pays flat rate past 48h because UK sets no statutory OT premium`() {
        val gb = profile(RegionCode.GB, rate = 15.0)
        val settings = settingsWithProfile(gb)
        val s = shift("2024-01-12T08:00:00Z", "2024-01-12T18:00:00Z")
        val bd = PayrollCalculator.calculateShiftPay(s, settings, listOf(gb), priorWeekMinutes = 2880)!!
        assertEquals(1, bd.brackets.size)
        assertEquals(1.0, bd.brackets[0].rate, 0.0)
        assertNear(150.0, bd.totalGross)
    }

    @Test
    fun `HIGHEST_ONLY uses max of daily and weekly multipliers`() {
        val rules = CompensationRules(
            dailyStandardMinutes = 480,
            weeklyStandardMinutes = 2400,
            overtimeEnabled = true,
            dailyOvertimeTiers = listOf(OvertimeTier(480, 1.5)),
            weeklyOvertimeTiers = listOf(OvertimeTier(2400, 1.25)),
        )
        assertEquals(1.5, PayrollCalculator.combineRates(1.5, 1.25, StackingPolicy.HIGHEST_ONLY), 0.0)
        assertEquals(1.25, PayrollCalculator.combineRates(1.0, 1.25, StackingPolicy.HIGHEST_ONLY), 0.0)
        val resolved = com.elmtrackr.app.domain.model.ResolvedCompensation(
            profileId = "p",
            profileName = "T",
            regionCode = RegionCode.CUSTOM,
            currencyCode = "USD",
            timezone = "UTC",
            baseHourlyRate = 10.0,
            rules = rules,
            stackingPolicy = StackingPolicy.HIGHEST_ONLY,
        )
        val segments = PayrollCalculator.buildCombinedRateSegments(resolved, 600, priorWeekMinutes = 2400)
        assertNotNull(segments.find { it.second == 1.5 })
    }

    @Test
    fun `MULTIPLICATIVE multiplies daily and weekly multipliers`() {
        assertEquals(1.875, PayrollCalculator.combineRates(1.5, 1.25, StackingPolicy.MULTIPLICATIVE), 0.0001)
        assertEquals(1.25, PayrollCalculator.combineRates(1.0, 1.25, StackingPolicy.MULTIPLICATIVE), 0.0)
    }

    @Test
    fun `BASE_PLUS_PREMIUM adds the premium on top of the other rate`() {
        assertEquals(1.75, PayrollCalculator.combineRates(1.5, 1.25, StackingPolicy.BASE_PLUS_PREMIUM), 0.0001)
    }

    @Test
    fun `regular-rate stacking modes combine like highest only`() {
        assertEquals(1.5, PayrollCalculator.combineRates(1.5, 1.25, StackingPolicy.PREMIUM_IN_REGULAR_RATE), 0.0)
        assertEquals(1.5, PayrollCalculator.combineRates(1.5, 1.25, StackingPolicy.EXCLUDED_FROM_REGULAR_RATE), 0.0)
    }

    @Test
    fun `ADDITIVE stacks daily and weekly premiums above 1x`() {
        assertEquals(1.5, PayrollCalculator.combineRates(1.25, 1.25, StackingPolicy.ADDITIVE), 0.0)
        val rules = CompensationRules(
            dailyStandardMinutes = 480,
            weeklyStandardMinutes = 2400,
            overtimeEnabled = true,
            dailyOvertimeTiers = listOf(OvertimeTier(480, 1.25)),
            weeklyOvertimeTiers = listOf(OvertimeTier(2400, 1.25)),
        )
        val p = profile(RegionCode.CUSTOM, rate = 60.0, rules = rules, stacking = StackingPolicy.ADDITIVE)
        val settings = settingsWithProfile(p)
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T19:00:00Z")
        val bd = PayrollCalculator.calculateShiftPay(s, settings, listOf(p), priorWeekMinutes = 2400)!!
        val otBracket = bd.brackets.last { it.rate > 1.0 }
        assertEquals(1.5, otBracket.rate, 0.001)
    }

    // ── Pay weeks across a month boundary ────────────────────────────────────

    /**
     * A pay week straddling the 1st.
     *
     * January 2024 ends on a Wednesday, so the pay week Sun 28 Jan – Sat 3 Feb spans
     * both months. Four 8-hour days in January put 1,920 minutes into that week
     * before February starts; a 10-hour shift on 1 February brings the week to 2,520
     * against the US federal weekly standard of 2,400.
     *
     * Every caller used to pass one month, so the February shift began the week with
     * `priorWeekMinutes = 0` and saw 600 minutes against a 2,400 threshold — no
     * overtime at all. The correct answer is 120 minutes of it at 1.5×.
     */
    private fun januaryTail() = listOf(
        shift("2024-01-28T09:00:00Z", "2024-01-28T17:00:00Z", id = "jan-28"),
        shift("2024-01-29T09:00:00Z", "2024-01-29T17:00:00Z", id = "jan-29"),
        shift("2024-01-30T09:00:00Z", "2024-01-30T17:00:00Z", id = "jan-30"),
        shift("2024-01-31T09:00:00Z", "2024-01-31T17:00:00Z", id = "jan-31"),
    )

    private fun februaryFirst() =
        shift("2024-02-01T09:00:00Z", "2024-02-01T19:00:00Z", id = "feb-01")

    @Test
    fun `a pay week straddling the first counts the previous month's minutes`() {
        val p = profile(RegionCode.US, rate = 60.0)
        val feb = februaryFirst()

        val withContext = PayrollCalculator.calculateShiftPayInContext(
            feb, januaryTail() + feb, settingsWithProfile(p), listOf(p),
        )!!

        val overtimeMinutes = withContext.brackets.filter { it.rate > 1.0 }.sumOf { it.minutes }
        assertEquals(120, overtimeMinutes)
        assertNear(660.0, withContext.totalGross)
    }

    /** The same shift judged on the month alone — the behaviour that was wrong. */
    @Test
    fun `the same shift with only its own month sees no weekly overtime`() {
        val p = profile(RegionCode.US, rate = 60.0)
        val feb = februaryFirst()

        val monthOnly = PayrollCalculator.calculateShiftPayInContext(
            feb, listOf(feb), settingsWithProfile(p), listOf(p),
        )!!

        assertEquals(0, monthOnly.brackets.filter { it.rate > 1.0 }.sumOf { it.minutes })
        assertNear(600.0, monthOnly.totalGross)
    }

    /**
     * The monthly summary reports February only, but has to reach the same answer for
     * the shift it does report. Nothing from January may appear in the total.
     */
    @Test
    fun `sumMonthlyPay reports one month but accumulates the whole pay week`() {
        val p = profile(RegionCode.US, rate = 60.0)
        val feb = februaryFirst()

        val summary = PayrollCalculator.sumMonthlyPay(
            shifts = listOf(feb),
            settings = settingsWithProfile(p),
            profiles = listOf(p),
            contextShifts = januaryTail() + feb,
        )

        assertNear(660.0, summary.totalGross)
        assertNear(180.0, summary.overtimeGross)
    }

    /** Context defaults to the reported list, so an unwired caller is unchanged. */
    @Test
    fun `sumMonthlyPay without context falls back to the reported month`() {
        val p = profile(RegionCode.US, rate = 60.0)
        val feb = februaryFirst()

        val summary = PayrollCalculator.sumMonthlyPay(
            shifts = listOf(feb),
            settings = settingsWithProfile(p),
            profiles = listOf(p),
        )

        assertNear(600.0, summary.totalGross)
    }

    /**
     * The report's hours must move with the money. Its weekly allowance is filled by
     * the prior week's minutes too, but its overtime figure stays bounded by the
     * minutes it actually reports — January's overtime belongs to January's report.
     */
    @Test
    fun `the report counts prior-week minutes toward the weekly threshold`() {
        val p = profile(RegionCode.US, rate = 60.0)
        val feb = februaryFirst()
        val settings = settingsWithProfile(p)

        val withContext = MonthlyReportBuilder.buildMonthlyReport(
            2024, 2, listOf(feb), settings, listOf(p),
            contextShifts = januaryTail() + feb,
        )
        val monthOnly = MonthlyReportBuilder.buildMonthlyReport(
            2024, 2, listOf(feb), settings, listOf(p),
        )

        assertEquals(600, withContext.totalMinutes)
        assertEquals(120, withContext.overtimeMinutes)
        assertEquals(0, monthOnly.overtimeMinutes)
    }

    /**
     * Found while wiring the pay-week context. The US federal preset is weekly-only —
     * `dailyOvertimeTiers` is empty — but the report counted every minute past its
     * 480-minute daily *standard* as overtime, so a single 10-hour day showed 120
     * overtime minutes that no pay figure ever paid.
     */
    @Test
    fun `the report counts no daily overtime for a weekly-only profile`() {
        val p = profile(RegionCode.US, rate = 60.0)
        val tenHourDay = shift("2024-02-05T09:00:00Z", "2024-02-05T19:00:00Z", id = "mon")
        val settings = settingsWithProfile(p)

        val report = MonthlyReportBuilder.buildMonthlyReport(
            2024, 2, listOf(tenHourDay), settings, listOf(p),
        )
        val pay = PayrollCalculator.calculateShiftPayInContext(
            tenHourDay, listOf(tenHourDay), settings, listOf(p),
        )!!

        assertEquals(0, report.overtimeMinutes)
        assertEquals(0, pay.brackets.filter { it.rate > 1.0 }.sumOf { it.minutes })
        assertEquals(600, report.regularMinutes)
    }

    /** A profile that does have a daily ladder still reports daily overtime. */
    @Test
    fun `a profile with daily tiers still reports daily overtime`() {
        val p = profile(RegionCode.US_CA, rate = 60.0)
        val tenHourDay = shift("2024-02-05T09:00:00Z", "2024-02-05T19:00:00Z", id = "mon")

        val report = MonthlyReportBuilder.buildMonthlyReport(
            2024, 2, listOf(tenHourDay), settingsWithProfile(p), listOf(p),
        )

        assertEquals(120, report.overtimeMinutes)
    }

    /** Report hours and paid overtime agree on the straddling week. */
    @Test
    fun `report and payroll agree across the month boundary`() {
        val p = profile(RegionCode.US, rate = 60.0)
        val feb = februaryFirst()
        val settings = settingsWithProfile(p)
        val context = januaryTail() + feb

        val report = MonthlyReportBuilder.buildMonthlyReport(
            2024, 2, listOf(feb), settings, listOf(p), contextShifts = context,
        )
        val pay = PayrollCalculator.calculateShiftPayInContext(feb, context, settings, listOf(p))!!

        assertEquals(
            pay.brackets.filter { it.rate > 1.0 }.sumOf { it.minutes },
            report.overtimeMinutes,
        )
    }

    // ── Midnight-crossing weekend classification (generic engine) ────────────

    /**
     * Saturday is the only weekend day, so a shift crossing into or out of it is
     * genuinely half weekend. The US federal preset disables weekend premium, so the
     * flag is turned on here with a rate to measure.
     */
    private val saturdayOnlyRules = RegionPresets.forRegion(RegionCode.US).rules.copy(
        weekendEnabled = true,
        weekendMultiplier = 1.5,
        weekendDays = listOf(6),
    )

    private fun saturdayOnlyProfile() =
        profile(RegionCode.US, rate = 60.0, rules = saturdayOnlyRules)

    /**
     * The defect: weekend status was decided once from the shift's start date, so
     * every minute of a Friday-night shift was paid at the weekday rate no matter how
     * far into Saturday it ran. 2024-01-05 is a Friday.
     *
     * 60 minutes on Friday, 420 on Saturday: 60 × 1.0 + 420 × 1.5 = 690, where the
     * whole shift used to pay 480.
     */
    @Test
    fun `a shift running from Friday into Saturday pays the weekend rate for Saturday`() {
        val p = saturdayOnlyProfile()
        val s = shift("2024-01-05T23:00:00Z", "2024-01-06T07:00:00Z")

        val pay = PayrollCalculator.calculateShiftPayInContext(s, listOf(s), settingsWithProfile(p), listOf(p))!!

        assertNear(690.0, pay.totalGross)
        assertNear(630.0, pay.weekendGross)
        assertNear(60.0, pay.regularGross)
    }

    /**
     * The mirror image, and the more expensive half of the bug: starting on Saturday
     * used to pay the *whole* shift at the weekend rate, including the seven hours
     * that fell on Sunday.
     */
    @Test
    fun `a shift running from Saturday into Sunday pays the weekend rate only for Saturday`() {
        val p = saturdayOnlyProfile()
        val s = shift("2024-01-06T23:00:00Z", "2024-01-07T07:00:00Z")

        val pay = PayrollCalculator.calculateShiftPayInContext(s, listOf(s), settingsWithProfile(p), listOf(p))!!

        assertNear(510.0, pay.totalGross)
        assertNear(90.0, pay.weekendGross)
        assertNear(420.0, pay.regularGross)
    }

    /**
     * The invariant that made this worth fixing rather than documenting: the report's
     * weekend *hours* and the payroll's weekend *money* have to describe the same
     * minutes. Asserted against the report builder's own output.
     */
    @Test
    fun `payroll weekend minutes match the report weekend minutes across midnight`() {
        val p = saturdayOnlyProfile()
        listOf(
            shift("2024-01-05T23:00:00Z", "2024-01-06T07:00:00Z", id = "fri-sat"),
            shift("2024-01-06T23:00:00Z", "2024-01-07T07:00:00Z", id = "sat-sun"),
        ).forEach { s ->
            val settings = settingsWithProfile(p)
            val breakdown = MonthlyReportBuilder.buildShiftBreakdown(s, settings, listOf(p))
            val pay = PayrollCalculator.calculateShiftPayInContext(s, listOf(s), settings, listOf(p))!!
            val paidWeekendMinutes = pay.brackets
                .filter { it.label.contains("Weekend", ignoreCase = true) }
                .sumOf { it.minutes }

            assertEquals(s.id, breakdown.weekendMinutes, paidWeekendMinutes)
        }
    }

    /** A shift wholly inside one day keeps its previous single-ladder treatment. */
    @Test
    fun `a shift entirely on Saturday is still all weekend`() {
        val p = saturdayOnlyProfile()
        val s = shift("2024-01-06T09:00:00Z", "2024-01-06T17:00:00Z")

        val pay = PayrollCalculator.calculateShiftPayInContext(s, listOf(s), settingsWithProfile(p), listOf(p))!!

        assertNear(720.0, pay.totalGross)
        assertNear(720.0, pay.weekendGross)
        assertTrue(pay.isSpecial)
    }

    @Test
    fun `a shift entirely on a weekday is unaffected`() {
        val p = saturdayOnlyProfile()
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z")

        val pay = PayrollCalculator.calculateShiftPayInContext(s, listOf(s), settingsWithProfile(p), listOf(p))!!

        assertNear(480.0, pay.totalGross)
        assertNear(0.0, pay.weekendGross)
    }

    /**
     * A holiday is declared on the shift, not derived from the calendar, so it stays
     * all-or-nothing rather than being split per day.
     */
    @Test
    fun `a special-day shift crossing midnight is not split`() {
        val p = saturdayOnlyProfile()
        val s = shift("2024-01-05T23:00:00Z", "2024-01-06T07:00:00Z", special = true)

        val pay = PayrollCalculator.calculateShiftPayInContext(s, listOf(s), settingsWithProfile(p), listOf(p))!!

        assertNear(0.0, pay.weekendGross)
        assertTrue(pay.holidayGross > 0.0)
    }

    /** forceRegularRate overrides the calendar on both sides of midnight. */
    @Test
    fun `a force-regular shift crossing midnight pays no weekend premium`() {
        val p = saturdayOnlyProfile()
        val s = shift("2024-01-05T23:00:00Z", "2024-01-06T07:00:00Z").copy(forceRegularRate = true)

        val pay = PayrollCalculator.calculateShiftPayInContext(s, listOf(s), settingsWithProfile(p), listOf(p))!!

        assertNear(480.0, pay.totalGross)
        assertNear(0.0, pay.weekendGross)
    }

    /** No minute may be lost or paid twice when the ladder is split in two. */
    @Test
    fun `a split shift pays for exactly its payable minutes`() {
        val p = saturdayOnlyProfile()
        val s = shift("2024-01-05T23:00:00Z", "2024-01-06T07:00:00Z", break_ = 30)

        val pay = PayrollCalculator.calculateShiftPayInContext(s, listOf(s), settingsWithProfile(p), listOf(p))!!

        assertEquals(450, pay.brackets.sumOf { it.minutes })
    }

    // ── Payable time rules (breaks, rounding, minimum shift) ─────────────────

    @Test
    fun `paid breaks keep break minutes payable`() {
        val rules = CompensationRules(paidBreaks = true)
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z", break_ = 30)
        assertEquals(480, PayrollCalculator.payableNetMinutes(s, rules))
    }

    @Test
    fun `auto break deduction applies only when no break was recorded`() {
        val rules = CompensationRules(autoDeductBreakMinutes = 30)
        val noBreak = shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z")
        val withBreak = shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z", break_ = 20)
        assertEquals(450, PayrollCalculator.payableNetMinutes(noBreak, rules))
        assertEquals(460, PayrollCalculator.payableNetMinutes(withBreak, rules))
    }

    @Test
    fun `rounding and minimum shift adjust payable minutes`() {
        val rounding = CompensationRules(
            rounding = RoundingRules(enabled = true, incrementMinutes = 15, direction = "nearest"),
        )
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T16:53:00Z") // 473 min
        assertEquals(480, PayrollCalculator.payableNetMinutes(s, rounding))

        val minimum = CompensationRules(minimumShiftMinutes = 180)
        val short = shift("2024-01-08T09:00:00Z", "2024-01-08T10:00:00Z")
        assertEquals(180, PayrollCalculator.payableNetMinutes(short, minimum))
    }

    /**
     * The composed case neither of the tests above covers, and the one that was
     * broken: rounding runs first and can reach zero, and the minimum guard used to
     * exclude zero. A seven-minute call-out paid nothing on rules that promise two
     * hours.
     */
    @Test
    fun `rounding to zero does not cancel the minimum shift guarantee`() {
        val rules = CompensationRules(
            rounding = RoundingRules(enabled = true, incrementMinutes = 15, direction = "nearest"),
            minimumShiftMinutes = 120,
        )
        // 7 minutes: (7 + 7) / 15 = 0 at a 15-minute nearest increment.
        val short = shift("2024-01-08T09:00:00Z", "2024-01-08T09:07:00Z")

        assertEquals(120, PayrollCalculator.payableNetMinutes(short, rules))
    }

    /** "Down" reaches zero for anything under one increment, so it needs the same floor. */
    @Test
    fun `rounding down below one increment still gets the minimum`() {
        val rules = CompensationRules(
            rounding = RoundingRules(enabled = true, incrementMinutes = 30, direction = "down"),
            minimumShiftMinutes = 60,
        )
        val short = shift("2024-01-08T09:00:00Z", "2024-01-08T09:20:00Z")

        assertEquals(60, PayrollCalculator.payableNetMinutes(short, rules))
    }

    /**
     * The other half of the guard. A minimum is a floor on time actually worked, so a
     * shift with none of it must stay at zero — otherwise a break longer than the
     * shift would mint payable minutes out of nothing.
     */
    @Test
    fun `a shift with no worked minutes is not lifted to the minimum`() {
        val rules = CompensationRules(minimumShiftMinutes = 120)
        val allBreak = shift("2024-01-08T09:00:00Z", "2024-01-08T09:30:00Z", break_ = 30)

        assertEquals(0, PayrollCalculator.payableNetMinutes(allBreak, rules))
    }

    /** Rounding away from zero is unaffected: the minimum only ever raises. */
    @Test
    fun `the minimum does not lower a shift that rounds above it`() {
        val rules = CompensationRules(
            rounding = RoundingRules(enabled = true, incrementMinutes = 15, direction = "up"),
            minimumShiftMinutes = 60,
        )
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T11:53:00Z") // 173 → 180

        assertEquals(180, PayrollCalculator.payableNetMinutes(s, rules))
    }

    @Test
    fun `percentage deductions reduce net gross`() {
        val rules = RegionPresets.forRegion(RegionCode.US).rules.copy(
            deductionsEnabled = true,
            deductionsMode = "percentage",
            deductionsPercentage = 20.0,
        )
        val p = profile(RegionCode.US, rate = 30.0, rules = rules)
        val settings = settingsWithProfile(p)
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z")
        val bd = PayrollCalculator.calculateShiftPay(s, settings, listOf(p))!!
        assertNear(240.0, bd.totalGross)
        assertNear(48.0, bd.deductionsGross)
        assertNear(192.0, bd.netGross)
    }

    @Test
    fun `fixed deductions are charged once per period, not per shift`() {
        // "Fixed" is a charge on the pay period (a monthly fee). Applying it per shift
        // multiplied it by the shift count: 4 shifts x 300 deducted 1200 from a month.
        val rules = RegionPresets.forRegion(RegionCode.US).rules.copy(
            deductionsEnabled = true,
            deductionsMode = "fixed",
            deductionsFixedAmount = 300.0,
        )
        val p = profile(RegionCode.US, rate = 30.0, rules = rules)
        val settings = settingsWithProfile(p)
        val shifts = (8..11).map { day ->
            shift("2024-01-%02dT09:00:00Z".format(day), "2024-01-%02dT17:00:00Z".format(day))
        }

        val summary = PayrollCalculator.sumMonthlyPay(shifts, settings, listOf(p))

        assertNear(960.0, summary.totalGross)
        assertNear(300.0, summary.deductionsGross)
        assertNear(660.0, summary.netGross)
        // No single shift carries the period fee.
        val perShift = PayrollCalculator.calculateShiftPay(shifts.first(), settings, listOf(p))!!
        assertNear(0.0, perShift.deductionsGross)
    }

    @Test
    fun `monthly summary reconciles when deductions exceed gross`() {
        // Per-shift net is floored at 0 while deductions kept accumulating, so the summary
        // could report totalGross - deductionsGross != netGross.
        val rules = RegionPresets.forRegion(RegionCode.US).rules.copy(
            deductionsEnabled = true,
            deductionsMode = "fixed",
            deductionsFixedAmount = 5_000.0,
        )
        val p = profile(RegionCode.US, rate = 30.0, rules = rules)
        val settings = settingsWithProfile(p)
        val shifts = listOf(shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z"))

        val summary = PayrollCalculator.sumMonthlyPay(shifts, settings, listOf(p))

        assertNear(240.0, summary.totalGross)
        assertNear(0.0, summary.netGross)
    }

    // ── Israeli weekly rest + overtime ────────────────────────────────────────

    private fun ilProfile(rate: Double = 58.0) = profile(
        RegionCode.IL,
        rate = rate,
        rules = RegionPresets.forRegion(RegionCode.IL).rules,
        stacking = RegionPresets.forRegion(RegionCode.IL).stackingPolicy,
        id = "il",
    ).copy(timezone = "Asia/Jerusalem")

    /** Fri 20:00 → Sat 02:30 Jerusalem (6.5 h, weekly rest + night). */
    private fun fridayNightShift6_5h() = shift(
        "2024-03-15T18:00:00Z",
        "2024-03-16T00:30:00Z",
    )

    /** Fri 20:00 → Sat 04:00 Jerusalem (8 h, weekly rest + night). */
    private fun fridayNightShift8h() = shift(
        "2024-03-15T18:00:00Z",
        "2024-03-16T02:00:00Z",
    )

    /** Wed 22:00 → Thu 06:00 Jerusalem (8 h, night, not weekly rest). */
    private fun weekdayNightShift8h() = shift(
        "2024-03-13T20:00:00Z",
        "2024-03-14T04:00:00Z",
    )

    /** Fri 20:00 → Sat 05:06 Jerusalem (9.1 h, weekly rest + night). */
    private fun fridayNightShift9_1h() = shift(
        "2024-03-15T18:00:00Z",
        "2024-03-16T03:06:00Z",
    )

    /** Fri 08:00 → Fri 17:06 Jerusalem (9.1 h, day before weekly rest). */
    private fun fridayDayBeforeRest9_1h() = shift(
        "2024-03-15T06:00:00Z",
        "2024-03-15T15:06:00Z",
    )

    /** Wed 09:00 → Wed 14:00 Jerusalem (5 h, regular weekday). */
    private fun weekdayShift5h() = shift(
        "2024-03-13T07:00:00Z",
        "2024-03-13T12:00:00Z",
    )

    private fun ilSettings(profile: CompensationProfile) = settingsWithProfile(profile)

    @Test
    fun `IL test 1 - standalone Friday night weekly rest at 150 percent`() {
        val il = ilProfile()
        val bd = PayrollCalculator.calculateShiftPay(
            fridayNightShift6_5h(),
            settingsWithProfile(il),
            listOf(il),
            priorWeekMinutes = 0,
        )!!
        assertEquals(1, bd.brackets.size)
        assertEquals(390, bd.brackets[0].minutes)
        assertEquals(1.5, bd.brackets[0].rate, 0.001)
        assertNear(565.50, bd.totalGross)
        assertTrue(bd.brackets[0].label.contains("Weekly rest regular", ignoreCase = true))
    }

    @Test
    fun `IL test 2 - Friday night after 40 weekly regular hours`() {
        val il = ilProfile()
        val bd = PayrollCalculator.calculateShiftPay(
            fridayNightShift6_5h(),
            settingsWithProfile(il),
            listOf(il),
            priorWeekMinutes = 40 * 60,
        )!!
        assertEquals(3, bd.brackets.size)
        assertNear(174.0, bd.brackets[0].amount)
        assertNear(203.0, bd.brackets[1].amount)
        assertNear(290.0, bd.brackets[2].amount)
        assertNear(667.00, bd.totalGross)
    }

    @Test
    fun `IL test 3 - Friday night after 42 weekly regular hours`() {
        val il = ilProfile()
        val bd = PayrollCalculator.calculateShiftPay(
            fridayNightShift6_5h(),
            settingsWithProfile(il),
            listOf(il),
            priorWeekMinutes = 42 * 60,
        )!!
        assertEquals(2, bd.brackets.size)
        assertEquals(120, bd.brackets[0].minutes)
        assertEquals(1.75, bd.brackets[0].rate, 0.001)
        assertEquals(270, bd.brackets[1].minutes)
        assertEquals(2.0, bd.brackets[1].rate, 0.001)
        assertNear(725.00, bd.totalGross)
    }

    @Test
    fun `IL test 4 - long Friday night crosses night daily OT threshold`() {
        val il = ilProfile(rate = 50.0)
        val bd = PayrollCalculator.calculateShiftPay(
            fridayNightShift9_1h(),
            ilSettings(il),
            listOf(il),
            priorWeekMinutes = 0,
        )!!
        assertEquals(3, bd.brackets.size)
        assertNear(525.0, bd.brackets[0].amount)
        assertNear(175.0, bd.brackets[1].amount)
        assertNear(10.0, bd.brackets[2].amount)
        assertNear(710.00, bd.totalGross)
        assertTrue(bd.brackets[1].label.contains("overtime", ignoreCase = true))
    }

    @Test
    fun `IL test 5 - Friday day before rest uses day-before threshold`() {
        val il = ilProfile(rate = 50.0)
        val bd = PayrollCalculator.calculateShiftPay(
            fridayDayBeforeRest9_1h(),
            ilSettings(il),
            listOf(il),
            priorWeekMinutes = 0,
        )!!
        // 08:00–17:06: 7 h regular (day-before-rest standard), 2 h daily OT at 125%,
        // and the final 6 minutes fall after the 17:00 weekly-rest start while past
        // the first two OT hours — rest premium plus additional-OT premium → 200%.
        assertTrue(bd.isSpecial)
        assertEquals(3, bd.brackets.size)
        assertNear(350.0, bd.brackets[0].amount)
        assertNear(125.0, bd.brackets[1].amount)
        assertNear(10.0, bd.brackets[2].amount)
        assertNear(485.00, bd.totalGross)
        assertTrue(bd.brackets[1].label.contains("Daily overtime", ignoreCase = true))
        assertTrue(bd.brackets[2].label.contains("Weekly rest", ignoreCase = true))
    }

    @Test
    fun `IL test 6 - weekday shift after 40 weekly regular hours`() {
        val il = ilProfile(rate = 50.0)
        val bd = PayrollCalculator.calculateShiftPay(
            weekdayShift5h(),
            ilSettings(il),
            listOf(il),
            priorWeekMinutes = 40 * 60,
        )!!
        assertEquals(3, bd.brackets.size)
        assertNear(100.0, bd.brackets[0].amount)
        assertNear(125.0, bd.brackets[1].amount)
        assertNear(75.0, bd.brackets[2].amount)
        assertNear(300.00, bd.totalGross)
        assertTrue(bd.brackets[2].label.contains("Weekly overtime", ignoreCase = true))
    }

    @Test
    fun `IL weekly accumulator excludes overtime hours from prior shifts`() {
        val il = ilProfile()
        val settings = ilSettings(il)
        val zone = java.time.ZoneId.of("Asia/Jerusalem")
        val resolved = com.elmtrackr.app.domain.compensation.CompensationResolver
            .resolveShiftCompensation(fridayNightShift6_5h(), settings, listOf(il))
        val priorLongOtShift = shift(
            "2024-03-13T07:00:00Z",
            "2024-03-13T20:00:00Z",
            id = "long-ot",
        )
        val week = listOf(priorLongOtShift)
        val regularMinutes = com.elmtrackr.app.domain.compensation.IsraeliCompensationEngine
            .getWeeklyRegularMinutesBeforeShift(
                fridayNightShift6_5h(),
                week,
                resolved,
                zone,
                settings,
                listOf(il),
                emptyList(),
            )
        assertTrue(
            "Prior 13h shift should not count all minutes as regular weekly hours",
            regularMinutes < (13 * 60),
        )
    }

    @Test
    fun `IL weekly rest - long Friday night crosses night daily OT at 7 hours`() {
        val il = ilProfile()
        val bd = PayrollCalculator.calculateShiftPay(
            fridayNightShift8h(),
            settingsWithProfile(il),
            listOf(il),
            priorWeekMinutes = 0,
        )!!
        assertEquals(2, bd.brackets.size)
        assertEquals(420, bd.brackets[0].minutes)
        assertEquals(1.5, bd.brackets[0].rate, 0.001)
        assertEquals(60, bd.brackets[1].minutes)
        assertEquals(1.75, bd.brackets[1].rate, 0.001)
        assertNear(710.50, bd.totalGross)
    }

    @Test
    fun `IL night - under two night hours keeps the regular daily standard`() {
        val il = ilProfile(rate = 60.0)
        // Wed 14:00 → 23:00 Jerusalem: 9 h with only one hour inside the night
        // window, so this is not night work — the 8.6 h standard applies.
        val s = shift("2024-03-13T12:00:00Z", "2024-03-13T21:00:00Z")
        val bd = PayrollCalculator.calculateShiftPay(s, ilSettings(il), listOf(il), priorWeekMinutes = 0)!!
        assertEquals(2, bd.brackets.size)
        assertEquals(516, bd.brackets[0].minutes)
        assertEquals(1.0, bd.brackets[0].rate, 0.0)
        assertEquals(24, bd.brackets[1].minutes)
        assertEquals(1.25, bd.brackets[1].rate, 0.001)
    }

    @Test
    fun `IL night - regular weekday night shift uses 100 and 125 tiers`() {
        val il = ilProfile()
        val bd = PayrollCalculator.calculateShiftPay(
            weekdayNightShift8h(),
            settingsWithProfile(il),
            listOf(il),
            priorWeekMinutes = 0,
        )!!
        assertFalse(bd.isSpecial)
        assertEquals(2, bd.brackets.size)
        assertEquals(420, bd.brackets[0].minutes)
        assertEquals(1.0, bd.brackets[0].rate, 0.0)
        assertEquals(60, bd.brackets[1].minutes)
        assertEquals(1.25, bd.brackets[1].rate, 0.001)
        assertNear(478.50, bd.totalGross)
    }

    // ── Custom night premium: proportional attribution (regression) ──────────

    private fun nightRules(applyTo: String, overtime: Boolean = false) = CompensationRules(
        overtimeEnabled = overtime,
        dailyOvertimeTiers = if (overtime) listOf(OvertimeTier(480, 1.25)) else emptyList(),
        weeklyOvertimeTiers = emptyList(),
        weekendEnabled = false,
        holidayEnabled = false,
        nightEnabled = true,
        nightStartTime = "22:00",
        nightEndTime = "06:00",
        nightMultiplier = 1.25,
        nightApplyTo = applyTo,
    )

    @Test
    fun `custom night window mode pays the uplift only on night minutes`() {
        // 18:00 → 02:00 UTC: 480 net minutes, 240 inside the 22:00–06:00 window.
        val p = profile(
            RegionCode.CUSTOM,
            rules = nightRules("minutes_inside_window"),
            stacking = StackingPolicy.HIGHEST_ONLY,
        )
        val s = shift("2024-01-08T18:00:00Z", "2024-01-09T02:00:00Z")

        val bd = PayrollCalculator.calculateShiftPay(s, settingsWithProfile(p), listOf(p))!!

        // 480 min at 100% + 240 night min at +25% of 60/h = 480 + 60.
        assertNear(540.0, bd.totalGross)
        assertNear(60.0, bd.nightGross)
        assertNear(480.0, bd.regularGross)
    }

    @Test
    fun `custom night entire-shift mode pays the uplift on every minute`() {
        val p = profile(
            RegionCode.CUSTOM,
            rules = nightRules("entire_shift"),
            stacking = StackingPolicy.HIGHEST_ONLY,
        )
        val s = shift("2024-01-08T18:00:00Z", "2024-01-09T02:00:00Z")

        val bd = PayrollCalculator.calculateShiftPay(s, settingsWithProfile(p), listOf(p))!!

        assertNear(600.0, bd.totalGross)
        assertNear(120.0, bd.nightGross)
        assertNear(480.0, bd.regularGross)
    }

    @Test
    fun `custom night with overtime keeps every category bucket non-negative`() {
        // 16:00 → 02:00 UTC: 600 net minutes, 240 at night, 120 in daily OT.
        val p = profile(
            RegionCode.CUSTOM,
            rules = nightRules("minutes_inside_window", overtime = true),
            stacking = StackingPolicy.HIGHEST_ONLY,
        )
        val s = shift("2024-01-08T16:00:00Z", "2024-01-09T02:00:00Z")

        val bd = PayrollCalculator.calculateShiftPay(s, settingsWithProfile(p), listOf(p))!!

        assertTrue(bd.regularGross >= 0.0)
        assertTrue(bd.overtimeGross >= 0.0)
        assertTrue(bd.nightGross >= 0.0)
        // Category buckets must partition the total exactly.
        assertNear(
            bd.totalGross,
            bd.regularGross + bd.overtimeGross + bd.weekendGross + bd.holidayGross + bd.nightGross,
        )
        // Night premium never exceeds night minutes × delta (240 × 0.25 × 1.0/min).
        assertTrue(bd.nightGross <= 60.0 + 0.01)
    }

    // ── Night premium on the Israeli engine (regression) ─────────────────────

    /**
     * The Israeli engine returned `nightGross = 0.0` unconditionally, so a user on
     * the IL region could configure a night premium in Compensation rules and see no
     * effect anywhere and no warning — the night rules were honoured only by the
     * generic engine. Same shift, same rules; only the region differs.
     */
    @Test
    fun `the israeli engine pays the night uplift on night minutes`() {
        val ilRules = RegionPresets.forRegion(RegionCode.IL).rules.copy(
            nightEnabled = true,
            nightStartTime = "22:00",
            nightEndTime = "06:00",
            nightMultiplier = 1.25,
            nightApplyTo = "minutes_inside_window",
        )
        val p = profile(RegionCode.IL, rules = ilRules)
        // 18:00 → 02:00 UTC: 480 net minutes, 240 inside the 22:00–06:00 window.
        val s = shift("2024-01-08T18:00:00Z", "2024-01-09T02:00:00Z")

        val bd = PayrollCalculator.calculateShiftPay(s, settingsWithProfile(p), listOf(p))!!

        assertTrue(
            "night uplift did not reach the IL engine (nightGross=${bd.nightGross})",
            bd.nightGross > 0.0,
        )

        // Derived, not copied from the generic engine: IL's daily standard is 420
        // minutes, so this shift is 420 regular + 60 daily overtime at 1.25, and
        // half the shift (240 of 480 minutes) is inside the night window.
        //
        // The regular segment carries the whole uplift — 420 min × 60/h × 0.5 ×
        // (1.25 − 1.00) = 52.50. The overtime segment gets none: IL stacks
        // highest-only, and its 1.25 overtime rate already equals the 1.25 night
        // rate, so there is nothing to add. That is why this is not a flat 25% on
        // all 240 night minutes.
        assertNear(52.50, bd.nightGross)
        assertNear(547.50, bd.totalGross)
        assertEquals(1.125, bd.brackets.first().rate, 0.001)
    }

    /** With the premium switched off the IL engine must be unchanged. */
    @Test
    fun `the israeli engine pays no night uplift when the rule is off`() {
        val p = profile(RegionCode.IL)
        val s = shift("2024-01-08T18:00:00Z", "2024-01-09T02:00:00Z")

        val bd = PayrollCalculator.calculateShiftPay(s, settingsWithProfile(p), listOf(p))!!

        assertNear(0.0, bd.nightGross)
    }

    /**
     * The night uplift is carried inside each bracket's blended rate, so the
     * category buckets plus the night total must still reconstruct the gross — the
     * property that keeps every bucket non-negative.
     */
    @Test
    fun `israeli buckets plus night reconstruct the gross`() {
        val ilRules = RegionPresets.forRegion(RegionCode.IL).rules.copy(
            nightEnabled = true,
            nightStartTime = "22:00",
            nightEndTime = "06:00",
            nightMultiplier = 1.5,
            nightApplyTo = "minutes_inside_window",
        )
        val p = profile(RegionCode.IL, rules = ilRules)
        val s = shift("2024-01-08T18:00:00Z", "2024-01-09T04:00:00Z")

        val bd = PayrollCalculator.calculateShiftPay(s, settingsWithProfile(p), listOf(p))!!

        assertNear(
            bd.totalGross,
            bd.regularGross + bd.overtimeGross + bd.weekendGross + bd.holidayGross + bd.nightGross,
        )
        listOf(bd.regularGross, bd.overtimeGross, bd.weekendGross, bd.holidayGross, bd.nightGross)
            .forEach { assertTrue("a bucket went negative: $it", it >= -0.001) }
    }

    /**
     * forceRegularRate means "pay this at the regular rate regardless". The IL
     * engine's own copy of the breakdown loop omitted it from the manual-holiday
     * check, so the flag was honoured on one path and ignored on the other.
     */
    @Test
    fun `force regular rate suppresses the manual holiday flag on the israeli engine`() {
        val p = profile(RegionCode.IL)
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z")
            .copy(isSpecialDay = true, forceRegularRate = true)

        val bd = PayrollCalculator.calculateShiftPay(s, settingsWithProfile(p), listOf(p))!!

        assertEquals(false, bd.isSpecial)
    }

    // ── Turning overtime off must not raise pay ───────────────────────────────

    /**
     * The Israeli preset with overtime switched off. Weekly rest still applies — Friday
     * from 17:00 and all of Saturday — but no overtime ladder sits on top.
     */
    private fun ilProfileWithoutOvertime() = profile(
        RegionCode.IL,
        rules = RegionPresets.forRegion(RegionCode.IL).rules.copy(overtimeEnabled = false),
        id = "p-il-no-ot",
    )

    private fun ilProfileWithOvertime() = profile(RegionCode.IL, id = "p-il-ot")

    /**
     * The defect: the no-overtime path classified weekly rest from the shift's start
     * *date* through `WeekendRules.isWeekendDate`, a whole-day test, so the whole of
     * Friday counted as rest. A Friday morning shift was paid 480 minutes at 1.5×
     * (720) with overtime off, against 495 with it on — switching a *premium* off
     * raised the estimate by 45%.
     *
     * Friday 5 Jan 2024, 08:00–16:00: entirely before the 17:00 rest boundary, so all
     * 480 minutes are ordinary weekday time.
     */
    @Test
    fun `a Friday morning shift is not weekly rest when overtime is off`() {
        val p = ilProfileWithoutOvertime()
        val s = shift("2024-01-05T08:00:00Z", "2024-01-05T16:00:00Z")

        val pay = PayrollCalculator.calculateShiftPayInContext(s, listOf(s), settingsWithProfile(p), listOf(p))!!

        assertNear(480.0, pay.totalGross)
        assertNear(480.0, pay.regularGross)
        assertNear(0.0, pay.weekendGross)
        assertFalse(pay.isSpecial)
    }

    /**
     * The invariant behind that number: overtime is a premium, so switching it off can
     * only ever lower an estimate. Asserted against the overtime path's own output so
     * neither side can drift without this failing.
     */
    @Test
    fun `switching overtime off never raises pay for a Friday morning shift`() {
        val withOt = ilProfileWithOvertime()
        val withoutOt = ilProfileWithoutOvertime()
        val s = shift("2024-01-05T08:00:00Z", "2024-01-05T16:00:00Z")

        val paidWithOt = PayrollCalculator
            .calculateShiftPayInContext(s, listOf(s), settingsWithProfile(withOt), listOf(withOt))!!
        val paidWithoutOt = PayrollCalculator
            .calculateShiftPayInContext(s, listOf(s), settingsWithProfile(withoutOt), listOf(withoutOt))!!

        assertTrue(
            "overtime off (${paidWithoutOt.totalGross}) must not exceed " +
                "overtime on (${paidWithOt.totalGross})",
            paidWithoutOt.totalGross <= paidWithOt.totalGross + 0.01,
        )
    }

    /**
     * A Friday shift that crosses the rest boundary is split at it, rather than being
     * decided by whichever side it started on. 14:00–22:00: three hours of weekday
     * time then five of weekly rest.
     */
    @Test
    fun `a Friday shift crossing the rest boundary splits at it when overtime is off`() {
        val p = ilProfileWithoutOvertime()
        val s = shift("2024-01-05T14:00:00Z", "2024-01-05T22:00:00Z")

        val pay = PayrollCalculator.calculateShiftPayInContext(s, listOf(s), settingsWithProfile(p), listOf(p))!!

        // 180 min at 1.0 + 300 min at 1.5, at 60/h.
        assertNear(630.0, pay.totalGross)
        assertNear(180.0, pay.regularGross)
        assertNear(450.0, pay.weekendGross)
        assertTrue(pay.isSpecial)
    }

    /** Saturday has no rest-start boundary, so it stays weekly rest end to end. */
    @Test
    fun `a Saturday shift is all weekly rest when overtime is off`() {
        val p = ilProfileWithoutOvertime()
        val s = shift("2024-01-06T09:00:00Z", "2024-01-06T17:00:00Z")

        val pay = PayrollCalculator.calculateShiftPayInContext(s, listOf(s), settingsWithProfile(p), listOf(p))!!

        assertNear(720.0, pay.totalGross)
        assertNear(720.0, pay.weekendGross)
        assertTrue(pay.isSpecial)
    }

    /** An ordinary weekday is untouched by any of this. */
    @Test
    fun `a Sunday shift is ordinary time when overtime is off`() {
        val p = ilProfileWithoutOvertime()
        val s = shift("2024-01-07T09:00:00Z", "2024-01-07T17:00:00Z")

        val pay = PayrollCalculator.calculateShiftPayInContext(s, listOf(s), settingsWithProfile(p), listOf(p))!!

        assertNear(480.0, pay.totalGross)
        assertNear(480.0, pay.regularGross)
        assertFalse(pay.isSpecial)
    }
}
