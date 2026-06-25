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
}
