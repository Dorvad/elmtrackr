package com.elmtrackr.app.ui.components.motion

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import com.elmtrackr.app.ui.design.AuroraSoftEase
import java.time.Duration

@Composable
fun LiveClockTimer(elapsed: Duration, modifier: Modifier = Modifier) {
    val safeElapsed = if (elapsed.isNegative) Duration.ZERO else elapsed
    val hh = safeElapsed.toHours().toString().padStart(2, '0')
    val mm = (safeElapsed.toMinutes() % 60).toString().padStart(2, '0')
    val ss = (safeElapsed.seconds % 60).toString().padStart(2, '0')

    Row(modifier = modifier) {
        AnimatedDigitPair(hh)
        Text(":", style = MaterialTheme.typography.displayLarge)
        AnimatedDigitPair(mm)
        Text(":", style = MaterialTheme.typography.displayLarge)
        AnimatedDigitPair(ss)
    }
}

@Composable
fun AnimatedDigitPair(value: String) {
    val animationsEnabled = !LocalInspectionMode.current && ValueAnimator.areAnimatorsEnabled()
    value.forEach { digit ->
        if (animationsEnabled) {
            AnimatedContent(
                targetState = digit,
                transitionSpec = {
                    slideInVertically { it } togetherWith slideOutVertically { -it }
                },
                label = "clock-digit",
            ) { d ->
                Text(d.toString(), style = MaterialTheme.typography.displayLarge)
            }
        } else {
            Text(digit.toString(), style = MaterialTheme.typography.displayLarge)
        }
    }
}

@Composable
fun Modifier.activeShiftPulse(active: Boolean): Modifier {
    if (!active || LocalInspectionMode.current || !ValueAnimator.areAnimatorsEnabled()) return this
    val infiniteTransition = rememberInfiniteTransition(label = "active-shift-pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = AuroraSoftEase),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "active-shift-pulse-scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
