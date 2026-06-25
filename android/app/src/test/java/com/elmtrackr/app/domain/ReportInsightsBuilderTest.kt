package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

class ReportInsightsBuilderTest {
    @Test
    fun `build summarizes completed shifts`() {
        val shifts = listOf(
            Shift("one", "user", Instant.parse("2026-06-01T08:00:00Z"), Instant.parse("2026-06-01T16:00:00Z")),
            Shift("two", "user", Instant.parse("2026-06-02T08:00:00Z"), Instant.parse("2026-06-02T18:00:00Z")),
        )
        val result = ReportInsightsBuilder.build(
            shifts,
            UserSettings("settings", "user", dailyOvertimeThresholdMinutes = 480),
        )

        assertNotNull(result)
        assertEquals(540, result?.averageShiftMinutes)
        assertEquals(600, result?.longestShiftMinutes)
        assertEquals(1, result?.overtimeShiftCount)
    }
}
