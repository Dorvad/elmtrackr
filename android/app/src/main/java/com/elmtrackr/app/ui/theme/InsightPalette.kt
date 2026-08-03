package com.elmtrackr.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.elmtrackr.app.domain.InsightColor

/**
 * Gradients for the insight cards on the Reports screen.
 *
 * This palette used to live inside `ReportsScreen` as six Tailwind hues written
 * as raw hex — effectively a second design system running alongside Aurora, and
 * the only part of the app with no dark arm at all. Re-expressed here in Aurora
 * tokens: amber folds into the Peach family and sky into Aqua, so the set gains
 * a dark treatment while losing two off-brand hues.
 *
 * The dark arms are deliberately not just the light values. A gradient tuned for
 * a white page reads as a glowing slab on `AuroraDarkBg`, so the dark ends start
 * from the lighter, desaturated members of each family.
 */
@Composable
fun InsightColor.gradientBrush(): Brush {
    val dark = isAuroraDarkTheme()
    return when (this) {
        InsightColor.INDIGO -> gradient(AuroraIndigo, AuroraPlum, dark)
        InsightColor.VIOLET -> gradient(AuroraPlum, AuroraPlumDeep, dark)
        InsightColor.EMERALD -> gradient(AuroraSuccess, AuroraSuccessDeep, dark, darkStart = AuroraDarkSuccess)
        InsightColor.AMBER -> gradient(AuroraPeach, AuroraPeachDeep, dark, darkStart = AuroraDarkOvertimeInk)
        InsightColor.ROSE -> gradient(AuroraRose, AuroraRoseDeep, dark, darkStart = AuroraDarkRose)
        InsightColor.SKY -> gradient(AuroraAqua, AuroraAquaDeep, dark, darkStart = AuroraDarkInfoInk)
    }
}

/**
 * Secondary text drawn on top of [gradientBrush].
 *
 * Pale tints of the card's own hue rather than a flat white, which is what gives
 * the insight cards their depth. In dark mode the gradients are lighter, so the
 * sub-text has to darken to stay legible — the previous single set of pastels
 * was applied over both themes.
 */
@Composable
fun InsightColor.subTextColor(): Color = if (isAuroraDarkTheme()) {
    when (this) {
        InsightColor.INDIGO -> Color(0xFF241C63)
        InsightColor.VIOLET -> Color(0xFF2B1259)
        InsightColor.EMERALD -> Color(0xFF06382A)
        InsightColor.AMBER -> Color(0xFF4A2410)
        InsightColor.ROSE -> Color(0xFF4E0F20)
        InsightColor.SKY -> Color(0xFF063942)
    }
} else {
    when (this) {
        InsightColor.INDIGO -> Color(0xFFBFB8FF)
        InsightColor.VIOLET -> Color(0xFFD8B4FE)
        InsightColor.EMERALD -> Color(0xFFA7F3D0)
        InsightColor.AMBER -> Color(0xFFFFD9C4)
        InsightColor.ROSE -> Color(0xFFFDA4AF)
        InsightColor.SKY -> Color(0xFFB9EFF5)
    }
}

private fun gradient(
    start: Color,
    end: Color,
    dark: Boolean,
    darkStart: Color = start,
): Brush = Brush.linearGradient(
    if (dark) listOf(darkStart, end) else listOf(start, end),
)
