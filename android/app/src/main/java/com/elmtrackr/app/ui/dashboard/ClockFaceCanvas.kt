package com.elmtrackr.app.ui.dashboard

import androidx.compose.ui.geometry.CornerRadius as GeometryCornerRadius
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPeach
import com.elmtrackr.app.ui.theme.AuroraPlum

/**
 * The clock faces as drawings, with nothing else attached.
 *
 * Until this file existed the faces lived inline in the dashboard's clock card,
 * and the store previewed them with a second, hand-sketched set of drawings —
 * pixel literals tuned for a 68dp tile that fell apart at any other size and
 * never quite matched the face being sold. There is now one renderer. The
 * dashboard calls it at the card's size; the store calls it through a scaled
 * canvas, so a preview is the real face at a smaller scale, pulse and all.
 *
 * Geometry follows the design reference's 312×176 canvas: x positions stretch
 * with the width, everything else is in dp. Every face renders a complete still
 * at `pulse = 0` — idle and reduce-motion are the same frame.
 */

/** What a face is asked to show: the shift as the face reads it. */
internal data class ClockFaceScene(
    /** Running-gated progress through the day goal, 0..1. The older faces read this. */
    val progress: Float,
    /** Whole-day progress, 0..1, meaningful while clocked out. The v1.2+ faces read this. */
    val dayProgress: Float,
    val dayOvertime: Boolean,
    /** How far into the two-hour overtime extension, 0..1. */
    val overtimeExtension: Float,
    val running: Boolean,
    /** Hours worked today, 0..8, for Sprout's growth. */
    val growthHours: Float,
    /** Vinyl's tonearm position — the day progress after the needle-drop ease. */
    val vinylProgress: Float,
    /** Vinyl's disc rotation, in degrees. */
    val vinylSpinDegrees: Float,
    /**
     * The figures the [drawsOwnReading] faces print, or null for every other face.
     *
     * Nullable rather than defaulted so a caller that renders a Stats-for-nerds face
     * without supplying figures fails visibly (the face draws its frame and no numbers)
     * instead of quietly printing a plausible-looking zero. Every face that does not
     * read it ignores it entirely.
     */
    val telemetry: ClockFaceTelemetry? = null,
)

/** The colours a face is drawn with. */
internal class ClockFacePalette(
    val accent: Color,
    val foreground: Color,
    /** The quiet track behind progress — rings, rails, vessels. */
    val track: Color,
    /** The plate the face sits on; Metro and Summit paint their stations with it. */
    val plate: Color,
    /**
     * How the Stats-for-nerds faces lay out their text, or null for the rest.
     *
     * Nullable because a [TextMeasurer] can only be obtained in composition
     * (`rememberTextMeasurer()`), and the twenty-four faces that draw no text should not
     * have to acquire one. A [drawsOwnReading] face with no measurer draws its frame and
     * omits its figures rather than crashing — a preview that cannot measure text is a
     * visual gap, not a reason to take the screen down.
     */
    val textMeasurer: TextMeasurer? = null,
)

// The plates of the faces that bring their own darkness, and Retro's amber.
private val BoldPlate = Color(0xff222038)
private val NightPlate = Color(0xff080b25)
private val RetroPlate = Color(0xff2b2418)
private val VinylPlate = Color(0xff181530)
private val RetroAmber = Color(0xffffc857)

// Stats-for-nerds plates. Readout is a terminal; Gauge borrows the dark
// surface so the dial reads as an instrument rather than a card.
private val TerminalPlate = Color(0xff10141f)
private val GaugePlate = Color(0xff151d2e)

/** The faces whose plate is dark whatever the theme does. */
internal fun SupportedClockStyle.hasDarkPlate(): Boolean = when (this) {
    SupportedClockStyle.BOLD, SupportedClockStyle.NIGHT, SupportedClockStyle.RETRO,
    SupportedClockStyle.VINYL, SupportedClockStyle.METER,
    SupportedClockStyle.READOUT, SupportedClockStyle.GAUGE -> true
    else -> false
}

/**
 * The faces that print their own numerals, so the composed centre readout is
 * suppressed for them.
 *
 * Every other face is a drawing with the elapsed time laid over it by the
 * dashboard. These four *are* readouts — Readout prints four aligned rows,
 * Sparkline a headline figure, Gauge a centred time inside the dial, Matrix a
 * cell count and a footer. Compositing the shared display on top would print
 * the elapsed time twice, in two type scales, overlapping.
 *
 * A property of the face rather than a branch in the dashboard, so a new face
 * declares its own answer and the store's preview gets the same treatment for
 * free.
 */
