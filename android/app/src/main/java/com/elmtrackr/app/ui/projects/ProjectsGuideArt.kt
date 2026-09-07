package com.elmtrackr.app.ui.projects

import androidx.compose.ui.geometry.CornerRadius as GeometryCornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPeachDeep
import com.elmtrackr.app.ui.theme.AuroraPlum
import kotlin.math.cos
import kotlin.math.sin

/**
 * The four drawings behind [ProjectsGuide], and nothing else.
 *
 * Split from the screen rather than kept beside it because that is what
 * `DesignSystemBudgetTest` asks for: these are illustration geometry — a bar is
 * 12dp tall because that is how thick the bar looks, not because a layout token
 * says so — and a screen carrying them in the same file would hide real spacing
 * drift behind the exemption they need.
 *
 * Each takes the guide's shared 0→1 demo clock and draws that instant. No state,
 * no animation of their own: the clock is held at 1 when reduce-motion is on, so
 * passing 1 here is the finished picture and these functions never need to know
 * which case they are in.
 */

/** Progress of a sub-scene running between [from] and [to] on the demo clock. */
internal fun stage(t: Float, from: Float, to: Float): Float =
    ((t - from) / (to - from)).coerceIn(0f, 1f)

internal fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x)

/**
 * Page 1 — a project form filling itself in.
 *
 * Name and client first, then the agreed amount, then the tax stacked on top of
 * it, then the rule and the client total underneath. Drawn as a stack rather
 * than three separate figures because the point of the page is that they are one
 * sum: the total is literally the two bars above it laid end to end.
 */
internal fun DrawScope.drawSetupDemo(t: Float) {
    val cardW = size.width * 0.88f
    val left = (size.width - cardW) / 2f
    val appear = easeOut(stage(t, 0f, 0.12f))

    drawRoundRect(
        AuroraIndigo.copy(alpha = 0.06f * appear),
        Offset(left, 0f),
        Size(cardW, size.height),
        GeometryCornerRadius(14.dp.toPx()),
    )
    drawRoundRect(
        AuroraIndigo.copy(alpha = 0.22f * appear),
        Offset(left, 0f),
        Size(cardW, size.height),
        GeometryCornerRadius(14.dp.toPx()),
        style = Stroke(1.dp.toPx()),
    )

    val inset = left + 16.dp.toPx()
    val innerW = cardW - 32.dp.toPx()
    fun bar(y: Float, w: Float, h: Float, color: Color) {
        if (w <= 0.5f) return
        drawRoundRect(color, Offset(inset, y), Size(w, h), GeometryCornerRadius(h / 2f))
    }

    // Who it is for.
    bar(16.dp.toPx(), innerW * 0.52f * appear, 7.dp.toPx(), AuroraIndigo.copy(alpha = 0.85f))
    bar(30.dp.toPx(), innerW * 0.34f * appear, 5.dp.toPx(), AuroraIndigo.copy(alpha = 0.28f))

    // The agreed fee, the tax on top of it, the total below the rule. Each waits
    // for the one before, so the order the user types in is the order they see.
    val feeT = easeOut(stage(t, 0.18f, 0.42f))
    val taxT = easeOut(stage(t, 0.44f, 0.62f))
    val ruleT = stage(t, 0.64f, 0.72f)
    val totalT = easeOut(stage(t, 0.72f, 0.94f))

    bar(56.dp.toPx(), innerW * 0.60f * feeT, 12.dp.toPx(), AuroraIndigo)
    bar(76.dp.toPx(), innerW * 0.18f * taxT, 12.dp.toPx(), AuroraPlum)
    if (ruleT > 0f) {
        drawLine(
            AuroraIndigo.copy(alpha = 0.22f * ruleT),
            Offset(inset, 98.dp.toPx()),
            Offset(inset + innerW * ruleT, 98.dp.toPx()),
            1.dp.toPx(),
        )
    }
    // 0.60 + 0.18 = 0.78 — the total bar is exactly the two above it end to end.
    bar(108.dp.toPx(), innerW * 0.78f * totalT, 14.dp.toPx(), AuroraAqua)
}

/**
 * Page 2 — clocking in against a project.
 *
 * The mode switch is the whole lesson: project time is the ordinary clock-in
 * with the left segment swapped for the right one, not a second timer somewhere
 * else. So the pill slides first, a project chip lights up, and only then does
 * the clock run and pour its hours into the project.
 */
