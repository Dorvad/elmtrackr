package com.elmtrackr.app.ui.dashboard

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPeach
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Stats for nerds: the same shift in four notations.
 *
 * Every other face is a metaphor with the elapsed time laid over it. These four print
 * their own figures, which makes them the only faces in the app that draw text — hence
 * the [TextMeasurer] each takes, and hence [SupportedClockStyle.drawsOwnReading], which
 * stops the dashboard compositing a second time display on top.
 *
 * Geometry follows the same 312×176 reference box as the rest of the renderer: x
 * positions stretch with the width, everything else is dp mapped 1:1. Each face renders
 * a complete still at `pulse = 0`, so idle and reduce-motion are the same frame.
 *
 * Numbers are never computed here. [ClockFaceTelemetry] arrives with the money already
 * resolved by the payroll engine and formatted for the locale; this file measures strings
 * and places them.
 */

/** Terminal inks. Named rather than inlined because three of the four faces share them. */
private val TerminalKey = Color(0xff8296ae)
private val TerminalValue = Color(0xffe8f0f8)
private val TerminalDim = Color(0xff5f6e85)

/**
 * A monospace type scale, because these faces are columns of digits.
 *
 * `FontFamily.Monospace` rather than the app's Hanken Grotesk: a proportional font makes
 * a right-aligned figure jitter as its digits change, which is the same defect Wave G's
 * tabular numerals fixed on the dashboard timer. Monospace is the stronger form of that
 * fix and it suits the notation.
 */
private fun mono(size: Int, weight: FontWeight, color: Color, letterSpacing: Float = 0f) =
    TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = size.sp,
        fontWeight = weight,
        color = color,
        letterSpacing = letterSpacing.sp,
    )

/** Draws [text] with its left edge at [x] and its vertical centre at [y]. */
private fun DrawScope.textLeft(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    style: TextStyle,
) {
    val laid = measurer.measure(text, style)
    drawText(laid, topLeft = Offset(x, y - laid.size.height / 2f))
}

/** Draws [text] with its right edge at [x] and its vertical centre at [y]. */
private fun DrawScope.textRight(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    style: TextStyle,
) {
    val laid = measurer.measure(text, style)
    drawText(laid, topLeft = Offset(x - laid.size.width, y - laid.size.height / 2f))
}

/** Draws [text] centred horizontally on [x], vertical centre at [y]. */
private fun DrawScope.textCentre(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    style: TextStyle,
) {
    val laid = measurer.measure(text, style)
    drawText(laid, topLeft = Offset(x - laid.size.width / 2f, y - laid.size.height / 2f))
}

/** The width [text] would occupy, for laying a second run beside it. */
private fun TextMeasurer.widthOf(text: String, style: TextStyle): Float =
    measure(text, style).size.width.toFloat()

// ── Readout ───────────────────────────────────────────────────────────────────

/**
 * Four aligned rows of telemetry, a blinking block cursor and a progress hairline.
 *
 * Labels left, values right, so the figures form a column that does not move as they
 * change. The cursor is the only thing that animates: it is the running indicator, and a
 * terminal is the one idiom where a blink reads as "live" rather than as decoration.
 */
internal fun DrawScope.drawReadoutFace(
    telemetry: ClockFaceTelemetry,
    pulse: Float,
    running: Boolean,
    accent: Color,
    measurer: TextMeasurer,
) {
    val left = 26.dp.toPx()
    val right = size.width - 26.dp.toPx()
    val eyebrow = mono(11, FontWeight.SemiBold, TerminalDim, letterSpacing = 1.1f)

    textLeft(measurer, "SHIFT · LIVE", left, 24.dp.toPx(), eyebrow)
    textRight(measurer, "TARGET ${telemetry.goalClock}", right, 24.dp.toPx(), eyebrow)

    val keyStyle = mono(16, FontWeight.Medium, TerminalKey)
    val rows = listOf(
        "ELAPSED" to (telemetry.elapsedClock to false),
        "EARNED" to (telemetry.earnedText to true),
        "RATE" to (telemetry.rateText to false),
        "LEFT" to (telemetry.remainingClock to false),
    )
    rows.forEachIndexed { index, (label, value) ->
        val y = (52 + index * 26).dp.toPx()
        textLeft(measurer, label, left, y, keyStyle)
        // Earnings take the accent; the rest are plain, so one figure leads the column.
        val ink = if (value.second) accent else TerminalValue
        textRight(measurer, value.first, right, y, mono(18, FontWeight.Bold, ink))
    }

    // The cursor. Half the phase on, half off — a square wave, not a fade, because a
    // terminal cursor does not fade. Solid and dim while idle: the face must render a
    // complete still at pulse = 0 for reduce-motion.
    val cursorAlpha = if (running && pulse < 0.5f) 0.9f else 0.14f
    drawRect(
        color = accent.copy(alpha = cursorAlpha),
        topLeft = Offset(left, 149.dp.toPx()),
        size = Size(9.dp.toPx(), 14.dp.toPx()),
    )

    val barLeft = left + 18.dp.toPx()
    val barTop = 154.dp.toPx()
    val barHeight = 3.dp.toPx()
    val radius = CornerRadius(1.5.dp.toPx())
    drawRoundRect(
        color = Color.White.copy(alpha = 0.1f),
        topLeft = Offset(barLeft, barTop),
        size = Size(right - barLeft, barHeight),
        cornerRadius = radius,
    )
    drawRoundRect(
        color = accent,
        topLeft = Offset(barLeft, barTop),
        size = Size((right - barLeft) * telemetry.progress, barHeight),
        cornerRadius = radius,
    )
}

