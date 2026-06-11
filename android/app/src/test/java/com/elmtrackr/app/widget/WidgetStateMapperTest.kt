package com.elmtrackr.app.widget

import com.elmtrackr.app.domain.model.Shift
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

    @Test
    fun `idle when shift is null`() {
        val state = WidgetStateMapper.map(null)
        assertFalse(state.isActive)
        assertEquals("", state.shiftId)
        assertEquals("", state.startTimeLabel)
    }

    @Test
    fun `idle when shift has end time`() {
        val shift = makeShift(endTime = Instant.now())
        val state = WidgetStateMapper.map(shift)
        assertFalse(state.isActive)
        assertEquals("", state.shiftId)
        assertEquals("", state.startTimeLabel)
    }

    @Test
    fun `active when shift has no end time`() {
        val shift = makeShift()
        val state = WidgetStateMapper.map(shift)
        assertTrue(state.isActive)
        assertEquals("shift-1", state.shiftId)
        assertFalse(state.startTimeLabel.isEmpty())
    }

    @Test
    fun `start time label is HH colon mm format`() {
        val shift = makeShift()
        val state = WidgetStateMapper.map(shift)
        assertTrue(
            "Expected HH:mm but got: ${state.startTimeLabel}",
            state.startTimeLabel.matches(Regex("\\d{2}:\\d{2}")),
        )
    }

    @Test
    fun `date label populated for idle state`() {
        val state = WidgetStateMapper.map(null)
        assertFalse(state.dateLabel.isEmpty())
    }

    @Test
    fun `date label populated for active state`() {
        val shift = makeShift()
        val state = WidgetStateMapper.map(shift)
        assertFalse(state.dateLabel.isEmpty())
    }

    @Test
    fun `pending count passed through for active shift`() {
        val shift = makeShift()
        val state = WidgetStateMapper.map(shift, pendingCount = 5)
        assertEquals(5, state.pendingCount)
    }

    @Test
    fun `pending count passed through for idle state`() {
        val state = WidgetStateMapper.map(null, pendingCount = 2)
        assertEquals(2, state.pendingCount)
    }

    @Test
    fun `pending count defaults to zero`() {
        val state = WidgetStateMapper.map(null)
        assertEquals(0, state.pendingCount)
    }
}
