package com.elmtrackr.app.domain.time

import com.elmtrackr.app.domain.OvernightShiftDetector
import com.elmtrackr.app.domain.WeekendRules
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WorkTimezoneTest {

    private val jerusalem = ZoneId.of("Asia/Jerusalem")
    private val settings = UserSettings(
        id = "cfg",
        userId = "u1",
        timezone = "Asia/Jerusalem",
        weekendDays = listOf(5, 6),
    )

    private fun shift(start: String, end: String) = Shift(
        id = "s1",
        userId = "u1",
        startTime = Instant.parse(start),
        endTime = Instant.parse(end),
    )

    @Test
    fun `month range uses Jerusalem midnight boundaries`() {
        val (from, to) = WorkTimezone.monthRangeEpochMillis(2024, 3, jerusalem)
        val marchStart = LocalDate.of(2024, 3, 1).atStartOfDay(jerusalem).toInstant()
        val aprilStart = LocalDate.of(2024, 4, 1).atStartOfDay(jerusalem).toInstant()
        assertEquals(marchStart.toEpochMilli(), from)
        assertEquals(aprilStart.toEpochMilli(), to)
    }

    @Test
    fun `shift starting before Jerusalem midnight belongs to previous local day`() {
        // 2024-03-14 22:30 UTC = 2024-03-15 00:30 IST
        val s = shift("2024-03-14T22:30:00Z", "2024-03-14T23:30:00Z")
        assertEquals(LocalDate.of(2024, 3, 15), WorkTimezone.shiftLocalDate(s, settings))
    }

    @Test
    fun `Friday night shift in Jerusalem counts as weekend segment`() {
        // Fri 2024-03-15 18:00 → Sat 2024-03-16 02:00 Jerusalem
        val s = shift("2024-03-15T16:00:00Z", "2024-03-16T00:00:00Z")
        val segments = OvernightShiftDetector.splitShiftByDay(s, jerusalem)
        val annotated = WeekendRules.annotateWeekendSegments(segments, settings.weekendDays)
        assertTrue(annotated.any { it.isWeekend })
    }

    @Test
    fun `overnight shift crossing Jerusalem midnight splits into two days`() {
        val s = shift("2024-03-14T20:00:00Z", "2024-03-15T04:00:00Z")
        val segments = OvernightShiftDetector.splitShiftByDay(s, jerusalem)
        assertEquals(2, segments.size)
        assertEquals("2024-03-14", segments[0].date)
        assertEquals("2024-03-15", segments[1].date)
    }

    @Test
    fun `DST spring forward day range still covers full local day`() {
        // Israel DST 2024 started March 29 — use that Saturday
        val dstDate = LocalDate.of(2024, 3, 29)
        val (from, to) = WorkTimezone.dayRangeEpochMillis(dstDate, jerusalem)
        assertTrue(to > from)
        val hours = (to - from) / (60 * 60 * 1000)
        assertTrue(hours in 23..25)
    }

    @Test
    fun `same instant is not overnight in Jerusalem when local dates match`() {
        val s = shift("2024-03-10T06:00:00Z", "2024-03-10T14:00:00Z")
        assertFalse(OvernightShiftDetector.isOvernight(s, jerusalem))
    }
}
