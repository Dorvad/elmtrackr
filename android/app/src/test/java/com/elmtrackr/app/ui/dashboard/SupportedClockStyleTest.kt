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
    fun `every persisted style maps to its native renderer`() {
        ClockStyle.entries.forEach { style ->
            assertEquals(
                "Expected a matching native renderer for $style",
                SupportedClockStyle.valueOf(style.name),
                style.toSupportedOrDefault(),
            )
        }
    }

    @Test
    fun `the v1_2 faces parse from their wire values`() {
        assertEquals(ClockStyle.METRO, ClockStyle.fromPersisted("metro"))
        assertEquals(ClockStyle.VINYL, ClockStyle.fromPersisted("vinyl"))
        assertEquals(ClockStyle.LUNA, ClockStyle.fromPersisted("luna"))
    }
}
