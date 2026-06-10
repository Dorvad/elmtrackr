package com.elmtrackr.app.domain

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

    private fun shift(start: String, end: String?, break_: Int = 0, special: Boolean = false) =
        Shift(
            id = "s1", userId = "u1",
            startTime = Instant.parse(start),
            endTime = end?.let { Instant.parse(it) },
            breakMinutes = break_,
            isSpecialDay = special,
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
        // 9:00→17:00 Mon = 480 min net, rate=60/h → 100% only = 480.0
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
        // 8:00→19:00 Mon = 660 min net; threshold=480
        //   Tier 1: 480 min × (60/60) × 1.0 = 480.0
        //   Tier 2: 120 min × (60/60) × 1.25 = 150.0
        //   Tier 3:  60 min × (60/60) × 1.5  =  90.0 → total 720.0
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
        // 2024-01-05 is a Friday (UTC). 480 min net.
        //   Tier 1: 120 min × 1.5 = 180
        //   Tier 2: 120 min × 1.75 = 210
        //   Tier 3: 240 min × 2.0 = 480 → total 870
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

    // ── Short shift (under 2 OT hours) — only one extra bracket ──────────────

    @Test
    fun `calculateShiftPay - 9h shift creates two weekday brackets`() {
        // 480 regular + 60 OT (< 120) → only tiers 1 and 2 used
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T18:00:00Z")
        val bd = PayrollCalculator.calculateShiftPay(s, defaultSettings)!!
        assertEquals(2, bd.brackets.size)
        assertEquals(480, bd.brackets[0].minutes)
        assertEquals(60,  bd.brackets[1].minutes)
    }

    // ── Special tier short (under 2 + 2h) ────────────────────────────────────

    @Test
    fun `calculateShiftPay - 3h Shabbat shift creates two special brackets`() {
        // 180 min: 120 at 150% + 60 at 175%
        val s = shift("2024-01-06T09:00:00Z", "2024-01-06T12:00:00Z") // Saturday
        val bd = PayrollCalculator.calculateShiftPay(s, defaultSettings)!!
        assertTrue(bd.isSpecial)
        assertEquals(2, bd.brackets.size)
        assertEquals(120, bd.brackets[0].minutes); assertEquals(1.5, bd.brackets[0].rate, 0.0)
        assertEquals(60,  bd.brackets[1].minutes); assertEquals(1.75, bd.brackets[1].rate, 0.001)
    }
}
