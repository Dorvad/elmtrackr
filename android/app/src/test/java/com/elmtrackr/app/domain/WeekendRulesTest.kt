package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.DaySegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekendRulesTest {

    // JS encoding: 0=Sun … 6=Sat. 2026-07-04 is a Saturday, 2026-07-05 a Sunday.
    private val friSat = listOf(5, 6)

    @Test
    fun isWeekendDate_matches_configured_days() {
        assertTrue(WeekendRules.isWeekendDate("2026-07-04", friSat))
        assertTrue(WeekendRules.isWeekendDate("2026-07-03", friSat))
        assertFalse(WeekendRules.isWeekendDate("2026-07-05", friSat))
        assertFalse(WeekendRules.isWeekendDate("2026-07-06", friSat))
    }

    @Test
    fun isWeekendDate_treats_malformed_dates_as_non_weekend() {
        assertFalse(WeekendRules.isWeekendDate("", friSat))
        assertFalse(WeekendRules.isWeekendDate("not-a-date", friSat))
        assertFalse(WeekendRules.isWeekendDate("2026-13-40", friSat))
    }

    @Test
    fun annotateWeekendSegments_keeps_segments_and_flags_weekends() {
        val segments = listOf(
            DaySegment(date = "2026-07-03", minutes = 120),
            DaySegment(date = "2026-07-05", minutes = 60),
        )
        val annotated = WeekendRules.annotateWeekendSegments(segments, friSat)
        assertEquals(2, annotated.size)
        assertTrue(annotated[0].isWeekend)
        assertFalse(annotated[1].isWeekend)
        assertEquals(120, WeekendRules.totalWeekendMinutes(annotated))
    }
}
