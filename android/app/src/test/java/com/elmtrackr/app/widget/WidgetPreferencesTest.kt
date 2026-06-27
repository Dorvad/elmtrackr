package com.elmtrackr.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPreferencesTest {

    @Test
    fun `elapsed label empty when idle`() {
        val state = WidgetPreferences.DisplayState(
            isActive = false,
            shiftId = "",
            startTimeLabel = "--:--",
            dateLabel = "Wed 25 Jun",
            lastPunchLabel = "",
            pendingCount = 0,
            shiftStartEpochMillis = 0L,
            lastPunchEndEpochMillis = 0L,
        )
        assertEquals("", state.elapsedLabel)
        assertEquals("--:--", state.primaryTimeLabel)
        assertEquals("Clock In", state.actionLabel)
    }

    @Test
    fun `active state shows elapsed and clock out action`() {
        val started = System.currentTimeMillis() - 90 * 60_000L
        val state = WidgetPreferences.DisplayState(
            isActive = true,
            shiftId = "shift-1",
            startTimeLabel = "09:00",
            dateLabel = "Wed 25 Jun",
            lastPunchLabel = "Since 09:00",
            pendingCount = 0,
            shiftStartEpochMillis = started,
            lastPunchEndEpochMillis = 0L,
        )
        assertEquals("1h 30m", state.elapsedLabel)
        assertEquals("1h 30m", state.primaryTimeLabel)
        assertEquals("Clock Out", state.actionLabel)
        assertTrue(state.subtitleLabel.contains("09:00"))
    }
}