internal fun SupportedClockStyle.drawsOwnReading(): Boolean = when (this) {
    SupportedClockStyle.READOUT, SupportedClockStyle.SPARKLINE,
    SupportedClockStyle.GAUGE, SupportedClockStyle.MATRIX -> true
    else -> false
}

/**
 * The plate colour for [style]; [surface] for every face that borrows the
 * theme's. Shared with the store's preview: a face sold on a navy plate has to
 * be shown on one.
 */
internal fun clockFacePlate(style: SupportedClockStyle, surface: Color): Color = when (style) {
    SupportedClockStyle.BOLD -> BoldPlate
    SupportedClockStyle.NIGHT -> NightPlate
    SupportedClockStyle.RETRO -> RetroPlate
    SupportedClockStyle.VINYL -> VinylPlate
    SupportedClockStyle.METER -> PaydayHousing
    SupportedClockStyle.READOUT -> TerminalPlate
    SupportedClockStyle.GAUGE -> GaugePlate
    else -> surface
}

/** The Aurora face's plate — the brand gradient rather than a colour. */
internal fun auroraFacePlate(): Brush = Brush.linearGradient(
    colorStops = arrayOf(0f to AuroraIndigo, 0.42f to AuroraPlum, 1f to AuroraAqua),
)

/**
 * The accent [style] draws its progress in. Overtime wins: every face flips to
 * premium peach once the day goal is passed, the one signal they all share.
 */
internal fun clockFaceAccent(style: SupportedClockStyle, overtime: Boolean): Color = when {
    overtime -> AuroraPeach
    style == SupportedClockStyle.RETRO -> RetroAmber
    style == SupportedClockStyle.NIGHT -> AuroraAqua
    style == SupportedClockStyle.TIDE -> AuroraAqua
    style == SupportedClockStyle.VINYL -> AuroraAqua
    style == SupportedClockStyle.SPROUT -> SproutLeafDeep
    // Gold on Meter's dark plate; the deeper gold everywhere the accent sits
    // on a light surface.
    style == SupportedClockStyle.METER -> PaydayGold
    style == SupportedClockStyle.STACKS -> PaydayGoldDeep
    style == SupportedClockStyle.JAR -> PaydayGoldDeep
    style == SupportedClockStyle.TICKER -> PaydayGoldDeep
    // Aqua on the terminal plate, peach on the gauge — the gauge's redline is
    // peach too, so the needle and the overtime band share one ink.
    style == SupportedClockStyle.READOUT -> AuroraAqua
    style == SupportedClockStyle.GAUGE -> AuroraPeach
    else -> AuroraIndigo
}

/**
 * Draws [style] into this scope, which is expected to be the 176dp face box.
 *
 * [pulse] is the shared 1.8s phase, 0..1 linear. The readout — the elapsed
 * time, Retro's flip board — is not drawn here: on the dashboard it is live
 * text composed over the canvas, and the store draws its own sample.
 */
