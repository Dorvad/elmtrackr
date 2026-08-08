package com.elmtrackr.wear.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.elmtrackr.wear.PunchCountdown
import com.elmtrackr.wear.R
import com.elmtrackr.wear.sync.WearConfirmation

/**
 * Face scaffold shared by every screen: wordmark pinned to the top, content
 * centered on the vertical axis — the symmetric composition from the mockup.
 */
@Composable
private fun WearFace(
    onTap: (() -> Unit)? = null,
    onTapLabel: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .let { base ->
                if (onTap != null) {
                    base.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        // Screen readers announce the action ("Punch in") instead
                        // of a bare "double tap to activate" on the unlabeled face.
                        onClickLabel = onTapLabel,
                        role = Role.Button,
                        onClick = onTap,
                    )
                } else {
                    base
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Clear of the curved TimeText band along the top bezel.
                .padding(top = 28.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            WearBrandLabel()
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun StatusDotRow(label: String, dotColor: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            fontWeight = FontWeight.Bold,
            color = AuroraOnSurface.copy(alpha = 0.85f),
        )
    }
}

@Composable
fun SetupScreen(onRefresh: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    WearFace {
        // Scrollable so oversized accessibility fonts push content into a
        // scroll instead of clipping it against the round bezel.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WearAppLogo(size = 34.dp)
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.setup_body),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = AuroraOnSurface.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 5.dp, bottom = 12.dp),
            )
            Button(
                onClick = onRefresh,
                modifier = Modifier.wearPressScale(interactionSource),
            ) {
                Text(stringResource(R.string.wear_refresh))
            }
        }
    }
}

/** Clocked out: bolt button, PUNCH IN, status, last-punch detail — one tap anywhere punches in. */
@Composable
fun IdleScreen(
    lastPunchLabel: String,
    todayShort: String,
    onPunchIn: () -> Unit,
    isLoading: Boolean,
) {
    val detail = listOfNotNull(
        lastPunchLabel.ifBlank { null },
        todayShort.ifBlank { null }?.let { stringResource(R.string.wear_today, it) },
    ).joinToString(" · ")

    WearFace(
        onTap = onPunchIn.takeIf { !isLoading },
        onTapLabel = stringResource(R.string.punch_in),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WearBoltButton(size = 60.dp, isLoading = isLoading)
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.punch_in).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.sp),
                fontWeight = FontWeight.Bold,
                color = AuroraOnSurface,
            )
            Spacer(Modifier.height(6.dp))
            StatusDotRow(
                label = stringResource(R.string.wear_clocked_out),
                dotColor = AuroraOnSurface.copy(alpha = 0.55f),
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraOnSurface.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    // Two lines + ellipsis: large accessibility fonts wrap
                    // instead of being clipped at the screen edge.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, start = 20.dp, end = 20.dp),
                )
            }
        }
    }
}

/** Clocked in: goal ring around a live count-up — one tap anywhere stops. */
@Composable
fun RunningScreen(
    elapsed: String,
    progressPercent: Int,
    onPunchOut: () -> Unit,
    isLoading: Boolean,
) {
    WearFace(
        onTap = onPunchOut.takeIf { !isLoading },
        onTapLabel = stringResource(R.string.punch_out),
    ) {
        AuroraProgressRing(progressPercent = progressPercent)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StatusDotRow(
                label = stringResource(R.string.wear_on_shift),
                dotColor = AuroraGreen,
            )
            Spacer(Modifier.height(2.dp))
            WearAnimatedTimeLabel(
                value = elapsed.ifBlank { "0:00" },
                // Capped scale: the count-up numerals grow with accessibility
                // fonts up to the point where they would clip inside the ring.
                style = MaterialTheme.typography.displayMedium
                    .copy(fontWeight = FontWeight.Bold)
                    .withCappedFontScale(),
            )
            Spacer(Modifier.height(2.dp))
            if (isLoading) {
                androidx.wear.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.wear_tap_to_stop),
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraOnSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * Full-screen 3-2-1 countdown before a punch is sent: a draining ring, a
 * haptic tick per second, and a tap anywhere to cancel.
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
            .background(Color.Black.copy(alpha = 0.94f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(R.string.countdown_tap_to_cancel),
                role = Role.Button,
                onClick = onCancel,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2f + 10.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            drawArc(
                color = AuroraOnSurface.copy(alpha = 0.2f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = AuroraOnSurface,
                startAngle = -90f,
                sweepAngle = 360f * ring.value.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    if (countdown.isPunchIn) R.string.countdown_punching_in else R.string.countdown_punching_out,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = AuroraOnSurface.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
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
                        style = MaterialTheme.typography.displayLarge.withCappedFontScale(),
                        color = AuroraOnSurface,
                    )
                }
            } else {
                Text(
                    text = countdown.secondsLeft.toString(),
                    style = MaterialTheme.typography.displayLarge.withCappedFontScale(),
                    color = AuroraOnSurface,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.countdown_tap_to_cancel),
                style = MaterialTheme.typography.labelSmall,
                color = AuroraOnSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
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
            modifier = Modifier.padding(top = 10.dp, start = 12.dp, end = 12.dp),
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
