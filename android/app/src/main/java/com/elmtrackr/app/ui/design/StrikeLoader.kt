package com.elmtrackr.app.ui.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraDarkInk
import com.elmtrackr.app.ui.theme.AuroraNavy
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.isAuroraDarkTheme

/**
 * "Strike & settle" — the branded post-login loader from the design handoff.
 *
 * The app mark assembles itself in a 3400ms loop: the ring draws itself around
 * from the bottom-left gap edge, the bolt strikes in with a glow flash,
 * overshoots and settles, holds, then the whole mark fades out and the loop
 * restarts. All geometry lives in a 0–100 viewBox and scales uniformly with the
 * composable's size, so callers only choose a size.
 *
 * When system animations are off (Remove animations / animator scale 0) the
 * static, fully-assembled mark is drawn instead of looping, per the spec.
 * Purely decorative — callers that need TalkBack output add their own
 * semantics.
 */
@Composable
fun StrikeLoader(modifier: Modifier = Modifier) {
    val animated = auroraMotionEnabled()
    val t: Float
    if (animated) {
        // One master clock, 0 → 1 linear over the full loop; every element's
        // value is derived from it so the segments can never drift apart.
        val transition = rememberInfiniteTransition(label = "strike-loader")
        val clock by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(LOOP_MILLIS, easing = LinearEasing)),
            label = "strike-loader-t",
        )
        t = clock
    } else {
        t = STATIC_T
    }

    // The spec defines the bolt on a light surface; on the dark scheme the navy
    // fill would disappear into the background, so the bolt takes the dark
    // theme's ink. Ring gradient and glow read well on both.
    val boltColor = if (isAuroraDarkTheme()) AuroraDarkInk else AuroraNavy

    Box(
        modifier.drawWithCache {
            // Geometry and brushes depend only on the canvas size, so they are
            // built once here and reused across the animation's frames.
            val u = size.minDimension / 100f
            val bolt = Path().apply {
                moveTo(56f * u, 24.5f * u)
                lineTo(37.5f * u, 56f * u)
                lineTo(48.5f * u, 56f * u)
                lineTo(45.5f * u, 80f * u)
                lineTo(64.5f * u, 48.5f * u)
                lineTo(53.5f * u, 48.5f * u)
                close()
            }
            val ringBrush = Brush.linearGradient(
                colors = listOf(AuroraPlum, AuroraAqua),
                start = Offset.Zero,
                end = Offset(100f * u, 100f * u),
            )
            val glowPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(255, 0x8B, 0x5C, 0xF6)
                maskFilter = android.graphics.BlurMaskFilter(
                    GLOW_BLUR_UNITS * u,
                    android.graphics.BlurMaskFilter.Blur.NORMAL,
                )
            }
            val glowPath = bolt.asAndroidPath()
            onDrawBehind {
                drawMark(t, u, bolt, glowPath, glowPaint, ringBrush, boltColor)
            }
        },
    )
}

private fun DrawScope.drawMark(
    t: Float,
    u: Float,
    bolt: Path,
    glowPath: android.graphics.Path,
    glowPaint: android.graphics.Paint,
    ringBrush: Brush,
    boltColor: Color,
) {
    // --- values derived from the master clock (segment boundaries per spec) ---
    val sweep = ARC_SWEEP_DEGREES * seg(t, 0.08f, 0.46f, ArcEasing)

    // The bolt easing overshoots (spring feel); alpha must stay within 0..1.
    val boltIn = seg(t, 0.40f, 0.50f, BoltEasing)
    val boltAlpha = boltIn.coerceIn(0f, 1f)
    val boltY = lerp(-13f, 0f, boltIn) * u
    val boltScale = when {
        t < 0.50f -> lerp(1.25f, 0.94f, boltIn)
        t < 0.58f -> lerp(0.94f, 1.04f, seg(t, 0.50f, 0.58f, BoltEasing))
        else -> lerp(1.04f, 1.00f, seg(t, 0.58f, 0.66f, BoltEasing))
    }
    val flashAlpha =
        if (t < 0.53f) GLOW_MAX_ALPHA * seg(t, 0.46f, 0.53f)
        else GLOW_MAX_ALPHA * (1f - seg(t, 0.53f, 0.64f))

    // Exit: the whole mark fades and shrinks as one group, then a blank hold.
    val exit = seg(t, 0.86f, 0.96f, ExitEasing)
    val groupAlpha = 1f - exit
    val groupScale = lerp(1f, 0.93f, exit)
    if (groupAlpha <= 0f) return

    // Center the 100-unit box inside the canvas.
    val dx = (size.width - 100f * u) / 2f
    val dy = (size.height - 100f * u) / 2f
    translate(left = dx, top = dy) {
        scale(groupScale, pivot = Offset(50f * u, 50f * u)) {
            // Ring: r=40u, stroke 11.5u, round caps; the visible 302° arc draws
            // clockwise from the bottom-left gap edge (119°, 0° = 3 o'clock).
            if (sweep > 0f) {
                drawArc(
                    brush = ringBrush,
                    startAngle = ARC_START_DEGREES,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(10f * u, 10f * u),
                    size = Size(80f * u, 80f * u),
                    style = Stroke(width = 11.5f * u, cap = StrokeCap.Round),
                    alpha = groupAlpha,
                )
            }
            // Glow flash behind the bolt.
            val glow = (flashAlpha * groupAlpha).coerceIn(0f, 1f)
            if (glow > 0f) {
                glowPaint.alpha = (glow * 255).toInt()
                drawContext.canvas.nativeCanvas.drawPath(glowPath, glowPaint)
            }
            // Bolt: strikes in from above, overshoots, settles. Scale pivots on
            // the bolt's own visual center, not the mark center.
            if (boltAlpha > 0f) {
                translate(top = boltY) {
                    scale(boltScale, pivot = Offset(51f * u, 52.2f * u)) {
                        drawPath(bolt, boltColor, alpha = (boltAlpha * groupAlpha).coerceIn(0f, 1f))
                    }
                }
            }
        }
    }
}

// Normalized progress of t through [a, b], eased; 0 before a, 1 after b.
private fun seg(t: Float, a: Float, b: Float, easing: Easing = LinearEasing): Float =
    easing.transform(((t - a) / (b - a)).coerceIn(0f, 1f))

private fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f

private const val LOOP_MILLIS = 3400
private const val ARC_START_DEGREES = 119f
private const val ARC_SWEEP_DEGREES = 302f
private const val GLOW_MAX_ALPHA = 0.6f
private const val GLOW_BLUR_UNITS = 6f

/** A point inside the hold segment: full mark, no flash — the reduce-motion frame. */
private const val STATIC_T = 0.75f

private val ArcEasing = CubicBezierEasing(0.6f, 0.05f, 0.2f, 1f)
private val BoltEasing = CubicBezierEasing(0.34f, 1.4f, 0.5f, 1f)
private val ExitEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
