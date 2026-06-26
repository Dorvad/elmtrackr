package com.elmtrackr.app.ui.design

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

val AuroraEaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
val AuroraSoftEase = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)

object AuroraMotion {
    const val PressMillis = 150
    const val FadeMillis = 250
    const val RiseMillis = 350
    const val StaggerMillis = 50L
}

@Composable
fun Modifier.auroraEnter(
    index: Int = 0,
    offsetY: Dp = 10.dp,
    durationMillis: Int = AuroraMotion.RiseMillis,
): Modifier {
    if (!ValueAnimator.areAnimatorsEnabled()) return this

    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis, easing = AuroraEaseOut),
        label = "aurora-enter-alpha",
    )
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis, easing = AuroraEaseOut),
        label = "aurora-enter-offset",
    )
    val offsetPx = with(LocalDensity.current) { offsetY.toPx() }

    LaunchedEffect(Unit) {
        delay(index.coerceAtLeast(0) * AuroraMotion.StaggerMillis)
        visible = true
    }

    return graphicsLayer {
        this.alpha = alpha
        translationY = offsetPx * (1f - progress)
    }
}

@Composable
fun Modifier.auroraPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.98f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && ValueAnimator.areAnimatorsEnabled()) pressedScale else 1f,
        animationSpec = tween(AuroraMotion.PressMillis, easing = AuroraSoftEase),
        label = "aurora-press-scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
