package com.elmtrackr.app.ui.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ui.theme.AuroraPeach
import com.elmtrackr.app.ui.theme.AuroraPeachDeep
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

/**
 * The Payday faces: Meter, Stacks, Jar and Ticker. The shift drawn as what it
 * earns — in metaphor only. Nothing here prints an amount: the dashboard is
 * glanceable in public, and the drawings need no data beyond the whole-day
 * progress and overtime state every face already reads (Sprout precedent), so
 * they stay meaningful while clocked out and never depend on a configured
 * hourly rate.
 *
 * Each draws inside the shared 176dp face box on the single 1800ms pulse.
 * Geometry follows the design reference's 312×176 canvas: x positions stretch
 * with the card width, everything else in dp. Every face renders a complete
 * still at `pulse = 0` — idle and reduce-motion are the same frame.
 *
 * Overtime is the richer state everywhere: gold work turns premium peach, the
 * same flip the card's accent makes.
 */

// The pack's pigments. Gold is the app's established one — Retro's accent and
// Summit's sun — so Payday reads as family, not a new colour system. Internal
// rather than private: the card's accent/background arms and the store preview
// live in budget-counted files and import these instead of re-declaring hexes.
internal val PaydayGold = Color(0xFFFFC857)
internal val PaydayGoldDeep = Color(0xFFDF9E2E)
internal val PaydayHousing = Color(0xFF241D12)
private val PaydayGoldSoft = Color(0xFFFFE08A)

// ── Meter ─────────────────────────────────────────────────────────────────────

/**
 * Meter face: the day on the fare meter. Four abstract drums scroll at
 * decade-spaced rates — no numerals, so the drawing mirrors cleanly in RTL —
 * over a dark housing, with the day's eight goal-hours as a notch row beneath.
 * Overtime restrokes the bezels and lamp premium-peach and extends the notch
 * row as a dashed peach run, the meter's "rate raised" state.
 */
internal fun DrawScope.drawMeterFace(
    progress: Float,
    overtime: Boolean,
    overtimeProgress: Float,
    pulse: Float,
    running: Boolean,
    foreground: Color,
) {
    if (layoutDirection == LayoutDirection.Rtl) {
        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawMeterDrums(progress, overtime, overtimeProgress, pulse, running, foreground)
        }
    } else {
        drawMeterDrums(progress, overtime, overtimeProgress, pulse, running, foreground)
    }
}

