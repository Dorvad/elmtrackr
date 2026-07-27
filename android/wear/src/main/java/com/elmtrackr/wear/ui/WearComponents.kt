package com.elmtrackr.wear.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.elmtrackr.wear.R
import com.elmtrackr.wear.sync.WearAuroraColors

internal val AuroraIndigo = Color(WearAuroraColors.INDIGO)
internal val AuroraPlum = Color(WearAuroraColors.PLUM)
internal val AuroraAqua = Color(WearAuroraColors.AQUA)
internal val AuroraOnSurface = Color(WearAuroraColors.ON_SURFACE)
internal val AuroraGreen = Color(0xFF34D399)

/**
 * Pure black face background, per the Wear OS quality guidelines: apps and
 * tiles must sit on a black background so they blend with the bezel and save
 * power on AMOLED displays. Brand color lives in the accents — the ring, the
 * bolt button, and the status dots — instead of the backdrop.
 */
@Composable
fun WearAuroraBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = Color.Black)
    }
}

/**
 * Caps the effective font scale for oversized display text. Accessibility
 * font sizes still grow the text up to [maxScale]; beyond that the numerals
 * would push past the round bezel and clip, so growth stops there. Body and
 * label text keeps scaling normally.
 */
@Composable
fun TextStyle.withCappedFontScale(maxScale: Float = 1.3f): TextStyle {
    val fontScale = LocalDensity.current.fontScale
    if (fontScale <= maxScale) return this
    val factor = maxScale / fontScale
    return copy(
        fontSize = fontSize * factor,
        lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight,
    )
}

/** Letter-spaced wordmark shown at the top of every face. */
@Composable
fun WearBrandLabel(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.app_name).uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
        fontWeight = FontWeight.SemiBold,
        color = AuroraOnSurface.copy(alpha = 0.85f),
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/** The white bolt punch button from the mockup (visual only — the face is the tap target). */
@Composable
fun WearBoltButton(
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    isLoading: Boolean = false,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.tile_bolt_button),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )
        if (isLoading) {
            // Theme primary is Aurora indigo — reads well on the white circle.
            CircularProgressIndicator(modifier = Modifier.size(size - 16.dp))
        }
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
            .background(Color.White),
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
 * Thin ring hugging the screen edge that fills with the share of the daily
 * goal already worked. White per the mockup; the gradient background does
 * the coloring.
 */
@Composable
fun AuroraProgressRing(
    progressPercent: Int,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 7.dp,
    color: Color = Color.White,
) {
    val motion = wearMotionEnabled()
    val target = progressPercent.coerceIn(0, 100) / 100f
    val animated = animateFloatAsState(
        targetValue = target,
        animationSpec = tween(if (motion) 900 else 0),
        label = "wear-progress-ring",
    ).value

    Canvas(modifier = modifier.fillMaxSize()) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f + 14.dp.toPx()
        val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
        drawArc(
            color = color.copy(alpha = 0.25f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val sweep = (360f * animated).coerceAtLeast(8f)
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
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
