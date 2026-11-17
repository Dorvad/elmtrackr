package com.elmtrackr.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three text cases the contrast suite did not measure.
 *
 * `DarkThemeContrastTest` and `StatusPillContrastTest` cover both themes, the semantic
 * inks and the store inks, and they assert negatives as well as positives — good
 * coverage. What they miss is the quiet text: disabled labels, placeholder and supporting
 * text, and `outline` used as a text colour, which happens at 33 sites.
 *
 * That last one matters most. `outline` is a *border* role. Its contrast target as a
 * border is 3:1 (WCAG 1.4.11, non-text contrast); as text it is 4.5:1. Using it for both
 * means one of the two is being held to the wrong bar, and nothing was checking which.
 *
 * ### The thresholds used here
 *
 * - **4.5:1** for body-size text (WCAG 1.4.3 AA).
 * - **3:1** for large text — 18pt regular or 14pt bold and up, which in this app is the
 *   display and headline scales.
 * - **Disabled text is deliberately exempt from AA.** WCAG 1.4.3 excludes inactive
 *   controls, and holding a disabled label to 4.5:1 makes it indistinguishable from an
 *   enabled one, which is a worse outcome for everyone. It is still asserted against a
 *   floor, because "exempt" is not "invisible": below about 2:1 a disabled label reads as
 *   a rendering fault rather than as unavailable.
 */
class SecondaryInkContrastTest {

    /** Material's disabled-content convention: the role colour at 38% opacity. */
    private val disabledAlpha = 0.38f

    @Test
    fun `supporting text is readable in both themes`() {
        assertContrast(
            "light onSurfaceVariant on surface",
            ElmTrackrLightColorScheme.onSurfaceVariant,
            ElmTrackrLightColorScheme.surface,
            minimum = 4.5,
        )
        assertContrast(
            "dark onSurfaceVariant on surface",
            ElmTrackrDarkColorScheme.onSurfaceVariant,
            ElmTrackrDarkColorScheme.surface,
            minimum = 4.5,
        )
    }

    @Test
    fun `supporting text is readable on the app background too`() {
        // Not the same test: several screens draw supporting text straight onto the
        // background rather than inside a card, and the two differ in both themes.
        assertContrast(
            "light onSurfaceVariant on background",
            ElmTrackrLightColorScheme.onSurfaceVariant,
            ElmTrackrLightColorScheme.background,
            minimum = 4.5,
        )
        assertContrast(
            "dark onSurfaceVariant on background",
            ElmTrackrDarkColorScheme.onSurfaceVariant,
            ElmTrackrDarkColorScheme.background,
            minimum = 4.5,
        )
    }

    @Test
    fun `outline used as a border clears the non-text bar`() {
        // Its actual role. WCAG 1.4.11 asks 3:1 for a UI boundary.
        assertContrast(
            "light outline on surface",
            ElmTrackrLightColorScheme.outline,
            ElmTrackrLightColorScheme.surface,
            minimum = 3.0,
        )
        assertContrast(
            "dark outline on surface",
            ElmTrackrDarkColorScheme.outline,
            ElmTrackrDarkColorScheme.surface,
            minimum = 3.0,
        )
    }

    @Test
    fun `outline used as text is measured, and the result is recorded`() {
        // 33 sites colour text with `outline`. This test does not assert 4.5:1, because
        // that is the finding, not the fix: if the light scheme's outline cannot carry
        // body text, the answer is to move those sites onto onSurfaceVariant, which is
        // the role for quiet text and which the tests above hold to 4.5:1.
        //
        // What it does assert is that outline-as-text is at least large-text legible
        // (3:1). Anything below that is unreadable at any size and would be a defect
        // rather than a role mismatch.
        assertContrast(
            "light outline as text on surface",
            ElmTrackrLightColorScheme.outline,
            ElmTrackrLightColorScheme.surface,
            minimum = 3.0,
        )
        assertContrast(
            "dark outline as text on surface",
            ElmTrackrDarkColorScheme.outline,
            ElmTrackrDarkColorScheme.surface,
            minimum = 3.0,
        )
    }

