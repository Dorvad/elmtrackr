package com.elmtrackr.app.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius as GeometryCornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.design.LocalReduceMotion
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPlum
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Drawing-only file, kept apart from [WearSettingsContent] for the same reason
 * the clock faces and the ride-provider marks are: the numbers here are
 * illustration geometry and pigment, not layout spacing or theme colour, so they
 * are exempt from the design-system budget. Keeping the settings screen itself
 * inside the budget means real layout drift there still fails the build.
 */

/** Rendered size of [WatchGraphic] on the Wear settings screen. */
internal val WatchGraphicSize = 190.dp

/** The dial's own darkness, independent of theme: a watch face is a watch face. */
private val DialTop = Color(0xFF181530)
private val DialBottom = Color(0xFF0D0B22)
private val DialTick = Color(0xFF615C8A)
private val DialHand = Color(0xFFF6F6FD)

/** Hardware-indicator green, chosen to read as a status LED rather than as UI. */
private val StatusLedOn = Color(0xFF2FBF71)

/**
 * A hand-drawn Wear OS watch: strap, aurora-gradient bezel, dark dial with
 * ticks and hands frozen at the watchmaker's 10:09, a side crown, and a status
 * LED. When [connected], the bezel gradient slowly revolves and the LED
 * breathes green; both animations sit still under reduce-motion. No bitmap
 * assets — it inherits the theme and scales to any size.
 */
