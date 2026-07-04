package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.OvertimeTier
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.PremiumType
import com.elmtrackr.app.domain.model.RegionCode
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
        // 8.5 h weekdays (510 min) Mon–Thu, then Sunday (skip Fri/Sat weekend)
        val week = listOf(
            shift("2024-01-08T09:00:00Z", "2024-01-08T17:30:00Z", id = "d1"),
            shift("2024-01-09T09:00:00Z", "2024-01-09T17:30:00Z", id = "d2"),
            shift("2024-01-10T09:00:00Z", "2024-01-10T17:30:00Z", id = "d3"),
            shift("2024-01-11T09:00:00Z", "2024-01-11T17:30:00Z", id = "d4"),
            shift("2024-01-14T09:00:00Z", "2024-01-14T17:30:00Z", id = "d5"),
        )
        val fifth = week.last()
        val isolated = PayrollCalculator.calculateShiftPay(fifth, settings, listOf(il))!!
        assertNear(510.0, isolated.totalGross)

        val inContext = PayrollCalculator.calculateShiftPayInContext(fifth, week, settings, listOf(il))!!
        // 480 min regular + 30 min at 125%
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
        // Mon 2024-01-08 … Sat 2024-01-13: six 6 h days, then Sunday: 10 h.
        val week = (8..13).map { day ->
            shift(
                "2024-01-%02dT08:00:00Z".format(day),
                "2024-01-%02dT14:00:00Z".format(day),
                id = "d$day",
            )
        } + shift("2024-01-14T08:00:00Z", "2024-01-14T18:00:00Z", id = "d14")
        val sunday = week.last()
        val bd = PayrollCalculator.calculateShiftPayInContext(sunday, week, settings, listOf(ca))!!
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
}
