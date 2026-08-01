package com.elmtrackr.app.ui.dashboard

import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPeach
import com.elmtrackr.app.ui.theme.AuroraPeachDeep
import com.elmtrackr.app.ui.theme.AuroraPlum
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The v1.2 expressive faces: Metro, Vinyl and Luna. Each draws inside the
 * shared 176dp [ExpressiveClockCard] face box on the single 1800 ms pulse, and
 * reads whole-day progress (Sprout precedent) so the face is worth a glance
 * while clocked out. Geometry follows the design reference's 312×176 canvas:
 * x positions stretch with the card width, everything else is in dp.
 */

// ── Metro ─────────────────────────────────────────────────────────────────────

/**
 * Metro face: the day is a commute. Stations mark hours 1–8 along the line,
 * the double-ring terminus is the overtime threshold, and the train rides the
 * line as the day progresses. Overtime rolls onto a dashed peach night line.
 * Clocked out, the train parks where today left off.
 */
internal fun DrawScope.drawMetroFace(
    progress: Float,
    overtime: Boolean,
    overtimeProgress: Float,
    pulse: Float,
    running: Boolean,
    foreground: Color,
    surface: Color,
) {
    // The line reads start → terminus in the reading direction.
    if (layoutDirection == LayoutDirection.Rtl) {
        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawMetroLine(progress, overtime, overtimeProgress, pulse, running, foreground, surface)
        }
    } else {
        drawMetroLine(progress, overtime, overtimeProgress, pulse, running, foreground, surface)
    }
}