// ── Sparkline ─────────────────────────────────────────────────────────────────

/**
 * Earnings plotted against the goal, with the remainder projected.
 *
 * The solid stroke is what has been earned; the dashed continuation is where the same
 * rate lands at the goal, so the face answers "am I ahead of it" rather than only "how
 * much so far". The head pulses while the shift runs.
 */
internal fun DrawScope.drawSparklineFace(
    telemetry: ClockFaceTelemetry,
    pulse: Float,
    running: Boolean,
    foreground: Color,
    measurer: TextMeasurer,
) {
    val left = 26.dp.toPx()
    val right = size.width - 26.dp.toPx()
    val top = 92.dp.toPx()
    val base = 144.dp.toPx()
    val samples = 72
    val target = max(telemetry.targetEarned, 0.01)

    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
    drawLine(
        color = AuroraIndigo.copy(alpha = 0.3f),
        start = Offset(left, top), end = Offset(right, top),
        strokeWidth = 1.5.dp.toPx(), pathEffect = dash,
    )
    drawLine(
        color = foreground.copy(alpha = 0.1f),
        start = Offset(left, (top + base) / 2f), end = Offset(right, (top + base) / 2f),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx())),
    )
    drawLine(
        color = foreground.copy(alpha = 0.14f),
        start = Offset(left, base), end = Offset(right, base),
        strokeWidth = 1.5.dp.toPx(),
    )
    textRight(
        measurer,
        telemetry.targetEarnedText,
        right,
        top - 10.dp.toPx(),
        mono(10, FontWeight.SemiBold, foreground.copy(alpha = 0.4f), letterSpacing = 0.6f),
    )

    // A straight line at a constant rate, which is what a flat hourly wage is. Curving
    // it would imply a rate that changes during the shift.
    fun pointAt(fraction: Float): Offset {
        val value = telemetry.targetEarned * fraction
        val y = base - (value / target).toFloat() * (base - top)
        return Offset(left + fraction * (right - left), y)
    }

    val progress = telemetry.progress
    val head = pointAt(progress)

    // The filled area under the earned portion.
    val area = Path().apply {
        moveTo(left, base)
        for (i in 0..samples) {
            val f = (i / samples.toFloat()) * progress
            val p = pointAt(f)
            lineTo(p.x, p.y)
        }
        lineTo(head.x, base)
        close()
    }
    drawPath(
        path = area,
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to AuroraIndigo.copy(alpha = 0.26f),
                1f to AuroraAqua.copy(alpha = 0.03f),
            ),
            startY = top,
            endY = base,
        ),
    )

    // The projection: same rate, to the goal.
    drawLine(
        color = foreground.copy(alpha = 0.22f),
        start = head, end = pointAt(1f),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
    )
    drawLine(
        color = AuroraIndigo,
        start = pointAt(0f), end = head,
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = AuroraIndigo.copy(alpha = 0.35f),
        start = head, end = Offset(head.x, base),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
    )
    if (running) {
        drawCircle(
            color = AuroraIndigo.copy(alpha = (1f - pulse) * 0.35f),
            radius = (5f + pulse * 7f).dp.toPx(),
            center = head,
            style = Stroke(1.5.dp.toPx()),
        )
    }
    drawCircle(AuroraIndigo, 4.5.dp.toPx(), head)
    drawCircle(Color.White, 1.8.dp.toPx(), head)

    // The headline figure, with the rate beside it and the clock opposite.
    val headline = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = foreground,
    )
    textLeft(measurer, telemetry.earnedText, left, 54.dp.toPx(), headline)
    val earnedWidth = measurer.widthOf(telemetry.earnedText, headline)
    textLeft(
        measurer,
        telemetry.rateText,
        left + earnedWidth + 10.dp.toPx(),
        52.dp.toPx(),
        mono(11, FontWeight.SemiBold, AuroraAqua),
    )
    textRight(
        measurer,
        telemetry.elapsedClock,
        right,
        52.dp.toPx(),
        mono(11, FontWeight.SemiBold, foreground.copy(alpha = 0.45f)),
    )

    // Hour ticks. Derived from the goal, so a six-hour goal gets six.
    val hours = (telemetry.goalMinutes / 60).coerceIn(1, 12)
    val tickStyle = mono(9, FontWeight.Medium, foreground.copy(alpha = 0.35f))
    for (hour in 1..hours) {
        val x = left + (hour / hours.toFloat()) * (right - left)
        drawLine(
            color = foreground.copy(alpha = 0.16f),
            start = Offset(x, base), end = Offset(x, base + 3.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
        )
        textCentre(measurer, hour.toString(), x, base + 15.dp.toPx(), tickStyle)
    }
}

