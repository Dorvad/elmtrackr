package com.elmtrackr.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTimeFormatTest {

    @Test
    fun `elapsed hms with hours`() {
        val start = System.currentTimeMillis() - (2 * 3600 + 34 * 60 + 14) * 1000L
        assertEquals("2:34:14", WidgetTimeFormat.elapsedHms(start))
    }

    @Test
    fun `minutes to hms`() {
        assertEquals("6:12:00", WidgetTimeFormat.minutesToHms(372))
    }
}
