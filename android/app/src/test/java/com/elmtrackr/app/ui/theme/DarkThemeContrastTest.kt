package com.elmtrackr.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * WCAG contrast floors for the colour tokens.
 *
 * Extended beyond the original dark-scheme pairs to cover two things the app was
 * getting wrong: the light scheme was never checked at all, and the semantic
 * roles are split into a saturated fill and a readable ink precisely because the
 * fill does not pass as text. The `success` pair below is the proof — the mid
 * tone that was previously used for positive deltas fails, and the ink passes.
 */
class DarkThemeContrastTest {

    @Test
    fun `dark theme text pairs meet WCAG AA contrast`() {
        assertContrast("background", DarkColorScheme.onBackground, DarkColorScheme.background, 4.5)
        assertContrast("surface", DarkColorScheme.onSurface, DarkColorScheme.surface, 4.5)
        assertContrast("surface variant", DarkColorScheme.onSurfaceVariant, DarkColorScheme.surfaceVariant, 4.5)
        assertContrast("primary", DarkColorScheme.onPrimary, DarkColorScheme.primary, 4.5)
        assertContrast("primary container", DarkColorScheme.onPrimaryContainer, DarkColorScheme.primaryContainer, 4.5)
        assertContrast("secondary", DarkColorScheme.onSecondary, DarkColorScheme.secondary, 4.5)
        assertContrast("tertiary", DarkColorScheme.onTertiary, DarkColorScheme.tertiary, 4.5)
    }

    @Test
    fun `light theme text pairs meet WCAG AA contrast`() {
        assertContrast("background", LightColorScheme.onBackground, LightColorScheme.background, 4.5)
        assertContrast("surface", LightColorScheme.onSurface, LightColorScheme.surface, 4.5)
        assertContrast("surface variant", LightColorScheme.onSurfaceVariant, LightColorScheme.surfaceVariant, 4.5)
        assertContrast("primary", LightColorScheme.onPrimary, LightColorScheme.primary, 4.5)
        assertContrast("primary container", LightColorScheme.onPrimaryContainer, LightColorScheme.primaryContainer, 4.5)
    }

    @Test
    fun `dark theme outlines remain visible`() {
        assertContrast("outline", DarkColorScheme.outline, DarkColorScheme.background, 3.0)
        assertContrast("outline variant", DarkColorScheme.outlineVariant, DarkColorScheme.background, 2.0)
    }

    /**
     * The mirror of the dark assertion above, which was the only one that existed.
     * `outline` measured 2.43:1 on the light background — below the 3:1 non-text floor
     * the dark scheme was already being held to.
     */
    @Test
    fun `light theme outlines remain visible`() {
        assertContrast("outline", LightColorScheme.outline, LightColorScheme.background, 3.0)
        assertContrast("outline variant", LightColorScheme.outlineVariant, LightColorScheme.background, 1.5)
    }

    @Test
    fun `semantic inks are readable on their own surfaces`() {
        assertContrast(
            "light success ink on surface",
            AuroraSemanticLight.successInk, LightColorScheme.surface, 4.5,
        )
        assertContrast(
            "light success ink on its container",
            AuroraSemanticLight.successInk, AuroraSemanticLight.successContainer, 4.5,
        )
        assertContrast(
            "light warning ink on surface",
            AuroraSemanticLight.warningInk, LightColorScheme.surface, 4.5,
        )
        assertContrast(
            "light info ink on surface",
            AuroraSemanticLight.infoInk, LightColorScheme.surface, 4.5,
        )

        assertContrast(
            "dark success ink on surface",
            AuroraSemanticDark.successInk, DarkColorScheme.surface, 4.5,
        )
        assertContrast(
            "dark warning ink on surface",
            AuroraSemanticDark.warningInk, DarkColorScheme.surface, 4.5,
        )
        assertContrast(
            "dark info ink on surface",
            AuroraSemanticDark.infoInk, DarkColorScheme.surface, 4.5,
        )
    }

    @Test
    fun `text drawn on a semantic fill is readable`() {
        assertContrast("on success", AuroraSemanticLight.onSuccess, AuroraSemanticLight.success, 4.5)
        assertContrast("on warning", AuroraSemanticLight.onWarning, AuroraSemanticLight.warning, 4.5)
        assertContrast("on info", AuroraSemanticLight.onInfo, AuroraSemanticLight.info, 4.5)
    }

    /**
     * The overtime stat value is the number a user reads off the card, and it was
     * drawn in AuroraPeachDeep — the graphic accent used for clock rings and
     * progress arcs — while the smaller label beside it already used the ink. The
     * larger, more important text was the one below AA.
     */
    @Test
    fun `the overtime ink is readable on the overtime container`() {
        assertContrast("light overtime ink on its container", AuroraOvertimeInk, AuroraOvertimeBg, 4.5)
        assertContrast(
            "dark overtime ink on its container",
            AuroraDarkOvertimeInk, AuroraDarkOvertimeBg, 4.5,
        )
    }

    /**
     * The reason the accent cannot be used for the value. If this starts passing,
     * AuroraPeachDeep has been darkened into something safe for text and the split
     * can be revisited.
     */
    @Test
    fun `the peach accent is not safe as overtime text`() {
        val ratio = contrastRatio(AuroraPeachDeep, AuroraOvertimeBg)
        assertTrue(
            "AuroraPeachDeep on the overtime container measured ${"%.2f".format(ratio)}:1 — " +
                "if this now passes 4.5:1, revisit the accent/ink split",
            ratio < 4.5,
        )
    }

    /**
     * The reason the fill/ink split exists. If this ever starts passing, the
     * fill has been changed into something safe for text and the split can be
     * revisited — but until then, using `success` as a text colour is a bug.
     */
    @Test
    fun `the success fill is not safe to use as body text`() {
        val ratio = contrastRatio(AuroraSemanticLight.success, LightColorScheme.surface)
        assertTrue(
            "AuroraSuccess on surface measured ${"%.2f".format(ratio)}:1 — if this now passes " +
                "4.5:1, revisit the fill/ink split documented in SemanticColors.kt",
            ratio < 4.5,
        )
    }

    private fun assertContrast(name: String, foreground: Color, background: Color, minimum: Double) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$name contrast was ${"%.2f".format(ratio)}; expected at least $minimum",
            ratio >= minimum,
        )
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val light = max(foreground.luminance(), background.luminance()).toDouble()
        val dark = min(foreground.luminance(), background.luminance()).toDouble()
        return (light + 0.05) / (dark + 0.05)
    }
}
