package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.model.SickPayTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SickPayCalculatorTest {

    private val israeliDefault = LeavePresets.israeliSickTiers

    @Test
    fun `first day of illness pays nothing under the Israeli default`() {
        assertEquals(0.0, multiplierFor(1), 0.0)
    }

    @Test
    fun `second and third days pay half`() {
        assertEquals(0.5, multiplierFor(2), 0.0)
        assertEquals(0.5, multiplierFor(3), 0.0)
    }

    @Test
    fun `fourth day onwards pays in full`() {
        assertEquals(1.0, multiplierFor(4), 0.0)
        assertEquals(1.0, multiplierFor(7), 0.0)
        assertEquals(1.0, multiplierFor(40), 0.0)
    }

    @Test
    fun `a workplace paying from day one pays the first day in full`() {
        val tier = SickPayCalculator.resolveTier(LeavePresets.fullPayFromDayOneTiers, 1)
        assertEquals(1.0, tier?.multiplier)
    }

    @Test
    fun `pay is the expected day scaled by the tier`() {
        val result = SickPayCalculator.calculate(2, expectedPay = 400.0, tiers = israeliDefault)
        assertEquals(0.5, result?.multiplier)
        assertEquals(200.0, result?.estimatedGrossPay ?: 0.0, 0.0001)
        assertEquals(2, result?.ordinal)
    }

    @Test
    fun `a ladder with a hole reports no tier rather than paying zero`() {
        // Zero is a real multiplier under the Israeli default, so "the policy does
        // not cover this day" cannot also be expressed as zero.
        val gappy = listOf(SickPayTier(fromDay = 3, toDay = 5, multiplier = 1.0))

        assertNull(SickPayCalculator.resolveTier(gappy, 1))
        assertNull(SickPayCalculator.calculate(1, expectedPay = 400.0, tiers = gappy))
        assertEquals(1.0, SickPayCalculator.resolveTier(gappy, 3)?.multiplier)
        assertNull(SickPayCalculator.resolveTier(gappy, 6))
    }

    @Test
    fun `an empty ladder never resolves`() {
        assertNull(SickPayCalculator.resolveTier(emptyList(), 1))
    }

    @Test
    fun `day zero and negative days never resolve`() {
        assertNull(SickPayCalculator.resolveTier(israeliDefault, 0))
        assertNull(SickPayCalculator.resolveTier(israeliDefault, -3))
    }

    @Test
    fun `a bounded tier wins over an open-ended one covering the same day`() {
        // The user should not have to order the list correctly for "days 2 to 3 at
        // 50 percent" to override "from day 1 at 100 percent".
        val overlapping = listOf(
            SickPayTier(fromDay = 1, toDay = null, multiplier = 1.0),
            SickPayTier(fromDay = 2, toDay = 3, multiplier = 0.5),
        )

        assertEquals(1.0, SickPayCalculator.resolveTier(overlapping, 1)?.multiplier)
        assertEquals(0.5, SickPayCalculator.resolveTier(overlapping, 2)?.multiplier)
        assertEquals(0.5, SickPayCalculator.resolveTier(overlapping, 3)?.multiplier)
        assertEquals(1.0, SickPayCalculator.resolveTier(overlapping, 4)?.multiplier)
    }

    @Test
    fun `tiers resolve the same however the list is ordered`() {
        val shuffled = israeliDefault.reversed()
        (1..10).forEach { day ->
            assertEquals(
                "day $day",
                SickPayCalculator.resolveTier(israeliDefault, day)?.multiplier,
                SickPayCalculator.resolveTier(shuffled, day)?.multiplier,
            )
        }
    }

    private fun multiplierFor(day: Int): Double =
        SickPayCalculator.resolveTier(israeliDefault, day)?.multiplier ?: -1.0
}
