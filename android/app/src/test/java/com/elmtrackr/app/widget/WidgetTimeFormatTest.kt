package com.elmtrackr.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTimeFormatTest {

    @Test
    fun `elapsed label uses minute precision`() {
        val now = 1_750_000_000_000L
        val start = now - (2 * 3600 + 34 * 60 + 14) * 1000L
        assertEquals("2:34", WidgetTimeFormat.elapsedHm(start, nowMillis = now))
    }

    @Test
    fun `elapsed label under an hour keeps leading zero hours`() {
        val now = 1_750_000_000_000L
        val start = now - (5 * 60 + 59) * 1000L
        assertEquals("0:05", WidgetTimeFormat.elapsedHm(start, nowMillis = now))
    }

    @Test
    fun `minutes to label`() {
        assertEquals("6:12", WidgetTimeFormat.minutesToHm(372))
    }
}