private fun DrawScope.drawMeterDrums(
    progress: Float,
    overtime: Boolean,
    overtimeProgress: Float,
    pulse: Float,
    running: Boolean,
    foreground: Color,
) {
    fun at(x: Float, y: Float) = Offset(x / 312f * size.width, y / 176f * size.height)

    val housingTopLeft = at(46f, 42f)
    val housingBottomRight = at(266f, 126f)
    val housingSize = Size(
        housingBottomRight.x - housingTopLeft.x,
        housingBottomRight.y - housingTopLeft.y,
    )
    val corner = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
    drawRoundRect(Color.White.copy(alpha = .05f), housingTopLeft, housingSize, corner)
    drawRoundRect(
        foreground.copy(alpha = .15f),
        housingTopLeft,
        housingSize,
        corner,
        style = Stroke(1.5.dp.toPx()),
    )

    // Four drum windows. Each drum turns a decade slower than the one to its
    // right; the rightmost also creeps with the pulse while running, the tick
    // of a live meter. Fractions, not digits: ticks read as motion at a
    // glance and never contradict the real elapsed reading below the face.
    val bezel = if (overtime) AuroraPeachDeep else PaydayGoldDeep.copy(alpha = .5f)
    val windowTop = at(0f, 54f).y
    val windowBottom = at(0f, 114f).y
    val windowHeight = windowBottom - windowTop
    val speeds = floatArrayOf(1f, 3f, 8f, 24f)
    repeat(4) { drum ->
        val left = at(57f + drum * 52f, 0f).x
        val right = at(99f + drum * 52f, 0f).x
        val window = Rect(left, windowTop, right, windowBottom)
        drawRoundRect(
            Color.Black.copy(alpha = .35f),
            window.topLeft,
            window.size,
            androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
        )
        drawRoundRect(
            bezel,
            window.topLeft,
            window.size,
            androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
            style = Stroke(1.5.dp.toPx()),
        )
        val creep = if (drum == 3 && running) pulse * .12f else 0f
        val fraction = (progress * speeds[drum] + creep) % 1f
        clipRect(window.left, window.top, window.right, window.bottom) {
            repeat(3) { tick ->
                val y = window.top + ((tick / 3f + fraction) % 1f) * windowHeight
                // Brightest mid-window, fading toward the edges — the cheap
                // way to suggest the drum's curve.
                val centerProximity = 1f - kotlin.math.abs(y - window.center.y) / (windowHeight / 2f)
                drawLine(
                    PaydayGold.copy(alpha = .3f + .4f * centerProximity.coerceIn(0f, 1f)),
                    Offset(window.left + 8.dp.toPx(), y),
                    Offset(window.right - 8.dp.toPx(), y),
                    2.dp.toPx(),
                    StrokeCap.Round,
                )
            }
        }
    }

    // The lamp: dim while parked, breathing while the meter runs, haloed
    // peach once the premium rate kicks in.
    val lamp = at(278f, 50f)
    when {
        overtime -> {
            drawCircle(AuroraPeach, 4.dp.toPx(), lamp)
            drawCircle(
                AuroraPeach.copy(alpha = (1f - pulse) * .3f),
                (4 + pulse * 5).dp.toPx(),
                lamp,
                style = Stroke(1.5.dp.toPx()),
            )
        }
        running -> drawCircle(PaydayGold.copy(alpha = .5f + pulse * .5f), 4.dp.toPx(), lamp)
        else -> drawCircle(PaydayGold.copy(alpha = .35f), 4.dp.toPx(), lamp)
    }

    // The goal-hour notch row: lit up to the day's progress, the current hour
    // breathing, and a dashed premium run stretching past the eighth notch on
    // overtime.
    val notchTop = at(0f, 144f).y
    val notchBottom = at(0f, 152f).y
    val hoursDone = progress * 8f
    repeat(8) { hour ->
        val x = at(57f + hour * 28.3f, 0f).x
        val color = when {
            hour < floor(hoursDone).toInt() -> PaydayGold
            hour < hoursDone -> PaydayGold.copy(alpha = .35f + pulse * .35f)
            else -> foreground.copy(alpha = .15f)
        }
        drawLine(color, Offset(x, notchTop), Offset(x, notchBottom), 3.dp.toPx(), StrokeCap.Round)
    }
    if (overtime && overtimeProgress > 0f) {
        val y = (notchTop + notchBottom) / 2f
        drawLine(
            AuroraPeach,
            Offset(at(266f, 0f).x, y),
            Offset(at(266f + overtimeProgress * 36f, 0f).x, y),
            3.dp.toPx(),
            StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
        )
    }
}

// ── Stacks ────────────────────────────────────────────────────────────────────

/**
 * Stacks face: hours as coin columns. Eight columns for the day's goal, six
 * coins each; completed hours stand full, the current hour piles up with the
 * newest coin landing on the pulse, hours still to come wait as one outlined
 * coin. Overtime stacks premium-peach coins above the goal shelf on the last
 * column — earnings past the line, drawn as exactly that.
 */
internal fun DrawScope.drawStacksFace(
    progress: Float,
    overtime: Boolean,
    overtimeProgress: Float,
    pulse: Float,
    running: Boolean,
    foreground: Color,
) {
    if (layoutDirection == LayoutDirection.Rtl) {
        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawCoinColumns(progress, overtime, overtimeProgress, pulse, running, foreground)
        }
    } else {
        drawCoinColumns(progress, overtime, overtimeProgress, pulse, running, foreground)
    }
}

