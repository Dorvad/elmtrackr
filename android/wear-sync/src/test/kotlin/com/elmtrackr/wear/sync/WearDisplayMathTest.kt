package com.elmtrackr.wear.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearDisplayMathTest {

    @Test
    fun elapsedHms_formatsHoursMinutesSeconds() {
        val start = 1_700_000_000_000L
        val now = start + (3_661_000L)
        assertEquals("1:01:01", WearDisplayMath.elapsedHms(start, now))
    }

    @Test
    fun progressPercent_clamps() {
        assertEquals(50, WearDisplayMath.progressPercent(240, 480))
        assertEquals(100, WearDisplayMath.progressPercent(600, 480))
    }

    @Test
    fun displayFor_activeShiftUsesElapsed() {
        val start = System.currentTimeMillis() - 90_000L
        val snapshot = WearShiftSnapshot(
            signedIn = true,
            isActive = true,
            shiftStartEpochMillis = start,
            startTimeLabel = "09:00",
            todayMinutes = 30,
        )
        val display = WearDisplayMath.displayFor(snapshot)
        assertEquals("CLOCKED IN", display.statusLabel)
        assertEquals("PUNCH OUT", display.actionLabel)
        assertTrue(display.elapsedHms.isNotEmpty())
    }
}
