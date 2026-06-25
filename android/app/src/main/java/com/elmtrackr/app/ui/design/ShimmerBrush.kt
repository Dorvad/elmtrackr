package com.elmtrackr.app.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Returns an animated diagonal shimmer [Brush] for use in skeleton loading screens.
 *
 * Usage:
 * ```
 * val shimmer = rememberShimmerBrush()
 * Box(Modifier.background(shimmer))
 * ```
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val baseColor   = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.surface

    val shimmerColors = listOf(
        baseColor,
        highlightColor.copy(alpha = 0.85f),
        baseColor,
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue  = 1600f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start  = Offset(translateAnim - 400f, translateAnim - 400f),
        end    = Offset(translateAnim, translateAnim),
    )
}

/** Convenience: a shimmer-filled rounded rectangle modifier. */
@Composable
fun shimmerBackground(
    brush: Brush = rememberShimmerBrush(),
): Brush = brush
