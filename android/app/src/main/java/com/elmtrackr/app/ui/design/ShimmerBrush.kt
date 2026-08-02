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
 * Returns a diagonal shimmer [Brush] for skeleton loading screens.
 *
 * Usage:
 * ```
 * val shimmer = rememberShimmerBrush()
 * Box(Modifier.background(shimmer))
 * ```
 *
 * With reduce-motion on, the gradient is evaluated at its start offset and held
 * there rather than the brush being skipped. This shimmer backs every skeleton
 * in the app, so the loading state of every major screen used to keep animating
 * regardless of the user's setting — and a skeleton that is invisible while
 * reducing motion would be worse than one that simply sits still.
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

    val translateAnim = if (auroraMotionEnabled()) {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue  = 1600f,
            animationSpec = infiniteRepeatable(
                animation  = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmer_translate",
        )
        animated
    } else {
        0f
    }

    return Brush.linearGradient(
        colors = shimmerColors,
        start  = Offset(translateAnim - 400f, translateAnim - 400f),
        end    = Offset(translateAnim, translateAnim),
    )
}