private fun DrawScope.drawMetroLine(
    progress: Float,
    overtime: Boolean,
    overtimeProgress: Float,
    pulse: Float,
    running: Boolean,
    foreground: Color,
    surface: Color,
) {
    fun at(x: Float, y: Float) = Offset(x / 312f * size.width, y / 176f * size.height)

    val line = Path().apply {
        moveTo(at(16f, 142f).x, at(16f, 142f).y)
        cubicTo(at(92f, 142f).x, at(92f, 142f).y, at(110f, 122f).x, at(110f, 122f).y, at(166f, 122f).x, at(166f, 122f).y)
        cubicTo(at(232f, 122f).x, at(232f, 122f).y, at(254f, 58f).x, at(254f, 58f).y, at(296f, 58f).x, at(296f, 58f).y)
    }
    val extension = Path().apply {
        moveTo(at(296f, 58f).x, at(296f, 58f).y)
        cubicTo(at(308f, 50f).x, at(308f, 50f).y, at(310f, 34f).x, at(310f, 34f).y, at(300f, 22f).x, at(300f, 22f).y)
    }
    val lineMeasure = PathMeasure().apply { setPath(line, false) }
    val extMeasure = PathMeasure().apply { setPath(extension, false) }

    val accent = if (overtime) AuroraPeachDeep else AuroraIndigo
    val lineWidth = 5.dp.toPx()

    fun strokeAlong(measure: PathMeasure, fraction: Float, color: Color, width: Float, dash: PathEffect? = null) {
        val segment = Path()
        measure.getSegment(0f, measure.length * fraction.coerceIn(0f, 1f), segment, true)
        drawPath(
            segment,
            color,
            style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash),
        )
    }

    // Track, overtime guide, then the ridden portions on top.
    strokeAlong(lineMeasure, 1f, foreground.copy(alpha = 0.10f), lineWidth)
    if (overtime) {
        strokeAlong(
            extMeasure, 1f, AuroraPeachDeep.copy(alpha = 0.45f), 3.dp.toPx(),
            PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 6.dp.toPx())),
        )
    }
    if (progress > 0.002f) strokeAlong(lineMeasure, progress, AuroraIndigo, lineWidth)
    if (overtime && overtimeProgress > 0.02f) {
        strokeAlong(extMeasure, overtimeProgress, AuroraPeachDeep, 3.dp.toPx())
    }

    // Stations at each hour; the terminus is a double ring at the OT threshold.
    repeat(9) { i ->
        val stop = lineMeasure.getPosition(lineMeasure.length * i / 8f)
        val passed = progress >= i / 8f - 0.001f
        if (i == 8) {
            drawCircle(
                if (passed) AuroraIndigo else foreground.copy(alpha = 0.25f),
                7.dp.toPx(), stop, style = Stroke(2.5.dp.toPx()),
            )
            drawCircle(if (passed) AuroraIndigo else foreground.copy(alpha = 0.25f), 2.6.dp.toPx(), stop)
        } else if (passed) {
            drawCircle(AuroraIndigo, 4.6.dp.toPx(), stop)
            drawCircle(surface, 2.dp.toPx(), stop)
        } else {
            drawCircle(surface, 4.6.dp.toPx(), stop)
            drawCircle(foreground.copy(alpha = 0.2f), 4.6.dp.toPx(), stop, style = Stroke(2.dp.toPx()))
        }
    }

    // The next station glows while the train approaches it.
    if (running && progress < 1f) {
        val next = (progress * 8f).toInt() + 1
        val halo = lineMeasure.getPosition(lineMeasure.length * (next.coerceAtMost(8) / 8f))
        drawCircle(
            AuroraIndigo.copy(alpha = (1f - pulse) * 0.4f),
            6.dp.toPx() + pulse * 8.dp.toPx(),
            halo,
            style = Stroke(2.dp.toPx()),
        )
    }

    // The train: parked at today's spot, bobbing gently while on shift.
    val measure = if (overtime) extMeasure else lineMeasure
    val distance = measure.length * (if (overtime) overtimeProgress.coerceAtLeast(0.02f) else progress)
    val position = measure.getPosition(distance)
    val tangent = measure.getTangent(distance)
    val bob = if (running) sin(pulse * 2f * Math.PI.toFloat()) * 1.2.dp.toPx() else 0f
    withTransform({
        translate(position.x, position.y + bob)
        rotate(Math.toDegrees(atan2(tangent.y, tangent.x).toDouble()).toFloat(), Offset.Zero)
    }) {
        drawRoundRect(
            accent,
            Offset(-12.dp.toPx(), -6.5.dp.toPx()),
            Size(24.dp.toPx(), 13.dp.toPx()),
            CornerRadius(6.5.dp.toPx()),
        )
        val glass = Color.White.copy(alpha = 0.9f)
        drawRoundRect(glass, Offset(-6.5.dp.toPx(), -2.5.dp.toPx()), Size(5.dp.toPx(), 5.dp.toPx()), CornerRadius(1.5.dp.toPx()))
        drawRoundRect(glass, Offset(0.5.dp.toPx(), -2.5.dp.toPx()), Size(5.dp.toPx(), 5.dp.toPx()), CornerRadius(1.5.dp.toPx()))
        drawCircle(glass, 1.4.dp.toPx(), Offset(9.4.dp.toPx(), 0f))
    }
}

// ── Vinyl ─────────────────────────────────────────────────────────────────────

private val VinylDisc = Color(0xFF211D3E)
private val VinylHole = Color(0xFF181530)
private val VinylPivotCap = Color(0xFF2A2650)
private val VinylHeadshell = Color(0xFFE8E6F5)

/**
 * Vinyl face: the shift on heavy rotation. The record spins while clocked in,
 * hour grooves light aqua as they are played, and the tonearm tracks inward —
 * outer edge at clock-in, run-out groove at the overtime threshold. With
 * reduced motion the spin freezes; the tonearm still shows progress.
 */