internal fun DrawScope.drawTrackDemo(t: Float) {
    val trackW = size.width * 0.52f
    val trackH = 22.dp.toPx()
    val trackLeft = 0f

    // The segmented pill, sliding from Hourly work to Project time.
    drawRoundRect(
        AuroraIndigo.copy(alpha = 0.10f),
        Offset(trackLeft, 0f),
        Size(trackW, trackH),
        GeometryCornerRadius(trackH / 2f),
    )
    val slide = easeOut(stage(t, 0.05f, 0.22f))
    val segW = trackW / 2f - 3.dp.toPx()
    drawRoundRect(
        AuroraIndigo,
        Offset(trackLeft + 3.dp.toPx() + (trackW / 2f - 3.dp.toPx()) * slide, 3.dp.toPx()),
        Size(segW, trackH - 6.dp.toPx()),
        GeometryCornerRadius((trackH - 6.dp.toPx()) / 2f),
    )

    // The project chip the user then picks.
    val chipT = easeOut(stage(t, 0.24f, 0.36f))
    val chipY = trackH + 12.dp.toPx()
    drawRoundRect(
        AuroraPlum.copy(alpha = 0.14f + 0.20f * chipT),
        Offset(trackLeft, chipY),
        Size(trackW * 0.62f, 18.dp.toPx()),
        GeometryCornerRadius(9.dp.toPx()),
    )
    drawRoundRect(
        AuroraPlum.copy(alpha = chipT),
        Offset(trackLeft, chipY),
        Size(trackW * 0.62f, 18.dp.toPx()),
        GeometryCornerRadius(9.dp.toPx()),
        style = Stroke(1.5.dp.toPx()),
    )

    // The clock-in tap, then the shift running.
    val clockCenter = Offset(size.width * 0.20f, size.height * 0.72f)
    val radius = size.height * 0.20f
    val tap = stage(t, 0.38f, 0.50f)
    if (tap > 0f && tap < 1f) {
        drawCircle(
            AuroraIndigo.copy(alpha = (1f - tap) * 0.45f),
            radius * (0.7f + tap * 1.1f),
            clockCenter,
            style = Stroke(2.dp.toPx()),
        )
    }
    drawCircle(AuroraIndigo.copy(alpha = 0.15f), radius, clockCenter, style = Stroke(3.dp.toPx()))
    val sweep = easeOut(stage(t, 0.46f, 0.88f))
    if (sweep > 0f) {
        drawArc(
            AuroraIndigo,
            startAngle = -90f,
            sweepAngle = 330f * sweep,
            useCenter = false,
            topLeft = Offset(clockCenter.x - radius, clockCenter.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
        )
        val angle = Math.toRadians(-90.0 + 330.0 * sweep)
        drawCircle(
            AuroraIndigo,
            3.dp.toPx(),
            clockCenter + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius),
        )
    }

    // Hours travelling from the shift into the project's hour bank.
    val laneY = clockCenter.y
    val laneStart = clockCenter.x + radius + 8.dp.toPx()
    val laneEnd = size.width * 0.68f
    drawLine(
        AuroraIndigo.copy(alpha = 0.2f),
        Offset(laneStart, laneY),
        Offset(laneEnd, laneY),
        1.5.dp.toPx(),
        StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
    )
    if (t > 0.5f) {
        repeat(3) { k ->
            val flow = (stage(t, 0.5f, 0.95f) * 2f + k * 0.33f) % 1f
            drawCircle(
                AuroraAqua.copy(alpha = 1f - flow * 0.55f),
                2.5.dp.toPx(),
                Offset(laneStart + (laneEnd - laneStart) * flow, laneY),
            )
        }
    }

    val bankLeft = size.width * 0.72f
    val bankW = size.width * 0.28f
    val bankTop = size.height * 0.46f
    val bankH = size.height * 0.54f
    drawRoundRect(
        AuroraPlum.copy(alpha = 0.10f),
        Offset(bankLeft, bankTop),
        Size(bankW, bankH),
        GeometryCornerRadius(10.dp.toPx()),
    )
    val fill = easeOut(stage(t, 0.55f, 0.95f))
    drawRoundRect(
        AuroraPlum.copy(alpha = 0.75f),
        Offset(bankLeft, bankTop + bankH * (1f - fill)),
        Size(bankW, bankH * fill),
        GeometryCornerRadius(10.dp.toPx()),
    )
}

/**
 * Page 3 — the effective rate, and why it moves.
 *
 * The fee bar never changes width. The hour ticks below it keep arriving, and
 * the rate marker slides *down* the scale as they do. That is the whole point
 * of the number and the one thing a static figure cannot show: a fixed price
 * pays less per hour the longer it takes.
 */
