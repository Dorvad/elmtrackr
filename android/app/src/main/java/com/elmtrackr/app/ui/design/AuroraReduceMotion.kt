package com.elmtrackr.app.ui.design

import android.animation.ValueAnimator
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalInspectionMode

val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun auroraMotionEnabled(): Boolean =
    !LocalInspectionMode.current &&
        ValueAnimator.areAnimatorsEnabled() &&
        !LocalReduceMotion.current
