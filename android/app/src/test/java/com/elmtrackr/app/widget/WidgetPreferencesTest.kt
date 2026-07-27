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
            lastPunchLabel = "",
            pendingCount = 0,
            shiftStartEpochMillis = started,
            lastPunchEndEpochMillis = 0L,
            todayMinutes = 372,
            dailyGoalMinutes = 480,
        )
        assertTrue(state.elapsedHms.startsWith("1:"))
        assertEquals(state.elapsedHms, state.primaryTimeLabel)
        assertEquals(77, state.progressPercent)
    }

    @Test
    fun `progress and time labels when idle`() {
        val state = WidgetPreferences.DisplayState(
            isActive = false,
            shiftId = "",
            startTimeLabel = "08:57",
            dateLabel = "Wed 25 Jun",
            lastPunchLabel = "",
            pendingCount = 0,
            shiftStartEpochMillis = 0L,
            lastPunchEndEpochMillis = 0L,
            todayMinutes = 372,
            dailyGoalMinutes = 480,
        )
        assertEquals("08:57", state.primaryTimeLabel)
        assertEquals("6:12", state.todayHms)
        assertEquals(108, state.progressRemainderMinutes)
        assertEquals(77, state.progressPercent)
    }

    @Test
    fun `timer refresh aligns to the next minute rollover`() {
        val start = 1_000_000L
        // 4:17 into the shift: next flip in 43s, plus the 1s landing margin.
        assertEquals(
            44L,
            WidgetTimerScheduler.alignedDelaySeconds(start, start + (4 * 60 + 17) * 1_000L),
        )
        // Exactly on a boundary: full minute plus margin, not an immediate re-run.
        assertEquals(
            61L,
            WidgetTimerScheduler.alignedDelaySeconds(start, start + 300_000L),
        )
        // Unknown start falls back to a plain minute.
        assertEquals(60L, WidgetTimerScheduler.alignedDelaySeconds(0L))
    }
}
