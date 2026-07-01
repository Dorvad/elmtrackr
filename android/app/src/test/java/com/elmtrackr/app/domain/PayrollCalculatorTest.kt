package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.OvertimeTier
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
    fun `calculateShiftPay - Friday shift uses Shabbat tiers (isSpecial true)`() {
        val s = shift("2024-01-05T09:00:00Z", "2024-01-05T17:00:00Z")
        val bd = PayrollCalculator.calculateShiftPay(s, defaultSettings)!!
        assertTrue(bd.isSpecial)
        assertEquals(3, bd.brackets.size)
        assertNear(180.0, bd.brackets[0].amount)
        assertNear(210.0, bd.brackets[1].amount)
        assertNear(480.0, bd.brackets[2].amount)
        assertNear(870.0, bd.totalGross)
    }

    @Test
    fun `calculateShiftPay - is_special_day flag triggers Shabbat tiers on weekday`() {
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z", special = true)
        val bd = PayrollCalculator.calculateShiftPay(s, defaultSettings)!!
        assertTrue(bd.isSpecial)
        assertEquals(1.5, bd.brackets[0].rate, 0.0)
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
    fun `calculateShiftPay - 3h Shabbat shift creates two special brackets`() {
        val s = shift("2024-01-06T09:00:00Z", "2024-01-06T12:00:00Z")
        val bd = PayrollCalculator.calculateShiftPay(s, defaultSettings)!!
        assertTrue(bd.isSpecial)
        assertEquals(2, bd.brackets.size)
        assertEquals(120, bd.brackets[0].minutes); assertEquals(1.5, bd.brackets[0].rate, 0.0)
        assertEquals(60,  bd.brackets[1].minutes); assertEquals(1.75, bd.brackets[1].rate, 0.001)
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
}
