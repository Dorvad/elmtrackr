package com.elmtrackr.app.ui.dashboard

import com.elmtrackr.app.domain.model.ClockStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class SupportedClockStyleTest {

    @Test
    fun `CLASSIC maps to CLASSIC`() {
        assertEquals(SupportedClockStyle.CLASSIC, ClockStyle.CLASSIC.toSupportedOrDefault())
    }

    @Test
    fun `MINIMAL maps to MINIMAL`() {
        assertEquals(SupportedClockStyle.MINIMAL, ClockStyle.MINIMAL.toSupportedOrDefault())
    }

    @Test
    fun `AURORA maps to AURORA`() {
        assertEquals(SupportedClockStyle.AURORA, ClockStyle.AURORA.toSupportedOrDefault())
    }

    @Test
    fun `unsupported styles fall back to CLASSIC`() {
        val unsupported = listOf(
            ClockStyle.FOCUS, ClockStyle.BOLD, ClockStyle.NIGHT,
            ClockStyle.RETRO, ClockStyle.PULSE, ClockStyle.DIAL,
            ClockStyle.STRAND, ClockStyle.PRISM,
        )
        unsupported.forEach { style ->
            assertEquals(
                "Expected CLASSIC fallback for $style",
                SupportedClockStyle.CLASSIC,
                style.toSupportedOrDefault(),
            )
        }
    }
}
