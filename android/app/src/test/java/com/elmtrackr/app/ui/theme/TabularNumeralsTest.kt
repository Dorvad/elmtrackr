package com.elmtrackr.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The number surfaces ask for tabular figures; prose does not.
 *
 * The dashboard timer redraws once a second in `displayLarge`. In both of these fonts a
 * `1` is narrower than a `0`, so without `tnum` the readout changes width as the digits
 * roll and the whole thing shifts sideways every second — the kind of defect that is
 * obvious in motion and invisible in a screenshot, which is why it survived four audits.
 * Right-aligned money and hours columns have the same problem down a report.
 *
 * Asserted here rather than left to a golden, because a golden captures one frame and the
 * jitter is between frames.
 */
class TabularNumeralsTest {

    @Test
    fun `the display styles carry tabular figures`() {
        // displayLarge is the running timer; displayMedium and displaySmall are the large
        // money figures on the dashboard and in the clock-face store.
        assertEquals("tnum", ElmTrackrTypography.displayLarge.fontFeatureSettings)
        assertEquals("tnum", ElmTrackrTypography.displayMedium.fontFeatureSettings)
        assertEquals("tnum", ElmTrackrTypography.displaySmall.fontFeatureSettings)
    }

    @Test
    fun `prose styles do not`() {
        // Tabular figures are wider and read worse in a sentence, which is why the fonts
        // default to proportional. Turning it on globally would be a regression in every
        // paragraph to fix a problem that only exists in a column of numbers.
        assertNull(ElmTrackrTypography.bodyLarge.fontFeatureSettings)
        assertNull(ElmTrackrTypography.bodyMedium.fontFeatureSettings)
        assertNull(ElmTrackrTypography.titleLarge.fontFeatureSettings)
        assertNull(ElmTrackrTypography.labelLarge.fontFeatureSettings)
    }
}
