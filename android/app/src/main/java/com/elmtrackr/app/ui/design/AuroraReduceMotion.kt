package com.elmtrackr.app.ui.design

import android.animation.ValueAnimator
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalInspectionMode

val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun auroraMotionEnabled(): Boolean =
    !LocalInspectionMode.current &&
        ValueAnimator.areAnimatorsEnabled() &&
        !LocalReduceMotion.current

/**
 * A tween that collapses to an instant jump when motion is reduced.
 *
 * For animations whose *value* still matters when the motion does not — a
 * selection pill's alpha, an entrance scale. Gating those at the call site means
 * writing an if/else around every one and getting it wrong on the ones nobody
 * revisits, which is how the side navigation and the empty state kept animating
 * with the setting on. Passing this spec instead makes the reduced case the
 * default behaviour of the animation rather than an extra branch beside it.
 *
 * [snap] rather than a zero-duration tween: the target value is applied
 * immediately and completely, so nothing that depends on the animated value ends
 * up in a half-finished state.
 */
@Composable
fun <T> auroraAnimationSpec(
    durationMillis: Int,
    easing: Easing = FastOutSlowInEasing,
): FiniteAnimationSpec<T> =
    if (auroraMotionEnabled()) tween(durationMillis, easing = easing) else snap()
