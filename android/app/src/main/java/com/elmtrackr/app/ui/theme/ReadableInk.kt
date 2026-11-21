package com.elmtrackr.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * Text colours worked out from the surface they sit on, for the places where that
 * surface is not a theme role: the clock faces each bring their own accent and plate,
 * so a fixed "white on accent" pairing passed on some faces and failed on others.
 */

/** The WCAG 2.x contrast ratio between two opaque colours, 1.0 to 21.0. */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/**
 * The label for text on a solid [fill]: white where white reads better, otherwise
 * [darkInk]. Dark text on light accents — gold, aqua, peach, green — and white on the
 * deep ones, which is the rule [auroraStatusPillInk] already applies to pills.
 */
fun labelOnFill(fill: Color, darkInk: Color = AuroraNavy): Color =
    if (contrastRatio(Color.White, fill) >= contrastRatio(darkInk, fill)) Color.White else darkInk

/**
 * [ink] itself when it already reaches [minRatio] on [background]; otherwise [ink]
 * moved toward black or white, whichever the background allows more contrast with,
 * in small steps until it does. The hue survives, so an accent-coloured label stays
 * recognisably the accent — tinted from the colour, never swapped for grey.
 */
fun readableInk(ink: Color, background: Color, minRatio: Float = 4.5f): Color {
    if (contrastRatio(ink, background) >= minRatio) return ink
    val target = if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) {
        Color.Black
    } else {
        Color.White
    }
    for (step in 1..INK_STEPS) {
        val candidate = lerp(ink, target, step / INK_STEPS.toFloat())
        if (contrastRatio(candidate, background) >= minRatio) return candidate
    }
    return target
}

private const val INK_STEPS = 20
