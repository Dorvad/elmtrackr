package com.elmtrackr.wear.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Text
import com.elmtrackr.wear.R
import com.elmtrackr.wear.sync.WearAuroraColors

internal val AuroraIndigo = Color(WearAuroraColors.INDIGO)
internal val AuroraPlum = Color(WearAuroraColors.PLUM)
internal val AuroraAqua = Color(WearAuroraColors.AQUA)

/** Primary text. The phone's dark `AuroraDarkInk`. */
internal val AuroraInk = Color(WearAuroraColors.INK)

/** Secondary text — status labels, the detail line. The phone's `AuroraDarkInk2`. */
internal val AuroraInk2 = Color(WearAuroraColors.INK2)

/** The quietest text the watch draws, and hairlines. The phone's `AuroraDarkOutline`. */
internal val AuroraOutline = Color(WearAuroraColors.OUTLINE)

/** The running-shift dot. The phone's `AuroraDarkSuccess`, now shared rather than inlined. */
internal val AuroraSuccess = Color(WearAuroraColors.SUCCESS)

/** Older name for [AuroraInk]; both resolve to the same token. */
internal val AuroraOnSurface = AuroraInk

/**
 * The Aurora gradient: indigo → plum → aqua, at the phone's own stops.
 *
 * The phone paints its primary buttons, its wordmark mark and its nav pill with
 * exactly this ramp (`ElmButton.auroraGradient`, `ElmSideNavigation.logoGradient`).
 * On the watch it is the whole of the brand: the July review required the
 * *background* to be black, which moved the gradient into the foreground rather
 * than deleting it. Ending on aqua also does real work here — flat indigo reads
 * 3.8:1 against black, aqua 10.3:1, so a ramp that finishes bright is what keeps
 * a 7dp ring visible outdoors.
 */
internal val AuroraGradientStops = arrayOf(
    0.00f to AuroraIndigo,
    0.42f to AuroraPlum,
    1.00f to AuroraAqua,
)

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
        style = WearElmType.wordmark,
        color = AuroraInk2,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/**
 * The punch mark (visual only — the whole face is the tap target).
 *
 * A gradient disc with a white bolt on it, which is how the phone draws the
 * same mark everywhere it appears: the dashboard header, the projects wizard,
 * the side-nav logo. The watch used to invert it — a white disc with a
 * `#4664F4` bolt — and that blue is in neither app's palette, so the brand's
 * primary mark was the one thing the two apps did not share.
 */
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
            // Colour comes from the theme's primary (indigo). That read well on
            // the old white disc; on the new gradient one it sits over the
            // indigo end, so check it on a device and give the indicator an
            // explicit white if it disappears.
            CircularProgressIndicator(modifier = Modifier.size(size - 16.dp))
        }
    }
}

/**
 * The app badge on the setup screen.
 *
 * Now the same gradient disc as [WearBoltButton] rather than the launcher
 * bitmap on a white plate. Two reasons: the white plate was the last white
 * surface left on a face the Wear guidelines want black, and the mark the
 * wearer meets on the sign-in screen should be the mark they know from the
 * phone.
 */
/**
 * The watch's primary button — the phone's `ElmGradientButton`, at watch scale.
 *
 * Built from foundation primitives rather than Wear Material 3's `Button` so
 * the fill can be the Aurora ramp instead of a flat container colour, which is
 * how the phone draws every primary action.
 *
 * The corner radius is the phone's `CornerRadius.Button` (18dp), not the Wear
 * default pill. That is a deliberate divergence from the platform default in
 * favour of the two apps agreeing; it is a shape choice, not a guideline
 * question. The 48dp floor is not negotiable in the same way — it is the
 * accessibility minimum for a touch target, and a wrist is a worse aiming
 * surface than a phone held in two hands.
 */
@Composable
fun WearGradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .wearPressScale(source)
            .clip(RoundedCornerShape(ElmButtonCornerRadius))
            .background(Brush.linearGradient(colorStops = AuroraGradientStops))
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** The phone's `CornerRadius.Button`. */
private val ElmButtonCornerRadius = 18.dp

@Composable
fun WearAppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    Image(
        painter = painterResource(R.drawable.tile_bolt_button),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

/**
 * Thin ring hugging the screen edge that fills with the share of the daily
 * goal already worked.
 *
 * Painted with the Aurora gradient, which is what the phone's clock card and
 * primary buttons use. It was flat white, left over from when the face behind
 * it was a full-bleed indigo gradient and white was the only thing that would
 * read on it; against black the brand ramp reads better and matches the phone.
 *
 * The sweep starts at 12 o'clock and runs clockwise, so the gradient is
 * rotated to put indigo at the top and finish on aqua — the same direction of
 * travel as the phone's left-to-right button fill.
 */
@Composable
fun AuroraProgressRing(
    progressPercent: Int,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 7.dp,
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
        // The unfilled remainder. Kept neutral rather than a dimmed brand
        // colour: it is the absence of progress, and a faint indigo track reads
        // as a second, competing arc.
        drawArc(
            color = AuroraOutline.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val brush = Brush.sweepGradient(
            colorStops = AuroraGradientStops,
            center = Offset(size.width / 2f, size.height / 2f),
        )
        val sweep = (360f * animated).coerceAtLeast(8f)
        // rotate so the sweep gradient's 0 degrees lands at 12 o'clock, where
        // the arc starts, instead of at 3 o'clock where sweepGradient begins.
        rotate(degrees = -90f) {
            drawArc(
                brush = brush,
                startAngle = 0f,
                sweepAngle = sweep,
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
    color: Color = AuroraInk,
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