private fun DrawScope.drawCoinColumns(
    progress: Float,
    overtime: Boolean,
    overtimeProgress: Float,
    pulse: Float,
    running: Boolean,
    foreground: Color,
) {
    fun at(x: Float, y: Float) = Offset(x / 312f * size.width, y / 176f * size.height)

    val baseline = at(0f, 158f).y
    val coinWidth = at(30f, 0f).x
    val coinHeight = 6.dp.toPx()
    val pitch = 8.dp.toPx()
    val shelfY = baseline - 6 * pitch - 2.dp.toPx()

    drawLine(
        foreground.copy(alpha = .2f),
        Offset(at(20f, 0f).x, baseline),
        Offset(at(292f, 0f).x, baseline),
        2.dp.toPx(),
        StrokeCap.Round,
    )
    // The goal shelf: six coins reaches it. Everything above is overtime's.
    drawLine(
        if (overtime) AuroraPeach.copy(alpha = .5f) else foreground.copy(alpha = .18f),
        Offset(at(20f, 0f).x, shelfY),
        Offset(at(292f, 0f).x, shelfY),
        1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
    )

    fun drawCoin(centerX: Float, top: Float, fill: Color, rim: Color, alpha: Float = 1f) {
        val topLeft = Offset(centerX - coinWidth / 2f, top)
        val size = Size(coinWidth, coinHeight)
        drawOval(fill.copy(alpha = fill.alpha * alpha), topLeft, size)
        drawOval(rim.copy(alpha = rim.alpha * alpha), topLeft, size, style = Stroke(1.dp.toPx()))
    }

    val hoursDone = progress * 8f
    repeat(8) { hour ->
        val centerX = at(40f + hour * 36f, 0f).x
        val coins = when {
            hour < floor(hoursDone).toInt() -> 6
            hour < hoursDone -> ((hoursDone - hour) * 6f).toInt().coerceIn(0, 6)
            else -> 0
        }
        if (coins == 0) {
            // An hour still to come: one outlined coin marks the column so the
            // day's whole shape stays readable before it is earned.
            drawOval(
                foreground.copy(alpha = .12f),
                Offset(centerX - coinWidth / 2f, baseline - coinHeight),
                Size(coinWidth, coinHeight),
                style = Stroke(1.dp.toPx()),
            )
        } else {
            repeat(coins) { coin ->
                val top = baseline - coinHeight - coin * pitch
                val isNewest = running && hour.toFloat() < hoursDone && hour + 1 > hoursDone &&
                    coin == coins - 1
                drawCoin(
                    centerX,
                    top,
                    PaydayGold,
                    PaydayGoldDeep.copy(alpha = .8f),
                    alpha = if (isNewest) .35f + pulse * .65f else 1f,
                )
            }
            if (coins == 6) {
                // A full stack catches the light on top.
                drawLine(
                    PaydayGoldSoft.copy(alpha = .6f),
                    Offset(centerX - coinWidth * .28f, baseline - coinHeight / 2f - 5 * pitch),
                    Offset(centerX + coinWidth * .28f, baseline - coinHeight / 2f - 5 * pitch),
                    1.dp.toPx(),
                    StrokeCap.Round,
                )
            }
        }
    }

    if (overtime && overtimeProgress > 0f) {
        val extra = ceil(overtimeProgress * 4f).toInt().coerceIn(1, 4)
        val centerX = at(40f + 7 * 36f, 0f).x
        repeat(extra) { coin ->
            val top = baseline - coinHeight - (6 + coin) * pitch
            val isNewest = running && coin == extra - 1
            val alpha = if (isNewest) .35f + pulse * .65f else 1f
            drawOval(
                AuroraPeach.copy(alpha = alpha),
                Offset(centerX - coinWidth / 2f, top),
                Size(coinWidth, coinHeight),
            )
            drawOval(
                AuroraPeachDeep.copy(alpha = .8f * alpha),
                Offset(centerX - coinWidth / 2f, top),
                Size(coinWidth, coinHeight),
                style = Stroke(1.dp.toPx()),
            )
        }
    }
}

// ── Jar ───────────────────────────────────────────────────────────────────────

/**
 * Jar face: the day as a tip jar of liquid gold, Tide's sibling. The level
 * climbs with day progress toward a dashed goal line near the mouth; the
 * surface is two slow sine waves on the shared pulse, with gold motes rising
 * while the shift runs. Overtime pushes the liquid past the line and turns
 * the surface premium-peach. Symmetric, so it needs no RTL mirroring.
 */
