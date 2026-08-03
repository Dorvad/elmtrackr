package com.elmtrackr.app.ui.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Slide-and-fade transition between a screen and its sub-screen.
 *
 * [motionEnabled] is a parameter rather than a `CompositionLocal` read because
 * `transitionSpec` lambdas run outside composition. Pass `auroraMotionEnabled()`
 * from the composable that owns the `AnimatedContent`; that is what honours the
 * user's "reduce motion" setting. Reading `ValueAnimator.areAnimatorsEnabled()`
 * here — as this used to — only sees the system animator scale and silently
 * ignores the app's own preference.
 */
fun AnimatedContentTransitionScope<*>.auroraSubScreenTransition(
    forward: Boolean,
    motionEnabled: Boolean = true,
): ContentTransform {
    if (!motionEnabled) {
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
    content: @Composable (state: T) -> Unit,
) {
    if (!auroraMotionEnabled()) {
        content(targetState)
        return
    }
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(AuroraMotion.ContentCrossfadeMillis, easing = AuroraEaseOut)) togetherWith
                fadeOut(tween(AuroraMotion.ContentCrossfadeMillis, easing = AuroraEaseOut))
        },
        contentKey = contentKey,
        label = "aurora-state-crossfade",
    ) { state ->
        content(state)
    }
}