internal fun DrawScope.drawRateDemo(t: Float) {
    val left = 0f
    val innerW = size.width

    // The agreed fee: fixed, and drawn first so it reads as the constant.
    val feeT = easeOut(stage(t, 0f, 0.15f))
    drawRoundRect(
        AuroraIndigo,
        Offset(left, 6.dp.toPx()),
        Size(innerW * 0.72f * feeT, 14.dp.toPx()),
        GeometryCornerRadius(7.dp.toPx()),
    )

    // Hours accumulating underneath it, one tick at a time.
    val ticks = 12
    val tickTop = 44.dp.toPx()
    val tickW = 6.dp.toPx()
    val gap = (innerW * 0.72f - tickW) / (ticks - 1)
    val grown = stage(t, 0.18f, 0.78f) * ticks
    repeat(ticks) { index ->
        val on = (grown - index).coerceIn(0f, 1f)
        if (on <= 0f) return@repeat
        val h = 18.dp.toPx() * easeOut(on)
        drawRoundRect(
            AuroraAqua.copy(alpha = 0.35f + 0.5f * on),
            Offset(left + gap * index, tickTop + (18.dp.toPx() - h)),
            Size(tickW, h),
            GeometryCornerRadius(3.dp.toPx()),
        )
    }

    // The rate scale, and the marker falling along it as the hours mount.
    val scaleY = size.height - 24.dp.toPx()
    drawLine(
        AuroraIndigo.copy(alpha = 0.18f),
        Offset(left, scaleY),
        Offset(left + innerW, scaleY),
        3.dp.toPx(),
        StrokeCap.Round,
    )
    val fall = easeOut(stage(t, 0.2f, 0.85f))
    // Right (high rate, few hours) to left (low rate, many hours). Never all the
    // way to zero: the rate falls, it does not vanish.
    val startX = left + innerW * 0.88f
    val markerX = startX - innerW * 0.62f * fall
    // Trailed from where the marker started, so the distance it has travelled is
    // the drawing. Trailing to a fixed end point instead left the line zero-length
    // exactly when the demo finished — which, with reduce-motion on, is the only
    // frame anyone sees.
    if (markerX < startX) {
        drawLine(
            AuroraPlum,
            Offset(startX, scaleY),
            Offset(markerX, scaleY),
            3.dp.toPx(),
            StrokeCap.Round,
        )
    }
    drawCircle(AuroraPlum, 7.dp.toPx(), Offset(markerX, scaleY))
    drawCircle(AuroraPlum.copy(alpha = 0.22f), 13.dp.toPx(), Offset(markerX, scaleY))
}

/**
 * Page 4 — billing, payments and what falls out of them.
 *
 * The billed figure locks first and then stays put while the payments arrive:
 * that separation is why a later edit to the project's price leaves an issued
 * invoice alone. The outstanding bar shrinks with each payment, and turns peach
 * rather than empty at the end so the overdue state has somewhere to be shown.
 */
internal fun DrawScope.drawBillingDemo(t: Float) {
    val innerW = size.width
    val lock = easeOut(stage(t, 0f, 0.18f))

    // The billed snapshot. Outlined, not filled: it is a record, not a balance.
    drawRoundRect(
        AuroraIndigo.copy(alpha = 0.08f * lock),
        Offset(0f, 0f),
        Size(innerW, 40.dp.toPx()),
        GeometryCornerRadius(10.dp.toPx()),
    )
    drawRoundRect(
        AuroraIndigo.copy(alpha = 0.35f * lock),
        Offset(0f, 0f),
        Size(innerW, 40.dp.toPx()),
        GeometryCornerRadius(10.dp.toPx()),
        style = Stroke(1.5.dp.toPx()),
    )
    drawRoundRect(
        AuroraIndigo.copy(alpha = 0.8f),
        Offset(12.dp.toPx(), 15.dp.toPx()),
        Size((innerW - 24.dp.toPx()) * 0.45f * lock, 10.dp.toPx()),
        GeometryCornerRadius(5.dp.toPx()),
    )

    // Two payments landing, each taking a bite out of what is outstanding.
    val payOne = easeOut(stage(t, 0.24f, 0.44f))
    val payTwo = easeOut(stage(t, 0.52f, 0.72f))
    val coinY = 60.dp.toPx()
    listOf(0.22f to payOne, 0.44f to payTwo).forEach { (x, p) ->
        if (p <= 0f) return@forEach
        drawCircle(
            AuroraAqua.copy(alpha = p),
            8.dp.toPx() * p,
            Offset(innerW * x, coinY),
        )
    }

    // Outstanding: full, then two steps down, then flagged late.
    val barY = size.height - 30.dp.toPx()
    val barH = 14.dp.toPx()
    drawRoundRect(
        AuroraIndigo.copy(alpha = 0.10f),
        Offset(0f, barY),
        Size(innerW, barH),
        GeometryCornerRadius(barH / 2f),
    )
    val remaining = 1f - 0.35f * payOne - 0.35f * payTwo
    val overdue = stage(t, 0.80f, 0.92f)
    val barColor = if (overdue > 0f) {
        AuroraPeachDeep.copy(alpha = 0.45f + 0.55f * overdue)
    } else {
        AuroraIndigo.copy(alpha = 0.85f)
    }
    drawRoundRect(
        barColor,
        Offset(0f, barY),
        Size(innerW * remaining * lock, barH),
        GeometryCornerRadius(barH / 2f),
    )
    // The due date passing, marked on the bar it applies to.
    if (overdue > 0f) {
        val markX = innerW * remaining * lock
        drawLine(
            AuroraPeachDeep,
            Offset(markX, barY - 8.dp.toPx()),
            Offset(markX, barY + barH + 8.dp.toPx()),
            2.dp.toPx(),
            StrokeCap.Round,
        )
    }
}

