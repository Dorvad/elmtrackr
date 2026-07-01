package com.elmtrackr.app.ui.design

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode

fun AnimatedContentTransitionScope<*>.auroraSubScreenTransition(forward: Boolean): ContentTransform {
    if (!ValueAnimator.areAnimatorsEnabled()) {
        return fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    }
    return if (forward) {
        slideInHorizontally(tween(AuroraMotion.SubScreenEnterMillis, easing = AuroraEaseOut)) { it / 5 } +
            fadeIn(tween(AuroraMotion.FadeMillis, easing = AuroraEaseOut)) togetherWith
            slideOutHorizontally(tween(AuroraMotion.SubScreenExitMillis, easing = AuroraEaseOut)) { -it / 5 } +
            fadeOut(tween(AuroraMotion.PressMillis))
    } else {
        slideInHorizontally(tween(AuroraMotion.SubScreenEnterMillis, easing = AuroraEaseOut)) { -it / 5 } +
            fadeIn(tween(AuroraMotion.FadeMillis, easing = AuroraEaseOut)) togetherWith
            slideOutHorizontally(tween(AuroraMotion.SubScreenExitMillis, easing = AuroraEaseOut)) { it / 5 } +
            fadeOut(tween(AuroraMotion.PressMillis))
    }
}

@Composable
fun <T> AuroraStateCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any,
    content: @Composable (key: Any) -> Unit,
) {
    if (LocalInspectionMode.current || !ValueAnimator.areAnimatorsEnabled()) {
        content(contentKey(targetState))
        return
    }
    Crossfade(
        targetState = contentKey(targetState),
        animationSpec = tween(AuroraMotion.ContentCrossfadeMillis, easing = AuroraEaseOut),
        modifier = modifier,
        label = "aurora-state-crossfade",
        content = content,
    )
}
