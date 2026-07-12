package com.elmtrackr.wear.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.elmtrackr.wear.R
import com.elmtrackr.wear.sync.WearAuroraColors

internal val AuroraIndigo = Color(WearAuroraColors.INDIGO)
internal val AuroraPlum = Color(WearAuroraColors.PLUM)
internal val AuroraAqua = Color(WearAuroraColors.AQUA)
internal val AuroraSurface = Color(WearAuroraColors.SURFACE)
internal val AuroraOnSurface = Color(WearAuroraColors.ON_SURFACE)
internal val AuroraInk = Color(WearAuroraColors.INK2)

/**
 * Watch-sized version of the phone app's aurora mesh: dark navy base with
 * softly drifting indigo/plum/aqua glows and a vignette that keeps the edge
 * of round screens dark.
 */
@Composable
fun WearAuroraBackground(modifier: Modifier = Modifier) {
    val motion = wearMotionEnabled()
    val transition = rememberInfiniteTransition(label = "wear-aurora")
    val animatedDrift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wear-aurora-drift",
    )
    val drift = if (motion) animatedDrift else 0.5f

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(AuroraSurface)

        val dx = (drift - 0.5f) * w * 0.08f
        val dy = (drift - 0.5f) * h * 0.06f

        fun blob(center: Offset, radius: Float, color: Color, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }

        blob(Offset(w * 0.20f + dx, h * 0.12f + dy), w * 0.55f, AuroraIndigo, 0.35f)
        blob(Offset(w * 0.85f - dx, h * 0.30f + dy), w * 0.50f, AuroraPlum, 0.30f)
        blob(Offset(w * 0.50f + dx, h * 0.95f - dy), w * 0.60f, AuroraAqua, 0.22f)

        // Vignette: fade to the base surface toward the bezel of round screens.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, AuroraSurface.copy(alpha = 0.85f)),
                center = Offset(w / 2f, h / 2f),
                radius = maxOf(w, h) * 0.72f,
            ),
        )
    }
}

@Composable
fun WearAppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(AuroraIndigo, AuroraPlum, AuroraAqua)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size - 3.dp)
                .clip(CircleShape),
        )
    }
}

/**
 * Round punch button with an aurora gradient fill and press-scale feedback.
 */
@Composable
fun AuroraPunchButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    gradient: List<Color> = listOf(AuroraIndigo, AuroraPlum),
    diameter: Dp = 76.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(diameter)
            .wearPressScale(interactionSource)
            .clip(CircleShape)
            .background(Brush.linearGradient(if (enabled) gradient else listOf(AuroraInk, AuroraInk)))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = AuroraOnSurface,
            )
        }
    }
}

/**
 * Thin gradient arc hugging the screen edge that fills with the share of the
 * daily goal already worked.
 */
@Composable
fun AuroraProgressRing(
    progressPercent: Int,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 6.dp,
) {
    val motion = wearMotionEnabled()
    val target = progressPercent.coerceIn(0, 100) / 100f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(if (motion) 900 else 0),
        label = "wear-progress-ring",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f + 2.dp.toPx()
        val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
        drawArc(
            color = AuroraInk.copy(alpha = 0.35f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (animated > 0f) {
            drawArc(
                brush = Brush.sweepGradient(
                    0f to AuroraIndigo,
                    0.5f to AuroraPlum,
                    1f to AuroraAqua,
                    center = Offset(size.width / 2f, size.height / 2f),
                ),
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * Result mark that draws itself in — a checkmark on success, a cross on
 * failure. Used by the punch confirmation overlay.
 */
@Composable
fun AnimatedResultMark(
    success: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = AuroraAqua,
) {
    val motion = wearMotionEnabled()
    val progressAnim = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(if (motion) 450 else 0, delayMillis = if (motion) 120 else 0),
        )
    }
    val progress = progressAnim.value

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
        if (success) {
            val path = Path().apply {
                moveTo(w * 0.20f, h * 0.55f)
                lineTo(w * 0.42f, h * 0.76f)
                lineTo(w * 0.82f, h * 0.28f)
            }
            val measure = PathMeasure()
            measure.setPath(path, false)
            val partial = Path()
            measure.getSegment(0f, measure.length * progress, partial, true)
            drawPath(path = partial, color = color, style = stroke)
        } else {
            // Two strokes of the cross, drawn one after the other.
            val first = (progress * 2f).coerceIn(0f, 1f)
            val second = ((progress - 0.5f) * 2f).coerceIn(0f, 1f)
            if (first > 0f) {
                drawLine(
                    color = color,
                    start = Offset(w * 0.28f, h * 0.28f),
                    end = Offset(
                        w * (0.28f + (0.72f - 0.28f) * first),
                        h * (0.28f + (0.72f - 0.28f) * first),
                    ),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }
            if (second > 0f) {
                drawLine(
                    color = color,
                    start = Offset(w * 0.72f, h * 0.28f),
                    end = Offset(
                        w * (0.72f - (0.72f - 0.28f) * second),
                        h * (0.28f + (0.72f - 0.28f) * second),
                    ),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