// ── Gauge ─────────────────────────────────────────────────────────────────────

/**
 * Hours on shift against the goal, with the overtime hour redlined.
 *
 * The scale runs one hour past the goal so overtime has somewhere to point, and the
 * pointer rides the band rather than sweeping from the centre — a full-length needle
 * would cross the readout every time it passed the middle of the dial.
 */
internal fun DrawScope.drawGaugeFace(
    telemetry: ClockFaceTelemetry,
    pulse: Float,
    running: Boolean,
    accent: Color,
    measurer: TextMeasurer,
) {
    val centreX = size.width / 2f
    val centreY = 150.dp.toPx()
    val radius = 86.dp.toPx()
    val goalHours = (telemetry.goalMinutes / 60f).coerceAtLeast(1f)
    // One hour of headroom past the goal, so the redline band exists at any goal.
    val scaleHours = goalHours + 1f
    val hoursDone = (telemetry.elapsedMinutes / 60f).coerceIn(0f, scaleHours)
    fun angleOf(hours: Float) = 180f + (hours / scaleHours) * 180f

    val white = Color.White
    fun band(fromHours: Float, toHours: Float, color: Color) {
        val from = angleOf(fromHours)
        drawArc(
            color = color,
            startAngle = from,
            sweepAngle = angleOf(toHours) - from,
            useCenter = false,
            topLeft = Offset(centreX - radius, centreY - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
    band(0f, scaleHours, white.copy(alpha = 0.1f))
    band(goalHours, scaleHours, AuroraPeach.copy(alpha = 0.55f))
    band(0f, hoursDone, AuroraAqua.copy(alpha = 0.75f))

    // Ticks: one per quarter hour of the scale, every fourth one major.
    val tickCount = 36
    for (i in 0..tickCount) {
        val angle = (angleOf((i / tickCount.toFloat()) * scaleHours) * PI / 180).toFloat()
        val major = i % 4 == 0
        val inner = radius - (if (major) 13.dp.toPx() else 6.dp.toPx())
        drawLine(
            color = white.copy(alpha = if (major) 0.5f else 0.18f),
            start = Offset(centreX + cos(angle) * inner, centreY + sin(angle) * inner),
            end = Offset(
                centreX + cos(angle) * (radius - 1.dp.toPx()),
                centreY + sin(angle) * (radius - 1.dp.toPx()),
            ),
            strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
        )
    }

    // Labels outside the band, so the dial face stays free for the figures.
    val labelStyle = mono(11, FontWeight.SemiBold, white.copy(alpha = 0.42f))
    val step = if (goalHours <= 4f) 1 else 2
    var hour = 0
    while (hour <= goalHours.toInt()) {
        val angle = (angleOf(hour.toFloat()) * PI / 180).toFloat()
        textCentre(
            measurer,
            hour.toString(),
            centreX + cos(angle) * (radius + 14.dp.toPx()),
            centreY + sin(angle) * (radius + 14.dp.toPx()),
            labelStyle,
        )
        hour += step
    }

    textCentre(
        measurer, telemetry.elapsedClock, centreX, 106.dp.toPx(),
        mono(26, FontWeight.Bold, white),
    )
    textCentre(
        measurer, telemetry.earnedText, centreX, 130.dp.toPx(),
        mono(11, FontWeight.SemiBold, white.copy(alpha = 0.5f)),
    )

    // The pointer, riding the band.
    val angle = (angleOf(hoursDone) * PI / 180).toFloat()
    val nx = cos(angle)
    val ny = sin(angle)
    drawLine(
        color = accent,
        start = Offset(centreX + nx * (radius - 26.dp.toPx()), centreY + ny * (radius - 26.dp.toPx())),
        end = Offset(centreX + nx * (radius - 11.dp.toPx()), centreY + ny * (radius - 11.dp.toPx())),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round,
    )
    val tip = Offset(centreX + nx * (radius - 3.dp.toPx()), centreY + ny * (radius - 3.dp.toPx()))
    translate(tip.x, tip.y) {
        rotate(angleOf(hoursDone), Offset.Zero) {
            val head = Path().apply {
                moveTo(4.dp.toPx(), 0f)
                lineTo(-6.dp.toPx(), 5.5.dp.toPx())
                lineTo(-6.dp.toPx(), -5.5.dp.toPx())
                close()
            }
            drawPath(head, accent)
        }
    }
    if (running) {
        drawCircle(
            color = accent.copy(alpha = (1f - pulse) * 0.35f),
            radius = (5f + pulse * 7f).dp.toPx(),
            center = tip,
            style = Stroke(1.5.dp.toPx()),
        )
    }
}

// ── Matrix ────────────────────────────────────────────────────────────────────

/**
 * One row per hour, one cell per five minutes.
 *
 * Reads as a clock and a count at once: the filled cells are the elapsed time, and their
 * number is a figure the header prints. The live cell pulses so the eye finds "now"
 * without reading the header.
 */
internal fun DrawScope.drawMatrixFace(
    telemetry: ClockFaceTelemetry,
    pulse: Float,
    running: Boolean,
    foreground: Color,
    accent: Color,
    measurer: TextMeasurer,
) {
    val left = 42.dp.toPx()
    val right = size.width - 24.dp.toPx()
    val top = 38.dp.toPx()
    // Twelve five-minute cells to the hour; one row per hour of the goal.
    val columns = 60 / ClockFaceTelemetry.MINUTES_PER_CELL
    val rows = (telemetry.cellCount + columns - 1) / columns
    if (rows <= 0) return
    val gap = 2.5.dp.toPx()
    val cellHeight = 11.dp.toPx()
    val cellWidth = (right - left - gap * (columns - 1)) / columns
    val corner = CornerRadius(2.5.dp.toPx())

    val eyebrow = mono(10, FontWeight.SemiBold, foreground.copy(alpha = 0.45f), letterSpacing = 0.8f)
    textLeft(measurer, "5-MIN CELLS", left, 20.dp.toPx(), eyebrow)
    textRight(
        measurer,
        "${telemetry.cellsFilled} / ${telemetry.cellCount}",
        right,
        20.dp.toPx(),
        mono(10, FontWeight.SemiBold, foreground.copy(alpha = 0.7f)),
    )

    val rowLabel = mono(9, FontWeight.SemiBold, foreground.copy(alpha = 0.32f))
    for (row in 0 until rows) {
        val y = top + row * (cellHeight + gap)
        textRight(measurer, "H${row + 1}", left - 9.dp.toPx(), y + cellHeight / 2f, rowLabel)
        for (column in 0 until columns) {
            val index = row * columns + column
            if (index >= telemetry.cellCount) break
            val color = when {
                index < telemetry.cellsFilled -> AuroraIndigo
                index == telemetry.cellsFilled ->
                    accent.copy(alpha = if (running) 0.35f + pulse * 0.5f else 0.6f)
                else -> foreground.copy(alpha = 0.1f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(left + column * (cellWidth + gap), y),
                size = Size(cellWidth, cellHeight),
                cornerRadius = corner,
            )
        }
    }

    val footY = top + rows * (cellHeight + gap) + 8.dp.toPx()
    textLeft(
        measurer, telemetry.elapsedClock, left, footY,
        mono(10, FontWeight.SemiBold, foreground.copy(alpha = 0.5f)),
    )
    textRight(
        measurer, telemetry.remainingClock, right, footY,
        mono(10, FontWeight.SemiBold, foreground.copy(alpha = 0.35f)),
    )
}
