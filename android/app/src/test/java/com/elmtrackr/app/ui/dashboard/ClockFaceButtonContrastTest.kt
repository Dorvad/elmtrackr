package com.elmtrackr.app.ui.dashboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.elmtrackr.app.ui.theme.DarkColorScheme
import com.elmtrackr.app.ui.theme.LightColorScheme
import com.elmtrackr.app.ui.theme.contrastRatio
import com.elmtrackr.app.ui.theme.labelOnFill
import com.elmtrackr.app.ui.theme.readableInk
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The clock-in button and the status caption on every expressive face, idle and
 * running, normal and overtime, in both themes.
 *
 * Each face brings its own accent and plate, so the button's label used to be fixed
 * white on the accent and the running label the accent on a tint of itself. Measured
 * that way, 15 of the faces put the main action on the main screen below AA: 1.5:1 on
 * gold, about 2:1 on aqua and peach. The card now asks [labelOnFill] and
 * [readableInk] for its colours; this repeats that arithmetic over every face so a
 * new face, accent or plate that cannot be made legible fails here first.
 *
 * Mirrors `ExpressiveClockCard` in DashboardScreen.kt: the fill alphas and the 55%
 * idle caption are copied from it.
 */
class ClockFaceButtonContrastTest {

    private val surfaces = listOf(
        "light" to LightColorScheme.surface,
        "dark" to DarkColorScheme.surface,
    )
    private val onSurfaces = mapOf(
        "light" to LightColorScheme.onSurface,
        "dark" to DarkColorScheme.onSurface,
    )

    @Test
    fun `every face's clock button label reaches AA, idle and running`() {
        val failures = mutableListOf<String>()
        forEachFace { name, plate, accent, darkPlate, _ ->
            val idle = contrastRatio(labelOnFill(accent), accent)
            if (idle < AA) failures += "$name idle: %.2f".format(idle)

            val runningFill = accent.copy(alpha = if (darkPlate) .25f else .12f).compositeOver(plate)
            val running = contrastRatio(readableInk(accent, runningFill), runningFill)
            if (running < AA) failures += "$name running: %.2f".format(running)
        }
        assertTrue("Clock button below 4.5:1 —\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `every face's status caption reaches AA, idle and running`() {
        val failures = mutableListOf<String>()
        forEachFace { name, plate, accent, darkPlate, theme ->
            val running = contrastRatio(readableInk(accent, plate), plate)
            if (running < AA) failures += "$name running caption: %.2f".format(running)

            val foreground = if (darkPlate) Color.White else onSurfaces.getValue(theme)
            val idleInk = readableInk(foreground.copy(alpha = .55f).compositeOver(plate), plate)
            val idle = contrastRatio(idleInk, plate)
            if (idle < AA) failures += "$name idle caption: %.2f".format(idle)
        }
        assertTrue("Clock caption below 4.5:1 —\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    private fun forEachFace(
        block: (name: String, plate: Color, accent: Color, darkPlate: Boolean, theme: String) -> Unit,
    ) {
        for ((theme, surface) in surfaces) {
            for (style in SupportedClockStyle.entries) {
                val plate = clockFacePlate(style, surface)
                for (overtime in listOf(false, true)) {
                    val accent = clockFaceAccent(style, overtime)
                    val label = "$style/$theme${if (overtime) "/overtime" else ""}"
                    block(label, plate, accent, style.hasDarkPlate(), theme)
                }
            }
        }
    }

    private companion object {
        const val AA = 4.5f
    }
}
