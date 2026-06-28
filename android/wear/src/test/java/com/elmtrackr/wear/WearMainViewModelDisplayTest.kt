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
}
