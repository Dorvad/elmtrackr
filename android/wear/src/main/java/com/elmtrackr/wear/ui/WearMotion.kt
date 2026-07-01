package com.elmtrackr.wear.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode

private val WearEaseOut = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

@Composable
fun wearMotionEnabled(): Boolean =
    !LocalInspectionMode.current && ValueAnimator.areAnimatorsEnabled()

@Composable
fun Modifier.wearPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && wearMotionEnabled()) pressedScale else 1f,
        animationSpec = tween(140, easing = WearEaseOut),
        label = "wear-press-scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
