package com.elmtrackr.app.wear

import com.elmtrackr.app.widget.WidgetShiftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSnapshotMapperTest {

    @Test
    fun toWearSnapshot_mapsActiveShift() {
        val state = WidgetShiftState(
            isActive = true,
            shiftId = "shift-1",
            startTimeLabel = "09:00",
            dateLabel = "Mon 1 Jan",
            lastPunchLabel = "Since 09:00",
            pendingCount = 0,
            shiftStartEpochMillis = 1_700_000_000_000L,
            todayMinutes = 60,
            dailyGoalMinutes = 480,
        )
        val snapshot = state.toWearSnapshot(signedIn = true)
        assertTrue(snapshot.signedIn)
        assertTrue(snapshot.isActive)
        assertEquals("shift-1", snapshot.shiftId)
        assertEquals("09:00", snapshot.startTimeLabel)
        assertEquals(60, snapshot.todayMinutes)
    }
}