internal fun DrawScope.drawClockFace(
    style: SupportedClockStyle,
    scene: ClockFaceScene,
    palette: ClockFacePalette,
    pulse: Float,
) {
    val progress = scene.progress
    val dayProgress = scene.dayProgress
    val dayOvertime = scene.dayOvertime
    val overtimeExtension = scene.overtimeExtension
    val running = scene.running
    val growthHours = scene.growthHours
    val vinylProgress = scene.vinylProgress
    val vinylSpin = scene.vinylSpinDegrees
    val accent = palette.accent
    val foreground = palette.foreground
    val faceTrack = palette.track
    val plate = palette.plate
    val center = Offset(size.width / 2f, size.height / 2f)
    when (style) {
        SupportedClockStyle.FOCUS -> {
            val y = size.height - 18.dp.toPx()
            drawRoundRect(faceTrack, Offset(0f, y), Size(size.width, 8.dp.toPx()), GeometryCornerRadius(8.dp.toPx()))
            drawRoundRect(accent, Offset(0f, y), Size(size.width * progress, 8.dp.toPx()), GeometryCornerRadius(8.dp.toPx()))
        }
        SupportedClockStyle.BOLD -> repeat(4) { index ->
            val x = size.width * (.12f + index * .26f)
            drawLine(accent.copy(alpha = .12f + index * .04f), Offset(x - 45f, 0f), Offset(x + 45f, size.height), 18f)
        }
        SupportedClockStyle.NIGHT -> {
            repeat(28) { index ->
                val x = ((index * 73) % 101) / 100f * size.width
                val y = ((index * 47) % 97) / 100f * size.height
                drawCircle(Color.White.copy(alpha = if (index % 3 == 0) .25f + pulse * .65f else .3f), 1.2.dp.toPx() + index % 2, Offset(x, y))
            }
            if (running) drawCircle(accent.copy(alpha = .08f + pulse * .08f), 72.dp.toPx(), center)
        }
        SupportedClockStyle.RETRO -> {
            // Just the amber grid: the split-flap board — composed over
            // the canvas on the dashboard, drawn by the store's preview — is
            // the face.
            val gap = 13.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(accent.copy(alpha = .08f), Offset(x, 0f), Offset(x, size.height), 1f); x += gap }
            var y = 0f
            while (y < size.height) { drawLine(accent.copy(alpha = .08f), Offset(0f, y), Offset(size.width, y), 1f); y += gap }
        }
        SupportedClockStyle.PULSE -> repeat(3) { index ->
            val phase = (pulse + index / 3f) % 1f
            drawCircle(accent.copy(alpha = (1f - phase) * .32f), (32 + phase * 58).dp.toPx(), center, style = Stroke(2.dp.toPx()))
        }
        SupportedClockStyle.DIAL -> {
            val radius = 70.dp.toPx()
            repeat(60) { index ->
                val major = index % 5 == 0
                rotate(index * 6f, center) {
                    drawLine(if (major) foreground.copy(alpha = .65f) else foreground.copy(alpha = .2f), Offset(center.x, center.y - radius), Offset(center.x, center.y - radius + if (major) 10.dp.toPx() else 5.dp.toPx()), if (major) 2.dp.toPx() else 1.dp.toPx())
                }
            }
            rotate(progress * 360f, center) { drawLine(accent, center, Offset(center.x, center.y - radius + 14.dp.toPx()), 3.dp.toPx(), StrokeCap.Round) }
            drawCircle(accent, 7.dp.toPx(), center)
        }
        SupportedClockStyle.STRAND -> {
            val count = 20
            val lit = (progress * count).toInt()
            repeat(count) { index ->
                val x = (index + .5f) * size.width / count
                val color = if (index < lit) accent else foreground.copy(alpha = .16f + if (index == lit) pulse * .2f else 0f)
                drawLine(color, Offset(x, 10.dp.toPx()), Offset(x, size.height - 10.dp.toPx()), if (index == lit) 3.dp.toPx() else 1.5.dp.toPx(), StrokeCap.Round)
            }
        }
        SupportedClockStyle.PRISM -> {
            val top = Offset(center.x, 8.dp.toPx())
            val left = Offset(20.dp.toPx(), size.height - 10.dp.toPx())
            val right = Offset(size.width - 20.dp.toPx(), size.height - 10.dp.toPx())
            val triangle = Path().apply { moveTo(top.x, top.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close() }
            drawPath(triangle, accent.copy(alpha = .45f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            if (running) {
                val fillY = left.y - (left.y - top.y) * progress
                val half = (right.x - left.x) * (1f - progress) / 2f
                val fill = Path().apply { moveTo(center.x - half, fillY); lineTo(left.x, left.y); lineTo(right.x, right.y); lineTo(center.x + half, fillY); close() }
                drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = .2f), AuroraAqua.copy(alpha = .55f))))
            }
            drawCircle(accent.copy(alpha = .55f + pulse * .45f), 4.dp.toPx(), top)
            drawCircle(AuroraPlum.copy(alpha = .55f + pulse * .45f), 4.dp.toPx(), left)
            drawCircle(AuroraAqua.copy(alpha = .55f + pulse * .45f), 4.dp.toPx(), right)
        }
        SupportedClockStyle.SAND -> {
            val top = 10.dp.toPx()
            val bottom = size.height - 10.dp.toPx()
            val mid = size.height / 2f
            val bulbW = size.width * 0.44f
            val neck = size.width * 0.12f
            val glass = Path().apply {
                moveTo(center.x - bulbW / 2f, top)
                quadraticTo(center.x, top - 8.dp.toPx(), center.x + bulbW / 2f, top)
                lineTo(center.x + neck / 2f, mid - 2.dp.toPx())
                lineTo(center.x + bulbW / 2f, bottom)
                quadraticTo(center.x, bottom + 8.dp.toPx(), center.x - bulbW / 2f, bottom)
                lineTo(center.x - neck / 2f, mid + 2.dp.toPx())
                close()
            }
            drawPath(glass, faceTrack.copy(alpha = 0.55f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            if (running) {
                val topFillHeight = (mid - top - 10.dp.toPx()) * (1f - progress)
                if (topFillHeight > 2.dp.toPx()) {
                    val topSand = Path().apply {
                        moveTo(center.x - bulbW / 2f + 10.dp.toPx(), top + 6.dp.toPx())
                        lineTo(center.x + bulbW / 2f - 10.dp.toPx(), top + 6.dp.toPx())
                        lineTo(center.x + neck / 2f - 2.dp.toPx(), top + 6.dp.toPx() + topFillHeight)
                        lineTo(center.x - neck / 2f + 2.dp.toPx(), top + 6.dp.toPx() + topFillHeight)
                        close()
                    }
                    drawPath(topSand, Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.55f))))
                }
                val bottomFillHeight = (bottom - mid - 10.dp.toPx()) * progress
                if (bottomFillHeight > 2.dp.toPx()) {
                    val bottomSand = Path().apply {
                        moveTo(center.x - neck / 2f + 2.dp.toPx(), bottom - 6.dp.toPx() - bottomFillHeight)
                        lineTo(center.x + neck / 2f - 2.dp.toPx(), bottom - 6.dp.toPx() - bottomFillHeight)
                        lineTo(center.x + bulbW / 2f - 10.dp.toPx(), bottom - 6.dp.toPx())
                        lineTo(center.x - bulbW / 2f + 10.dp.toPx(), bottom - 6.dp.toPx())
                        close()
                    }
                    drawPath(bottomSand, Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), accent)))
                }
                repeat(3) { index ->
                    val phase = (pulse + index / 3f) % 1f
                    drawCircle(
                        accent.copy(alpha = 0.25f + phase * 0.45f),
                        2.dp.toPx(),
                        Offset(center.x + (index - 1) * 4.dp.toPx(), mid + (phase - 0.5f) * 10.dp.toPx()),
                    )
                }
            }
        }
        SupportedClockStyle.BLOCKS -> {
            val blockCount = 8
            val gap = 5.dp.toPx()
            val blockW = (size.width - gap * (blockCount - 1)) / blockCount
            val blockH = 30.dp.toPx()
            val baseY = size.height - blockH - 6.dp.toPx()
            val filled = (progress * blockCount).toInt()
            val partial = progress * blockCount - filled
            repeat(blockCount) { index ->
                val x = index * (blockW + gap)
                val isFilled = index < filled
                val isCurrent = index == filled && running
                val color = when {
                    isFilled -> accent
                    isCurrent -> accent.copy(alpha = 0.35f + pulse * 0.35f)
                    else -> foreground.copy(alpha = 0.12f)
                }
                val height = if (isCurrent) blockH * (0.5f + partial * 0.5f) else blockH
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, baseY + blockH - height),
                    size = Size(blockW, height),
                    cornerRadius = GeometryCornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
            }
        }
        SupportedClockStyle.ORBIT -> {
            val radius = 58.dp.toPx()
            drawCircle(
                faceTrack.copy(alpha = 0.65f),
                radius,
                center,
                style = Stroke(
                    1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f)),
                ),
            )
            if (running && progress > 0f) {
                drawArc(
                    color = accent.copy(alpha = 0.22f),
                    startAngle = -90f,
                    sweepAngle = progress * 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            val angle = Math.toRadians((progress * 360.0 - 90.0))
            val satX = center.x + kotlin.math.cos(angle).toFloat() * radius
            val satY = center.y + kotlin.math.sin(angle).toFloat() * radius
            if (running) {
                drawCircle(accent.copy(alpha = 0.12f + pulse * 0.12f), 16.dp.toPx(), Offset(satX, satY))
            }
            drawCircle(accent, 6.dp.toPx(), Offset(satX, satY))
            drawCircle(Color.White, 2.dp.toPx(), Offset(satX, satY))
        }
        SupportedClockStyle.TIDE -> {
            val radius = 74.dp.toPx()
            val vessel = Path().apply {
                addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
            }
            drawCircle(faceTrack.copy(alpha = .6f), radius, center, style = Stroke(2.dp.toPx()))
            // Idle keeps a calm pool at the bottom; while running the
            // waterline climbs with day-goal progress.
            val level = center.y + radius - (radius * 2f) * (.12f + progress * .76f)
            val wavePhase = pulse * 2f * Math.PI.toFloat()
            fun wave(phaseShift: Float, amplitude: Float): Path = Path().apply {
                moveTo(center.x - radius, level)
                var x = center.x - radius
                while (x <= center.x + radius) {
                    val y = level + kotlin.math.sin((x - center.x) / radius * 3.2f + wavePhase + phaseShift) * amplitude
                    lineTo(x, y)
                    x += 6f
                }
                lineTo(center.x + radius, center.y + radius)
                lineTo(center.x - radius, center.y + radius)
                close()
            }
            clipPath(vessel) {
                drawPath(wave(1.7f, 6.dp.toPx()), accent.copy(alpha = .25f))
                drawPath(
                    wave(0f, 4.dp.toPx()),
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = .55f), AuroraIndigo.copy(alpha = .8f)),
                        startY = level,
                        endY = center.y + radius,
                    ),
                )
                if (running) {
                    repeat(3) { index ->
                        val phase = (pulse + index / 3f) % 1f
                        val rise = (center.y + radius - level).coerceAtLeast(1f)
                        drawCircle(
                            Color.White.copy(alpha = (1f - phase) * .45f),
                            (2 + index % 2).dp.toPx(),
                            Offset(
                                center.x + (index - 1) * 24.dp.toPx(),
                                center.y + radius - 6.dp.toPx() - phase * (rise - 10.dp.toPx()).coerceAtLeast(0f),
                            ),
                        )
                    }
                }
            }
        }
        SupportedClockStyle.SPROUT -> drawSproutFace(
            growthHours = growthHours,
            pulse = pulse,
            running = running,
            foreground = foreground,
        )
        SupportedClockStyle.METRO -> drawMetroFace(
            progress = dayProgress,
            overtime = dayOvertime,
            overtimeProgress = overtimeExtension,
            pulse = pulse,
            running = running,
            foreground = foreground,
            surface = plate,
        )
        SupportedClockStyle.VINYL -> drawVinylFace(
            progress = vinylProgress,
            spinDegrees = vinylSpin,
            pulse = pulse,
            running = running,
            overtime = dayOvertime,
        )
        SupportedClockStyle.LUNA -> drawLunaFace(
            progress = dayProgress,
            pulse = pulse,
            running = running,
            overtime = dayOvertime,
        )
        SupportedClockStyle.SUMMIT -> drawSummitFace(
            progress = dayProgress,
            overtime = dayOvertime,
            overtimeProgress = overtimeExtension,
            pulse = pulse,
            running = running,
            foreground = foreground,
            surface = plate,
        )
        SupportedClockStyle.METER -> drawMeterFace(
            progress = dayProgress,
            overtime = dayOvertime,
            overtimeProgress = overtimeExtension,
            pulse = pulse,
            running = running,
            foreground = foreground,
        )
        SupportedClockStyle.STACKS -> drawStacksFace(
            progress = dayProgress,
            overtime = dayOvertime,
            overtimeProgress = overtimeExtension,
            pulse = pulse,
            running = running,
            foreground = foreground,
        )
        SupportedClockStyle.JAR -> drawJarFace(
            progress = dayProgress,
            overtime = dayOvertime,
            pulse = pulse,
            running = running,
            foreground = foreground,
        )
        SupportedClockStyle.TICKER -> drawTickerFace(
            progress = dayProgress,
            overtime = dayOvertime,
            overtimeProgress = overtimeExtension,
            pulse = pulse,
            running = running,
            foreground = foreground,
        )
        SupportedClockStyle.CLASSIC -> drawClassicRing(progress, accent, faceTrack)
        // The Stats-for-nerds four. Each needs figures and a measurer; without either
        // there is nothing to draw, and drawing a zeroed readout would be worse than
        // drawing none.
        SupportedClockStyle.READOUT -> withTelemetry(scene, palette) { telemetry, measurer ->
            drawReadoutFace(telemetry, pulse, running, accent, measurer)
        }
        SupportedClockStyle.SPARKLINE -> withTelemetry(scene, palette) { telemetry, measurer ->
            drawSparklineFace(telemetry, pulse, running, foreground, measurer)
        }
        SupportedClockStyle.GAUGE -> withTelemetry(scene, palette) { telemetry, measurer ->
            drawGaugeFace(telemetry, pulse, running, accent, measurer)
        }
        SupportedClockStyle.MATRIX -> withTelemetry(scene, palette) { telemetry, measurer ->
            drawMatrixFace(telemetry, pulse, running, foreground, accent, measurer)
        }
        else -> Unit
    }
}

