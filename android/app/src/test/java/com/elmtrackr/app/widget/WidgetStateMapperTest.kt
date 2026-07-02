package com.elmtrackr.app.widget

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
        val zone = ZoneId.of("UTC")
        val today = LocalDate.now(zone)
        val startTime = today.atStartOfDay(zone).plusMinutes(1).toInstant()
        val shift = makeShift(startTime = startTime, endTime = null)
        val state = WidgetStateMapper.map(context(active = shift, today = listOf(shift)))
        assertTrue(state.isActive)
        assertEquals("shift-1", state.shiftId)
        assertTrue(state.shiftStartEpochMillis > 0L)
        assertTrue(state.todayMinutes > 0)
    }

    @Test
    fun `today minutes sums completed shifts`() {
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val startTime = todayStart.plusSeconds(9 * 3600)
        val endTime = todayStart.plusSeconds(16 * 3600)
        val completed = makeShift(startTime = startTime, endTime = endTime)
        val state = WidgetStateMapper.map(context(today = listOf(completed)))
        assertEquals(420, state.todayMinutes)
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
