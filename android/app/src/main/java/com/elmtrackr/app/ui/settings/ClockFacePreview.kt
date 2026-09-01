package com.elmtrackr.app.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius as GeometryCornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.dashboard.ClockFacePalette
import com.elmtrackr.app.ui.dashboard.ClockFaceScene
import com.elmtrackr.app.ui.dashboard.FlipCardBottom
import com.elmtrackr.app.ui.dashboard.FlipCardTop
import com.elmtrackr.app.ui.dashboard.FlipSeam
import com.elmtrackr.app.ui.dashboard.SupportedClockStyle
import com.elmtrackr.app.ui.dashboard.auroraFacePlate
import com.elmtrackr.app.ui.dashboard.clockFaceAccent
import com.elmtrackr.app.ui.dashboard.clockFacePlate
import com.elmtrackr.app.ui.dashboard.drawClockFace
import com.elmtrackr.app.ui.dashboard.hasDarkPlate
import com.elmtrackr.app.ui.dashboard.toSupportedOrDefault
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Layout

/**
 * The dashboard face box's proportions — what every preview box should be
 * shaped like, so the face is seen as it will be worn.
 */
internal val ClockFaceAspect: Float = Layout.clockFaceBoxWidth / Layout.clockFaceBoxHeight

/**
 * A clock face, previewed: the real face, drawn by the dashboard's own
 * renderer, scaled to whatever box the caller gives it.
 *
 * This replaces a hand-sketched approximation of each face. The sketches were
 * drawn in pixel literals for a 68dp tile; at the store's hero size their
 * hairline strokes vanished, their dots became specks and nothing about them
 * matched the face being sold. Here the drawing is [drawClockFace] itself,
 * rendered onto a canvas scaled so the box stands for the 176dp face box the
 * faces were designed in. Strokes, glows and text scale with it, so a 54dp
 * thumbnail and a full-width hero are the same face at two sizes.
 *
 * What is shown is a staged shift — five hours into an eight-hour day, running
 * — because a face at rest reads nothing and a face in overtime is not the
 * common case. The pulse is the dashboard's 1.8s phase; [animate] false skips
 * the transition entirely rather than freezing its output, so an off-screen
 * page costs nothing. Reduced motion holds every face at its resting frame.
 *
 * The caller sizes the box — a fixed [Layout.facePreviewHeight] for a picker
 * tile, `aspectRatio(ClockFaceAspect)` for a product shot. [showBackground]
 * draws the face on its real plate: the theme surface for most faces, the
 * face's own dark plate for the ones that bring theirs, the brand gradient for
 * Aurora. [plate] replaces the theme surface for the faces that borrow it —
 * the store's cards are themselves near the dark surface, and a plate the
 * same colour as the card beneath it is no plate at all.
 */
