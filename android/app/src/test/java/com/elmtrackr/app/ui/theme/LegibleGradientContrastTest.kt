package com.elmtrackr.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * White text on the brand sweep that primary buttons, the shift pay card, the shop
 * button and the selected Reports tab wear.
 *
 * The decorative stops measured 3.7–4.3:1 under a primary button's label in a render
 * (plum alone is 4.23:1 against white). The legible stops are checked across the part
 * of the sweep a label can occupy — 10% to 90% — for full white (labels) and 90%
 * white (the pay card's secondary lines). Stops are interpolated per channel in sRGB,
 * as the gradient is drawn; a render of the old stops agreed with that model to
 * within one 8-bit step.
 */
class LegibleGradientContrastTest {

    @Test
    fun `white labels reach AA across the label area`() {
        assertFloor(textAlpha = 1f)
    }

    @Test
    fun `ninety percent white reaches AA across the label area`() {
        assertFloor(textAlpha = 0.9f)
    }

    private fun assertFloor(textAlpha: Float) {
        val failures = (10..90).mapNotNull { percent ->
            val t = percent / 100f
            val background = colorAt(t)
            val text = Color.White.copy(alpha = textAlpha).compositeOver(background)
            val ratio = contrastRatio(text, background)
            if (ratio < 4.5f) "t=$t: %.2f".format(ratio) else null
        }
        assertTrue("White at $textAlpha below 4.5:1 —\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    private fun colorAt(t: Float): Color {
        val stops = AuroraLegibleGradientStops
        for (i in 0 until stops.size - 1) {
            val (t0, c0) = stops[i]
            val (t1, c1) = stops[i + 1]
            if (t in t0..t1) return srgbLerp(c0, c1, (t - t0) / (t1 - t0))
        }
        return stops.last().second
    }

    /**
     * Per-channel interpolation in sRGB, which is how the gradient is painted.
     * Compose's `lerp(Color, Color, Float)` works in Oklab and would measure a
     * different, brighter midpoint than the one on screen.
     */
    private fun srgbLerp(a: Color, b: Color, f: Float): Color = Color(
        red = a.red + (b.red - a.red) * f,
        green = a.green + (b.green - a.green) * f,
        blue = a.blue + (b.blue - a.blue) * f,
    )
}
