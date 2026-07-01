package com.elmtrackr.app.ui.components.motion

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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.elmtrackr.app.ui.design.AuroraSoftEase
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import java.time.Duration

@Composable
fun ShiftElapsedDisplay(
    elapsedSeconds: Long,
    running: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge,
    color: Color = Color.Unspecified,
    inactiveText: String = "00:00",
    textAlign: TextAlign? = null,
) {
    val resolvedColor = if (color == Color.Unspecified) LocalContentColor.current else color
    if (running) {
        LiveClockTimer(
            elapsed = Duration.ofSeconds(elapsedSeconds.coerceAtLeast(0)),
            modifier = modifier,
            style = style,
            color = resolvedColor,
            textAlign = textAlign,
        )
    } else {
        Text(
            text = inactiveText,
            style = style,
            color = resolvedColor,
            textAlign = textAlign,
            modifier = modifier,
        )
    }
}

@Composable
fun LiveClockTimer(
    elapsed: Duration,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    val safeElapsed = if (elapsed.isNegative) Duration.ZERO else elapsed
    val hh = safeElapsed.toHours().toString().padStart(2, '0')
    val mm = (safeElapsed.toMinutes() % 60).toString().padStart(2, '0')
    val ss = (safeElapsed.seconds % 60).toString().padStart(2, '0')
    val resolvedColor = if (color == Color.Unspecified) LocalContentColor.current else color

    Row(modifier = modifier) {
        AnimatedDigitPair(hh, style = style, color = resolvedColor, textAlign = textAlign)
        Text(":", style = style, color = resolvedColor, textAlign = textAlign)
        AnimatedDigitPair(mm, style = style, color = resolvedColor, textAlign = textAlign)
        Text(":", style = style, color = resolvedColor, textAlign = textAlign)
        AnimatedDigitPair(ss, style = style, color = resolvedColor, textAlign = textAlign)
    }
}

@Composable
private fun AnimatedDigitPair(
    value: String,
    style: TextStyle,
    color: Color,
    textAlign: TextAlign?,
) {
    val animationsEnabled = auroraMotionEnabled()
    value.forEach { digit ->
        if (animationsEnabled) {
            AnimatedContent(
                targetState = digit,
                transitionSpec = {
                    slideInVertically { it } togetherWith slideOutVertically { -it }
                },
                label = "clock-digit",
            ) { d ->
                Text(d.toString(), style = style, color = color, textAlign = textAlign)
            }
        } else {
            Text(digit.toString(), style = style, color = color, textAlign = textAlign)
        }
    }
}

@Composable
fun Modifier.activeShiftPulse(active: Boolean): Modifier {
    if (!active || !auroraMotionEnabled()) return this
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
