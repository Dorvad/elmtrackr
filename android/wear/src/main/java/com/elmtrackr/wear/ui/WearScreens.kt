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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.elmtrackr.wear.PunchCountdown
import com.elmtrackr.wear.R
import com.elmtrackr.wear.sync.WearConfirmation

/**
 * Face scaffold shared by every screen: wordmark pinned to the top, content
 * centred on the vertical axis — the symmetric composition from the mockup.
 *
 * The wordmark and the content used to be two independent layers, each
 * positioned against the whole screen, so nothing stopped a content column that
 * grew past its default height from running into the wordmark. At the default
 * font size that was already a near miss on the idle face; at the largest
 * accessibility size the two overlapped outright, which Play recorded as "texts
 * are overlapping when a large font size is selected". [WearFaceLayout] now places
 * both from one measure pass: content stays screen-centred while it fits, slides
 * down below the wordmark when it would touch it, and when even that does not
 * fit the wordmark is dropped and the content scrolls. The two cannot overlap by
 * construction, at any font size, on any screen.
 */
@Composable
private fun WearFace(
    onTap: (() -> Unit)? = null,
    onTapLabel: String? = null,
    showWordmark: Boolean = true,
    /** Full-bleed layer drawn under the content — the goal ring. */
    decoration: (@Composable () -> Unit)? = null,
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
        decoration?.invoke()
        WearFaceLayout(
            showWordmark = showWordmark,
            wordmark = { WearBrandLabel() },
            content = {
                // One measurable for the layout below, scrollable so a column taller
                // than the space between the time and the bezel scrolls rather than
                // overflowing. Horizontal padding keeps wrapped lines off a round edge.
                Box(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            },
        )
    }
}

/**
 * Places the wordmark and the content without letting them meet.
 *
 * - `topReserve` clears the curved TimeText the scaffold draws along the top
 *   bezel; it grows with the font scale because TimeText does.
 * - Content is measured against the height left below that reserve, so a
 *   scrollable child gets a finite bound rather than infinity.
 * - If the wordmark, a gap and the content all fit, the content is screen-centred
 *   (the default composition) or pushed down just enough to clear the wordmark.
 * - Otherwise the wordmark is not placed at all and the content takes the space.
 */
