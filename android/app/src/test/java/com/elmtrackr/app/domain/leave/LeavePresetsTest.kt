package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.SickPayTier
import com.elmtrackr.app.domain.model.VacationPayBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeavePresetsTest {

    @Test
    fun `the Israeli preset carries the three-rung ladder and the statutory basis`() {
        val rules = LeavePresets.forRegion(RegionCode.IL)

        assertEquals(3, rules.sick.payTiers.size)
        assertEquals(VacationPayBasis.ISRAEL_STATUTORY_AVERAGE_90, rules.vacation.payBasis)
    }

    @Test
    fun `accrual is off in every preset`() {
        // The reference figures are recorded, but nothing accrues automatically:
        // for a part-time or irregular hourly worker a confidently wrong balance is
        // worse than no balance.
        RegionCode.entries.forEach { region ->
            val rules = LeavePresets.forRegion(region)
            assertFalse("sick accrual for $region", rules.sick.accrualEnabled)
            assertFalse("vacation accrual for $region", rules.vacation.accrualEnabled)
        }
    }

    @Test
    fun `the Israeli preset records the reference accrual figures without applying them`() {
        val sick = LeavePresets.forRegion(RegionCode.IL).sick

        assertEquals(1.5, sick.accrualDaysPerMonth!!, 0.0001)
        assertEquals(90.0, sick.maxAccruedDays!!, 0.0001)
        assertFalse(sick.accrualEnabled)
    }

    @Test
    fun `no preset invents a standard day length`() {
        // Converting hours to days needs a real workplace standard; guessing one
        // would silently move a balance.
        RegionCode.entries.forEach { region ->
            assertNull("standard day for $region", LeavePresets.forRegion(region).standardDayMinutes)
        }
    }

    @Test
    fun `regions with no encoded statutory ladder start at full pay from day one`() {
        RegionCode.entries.filter { it != RegionCode.IL }.forEach { region ->
            val tiers = LeavePresets.forRegion(region).sick.payTiers
            assertEquals("tiers for $region", 1, tiers.size)
            assertEquals(1.0, SickPayCalculator.resolveTier(tiers, 1)!!.multiplier, 0.0)
        }
    }

    @Test
    fun `a ladder paying less than the preset is flagged`() {
        val worse = listOf(SickPayTier(fromDay = 1, toDay = null, multiplier = 0.25))

        assertTrue(LeavePresets.paysLessThanPreset(RegionCode.IL, worse))
    }

    @Test
    fun `a ladder paying more than the preset is not flagged`() {
        assertFalse(LeavePresets.paysLessThanPreset(RegionCode.IL, LeavePresets.fullPayFromDayOneTiers))
    }

    @Test
    fun `the preset does not flag itself`() {
        assertFalse(LeavePresets.paysLessThanPreset(RegionCode.IL, LeavePresets.israeliSickTiers))
    }
}