internal fun DrawScope.drawVinylFace(
    progress: Float,
    spinDegrees: Float,
    pulse: Float,
    running: Boolean,
    overtime: Boolean,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f + 4.dp.toPx()
    val disc = Offset(cx, cy)
    val radius = 76.dp.toPx()
    val needleRadius = (74f - progress * 44f).dp.toPx()

    drawCircle(VinylDisc, radius, disc)
    drawCircle(Color.White.copy(alpha = 0.08f), radius, disc, style = Stroke(1.5.dp.toPx()))

    // Fine grooves, with a slightly brighter band every third ring.
    repeat(12) { i ->
        drawCircle(
            Color.White.copy(alpha = 0.035f + if (i % 3 == 0) 0.03f else 0f),
            (32f + i * 3.5f).dp.toPx(), disc, style = Stroke(1.dp.toPx()),
        )
    }
    // Hour grooves light up once the needle has played past them.
    repeat(8) { i ->
        val grooveRadius = (74f - (i + 1) * 5.5f).dp.toPx()
        drawCircle(
            if (grooveRadius >= needleRadius - 0.5.dp.toPx()) AuroraAqua.copy(alpha = 0.4f)
            else Color.White.copy(alpha = 0.1f),
            grooveRadius, disc, style = Stroke(1.2.dp.toPx()),
        )
    }

    // Spinning sheen wedges ride the rotation.
    rotate(spinDegrees, disc) {
        val sheenRect = Offset(cx - radius + 2.dp.toPx(), cy - radius + 2.dp.toPx())
        val sheenSize = Size((radius - 2.dp.toPx()) * 2f, (radius - 2.dp.toPx()) * 2f)
        listOf(-15f, 165f).forEach { start ->
            drawArc(Color.White.copy(alpha = 0.05f), start, 30f, useCenter = true, topLeft = sheenRect, size = sheenSize)
        }
    }
    drawCircle(if (overtime) AuroraPeachDeep else AuroraIndigo, 26.dp.toPx(), disc)
    drawCircle(Color.White.copy(alpha = 0.2f), 26.dp.toPx(), disc, style = Stroke(1.dp.toPx()))
    rotate(spinDegrees, disc) {
        drawCircle(Color.White.copy(alpha = 0.85f), 2.2.dp.toPx(), Offset(cx + 18.dp.toPx(), cy))
    }
    drawCircle(VinylHole, 3.2.dp.toPx(), disc)

    // Tonearm from the top-end pivot down to the needle on the groove.
    val pivot = Offset(size.width - 20.dp.toPx(), 22.dp.toPx())
    val needleAngle = -Math.PI.toFloat() / 3f
    val vibration = if (running) sin(pulse * 40f) * 0.4.dp.toPx() else 0f
    val needle = Offset(
        cx + cos(needleAngle) * needleRadius,
        cy + sin(needleAngle) * needleRadius + vibration,
    )
    drawLine(Color.White.copy(alpha = 0.35f), pivot, pivot + Offset(12.dp.toPx(), -4.dp.toPx()), 5.dp.toPx(), StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.6f), pivot, needle, 3.dp.toPx(), StrokeCap.Round)
    drawCircle(VinylPivotCap, 9.dp.toPx(), pivot)
    drawCircle(Color.White.copy(alpha = 0.15f), 9.dp.toPx(), pivot, style = Stroke(1.5.dp.toPx()))
    withTransform({
        translate(needle.x, needle.y)
        rotate(Math.toDegrees(atan2(needle.y - pivot.y, needle.x - pivot.x).toDouble()).toFloat(), Offset.Zero)
    }) {
        drawRoundRect(
            VinylHeadshell,
            Offset(-8.dp.toPx(), -4.dp.toPx()),
            Size(14.dp.toPx(), 8.dp.toPx()),
            CornerRadius(3.dp.toPx()),
        )
    }
    if (running) {
        drawCircle(
            AuroraAqua.copy(alpha = (1f - pulse) * 0.4f),
            5.dp.toPx() + pulse * 4.dp.toPx(),
            needle,
            style = Stroke(1.5.dp.toPx()),
        )
    }
}

// ── Luna ──────────────────────────────────────────────────────────────────────

private val LunaSurface = Color(0xFFF1F0FB)
private val LunaEdge = Color(0xFF181530)
private val LunaLitStart = Color(0xFFE3DFFF)
private val LunaLitEnd = Color(0xFFC4B8FA)

/**
 * Luna face: clock in at new moon, full at eight. The terminator sweeps as the
 * day progresses, the halo brightens, and the overtime threshold lands a
 * full-moon ring with a sparkle. Overtime turns the corona peach — a harvest
 * moon. Idle shows today's phase.
 */
