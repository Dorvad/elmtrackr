package com.elmtrackr.wear.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.elmtrackr.wear.PunchCountdown
import com.elmtrackr.wear.R
import com.elmtrackr.wear.sync.WearConfirmation

@Composable
fun SetupScreen(onRefresh: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WearAppLogo(size = 36.dp)
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = stringResource(R.string.setup_body),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = AuroraOnSurface.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
        )
        Button(
            onClick = onRefresh,
            modifier = Modifier.wearPressScale(interactionSource),
        ) {
            Text(stringResource(R.string.wear_refresh))
        }
    }
}

@Composable
fun IdleScreen(
    systemTime: String,
    lastPunchLabel: String,
    todayShort: String,
    onPunchIn: () -> Unit,
    isLoading: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WearAppLogo(size = 26.dp)
            WearAnimatedTimeLabel(
                value = systemTime,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.idle_last_punch),
                style = MaterialTheme.typography.labelSmall,
                color = AuroraOnSurface.copy(alpha = 0.7f),
            )
            WearAnimatedTimeLabel(
                value = lastPunchLabel.ifBlank { "--:--" },
                style = MaterialTheme.typography.displaySmall,
            )
            if (todayShort.isNotBlank()) {
                Text(
                    text = stringResource(R.string.wear_today, todayShort),
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraAqua,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        AuroraPunchButton(
            label = stringResource(R.string.punch_in),
            onClick = onPunchIn,
            isLoading = isLoading,
            gradient = listOf(AuroraIndigo, AuroraAqua),
            diameter = 68.dp,
        )
    }
}

@Composable
fun RunningScreen(
    elapsed: String,
    sinceLabel: String,
    progressPercent: Int,
    onPunchOut: () -> Unit,
    isLoading: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AuroraProgressRing(progressPercent = progressPercent)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot()
                Text(
                    text = stringResource(R.string.running_since, sinceLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroraOnSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WearAnimatedTimeLabel(elapsed, style = MaterialTheme.typography.displayLarge)
                Text(
                    text = stringResource(R.string.wear_percent_of_day, progressPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroraAqua,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            AuroraPunchButton(
                label = stringResource(R.string.punch_out),
                onClick = onPunchOut,
                isLoading = isLoading,
                gradient = listOf(AuroraPlum, AuroraIndigo),
                diameter = 64.dp,
            )
        }
    }
}

/** Pulsing "recording" dot shown while a shift is running. */
@Composable
private fun LiveDot() {
    val motion = wearMotionEnabled()
    val transition = rememberInfiniteTransition(label = "wear-live-dot")
    val pulse by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wear-live-dot-pulse",
    )
    val alpha = if (motion) pulse else 1f
    Box(
        modifier = Modifier
            .size(7.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(AuroraAqua),
    )
}

/**
 * Full-screen 3-2-1 countdown before a punch is sent: a draining gradient
 * ring, a haptic tick per second, and a tap anywhere to cancel.
 */
@Composable
fun CountdownOverlay(
    countdown: PunchCountdown,
    onCancel: () -> Unit,
) {
    val motion = wearMotionEnabled()
    val haptics = LocalHapticFeedback.current
    val ring = remember { Animatable(1f) }

    LaunchedEffect(countdown.secondsLeft) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val from = countdown.secondsLeft / TOTAL_COUNTDOWN_SECONDS
        val to = (countdown.secondsLeft - 1) / TOTAL_COUNTDOWN_SECONDS
        if (motion) {
            ring.snapTo(from)
            ring.animateTo(to, tween(1_000, easing = LinearEasing))
        } else {
            ring.snapTo(to)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuroraSurface.copy(alpha = 0.94f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2f + 10.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            drawArc(
                color = AuroraInk.copy(alpha = 0.3f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(
                    0f to AuroraAqua,
                    0.5f to AuroraIndigo,
                    1f to AuroraPlum,
                    center = Offset(size.width / 2f, size.height / 2f),
                ),
                startAngle = -90f,
                sweepAngle = 360f * ring.value.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(
                    if (countdown.isPunchIn) R.string.countdown_punching_in else R.string.countdown_punching_out,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = AuroraOnSurface.copy(alpha = 0.75f),
            )
            if (motion) {
                AnimatedContent(
                    targetState = countdown.secondsLeft,
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(tween(260), initialScale = 1.5f)) togetherWith
                            (fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.7f))
                    },
                    label = "wear-countdown-digit",
                ) { seconds ->
                    Text(
                        text = seconds.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = AuroraOnSurface,
                    )
                }
            } else {
                Text(
                    text = countdown.secondsLeft.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = AuroraOnSurface,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.countdown_tap_to_cancel),
                style = MaterialTheme.typography.labelSmall,
                color = AuroraOnSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
fun ConfirmationOverlay(confirmation: WearConfirmation) {
    val message = confirmation.message
    val haptics = LocalHapticFeedback.current
    var visible by remember(message) { mutableStateOf(false) }
    LaunchedEffect(message) {
        visible = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val scale by animateFloatAsState(
        targetValue = if (!wearMotionEnabled() || visible) 1f else 0.88f,
        animationSpec = tween(if (wearMotionEnabled()) 260 else 0),
        label = "wear-confirmation-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (!wearMotionEnabled() || visible) 1f else 0f,
        animationSpec = tween(if (wearMotionEnabled()) 220 else 0),
        label = "wear-confirmation-alpha",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        if (confirmation.isSuccess) {
                            listOf(AuroraIndigo.copy(alpha = 0.55f), AuroraAqua.copy(alpha = 0.55f))
                        } else {
                            listOf(AuroraPlum.copy(alpha = 0.55f), AuroraIndigo.copy(alpha = 0.55f))
                        },
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedResultMark(
                success = confirmation.isSuccess,
                size = 40.dp,
                color = AuroraOnSurface,
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun WearAnimatedTimeLabel(
    value: String,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    modifier: Modifier = Modifier,
) {
    if (!wearMotionEnabled()) {
        Text(text = value, style = style, modifier = modifier)
        return
    }
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            fadeIn(tween(180)) + slideInVertically { it / 3 } togetherWith
                fadeOut(tween(120)) + slideOutVertically { -it / 3 }
        },
        label = "wear-time-label",
        modifier = modifier,
    ) { label ->
        Text(text = label, style = style)
    }
}

private const val TOTAL_COUNTDOWN_SECONDS = 3f