@Composable
private fun WearFaceLayout(
    showWordmark: Boolean,
    wordmark: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Layout(
        contents = listOf(wordmark, content),
        modifier = Modifier.fillMaxSize(),
    ) { (wordmarkMeasurables, contentMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val topReserve = (4.dp.toPx() + 24.dp.toPx() * fontScale).toInt()
        val bottomReserve = 8.dp.toPx().toInt()
        val gap = 6.dp.toPx().toInt()

        val loose = Constraints(maxWidth = width)
        val wordmarkPlaceable = if (showWordmark) wordmarkMeasurables.firstOrNull()?.measure(loose) else null
        val contentPlaceable = contentMeasurables.first().measure(
            Constraints(maxWidth = width, maxHeight = (height - topReserve - bottomReserve).coerceAtLeast(0)),
        )

        val centredY = (height - contentPlaceable.height) / 2
        val belowWordmark = topReserve + (wordmarkPlaceable?.height ?: 0) + gap
        val wordmarkFits = wordmarkPlaceable != null &&
            belowWordmark + contentPlaceable.height <= height - bottomReserve
        val contentY = if (wordmarkFits) maxOf(centredY, belowWordmark) else maxOf(centredY, topReserve)

        layout(width, height) {
            if (wordmarkFits) {
                wordmarkPlaceable!!.placeRelative((width - wordmarkPlaceable.width) / 2, topReserve)
            }
            contentPlaceable.placeRelative((width - contentPlaceable.width) / 2, contentY)
        }
    }
}

@Composable
private fun StatusDotRow(label: String, dotColor: Color) {
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
            style = WearElmType.status,
            color = AuroraInk2,
            textAlign = TextAlign.Center,
            // A Row cannot wrap; the text can. Two lines with an ellipsis so a long
            // translation at a large font size wraps instead of leaving the screen.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
fun SetupScreen(onRefresh: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    // No wordmark on this face. It is the tallest of the six — badge, heading,
    // two lines of body and a 48dp button — and the wordmark sits in a separate
    // layer pinned to the top, so a column this tall slides underneath it. It
    // would also be the only screen showing the brand twice: the badge below is
    // the same mark. Dropping it fixes the overlap and removes the repetition.
    WearFace(showWordmark = false) {
        // The face itself scrolls (see WearFace), so this column only lays out; a
        // second vertical scroll here would be measured against infinite height
        // and throw during composition.
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WearAppLogo(size = 34.dp)
            Text(
                text = stringResource(R.string.setup_title),
                style = WearElmType.title,
                color = AuroraInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.setup_body),
                style = WearElmType.body,
                textAlign = TextAlign.Center,
                color = AuroraInk2,
                modifier = Modifier.padding(top = 5.dp, bottom = 12.dp),
            )
            WearGradientButton(
                onClick = onRefresh,
                interactionSource = interactionSource,
            ) {
                Text(
                    text = stringResource(R.string.wear_refresh),
                    style = WearElmType.button,
                    color = Color.White,
                )
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
                style = WearElmType.action,
                color = AuroraInk,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            StatusDotRow(
                label = stringResource(R.string.wear_clocked_out),
                dotColor = AuroraOutline,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = WearElmType.detail,
                    color = AuroraOutline,
                    textAlign = TextAlign.Center,
                    // Two lines + ellipsis: large accessibility fonts wrap
                    // instead of being clipped at the screen edge.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
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
        decoration = { AuroraProgressRing(progressPercent = progressPercent) },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StatusDotRow(
                label = stringResource(R.string.wear_on_shift),
                dotColor = AuroraSuccess,
            )
            Spacer(Modifier.height(2.dp))
            WearAnimatedTimeLabel(
                value = elapsed.ifBlank { "0:00" },
                // Capped scale: the count-up numerals grow with accessibility
                // fonts up to the point where they would clip inside the ring.
                style = WearElmType.countUp.withCappedFontScale(),
            )
            Spacer(Modifier.height(2.dp))
            if (isLoading) {
                androidx.wear.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.wear_tap_to_stop),
                    style = WearElmType.detail,
                    color = AuroraOutline,
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
                color = AuroraOutline.copy(alpha = 0.22f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Same ramp and same 12-o'clock origin as the goal ring on the
            // running face, so the two rings read as one component in two states.
            rotate(degrees = -90f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colorStops = AuroraGradientStops,
                        center = Offset(size.width / 2f, size.height / 2f),
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f * ring.value.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    if (countdown.isPunchIn) R.string.countdown_punching_in else R.string.countdown_punching_out,
                ),
                style = WearElmType.caption,
                color = AuroraInk2,
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
                        style = WearElmType.countdownDigit.withCappedFontScale(),
                        color = AuroraInk,
                    )
                }
            } else {
                Text(
                    text = countdown.secondsLeft.toString(),
                    style = WearElmType.countdownDigit.withCappedFontScale(),
                    color = AuroraInk,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.countdown_tap_to_cancel),
                style = WearElmType.caption,
                color = AuroraOutline,
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
                    if (confirmation.isSuccess) {
                        // Full-strength Aurora ramp, the phone's own stops. It
                        // was drawn at 55% alpha to sit on the old indigo face;
                        // on black that just made the brand look washed out.
                        Brush.linearGradient(colorStops = AuroraGradientStops)
                    } else {
                        Brush.linearGradient(listOf(AuroraPlum, AuroraIndigo))
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedResultMark(
                success = confirmation.isSuccess,
                size = 40.dp,
                color = Color.White,
            )
        }
        Text(
            text = message,
            // Capped like the numerals: a three-line failure message at 2x would
            // be taller than the screen. Three lines at 1.3x fit under the mark.
            style = WearElmType.title.withCappedFontScale(),
            color = AuroraInk,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 12.dp, end = 12.dp),
        )
    }
}

@Composable
private fun WearAnimatedTimeLabel(
    value: String,
    style: TextStyle = WearElmType.detail,
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
