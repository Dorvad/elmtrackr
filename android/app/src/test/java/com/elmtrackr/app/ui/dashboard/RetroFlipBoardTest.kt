package com.elmtrackr.app.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The board's text is what a screen reader announces and what the flip cells
 * split into digits, so the format has to match `LiveClockTimer` exactly —
 * a drifted format would flip a different set of cells than the plain timer
 * would have redrawn.
 */
class RetroFlipBoardTest {

    @Test
    fun `elapsed text matches the plain timer format`() {
        assertEquals("00:00:00", flipElapsedText(0))
        assertEquals("00:00:59", flipElapsedText(59))
        assertEquals("00:01:00", flipElapsedText(60))
        assertEquals("07:59:59", flipElapsedText(7 * 3600 + 59 * 60 + 59))
        assertEquals("12:03:04", flipElapsedText(12 * 3600 + 3 * 60 + 4))
    }

    @Test
    fun `hours keep growing past a day rather than wrapping`() {
        assertEquals("26:00:01", flipElapsedText(26 * 3600 + 1))
    }

    @Test
    fun `a negative elapsed clamps to zero like the plain timer`() {
        assertEquals("00:00:00", flipElapsedText(-5))
    }
}
