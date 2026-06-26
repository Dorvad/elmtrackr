package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OvernightShiftDetectorTest {

    private fun shift(start: String, end: String?, break_: Int = 0) = Shift(
        id = "s1", userId = "u1",
        startTime = Instant.parse(start),
        endTime = end?.let { Instant.parse(it) },
        breakMinutes = break_,
    )

    // ── isOvernight ───────────────────────────────────────────────────────────

    @Test
    fun `isOvernight - same-day shift is false`() {
        assertFalse(OvernightShiftDetector.isOvernight(
            shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z")
        ))
    }

    @Test
    fun `isOvernight - shift crossing midnight is true`() {
        assertTrue(OvernightShiftDetector.isOvernight(
            shift("2024-01-08T22:00:00Z", "2024-01-09T02:00:00Z")
        ))
    }

    @Test
    fun `isOvernight - active shift is false`() {
        assertFalse(OvernightShiftDetector.isOvernight(
            shift("2024-01-08T22:00:00Z", null)
        ))
    }

    // ── Test 2: splitShiftByDay for overnight shift ────────────────────────────

    @Test
    fun `splitShiftByDay - same-day shift produces one segment`() {
        val s = shift("2024-01-08T09:00:00Z", "2024-01-08T17:00:00Z") // 480 min net
        val segments = OvernightShiftDetector.splitShiftByDay(s)
        assertEquals(1, segments.size)
        assertEquals("2024-01-08", segments[0].date)
        assertEquals(480, segments[0].minutes)
    }

    @Test
    fun `splitShiftByDay - overnight shift produces two segments with correct dates`() {
        // 22:00 Mon → 02:00 Tue, 0 break → 240 min net
        val s = shift("2024-01-08T22:00:00Z", "2024-01-09T02:00:00Z")
        val segments = OvernightShiftDetector.splitShiftByDay(s)
        assertEquals(2, segments.size)
        assertEquals("2024-01-08", segments[0].date)
        assertEquals("2024-01-09", segments[1].date)
    }

    @Test
    fun `splitShiftByDay - overnight split is proportional`() {
        // 22:00→02:00 = 4h gross; each half = 2h → 50/50 split
        val s = shift("2024-01-08T22:00:00Z", "2024-01-09T02:00:00Z")
        val segments = OvernightShiftDetector.splitShiftByDay(s)
        assertEquals(120, segments[0].minutes)  // 22:00→midnight = 2h
        assertEquals(120, segments[1].minutes)  // midnight→02:00 = 2h
    }

    @Test
    fun `splitShiftByDay - break is distributed proportionally across segments`() {
        // 22:00→02:00 = 4h gross, 60 min break → 180 min net; 50/50 split
        val s = shift("2024-01-08T22:00:00Z", "2024-01-09T02:00:00Z", break_ = 60)
        val segments = OvernightShiftDetector.splitShiftByDay(s)
        assertEquals(2, segments.size)
        assertEquals(90, segments[0].minutes)
        assertEquals(90, segments[1].minutes)
    }

    @Test
    fun `splitShiftByDay - active shift returns empty list`() {
        val segments = OvernightShiftDetector.splitShiftByDay(shift("2024-01-08T22:00:00Z", null))
        assertTrue(segments.isEmpty())
    }

    @Test
    fun `splitShiftByDay - midnight-to-midnight shift produces one segment`() {
        // Exactly one calendar day, 480 min net
        val s = shift("2024-01-08T00:00:00Z", "2024-01-08T08:00:00Z")
        val segments = OvernightShiftDetector.splitShiftByDay(s)
        assertEquals(1, segments.size)
        assertEquals(480, segments[0].minutes)
    }

    // ── isWeekend annotation is false before WeekendRules ─────────────────────

    @Test
    fun `splitShiftByDay segments are not yet annotated as weekend`() {
        val s = shift("2024-01-05T09:00:00Z", "2024-01-05T17:00:00Z") // Friday
        val segments = OvernightShiftDetector.splitShiftByDay(s)
        assertFalse(segments.all { it.isWeekend })
    }
}
