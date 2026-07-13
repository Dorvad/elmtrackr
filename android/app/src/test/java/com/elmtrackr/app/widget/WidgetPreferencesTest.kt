package com.elmtrackr.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPreferencesTest {

    @Test
    fun `elapsed hms when active`() {
        val started = System.currentTimeMillis() - 90 * 60_000L - 14_000L
        val state = WidgetPreferences.DisplayState(
            isActive = true,
            shiftId = "shift-1",
            startTimeLabel = "08:57",
            dateLabel = "Wed 25 Jun",
            lastPunchLabel = "Since 08:57",
            pendingCount = 0,
            shiftStartEpochMillis = started,
            lastPunchEndEpochMillis = 0L,
            todayMinutes = 372,
            dailyGoalMinutes = 480,
        )
        assertTrue(state.elapsedHms.startsWith("1:"))
        assertEquals("PUNCH OUT", state.actionLabel)
        assertEquals(77, state.progressPercent)
    }

    @Test
    fun `progress labels when idle`() {
        val state = WidgetPreferences.DisplayState(
            isActive = false,
            shiftId = "",
            startTimeLabel = "08:57",
            dateLabel = "Wed 25 Jun",
            lastPunchLabel = "Last out • Today 08:57",
            pendingCount = 0,
            shiftStartEpochMillis = 0L,
            lastPunchEndEpochMillis = 0L,
            todayMinutes = 372,
            dailyGoalMinutes = 480,
        )
        assertEquals("PUNCH IN", state.actionLabel)
        assertTrue(state.progressSubLabel.contains("to goal"))
        assertEquals("6:12", state.todayHms)
    }
}