internal fun DrawScope.drawJarFace(
    progress: Float,
    overtime: Boolean,
    pulse: Float,
    running: Boolean,
    foreground: Color,
) {
    fun at(x: Float, y: Float) = Offset(x / 312f * size.width, y / 176f * size.height)

    val left = at(96f, 0f).x
    val right = at(216f, 0f).x
    val mouthLeft = at(116f, 32f)
    val mouthRight = at(196f, 32f)
    val shoulderY = at(0f, 56f).y
    val sideBottomY = at(0f, 140f).y
    val bottomY = at(0f, 156f).y
    val centerX = at(156f, 0f).x

    val vessel = Path().apply {
        moveTo(mouthLeft.x, mouthLeft.y)
        lineTo(mouthRight.x, mouthRight.y)
        quadraticTo(at(206f, 36f).x, at(206f, 36f).y, right, shoulderY)
        lineTo(right, sideBottomY)
        quadraticTo(right, bottomY, centerX, bottomY)
        quadraticTo(left, bottomY, left, sideBottomY)
        lineTo(left, shoulderY)
        quadraticTo(at(106f, 36f).x, at(106f, 36f).y, mouthLeft.x, mouthLeft.y)
        close()
    }

    // The liquid first, clipped to the vessel, so the glass strokes over it.
    val liquidTop = at(0f, 40f).y
    val fillFraction = .10f + progress * .78f
    val level = bottomY - (bottomY - liquidTop) * fillFraction
    val wavePhase = pulse * 2f * Math.PI.toFloat()
    val surfaceGold = if (overtime) AuroraPeach else PaydayGold
    fun wave(phaseShift: Float, amplitude: Float): Path = Path().apply {
        moveTo(left, level)
        var x = left
        while (x <= right) {
            val y = level + sin((x - centerX) / (right - left) * 6.4f + wavePhase + phaseShift) * amplitude
            lineTo(x, y)
            x += 6f
        }
        lineTo(right, bottomY)
        lineTo(left, bottomY)
        close()
    }
    clipPath(vessel) {
        drawPath(wave(1.7f, 6.dp.toPx()), PaydayGold.copy(alpha = .25f))
        drawPath(
            wave(0f, 4.dp.toPx()),
            Brush.verticalGradient(
                listOf(surfaceGold.copy(alpha = .55f), PaydayGoldDeep.copy(alpha = .85f)),
                startY = level,
                endY = bottomY,
            ),
        )
        if (running) {
            // Gold motes drifting up through the liquid, phase-staggered on
            // the shared pulse — Tide's bubbles, banked.
            repeat(3) { index ->
                val phase = (pulse + index / 3f) % 1f
                val rise = (bottomY - level).coerceAtLeast(1f)
                drawCircle(
                    PaydayGoldSoft.copy(alpha = (1f - phase) * .5f),
                    (2 + index % 2).dp.toPx(),
                    Offset(
                        centerX + (index - 1) * 22.dp.toPx(),
                        bottomY - 6.dp.toPx() - phase * (rise - 10.dp.toPx()).coerceAtLeast(0f),
                    ),
                )
            }
        }
    }

    drawPath(vessel, foreground.copy(alpha = .25f), style = Stroke(2.dp.toPx()))
    // The slot the day's coins drop through.
    drawRoundRect(
        foreground.copy(alpha = .25f),
        at(146f, 20f),
        Size(at(20f, 0f).x, 3.dp.toPx()),
        androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
    )
    // The goal line: reach it and the jar is full for the day.
    val goalY = bottomY - (bottomY - liquidTop) * .88f
    drawLine(
        if (overtime) AuroraPeach.copy(alpha = .5f) else foreground.copy(alpha = .3f),
        Offset(left, goalY),
        Offset(right, goalY),
        1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
    )
}

// ── Ticker ────────────────────────────────────────────────────────────────────

/**
 * The Ticker line's geometry, cached across frames — the same single-entry,
 * size-keyed cache the Metro line uses, and for the same reason: `Path` and
 * `PathMeasure` wrap native objects and none of this depends on the animation,
 * only on the face's size.
 */
private class TickerGeometry private constructor(val size: Size) {
    private val line = Path()
    private val extension = Path()

    val lineMeasure: PathMeasure
    val extensionMeasure: PathMeasure