@Composable
internal fun WatchFacePreview(
    style: ClockStyle,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    showBackground: Boolean = true,
    showReading: Boolean = true,
    readingStyle: TextStyle? = null,
    shape: Shape = RoundedCornerShape(CornerRadius.Medium),
    plate: Color? = null,
) {
    val face = style.toSupportedOrDefault()
    val motion = animate && auroraMotionEnabled()
    val pulse = previewPhase(motion, PULSE_MILLIS, 1f, "face-preview-pulse")
    val spin = previewPhase(motion && face == SupportedClockStyle.VINYL, SPIN_MILLIS, 360f, "face-preview-spin")

    val scheme = MaterialTheme.colorScheme
    val foreground = if (face.hasDarkPlate()) Color.White else scheme.onSurface
    // A lifted plate takes the next tone up for its track, so a ring or a rail
    // stays visible on it.
    val palette = ClockFacePalette(
        accent = clockFaceAccent(face, overtime = false),
        foreground = foreground,
        track = if (plate != null) scheme.outlineVariant else scheme.surfaceVariant,
        plate = clockFacePlate(face, plate ?: scheme.surface),
    )
    // One scope per preview, reused every frame: it only carries the virtual
    // size and the canvas it is handed.
    val drawScope = remember { CanvasDrawScope() }
    val textMeasurer = rememberTextMeasurer()

    val plateModifier = when {
        !showBackground -> Modifier
        face == SupportedClockStyle.AURORA -> Modifier.background(auroraFacePlate())
        else -> Modifier.background(palette.plate)
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .then(plateModifier),
        contentAlignment = Alignment.Center,
    ) {
        val boxHeight = if (maxHeight.isFinite) maxHeight else Layout.facePreviewHeight
        val ratio = boxHeight / Layout.clockFaceBoxHeight
        Canvas(Modifier.fillMaxWidth().height(boxHeight)) {
            val scale = size.height / Layout.clockFaceBoxHeight.toPx()
            if (scale <= 0f) return@Canvas
            // The face is drawn as if into the 176dp box, then the canvas
            // shrinks it: the drawing never learns it is a preview.
            val virtual = Size(size.width / scale, size.height / scale)
            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.scale(scale, scale)
                drawScope.draw(this, layoutDirection, canvas, virtual) {
                    drawClockFace(
                        style = face,
                        scene = PREVIEW_SCENE.copy(vinylSpinDegrees = spin.value),
                        palette = palette,
                        pulse = pulse.value,
                    )
                    if (face == SupportedClockStyle.RETRO && showReading) {
                        drawFlipBoard(textMeasurer, PREVIEW_READING, palette.accent, palette.plate)
                    }
                }
                canvas.restore()
            }
        }
        if (showReading && face != SupportedClockStyle.RETRO) {
            Text(
                PREVIEW_READING,
                style = readingStyle ?: previewReadingStyle(face, ratio),
                color = readingColor(face, foreground),
                maxLines = 1,
            )
        }
    }
}

/**
 * A 0..[target] phase on a linear loop, or a still zero. The animation is a
 * [State] read inside the draw lambda, so a running preview invalidates its
 * own draw pass and nothing else.
 */
@Composable
private fun previewPhase(active: Boolean, millis: Int, target: Float, label: String): State<Float> =
    if (active) {
        rememberInfiniteTransition(label = label).animateFloat(
            initialValue = 0f,
            targetValue = target,
            animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing), RepeatMode.Restart),
            label = "$label-value",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

/**
 * The dashboard's readout typography for [face], scaled by [ratio] — the
 * preview's height over the real face box's — and floored so a picker tile's
 * reading stays legible rather than faithful.
 */
@Composable
private fun previewReadingStyle(face: SupportedClockStyle, ratio: Float): TextStyle {
    val type = MaterialTheme.typography
    val base = when (face) {
        SupportedClockStyle.BOLD, SupportedClockStyle.MINIMAL -> type.displayLarge
        SupportedClockStyle.FOCUS -> type.displayMedium
        SupportedClockStyle.CLASSIC -> type.titleLarge
        else -> type.displaySmall
    }
    val weight = when (face) {
        SupportedClockStyle.MINIMAL -> FontWeight.Thin
        SupportedClockStyle.FOCUS -> FontWeight.Light
        else -> FontWeight.Bold
    }
    val scaled = base.fontSize * ratio
    return base.copy(
        fontSize = if (scaled.value < MIN_READING_SP) MIN_READING_SP.sp else scaled,
        lineHeight = TextUnit.Unspecified,
        fontWeight = weight,
    )
}

@Composable
private fun readingColor(face: SupportedClockStyle, foreground: Color): Color = when (face) {
    SupportedClockStyle.CLASSIC, SupportedClockStyle.MINIMAL -> MaterialTheme.colorScheme.primary
    SupportedClockStyle.AURORA -> Color.White
    else -> foreground
}

/**
 * Retro's split-flap board, drawn rather than composed: the dashboard's
 * [com.elmtrackr.app.ui.dashboard.RetroFlipBoard] is a row of animating
 * cells at a fixed size, and a preview needs the same board at any size.
 * Same cell geometry and pigments, parked between flips.
 */
