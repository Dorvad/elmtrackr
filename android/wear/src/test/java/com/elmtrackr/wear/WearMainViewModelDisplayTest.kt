package com.elmtrackr.wear

import com.elmtrackr.wear.sync.WearDisplayMath
import com.elmtrackr.wear.sync.WearShiftSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class WearMainViewModelDisplayTest {

    @Test
    fun displayFor_idleSnapshotShowsClockedOut() {
        val display = WearDisplayMath.displayFor(
            WearShiftSnapshot(
                signedIn = true,
                isActive = false,
                startTimeLabel = "17:30",
                lastPunchLabel = "Last out • Today 17:30",
            ),
        )
        assertEquals("CLOCKED OUT", display.statusLabel)
        assertEquals("PUNCH IN", display.actionLabel)
    }

    @Test
    fun elapsedHms_formatsMinuteAndHourPrecision() {
        val start = 1_000_000L
        assertEquals("5:07", WearDisplayMath.elapsedHms(start, start + (5 * 60 + 7) * 1_000L))
        assertEquals(
            "2:05:09",
            WearDisplayMath.elapsedHms(start, start + (2 * 3600 + 5 * 60 + 9) * 1_000L),
        )
    }

    @Test
    fun elapsedClampsToZeroWhenClockSkewsBackwards() {
        val start = 1_000_000L
        assertEquals("0:00", WearDisplayMath.elapsedHms(start, start - 60_000L))
        assertEquals("0:00", WearDisplayMath.elapsedHm(start, start - 60_000L))
    }

    @Test
    fun progressPercent_clampsAndHandlesZeroGoal() {
        assertEquals(0, WearDisplayMath.progressPercent(240, 0))
        assertEquals(50, WearDisplayMath.progressPercent(240, 480))
        assertEquals(100, WearDisplayMath.progressPercent(900, 480))
    }
}
