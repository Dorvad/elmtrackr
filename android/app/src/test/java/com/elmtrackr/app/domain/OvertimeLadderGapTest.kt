package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.OvertimeTier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the E5 decision: when a custom ladder's first tier starts later than the
 * standard, the minutes in between are paid at that first tier's rate, not at 1.0.
 *
 * The rule matters because the daily standard — not the first tier — is what
 * `ShiftClassifier` and `MonthlyReportBuilder` call the start of overtime. Paying 1.0
 * through the gap would put an hour in the report's overtime column and in the pay
 * engine's regular column, which is the divergence Wave B existed to remove.
 *
 * Nothing the app ships opens the gap: every `RegionPresets` ladder starts its first
 * tier exactly at the standard. These are hand-built ladders, which is the only way to
 * reach it.
 */
class OvertimeLadderGapTest {

    private val gappedDaily = CompensationRules(
        dailyStandardMinutes = 480,
        dailyOvertimeTiers = listOf(OvertimeTier(540, 1.5), OvertimeTier(600, 2.0)),
    )

    @Test
    fun `minutes up to the daily standard are straight time`() {
        assertEquals(1.0, PayrollCalculator.dailyMultiplier(480, gappedDaily), 1e-9)
    }

    @Test
    fun `minutes between the daily standard and the first tier take the first tier rate`() {
        // 481..540 are claimed by no tier. The standard triggered overtime, so they are
        // paid at the lowest rung rather than dropping back to 1.0.
        assertEquals(1.5, PayrollCalculator.dailyMultiplier(481, gappedDaily), 1e-9)
        assertEquals(1.5, PayrollCalculator.dailyMultiplier(540, gappedDaily), 1e-9)
    }

    @Test
    fun `tiers still apply normally once their own threshold is passed`() {
        assertEquals(1.5, PayrollCalculator.dailyMultiplier(541, gappedDaily), 1e-9)
        assertEquals(1.5, PayrollCalculator.dailyMultiplier(600, gappedDaily), 1e-9)
        assertEquals(2.0, PayrollCalculator.dailyMultiplier(601, gappedDaily), 1e-9)
    }

    @Test
    fun `a moved standard carries the whole ladder with it, so no new gap opens`() {
        // effectiveDailyOvertimeTiers shifts every tier by the same delta. A night
        // standard of 420 moves the first tier from 540 to 480, keeping the 60-minute
        // gap the author configured — it neither widens nor narrows.
        val nightStandard = 420
        val tiers = PayrollCalculator.effectiveDailyOvertimeTiers(gappedDaily, nightStandard)
        assertEquals(listOf(480, 540), tiers.map { it.afterMinutes })
        assertEquals(1.0, PayrollCalculator.dailyMultiplier(420, gappedDaily, nightStandard, tiers), 1e-9)
        assertEquals(1.5, PayrollCalculator.dailyMultiplier(421, gappedDaily, nightStandard, tiers), 1e-9)
        assertEquals(2.0, PayrollCalculator.dailyMultiplier(541, gappedDaily, nightStandard, tiers), 1e-9)
    }

    @Test
    fun `a ladder that starts at the standard has no gap at all`() {
        // The shape every shipped preset uses: first tier afterMinutes == standard.
        val flush = CompensationRules(
            dailyStandardMinutes = 480,
            dailyOvertimeTiers = listOf(OvertimeTier(480, 1.25)),
        )
        assertEquals(1.0, PayrollCalculator.dailyMultiplier(480, flush), 1e-9)
        assertEquals(1.25, PayrollCalculator.dailyMultiplier(481, flush), 1e-9)
    }

    @Test
    fun `no daily tiers means no overtime, however long the shift`() {
        val none = CompensationRules(dailyStandardMinutes = 480)
        assertEquals(1.0, PayrollCalculator.dailyMultiplier(900, none), 1e-9)
    }

    @Test
    fun `the weekly ladder follows the same gap rule`() {
        val gappedWeekly = CompensationRules(
            weeklyStandardMinutes = 2400,
            weeklyOvertimeTiers = listOf(OvertimeTier(2520, 1.5)),
        )
        assertEquals(1.0, PayrollCalculator.weeklyMultiplier(2400, gappedWeekly), 1e-9)
        assertEquals(1.5, PayrollCalculator.weeklyMultiplier(2401, gappedWeekly), 1e-9)
        assertEquals(1.5, PayrollCalculator.weeklyMultiplier(2521, gappedWeekly), 1e-9)
    }
}