/**
 * Classic's ring, as the dashboard card draws it: a 148dp track with the
 * progress arc over it, centred in the box so the readout can sit inside.
 */
private fun DrawScope.drawClassicRing(progress: Float, accent: Color, track: Color) {
    val strokeWidth = 10.dp.toPx()
    val diameter = 148.dp.toPx() - strokeWidth
    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
    val ringStroke = Stroke(strokeWidth, cap = StrokeCap.Round)
    drawArc(track, -90f, 360f, false, topLeft, Size(diameter, diameter), style = ringStroke)
    if (progress > 0f) {
        drawArc(accent, -90f, progress * 360f, false, topLeft, Size(diameter, diameter), style = ringStroke)
    }
}

internal val SproutLeafDeep = Color(0xFF2E9E6B)
private val SproutLeafLight = Color(0xFF43C98A)
private val SproutSoil = Color(0xFF7A5B44)
private val SproutBud = Color(0xFF7C4DD4)
private val SproutPetalA = Color(0xFF8B5CF6)
private val SproutPetalB = Color(0xFFB07CF8)
private val SproutCore = Color(0xFFFFC857)
private val SproutFirefly = Color(0xFFFFE08A)
private val SproutGlow = Color(0xFFFFB27D)

/**
 * Sprout face: the plant is the day. The stem rises with hours worked, one
 * true leaf unfurls per hour (1–6), a bud forms in hour 7, and the flower
 * blooms at eight. Growth tracks today's total, so the plant is worth a
 * glance even while clocked out; sway, fireflies, and the petal shimmer
 * only run during a shift.
 */