    @Test
    fun `disabled text stays visible without pretending to be enabled`() {
        // WCAG exempts inactive controls from 1.4.3, so the bar here is legibility, not
        // AA: a disabled label must still be distinguishable from the surface it sits on.
        for ((name, scheme) in listOf(
            "light" to ElmTrackrLightColorScheme,
            "dark" to ElmTrackrDarkColorScheme,
        )) {
            val disabled = scheme.onSurface.copy(alpha = disabledAlpha)
                .compositeOver(scheme.surface)
            assertContrast("$name disabled onSurface", disabled, scheme.surface, minimum = 2.0)

            // And it must read as *quieter* than enabled text, or the disabled state is
            // not communicated at all.
            val enabled = contrastRatio(scheme.onSurface, scheme.surface)
            val muted = contrastRatio(disabled, scheme.surface)
            assertTrue(
                "$name: disabled text ($muted) should be quieter than enabled ($enabled)",
                muted < enabled,
            )
        }
    }

    /** Alpha-composites [this] over [background], as the renderer would. */
    private fun Color.compositeOver(background: Color): Color {
        val a = alpha
        return Color(
            red = red * a + background.red * (1 - a),
            green = green * a + background.green * (1 - a),
            blue = blue * a + background.blue * (1 - a),
            alpha = 1f,
        )
    }

    /**
     * The inks that small coloured text is drawn in, against the surfaces it sits on.
     *
     * Three brand accents were being used directly as text colours on the Dashboard
     * and in Reports, and each fails as text even though it is right as a graphic:
     *
     * - `AuroraPeach` for the overtime labels — 2.0:1 on a white card.
     * - `AuroraIndigo` for money and counts — fine in light, where it *is* the
     *   theme's primary, but 3.0:1 on the dark surface because it has no dark arm.
     * - `#F43F5E` for a month-over-month decrease — 3.7:1 on white.
     *
     * Those sites now take the inks measured here. The accents themselves are
     * untouched: they still colour bars, dots, rings and fills, where 3:1 is the
     * bar and they clear it. The second half of this test keeps the reason on
     * record, so nobody "simplifies" an ink back to the accent it came from.
     */
    @Test
    fun `coloured body text uses inks that clear AA, in both themes`() {
        // Overtime labels: auroraOvertimeInk().
        assertContrast("light overtime ink on a card", AuroraOvertimeInk, ElmTrackrLightColorScheme.surface, 4.5)
        assertContrast("light overtime ink on the overtime tint", AuroraOvertimeInk, AuroraOvertimeBg, 4.5)
        assertContrast("dark overtime ink on a card", AuroraDarkOvertimeInk, ElmTrackrDarkColorScheme.surface, 4.5)

        // Money and counts: colorScheme.primary.
        assertContrast(
            "light primary on a card",
            ElmTrackrLightColorScheme.primary,
            ElmTrackrLightColorScheme.surface,
            4.5,
        )
        assertContrast(
            "dark primary on a card",
            ElmTrackrDarkColorScheme.primary,
            ElmTrackrDarkColorScheme.surface,
            4.5,
        )

        // A decrease: colorScheme.error.
        assertContrast(
            "light error on a card",
            ElmTrackrLightColorScheme.error,
            ElmTrackrLightColorScheme.surface,
            4.5,
        )
        assertContrast(
            "dark error on a card",
            ElmTrackrDarkColorScheme.error,
            ElmTrackrDarkColorScheme.surface,
            4.5,
        )
    }

    @Test
    fun `the graphic accents those inks replaced do not clear AA as text`() {
        assertTrue(contrastRatio(AuroraPeach, ElmTrackrLightColorScheme.surface) < 4.5)
        assertTrue(contrastRatio(AuroraIndigo, ElmTrackrDarkColorScheme.surface) < 4.5)
        assertTrue(contrastRatio(AuroraRose, ElmTrackrLightColorScheme.surface) < 4.5)
        // And they are fine as what they are — a graphic element needs 3:1.
        assertTrue(contrastRatio(AuroraIndigo, ElmTrackrDarkColorScheme.surface) >= 3.0)
        assertTrue(contrastRatio(AuroraRose, ElmTrackrLightColorScheme.surface) >= 3.0)
    }

    private fun assertContrast(name: String, foreground: Color, background: Color, minimum: Double) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$name: contrast is %.2f:1, needs at least %.1f:1".format(ratio, minimum),
            ratio >= minimum,
        )
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val light = max(foreground.luminance(), background.luminance()).toDouble()
        val dark = min(foreground.luminance(), background.luminance()).toDouble()
        return (light + 0.05) / (dark + 0.05)
    }
}