internal fun DrawScope.drawLunaFace(
    progress: Float,
    pulse: Float,
    running: Boolean,
    overtime: Boolean,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val moon = Offset(cx, cy)
    val radius = 60.dp.toPx()
    val accent = if (overtime) AuroraPeach else AuroraIndigo

    // Halo brightening with the day; breathing gently while on shift.
    val haloAlpha = 0.06f + 0.10f * progress + if (running) pulse * 0.04f else 0f
    val haloRadius = radius + 30.dp.toPx()
    drawCircle(
        Brush.radialGradient(
            0.33f to accent.copy(alpha = haloAlpha),
            1f to Color.Transparent,
            center = moon,
            radius = haloRadius,
        ),
        haloRadius, moon,
    )

    drawCircle(LunaSurface, radius, moon)
    drawCircle(LunaEdge.copy(alpha = 0.08f), radius, moon, style = Stroke(1.5.dp.toPx()))

    // Waxing phase: the lit side is the right semicircle closed by the
    // terminator ellipse, rx = r·cos(π·progress).
    val k = cos(Math.PI.toFloat() * progress)
    val terminatorRx = radius * abs(k)
    if (progress > 0.004f) {
        val lit = Path().apply {
            arcTo(Rect(cx - radius, cy - radius, cx + radius, cy + radius), -90f, 180f, false)
            arcTo(Rect(cx - terminatorRx, cy - radius, cx + terminatorRx, cy + radius), 90f, if (k > 0f) -180f else 180f, false)
            close()
        }
        drawPath(
            lit,
            Brush.linearGradient(
                listOf(LunaLitStart, LunaLitEnd),
                start = Offset(cx - radius, cy),
                end = Offset(cx + radius, cy),
            ),
        )
        clipPath(lit) {
            val crater = AuroraIndigo.copy(alpha = 0.10f)
            drawCircle(crater, 7.dp.toPx(), Offset(cx + 20.dp.toPx(), cy - 16.dp.toPx()))
            drawCircle(crater, 5.dp.toPx(), Offset(cx + 4.dp.toPx(), cy + 18.dp.toPx()))
            drawCircle(crater, 3.5.dp.toPx(), Offset(cx + 28.dp.toPx(), cy + 6.dp.toPx()))
        }
        if (progress < 0.996f) {
            val terminator = Path().apply {
                arcTo(Rect(cx - terminatorRx, cy - radius, cx + terminatorRx, cy + radius), 90f, if (k > 0f) -180f else 180f, true)
            }
            drawPath(
                terminator,
                if (overtime) AuroraPeachDeep.copy(alpha = 0.4f) else AuroraIndigo.copy(alpha = 0.3f),
                style = Stroke(1.5.dp.toPx()),
            )
        }
    }

    // Full moon at the overtime threshold: a ring and a landed sparkle.
    if (progress >= 0.999f) {
        drawCircle(
            if (overtime) AuroraPeachDeep.copy(alpha = 0.45f) else AuroraIndigo.copy(alpha = 0.4f),
            radius + 8.dp.toPx(), moon, style = Stroke(1.5.dp.toPx()),
        )
        val star = Offset(cx + 52.dp.toPx(), cy - 52.dp.toPx())
        val arm = 5.dp.toPx() + if (running) pulse * 2.5.dp.toPx() else 0f
        drawLine(accent, star - Offset(arm, 0f), star + Offset(arm, 0f), 1.6.dp.toPx(), StrokeCap.Round)
        drawLine(accent, star - Offset(0f, arm), star + Offset(0f, arm), 1.6.dp.toPx(), StrokeCap.Round)
    }

    // Two ambient stars twinkle on the shared pulse while clocked in.
    listOf(
        Triple(Offset(cx - 88.dp.toPx(), cy - 40.dp.toPx()), 4.dp.toPx(), AuroraPlum to 0f),
        Triple(Offset(cx + 92.dp.toPx(), cy + 32.dp.toPx()), 3.dp.toPx(), AuroraAqua to 2.1f),
    ).forEach { (center, arm, colorPhase) ->
        val (color, phase) = colorPhase
        val alpha = if (running) {
            0.25f + 0.4f * abs(sin(pulse * 2f * Math.PI.toFloat() + phase))
        } else 0.3f
        drawLine(color.copy(alpha = alpha), center - Offset(arm, 0f), center + Offset(arm, 0f), 1.4.dp.toPx(), StrokeCap.Round)
        drawLine(color.copy(alpha = alpha), center - Offset(0f, arm), center + Offset(0f, arm), 1.4.dp.toPx(), StrokeCap.Round)
    }
}