private fun DrawScope.drawSproutFace(
    growthHours: Float,
    pulse: Float,
    running: Boolean,
    foreground: Color,
) {
    val cx = size.width / 2f
    val groundY = size.height - 16.dp.toPx()

    fun smooth(raw: Float): Float {
        val t = raw.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    if (running) {
        val auraCenter = Offset(cx, groundY - 60.dp.toPx())
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(SproutLeafDeep.copy(alpha = 0.06f + pulse * 0.03f), Color.Transparent),
                center = auraCenter,
                radius = 80.dp.toPx(),
            ),
            radius = 80.dp.toPx(),
            center = auraCenter,
        )
    }

    drawLine(
        foreground.copy(alpha = 0.12f),
        Offset(size.width * 0.08f, groundY),
        Offset(size.width * 0.92f, groundY),
        2.dp.toPx(),
        StrokeCap.Round,
    )
    drawOval(
        SproutSoil.copy(alpha = 0.6f),
        topLeft = Offset(cx - 27.dp.toPx(), groundY - 5.dp.toPx()),
        size = Size(54.dp.toPx(), 10.dp.toPx()),
    )

    if (growthHours <= 0.05f) {
        // A seed, waiting for the first clock-in of the day.
        drawCircle(SproutLeafDeep, 4.dp.toPx(), Offset(cx, groundY - 4.dp.toPx()))
        return
    }

    val stemH = 18.dp.toPx() + 15.dp.toPx() * growthHours
    val sway = if (running) {
        kotlin.math.sin(pulse * 2f * Math.PI.toFloat()) * 2.5.dp.toPx() * (growthHours / 2f).coerceAtMost(1f)
    } else 0f
    val tipX = cx + sway
    val tipY = groundY - stemH
    val ctrlX = cx - sway * 0.8f
    val ctrlY = groundY - stemH * 0.5f

    fun onStem(f: Float): Offset {
        val inv = 1f - f
        return Offset(
            inv * inv * cx + 2f * inv * f * ctrlX + f * f * tipX,
            inv * inv * groundY + 2f * inv * f * ctrlY + f * f * tipY,
        )
    }

    val stem = Path().apply {
        moveTo(cx, groundY)
        quadraticTo(ctrlX, ctrlY, tipX, tipY)
    }
    drawPath(stem, SproutLeafDeep, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))

    fun drawLeaf(at: Offset, dir: Int, length: Float, alpha: Float) {
        if (length <= 0.5f) return
        val leafTip = Offset(at.x + dir * length * 0.88f, at.y - length * 0.7f)
        val blade = Path().apply {
            moveTo(at.x, at.y)
            quadraticTo(at.x + dir * length * 0.45f, at.y - length * 0.75f, leafTip.x, leafTip.y)
            quadraticTo(at.x + dir * length * 0.62f, at.y + length * 0.06f, at.x, at.y)
            close()
        }
        drawPath(blade, Brush.linearGradient(listOf(SproutLeafLight, SproutLeafDeep), start = at, end = leafTip), alpha = alpha)
        drawLine(SproutLeafDeep.copy(alpha = alpha * 0.35f), at, leafTip, 1.dp.toPx(), StrokeCap.Round)
    }

    // First-hour cotyledon pair, folding away once true leaves arrive.
    val cotyledon = smooth(growthHours) * (1f - smooth(growthHours - 1.2f))
    if (cotyledon > 0.01f && growthHours < 2.2f) {
        val at = onStem(0.97f)
        drawLeaf(at, -1, 9.dp.toPx() * cotyledon, 0.95f)
        drawLeaf(at, 1, 9.dp.toPx() * cotyledon, 0.95f)
    }

    // One true leaf per hour (hours 1-6), alternating sides bottom-up.
    repeat(6) { i ->
        val t = smooth(growthHours - (i + 1))
        if (t <= 0f) return@repeat
        val at = onStem(0.22f + i * 0.115f)
        val dir = if (i % 2 == 0) -1 else 1
        drawLeaf(at, dir, (24f - i * 1.6f).dp.toPx() * t, 0.95f)
    }

    // Hour 7 raises a bud; hour 8 opens it.
    val tBud = smooth((growthHours - 7f) * 2f)
    val tBloom = smooth((growthHours - 7.5f) * 2f)
    val tip = onStem(1f)
    if (tBloom > 0.01f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SproutGlow.copy(alpha = 0.25f * tBloom + if (running) pulse * 0.06f else 0f),
                    Color.Transparent,
                ),
                center = tip,
                radius = (16f + pulse * 3f).dp.toPx(),
            ),
            radius = 20.dp.toPx(),
            center = tip,
        )
        repeat(6) { k ->
            rotate(k * 60f + if (running) pulse * 6f else 0f, tip) {
                drawOval(
                    color = if (k % 2 == 0) SproutPetalA else SproutPetalB,
                    topLeft = Offset(tip.x - 3.2.dp.toPx() * tBloom, tip.y - 16.dp.toPx() * tBloom),
                    size = Size(6.4.dp.toPx() * tBloom, 16.dp.toPx() * tBloom),
                    alpha = 0.95f,
                )
            }
        }
        drawCircle(SproutCore, 4.2.dp.toPx() * tBloom + 2.dp.toPx(), tip)
    } else if (tBud > 0.01f) {
        drawCircle(SproutBud, 5.dp.toPx() * tBud, Offset(tip.x, tip.y - 2.dp.toPx() * tBud))
        drawLeaf(tip, -1, 7.dp.toPx() * tBud, 0.9f)
        drawLeaf(tip, 1, 7.dp.toPx() * tBud, 0.9f)
    }

    // Fireflies drifting up while on shift — the "alive" cue that pulls you back.
    if (running && growthHours >= 1.5f) {
        repeat(3) { k ->
            val phase = (pulse + k / 3f) % 1f
            val fx = cx + (k - 1) * 26.dp.toPx() + kotlin.math.sin(phase * 6.28f + k) * 6.dp.toPx()
            val fy = groundY - phase * (stemH + 20.dp.toPx())
            drawCircle(SproutFirefly.copy(alpha = (1f - phase) * 0.5f), 1.8.dp.toPx(), Offset(fx, fy))
        }
    }
}

/**
 * Runs [draw] only when the scene carries both figures and a measurer.
 *
 * Both are nullable for reasons the properties document, and every Stats-for-nerds face
 * needs both, so the check lives in one place instead of four.
 */
private inline fun DrawScope.withTelemetry(
    scene: ClockFaceScene,
    palette: ClockFacePalette,
    draw: DrawScope.(ClockFaceTelemetry, TextMeasurer) -> Unit,
) {
    val telemetry = scene.telemetry ?: return
    val measurer = palette.textMeasurer ?: return
    draw(telemetry, measurer)
}
