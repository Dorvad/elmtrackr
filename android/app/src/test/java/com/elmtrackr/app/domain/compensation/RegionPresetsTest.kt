package com.elmtrackr.app.domain.compensation

import com.elmtrackr.app.domain.model.RegionCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionPresetsTest {

    @Test
    fun `onboarding region list puts Israel second to last`() {
        val codes = RegionPresets.all.map { it.regionCode }
        assertEquals(RegionCode.US, codes.first())
        assertEquals(RegionCode.US_CA, codes[1])
        assertEquals(RegionCode.IL, codes[codes.size - 2])
        assertEquals(RegionCode.CUSTOM, codes.last())
    }

    @Test
    fun `Israel preset uses 5-day week daily standard and weekly tiers`() {
        val il = RegionPresets.forRegion(RegionCode.IL).rules
        assertEquals(516, il.dailyStandardMinutes)
        assertEquals(420, il.nightDailyStandardMinutes)
        assertTrue(il.nightEnabled)
        assertEquals(2, il.weekendDays.size)
        assertTrue(il.weeklyOvertimeTiers.isNotEmpty())
        assertEquals(null, il.holidayTiers)
    }

    @Test
    fun `US federal preset is weekly-only`() {
        val us = RegionPresets.forRegion(RegionCode.US).rules
        assertTrue(us.dailyOvertimeTiers.isEmpty())
        assertEquals(1, us.weeklyOvertimeTiers.size)
    }

    @Test
    fun `US California preset includes double-time tier`() {
        val ca = RegionPresets.forRegion(RegionCode.US_CA).rules
        assertEquals(2, ca.dailyOvertimeTiers.size)
        assertEquals(2.0, ca.dailyOvertimeTiers.last().multiplier, 0.0)
    }
}