    init {
        fun at(x: Float, y: Float) = Offset(x / 312f * size.width, y / 176f * size.height)
        val points = listOf(
            16f to 146f, 40f to 132f, 60f to 138f, 84f to 118f, 104f to 124f,
            128f to 104f, 148f to 112f, 172f to 92f, 192f to 98f, 216f to 80f,
            236f to 86f, 260f to 72f, 280f to 66f,
        )
        val start = at(points.first().first, points.first().second)
        line.moveTo(start.x, start.y)
        points.drop(1).forEach { (x, y) -> line.lineTo(at(x, y).x, at(x, y).y) }

        extension.moveTo(at(280f, 66f).x, at(280f, 66f).y)
        extension.lineTo(at(292f, 46f).x, at(292f, 46f).y)
        extension.lineTo(at(302f, 30f).x, at(302f, 30f).y)

        lineMeasure = PathMeasure().apply { setPath(line, false) }
        extensionMeasure = PathMeasure().apply { setPath(extension, false) }
    }

    companion object {
        private var cached: TickerGeometry? = null

        fun forSize(size: Size): TickerGeometry =
            cached?.takeIf { it.size == size } ?: TickerGeometry(size).also { cached = it }
    }
}

/**
 * Ticker face: the day as a rising line. A market-style sparkline climbs with
 * day progress toward a dashed threshold — the daily goal — and overtime is
 * the break-out: a glowing premium-peach run above the line, because past the
 * threshold every minute trades higher. The head dot rides the line with a
 * breathing halo while the shift runs.
 */
internal fun DrawScope.drawTickerFace(
    progress: Float,
    overtime: Boolean,
    overtimeProgress: Float,
    pulse: Float,
    running: Boolean,
    foreground: Color,
) {
    if (layoutDirection == LayoutDirection.Rtl) {
        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawTickerLine(progress, overtime, overtimeProgress, pulse, running, foreground)
        }
    } else {
        drawTickerLine(progress, overtime, overtimeProgress, pulse, running, foreground)
    }
}

private fun DrawScope.drawTickerLine(
    progress: Float,
    overtime: Boolean,
    overtimeProgress: Float,
    pulse: Float,
    running: Boolean,
    foreground: Color,
) {
    fun at(x: Float, y: Float) = Offset(x / 312f * size.width, y / 176f * size.height)

    val geometry = TickerGeometry.forSize(size)
    val lineMeasure = geometry.lineMeasure
    val extMeasure = geometry.extensionMeasure

    fun strokeAlong(measure: PathMeasure, fraction: Float, color: Color, width: Float) {
        val segment = Path()
        measure.getSegment(0f, measure.length * fraction.coerceIn(0f, 1f), segment, true)
        drawPath(segment, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    drawLine(
        foreground.copy(alpha = .15f),
        at(16f, 152f),
        at(296f, 152f),
        1.5.dp.toPx(),
        StrokeCap.Round,
    )
    // The threshold the day is climbing toward; the goal, not a ceiling.
    val thresholdY = at(0f, 64f).y
    drawLine(
        if (overtime) AuroraPeach.copy(alpha = .5f) else foreground.copy(alpha = .3f),
        Offset(at(16f, 0f).x, thresholdY),
        Offset(at(296f, 0f).x, thresholdY),
        1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
    )

    // A wide low-alpha pass under the line is the glow; cheap and steady. The
    // line itself strokes in the deep gold — plain gold is a dark-plate
    // pigment and washes out on this face's light surface.
    strokeAlong(lineMeasure, progress, PaydayGold.copy(alpha = .25f), 7.dp.toPx())
    strokeAlong(lineMeasure, progress, PaydayGoldDeep, 3.dp.toPx())
    if (overtime && overtimeProgress > 0f) {
        strokeAlong(extMeasure, overtimeProgress, AuroraPeach.copy(alpha = .25f), 8.dp.toPx())
        strokeAlong(extMeasure, overtimeProgress, AuroraPeach, 3.dp.toPx())
    }

    val onExtension = overtime && overtimeProgress > 0f
    val head = if (onExtension) {
        extMeasure.getPosition(extMeasure.length * overtimeProgress.coerceIn(0f, 1f))
    } else {
        lineMeasure.getPosition(lineMeasure.length * progress.coerceIn(0f, 1f))
    }
    val headColor = if (onExtension) AuroraPeach else PaydayGoldDeep
    drawCircle(headColor, 4.dp.toPx(), head)
    if (running) {
        drawCircle(
            headColor.copy(alpha = (1f - pulse) * .35f),
            4.dp.toPx() + pulse * 6.dp.toPx(),
            head,
            style = Stroke(2.dp.toPx()),
        )
    }
}
