package com.elmtrackr.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

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
    fun `dark theme outlines remain visible`() {
        assertContrast("outline", DarkColorScheme.outline, DarkColorScheme.background, 3.0)
        assertContrast("outline variant", DarkColorScheme.outlineVariant, DarkColorScheme.background, 2.0)
    }

    private fun assertContrast(name: String, foreground: Color, background: Color, minimum: Double) {
        val light = max(foreground.luminance(), background.luminance()).toDouble()
        val dark = min(foreground.luminance(), background.luminance()).toDouble()
        val ratio = (light + 0.05) / (dark + 0.05)
        assertTrue("$name contrast was ${"%.2f".format(ratio)}; expected at least $minimum", ratio >= minimum)
    }
}
