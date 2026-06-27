package com.elmtrackr.app.widget

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WidgetStateMapperTest {

    private fun makeShift(
        startTime: Instant = Instant.now().minusSeconds(3600),
        endTime: Instant? = null,
    ) = Shift(
        id = "shift-1",
        userId = "user-1",
        startTime = startTime,
        endTime = endTime,
    )

    private fun context(
        active: Shift? = null,
        lastCompleted: Shift? = null,
        today: List<Shift> = emptyList(),
    ) = WidgetContext(
        activeShift = active,
        lastCompletedShift = lastCompleted,
        todayShifts = today,
        settings = UserSettings(id = "s", userId = "user-1"),
    )

    @Test
    fun `idle when shift is null`() {
        val state = WidgetStateMapper.map(context())
        assertFalse(state.isActive)
        assertEquals("", state.shiftId)
        assertEquals("--:--", state.startTimeLabel)
    }

    @Test
    fun `idle uses last completed shift end time when provided`() {
        val endedAt = Instant.parse("2024-01-08T17:30:00Z")
        val lastCompleted = makeShift(
            startTime = Instant.parse("2024-01-08T09:00:00Z"),
            endTime = endedAt,
        )
        val state = WidgetStateMapper.map(context(lastCompleted = lastCompleted))
        assertFalse(state.isActive)
        assertTrue(state.lastPunchLabel.contains("Last out"))
        assertEquals(endedAt.toEpochMilli(), state.lastPunchEndEpochMillis)
    }

    @Test
    fun `active when shift has no end time`() {
        val shift = makeShift()
        val state = WidgetStateMapper.map(context(active = shift, today = listOf(shift)))
        assertTrue(state.isActive)
        assertEquals("shift-1", state.shiftId)
        assertTrue(state.shiftStartEpochMillis > 0L)
        assertTrue(state.todayMinutes >= 59)
    }

    @Test
    fun `today minutes sums completed shifts`() {
        val completed = makeShift(
            startTime = Instant.now().minusSeconds(8 * 3600),
            endTime = Instant.now().minusSeconds(3600),
        )
        val state = WidgetStateMapper.map(context(today = listOf(completed)))
        assertTrue(state.todayMinutes >= 420)
    }

    @Test
    fun `daily goal comes from settings`() {
        val settings = UserSettings(
            id = "s",
            userId = "user-1",
            dailyOvertimeThresholdMinutes = 600,
        )
        val state = WidgetStateMapper.map(
            WidgetContext(null, null, emptyList(), settings),
        )
        assertEquals(600, state.dailyGoalMinutes)
    }
}
