package com.elmtrackr.app.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Flips content horizontally when the layout direction is right-to-left.
 * Used for directional icons (chevrons, arrows) that have no auto-mirrored
 * variant, so "forward" keeps pointing toward the reading direction in
 * Hebrew and other RTL locales.
 */
@Composable
fun Modifier.mirrorInRtl(): Modifier =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) scale(scaleX = -1f, scaleY = 1f) else this
