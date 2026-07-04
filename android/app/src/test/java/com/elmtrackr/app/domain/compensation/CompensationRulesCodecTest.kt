package com.elmtrackr.app.domain.compensation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompensationRulesCodecTest {

    @Test
    fun `decode ignores literal null json`() {
        val rules = CompensationRulesCodec.decode("null")
        assertEquals(480, rules.dailyStandardMinutes)
    }

    @Test
    fun `decodeSnapshot ignores literal null json`() {
        assertNull(CompensationRulesCodec.decodeSnapshot("null"))
    }

    @Test
    fun `decodeSnapshot ignores blank json`() {
        assertNull(CompensationRulesCodec.decodeSnapshot("   "))
    }

    @Test
    fun `seventh day rules survive an encode-decode round trip`() {
        val original = RegionPresets.forRegion(com.elmtrackr.app.domain.model.RegionCode.US_CA).rules
        val decoded = CompensationRulesCodec.decode(CompensationRulesCodec.encode(original))
        assertEquals(original.seventhDayEnabled, decoded.seventhDayEnabled)
        assertEquals(original.seventhDayTiers, decoded.seventhDayTiers)
    }

    @Test
    fun `decode defaults seventh day rules off for legacy json`() {
        val rules = CompensationRulesCodec.decode("""{"overtime":{"enabled":true}}""")
        assertEquals(false, rules.seventhDayEnabled)
        assertEquals(0, rules.seventhDayTiers.size)
    }
}