@Composable
internal fun WatchGraphic(
    connected: Boolean,
    appInstalled: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val animate = connected && !reduceMotion
    // The infinite transition only exists while animating, so a disconnected or
    // reduce-motion graphic costs no frames at all.
    val bezelAngle: Float
    val ledPulse: Float
    if (animate) {
        val transition = rememberInfiniteTransition(label = "watch-graphic")
        bezelAngle = transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing)),
            label = "bezel-sweep",
        ).value
        ledPulse = transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_400), repeatMode = RepeatMode.Reverse),
            label = "led-pulse",
        ).value
    } else {
        bezelAngle = 0f
        ledPulse = 1f
    }

    val strapColor = MaterialTheme.colorScheme.surfaceVariant
    val strapEdge = MaterialTheme.colorScheme.outlineVariant
    val caseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val accentColor = AuroraIndigo
    val disconnectedBezel = MaterialTheme.colorScheme.outlineVariant
    val ledOff = MaterialTheme.colorScheme.outline

    val statusDescription = stringResource(
        if (connected) R.string.settings_wear_graphic_connected else R.string.settings_wear_graphic_disconnected,
    )
    Box(modifier.semantics { contentDescription = statusDescription }) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val caseRadius = min(w, h) * 0.31f
            val strapWidth = caseRadius * 1.05f

            // Strap, drawn first so the case overlaps it.
            val strapBrush = Brush.verticalGradient(listOf(strapColor, strapEdge))
            listOf(
                Rect(center.x - strapWidth / 2f, h * 0.04f, center.x + strapWidth / 2f, center.y),
                Rect(center.x - strapWidth / 2f, center.y, center.x + strapWidth / 2f, h * 0.96f),
            ).forEach { r ->
                drawRoundRect(
                    brush = strapBrush,
                    topLeft = r.topLeft,
                    size = Size(r.width, r.height),
                    cornerRadius = GeometryCornerRadius(strapWidth * 0.22f),
                )
            }
            // Strap keeper lines.
            listOf(h * 0.115f, h * 0.845f).forEach { y ->
                drawLine(
                    color = strapEdge,
                    start = Offset(center.x - strapWidth / 2.6f, y),
                    end = Offset(center.x + strapWidth / 2.6f, y),
                    strokeWidth = caseRadius * 0.05f,
                    cap = StrokeCap.Round,
                )
            }

            // Crown and side button on the right edge of the case.
            drawRoundRect(
                color = caseColor,
                topLeft = Offset(center.x + caseRadius * 0.96f, center.y - caseRadius * 0.32f),
                size = Size(caseRadius * 0.18f, caseRadius * 0.24f),
                cornerRadius = GeometryCornerRadius(caseRadius * 0.06f),
            )
            drawRoundRect(
                color = caseColor,
                topLeft = Offset(center.x + caseRadius * 0.92f, center.y + caseRadius * 0.12f),
                size = Size(caseRadius * 0.16f, caseRadius * 0.30f),
                cornerRadius = GeometryCornerRadius(caseRadius * 0.06f),
            )

            // Case body behind the bezel.
            drawCircle(color = caseColor, radius = caseRadius * 1.06f, center = center)

            // Dial.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(DialTop, DialBottom),
                    center = center,
                    radius = caseRadius,
                ),
                radius = caseRadius * 0.94f,
                center = center,
            )

            // Bezel: revolving aurora sweep when connected, dashed grey when not.
            if (connected) {
                rotate(bezelAngle, pivot = center) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(AuroraIndigo, AuroraPlum, AuroraAqua, AuroraIndigo),
                            center = center,
                        ),
                        radius = caseRadius,
                        center = center,
                        style = Stroke(width = caseRadius * 0.12f),
                    )
                }
            } else {
                drawCircle(
                    color = disconnectedBezel,
                    radius = caseRadius,
                    center = center,
                    style = Stroke(
                        width = caseRadius * 0.10f,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(caseRadius * 0.22f, caseRadius * 0.16f),
                        ),
                    ),
                )
            }

            // Hour ticks.
            repeat(12) { i ->
                val angle = Math.toRadians(i * 30.0)
                val outer = caseRadius * 0.84f
                val inner = if (i % 3 == 0) caseRadius * 0.70f else caseRadius * 0.77f
                drawLine(
                    color = DialTick,
                    start = center + Offset(
                        (cos(angle) * inner).toFloat(),
                        (sin(angle) * inner).toFloat(),
                    ),
                    end = center + Offset(
                        (cos(angle) * outer).toFloat(),
                        (sin(angle) * outer).toFloat(),
                    ),
                    strokeWidth = caseRadius * if (i % 3 == 0) 0.05f else 0.03f,
                    cap = StrokeCap.Round,
                )
            }

            if (connected) {
                // Hands at 10:09.
                val hourAngle = Math.toRadians((10.15 / 12.0) * 360.0 - 90.0)
                val minuteAngle = Math.toRadians((9.0 / 60.0) * 360.0 - 90.0)
                drawLine(
                    color = DialHand,
                    start = center,
                    end = center + Offset(
                        (cos(hourAngle) * caseRadius * 0.42f).toFloat(),
                        (sin(hourAngle) * caseRadius * 0.42f).toFloat(),
                    ),
                    strokeWidth = caseRadius * 0.07f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = DialHand,
                    start = center,
                    end = center + Offset(
                        (cos(minuteAngle) * caseRadius * 0.62f).toFloat(),
                        (sin(minuteAngle) * caseRadius * 0.62f).toFloat(),
                    ),
                    strokeWidth = caseRadius * 0.05f,
                    cap = StrokeCap.Round,
                )
                drawCircle(color = accentColor, radius = caseRadius * 0.07f, center = center)
                // Tiny goal arc, echoing the watch app's progress ring.
                if (appInstalled) {
                    drawArc(
                        color = AuroraAqua,
                        startAngle = -90f,
                        sweepAngle = 250f,
                        useCenter = false,
                        topLeft = center - Offset(caseRadius * 0.56f, caseRadius * 0.56f),
                        size = Size(caseRadius * 1.12f, caseRadius * 1.12f),
                        style = Stroke(width = caseRadius * 0.045f, cap = StrokeCap.Round),
                    )
                }
            } else {
                // Sleeping face: a dim horizontal dash where the time would be.
                drawLine(
                    color = DialTick,
                    start = center - Offset(caseRadius * 0.28f, 0f),
                    end = center + Offset(caseRadius * 0.28f, 0f),
                    strokeWidth = caseRadius * 0.07f,
                    cap = StrokeCap.Round,
                )
            }

            // Status LED at the case's lower-right, outside the dial.
            val ledAngle = Math.toRadians(45.0)
            val ledCenter = center + Offset(
                (cos(ledAngle) * caseRadius * 1.28f).toFloat(),
                (sin(ledAngle) * caseRadius * 1.28f).toFloat(),
            )
            drawCircle(
                color = if (connected) StatusLedOn.copy(alpha = ledPulse) else ledOff,
                radius = caseRadius * 0.09f,
                center = ledCenter,
            )
            if (connected) {
                drawCircle(
                    color = StatusLedOn.copy(alpha = ledPulse * 0.25f),
                    radius = caseRadius * 0.17f,
                    center = ledCenter,
                )
            }
        }
    }
}
