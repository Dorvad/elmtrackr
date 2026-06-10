package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ShiftDurationCalculatorTest {

    private fun shift(start: String, end: String?, break_: Int = 0) = Shift(
        id = "s1", userId = "u1",
        startTime = Instant.parse(start),
        endTime = end?.let { Instant.parse(it) },
        breakMinutes = break_,
    )

    // ── Test 1: normal same-day shift ─────────────────────────────────────────

    @Test
    fun `grossMinutes - 8 hour same-day shift`() {
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z")
        assertEquals(480, ShiftDurationCalculator.grossMinutes(s))
    }

    @Test
    fun `netMinutes - same-day shift no break`() {
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z")
        assertEquals(480, ShiftDurationCalculator.netMinutes(s))
    }

    @Test
    fun `netMinutes equals gross when break is zero`() {
        val s = shift("2024-01-08T08:00:00Z", "2024-01-08T16:00:00Z")
        assertEquals(ShiftDurationCalculator.grossMinutes(s), ShiftDurationCalculator.netMinutes(s))
    }

    // ── Test 3: shift with break minutes ──────────────────────────────────────

    @Test
    fun `netMinutes subtracts break`() {
        // 9h gross, 30min break → 8h30m net
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T18:00:00Z", break_ = 30)
        assertEquals(540, ShiftDurationCalculator.grossMinutes(s))
        assertEquals(510, ShiftDurationCalculator.netMinutes(s))
    }

    @Test
    fun `netMinutes clamps to zero when break exceeds gross`() {
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T09:10:00Z", break_ = 60)
        assertEquals(0, ShiftDurationCalculator.netMinutes(s))
    }

    // ── Active shift (no end time) ─────────────────────────────────────────────

    @Test
    fun `grossMinutes returns null for active shift`() {
        val s = shift("2024-01-08T09:00:00Z", null)
        assertNull(ShiftDurationCalculator.grossMinutes(s))
    }

    @Test
    fun `netMinutes returns null for active shift`() {
        val s = shift("2024-01-08T09:00:00Z", null)
        assertNull(ShiftDurationCalculator.netMinutes(s))
    }

    @Test
    fun `elapsedMinutes returns null for completed shift`() {
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z")
        assertNull(ShiftDurationCalculator.elapsedMinutes(s))
    }

    @Test
    fun `elapsedMinutes counts minutes since start`() {
        val start = Instant.parse("2024-01-08T09:00:00Z")
        val now   = Instant.parse("2024-01-08T10:30:00Z")
        val s = shift("2024-01-08T09:00:00Z", null)
        assertEquals(90, ShiftDurationCalculator.elapsedMinutes(s, now))
    }

    // ── formatMinutes ─────────────────────────────────────────────────────────

    @Test
    fun `formatMinutes - zero`() = assertEquals("0m", ShiftDurationCalculator.formatMinutes(0))

    @Test
    fun `formatMinutes - minutes only`() = assertEquals("45m", ShiftDurationCalculator.formatMinutes(45))

    @Test
    fun `formatMinutes - hours only`() = assertEquals("8h", ShiftDurationCalculator.formatMinutes(480))

    @Test
    fun `formatMinutes - hours and minutes`() = assertEquals("8h 30m", ShiftDurationCalculator.formatMinutes(510))

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    fun `validateShiftTimes - valid range returns null`() {
        val start = Instant.parse("2024-01-08T09:00:00Z")
        val end   = Instant.parse("2024-01-08T17:00:00Z")
        assertNull(ShiftDurationCalculator.validateShiftTimes(start, end, 30))
    }

    @Test
    fun `validateShiftTimes - end before start returns error`() {
        val start = Instant.parse("2024-01-08T17:00:00Z")
        val end   = Instant.parse("2024-01-08T09:00:00Z")
        val result = ShiftDurationCalculator.validateShiftTimes(start, end, 0)
        assertEquals("End time must be after start time.", result)
    }

    @Test
    fun `validateShiftTimes - break equals gross returns error`() {
        val start = Instant.parse("2024-01-08T09:00:00Z")
        val end   = Instant.parse("2024-01-08T09:30:00Z") // 30 min gross
        val result = ShiftDurationCalculator.validateShiftTimes(start, end, 30)
        assertEquals("Break time cannot be equal to or longer than the shift duration.", result)
    }

    @Test
    fun `validateShiftTimes - negative break returns error`() {
        val start = Instant.parse("2024-01-08T09:00:00Z")
        val result = ShiftDurationCalculator.validateShiftTimes(start, null, -1)
        assertEquals("Break minutes cannot be negative.", result)
    }
}