private fun DrawScope.drawFlipBoard(
    measurer: TextMeasurer,
    text: String,
    digitColor: Color,
    surface: Color,
) {
    val cellW = FLIP_CELL_WIDTH.toPx()
    val cellH = FLIP_CELL_HEIGHT.toPx()
    val gap = FLIP_GAP.toPx()
    val separatorW = FLIP_SEPARATOR_WIDTH.toPx()
    val widths = text.map { if (it == ':') separatorW else cellW }
    var x = (size.width - (widths.sum() + gap * (text.length - 1))) / 2f
    val top = (size.height - cellH) / 2f
    val seamY = top + cellH / 2f
    val corner = GeometryCornerRadius(FLIP_CORNER.toPx())
    val digitStyle = TextStyle(fontSize = FLIP_DIGIT_SIZE, fontWeight = FontWeight.Bold, color = digitColor)
    val dot = digitColor.copy(alpha = FLIP_DOT_ALPHA)
    text.forEachIndexed { index, char ->
        val w = widths[index]
        if (char == ':') {
            val cx = x + w / 2f
            drawCircle(dot, FLIP_DOT_RADIUS.toPx(), Offset(cx, seamY - FLIP_DOT_SPREAD.toPx()))
            drawCircle(dot, FLIP_DOT_RADIUS.toPx(), Offset(cx, seamY + FLIP_DOT_SPREAD.toPx()))
        } else {
            clipRect(x, top, x + w, seamY) {
                drawRoundRect(FlipCardTop, Offset(x, top), Size(w, cellH), corner)
            }
            clipRect(x, seamY, x + w, top + cellH) {
                drawRoundRect(FlipCardBottom, Offset(x, top), Size(w, cellH), corner)
            }
            val digit = measurer.measure(char.toString(), digitStyle)
            drawText(
                digit,
                topLeft = Offset(x + (w - digit.size.width) / 2f, top + (cellH - digit.size.height) / 2f),
            )
            drawRect(FlipSeam, Offset(x, seamY - FLIP_SEAM.toPx() / 2f), Size(w, FLIP_SEAM.toPx()))
            val hinge = Size(FLIP_HINGE_WIDTH.toPx(), FLIP_HINGE_HEIGHT.toPx())
            drawRect(surface, Offset(x, seamY - hinge.height / 2f), hinge)
            drawRect(surface, Offset(x + w - hinge.width, seamY - hinge.height / 2f), hinge)
        }
        x += w + gap
    }
}

/**
 * The staged shift every preview shows: five hours into an eight-hour day and
 * running, so each face has something to say.
 */
private val PREVIEW_SCENE = ClockFaceScene(
    progress = 0.62f,
    dayProgress = 0.62f,
    dayOvertime = false,
    overtimeExtension = 0f,
    running = true,
    growthHours = 4.96f,
    vinylProgress = 0.62f,
    vinylSpinDegrees = 0f,
)

/** The reading the staged shift shows: 62% of eight hours. */
private const val PREVIEW_READING = "04:58"

/** The dashboard clock faces' shared phase. */
private const val PULSE_MILLIS = 1_800

/** Vinyl's revolution. */
private const val SPIN_MILLIS = 5_400

private const val MIN_READING_SP = 10f

// RetroFlipBoard's cell geometry.
private val FLIP_CELL_WIDTH = 33.dp
private val FLIP_CELL_HEIGHT = 56.dp
private val FLIP_GAP = 5.dp
private val FLIP_CORNER = 6.dp
private val FLIP_SEAM = 1.5.dp
private val FLIP_HINGE_WIDTH = 2.5.dp
private val FLIP_HINGE_HEIGHT = 9.dp
private val FLIP_SEPARATOR_WIDTH = 8.dp
private val FLIP_DOT_RADIUS = 2.dp
private val FLIP_DOT_SPREAD = 8.dp
private val FLIP_DIGIT_SIZE = 32.sp
private const val FLIP_DOT_ALPHA = 0.7f
