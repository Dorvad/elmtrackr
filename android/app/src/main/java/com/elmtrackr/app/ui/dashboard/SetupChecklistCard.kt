package com.elmtrackr.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.setup.SetupStep
import com.elmtrackr.app.ui.design.AuroraHaptics
import com.elmtrackr.app.ui.design.AuroraSoftEase
import com.elmtrackr.app.ui.design.ElmCard
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPeach
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.auroraSecondaryText
import kotlin.math.cos
import kotlin.math.sin

private val checklistGradient = Brush.linearGradient(
    colorStops = arrayOf(0f to AuroraIndigo, 0.5f to AuroraPlum, 1f to AuroraAqua),
)

/**
 * Dashboard "getting started" card: one row per [SetupStep], each with a small
 * animated motif, a plain-language explanation of the feature it teaches, and a
 * call to action. Rows tick off with an animated check as steps complete.
 */
@Composable
fun SetupChecklistCard(
    state: SetupChecklistUiState,
    onStepAction: (SetupStep) -> Unit,
    onDismiss: () -> Unit,
    onCelebrationDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.showCelebration) {
        SetupCompleteCelebrationDialog(onDismiss = onCelebrationDismissed)
    }

    val firstIncomplete = state.steps.firstOrNull { !it.isComplete }?.step
    var expandedKey by rememberSaveable(firstIncomplete) { mutableStateOf(firstIncomplete?.key) }
    val haptic = LocalHapticFeedback.current

    ElmCard(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChecklistProgressRing(
                    completed = state.completedCount,
                    total = state.totalCount,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.dashboard_setup_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(
                            R.string.dashboard_setup_subtitle,
                            state.completedCount,
                            state.totalCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = auroraSecondaryText(),
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.dashboard_setup_dismiss),
                        tint = auroraSecondaryText(),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            state.steps.forEach { stepState ->
                SetupStepRow(
                    step = stepState.step,
                    isComplete = stepState.isComplete,
                    isExpanded = !stepState.isComplete && expandedKey == stepState.step.key,
                    onToggle = {
                        AuroraHaptics.toggle(haptic)
                        expandedKey = if (expandedKey == stepState.step.key) null else stepState.step.key
                    },
                    onAction = { onStepAction(stepState.step) },
                )
            }
        }
    }
}

// ── Progress ring ─────────────────────────────────────────────────────────────

@Composable
private fun ChecklistProgressRing(completed: Int, total: Int) {
    val motionEnabled = auroraMotionEnabled()
    val fraction by animateFloatAsState(
        targetValue = if (total == 0) 0f else completed.toFloat() / total,
        animationSpec = if (motionEnabled) {
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
        } else {
            tween(0)
        },
        label = "setup-progress-fraction",
    )
    val progressDescription = stringResource(R.string.dashboard_setup_progress_accessibility, completed, total)
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .semantics { contentDescription = progressDescription },
    ) {
        Canvas(Modifier.size(46.dp)) {
            val strokeWidth = 5.dp.toPx()
            val inset = strokeWidth / 2f
            val diameter = size.minDimension - strokeWidth
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset),
                size = Size(diameter, diameter),
            )
            if (fraction > 0f) {
                drawArc(
                    brush = checklistGradient,
                    startAngle = -90f,
                    sweepAngle = fraction * 360f,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(inset, inset),
                    size = Size(diameter, diameter),
                )
            }
        }
        Text(
            text = "$completed/$total",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = AuroraIndigo,
        )
    }
}

// ── Step rows ─────────────────────────────────────────────────────────────────

@Composable
private fun SetupStepRow(
    step: SetupStep,
    isComplete: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onAction: () -> Unit,
) {
    val titleRes: Int
    val blurbRes: Int
    val bodyRes: Int
    val actionRes: Int
    when (step) {
        SetupStep.FIRST_SHIFT -> {
            titleRes = R.string.dashboard_setup_shift_title
            blurbRes = R.string.dashboard_setup_shift_blurb
            bodyRes = R.string.dashboard_setup_shift_body
            actionRes = R.string.dashboard_setup_shift_action
        }
        SetupStep.CLOCK_STYLE -> {
            titleRes = R.string.dashboard_setup_clock_title
            blurbRes = R.string.dashboard_setup_clock_blurb
            bodyRes = R.string.dashboard_setup_clock_body
            actionRes = R.string.dashboard_setup_clock_action
        }
        SetupStep.COMPENSATION -> {
            titleRes = R.string.dashboard_setup_compensation_title
            blurbRes = R.string.dashboard_setup_compensation_blurb
            bodyRes = R.string.dashboard_setup_compensation_body
            actionRes = R.string.dashboard_setup_compensation_action
        }
        SetupStep.PREMIUM -> {
            titleRes = R.string.dashboard_setup_premium_title
            blurbRes = R.string.dashboard_setup_premium_blurb
            bodyRes = R.string.dashboard_setup_premium_body
            actionRes = R.string.dashboard_setup_premium_action
        }
        SetupStep.TASKS -> {
            titleRes = R.string.dashboard_setup_tasks_title
            blurbRes = R.string.dashboard_setup_tasks_blurb
            bodyRes = R.string.dashboard_setup_tasks_body
            actionRes = R.string.dashboard_setup_tasks_action
        }
        SetupStep.WIDGET -> {
            titleRes = R.string.dashboard_setup_widget_title
            blurbRes = R.string.dashboard_setup_widget_blurb
            bodyRes = R.string.dashboard_setup_widget_body
            actionRes = R.string.dashboard_setup_widget_action
        }
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(200, easing = AuroraSoftEase),
        label = "setup-step-chevron",
    )
    val doneDescription = stringResource(R.string.dashboard_setup_step_done)
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isComplete,
                onClick = onToggle,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            SetupStepIcon(step = step, isComplete = isComplete, emphasized = isExpanded)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(titleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isComplete) auroraSecondaryText() else MaterialTheme.colorScheme.onSurface,
                )
                if (!isComplete) {
                    Text(
                        stringResource(blurbRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = auroraSecondaryText(),
                    )
                }
            }
            if (isComplete) {
                Box(Modifier.size(24.dp).semantics { contentDescription = doneDescription })
            } else {
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = auroraSecondaryText(),
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(chevronRotation),
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(tween(220, easing = AuroraSoftEase)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(160, easing = AuroraSoftEase)) + fadeOut(tween(120)),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 52.dp, bottom = 12.dp)) {
                Text(
                    stringResource(bodyRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ElmGradientButton(onClick = onAction, compact = true) {
                    Text(stringResource(actionRes), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Step icons ────────────────────────────────────────────────────────────────

/**
 * 40dp motif per step. Incomplete steps get a gentle looping animation (frozen
 * at a pleasant phase when reduce-motion is on); completing a step swaps in a
 * gradient badge with an animated check draw.
 */
@Composable
private fun SetupStepIcon(step: SetupStep, isComplete: Boolean, emphasized: Boolean) {
    val motionEnabled = auroraMotionEnabled()

    if (isComplete) {
        val checkProgress by animateFloatAsState(
            targetValue = 1f,
            animationSpec = if (motionEnabled) tween(450, easing = AuroraSoftEase) else tween(0),
            label = "setup-check-draw",
        )
        Box(
            Modifier
                .size(40.dp)
                .background(checklistGradient, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) { drawCheckMark(checkProgress) }
        }
        return
    }

    val phase = if (motionEnabled && emphasized) {
        val transition = rememberInfiniteTransition(label = "setup-step-motif")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2_400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "setup-step-motif-phase",
        ).value
    } else {
        0.35f
    }

    val scale by animateFloatAsState(
        targetValue = if (emphasized) 1.06f else 1f,
        animationSpec = tween(200, easing = AuroraSoftEase),
        label = "setup-step-icon-scale",
    )
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(AuroraIndigo.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(26.dp)) {
            when (step) {
                SetupStep.FIRST_SHIFT -> drawShiftMotif(phase, trackColor)
                SetupStep.CLOCK_STYLE -> drawClockStyleMotif(phase, trackColor)
                SetupStep.COMPENSATION -> drawCompensationMotif(phase)
                SetupStep.PREMIUM -> drawPremiumMotif(phase)
                SetupStep.TASKS -> drawTasksMotif(phase)
                SetupStep.WIDGET -> drawWidgetMotif(phase)
            }
        }
    }
}

private fun DrawScope.drawCheckMark(progress: Float) {
    if (progress <= 0f) return
    val start = Offset(size.width * 0.08f, size.height * 0.55f)
    val mid = Offset(size.width * 0.38f, size.height * 0.85f)
    val end = Offset(size.width * 0.92f, size.height * 0.18f)
    val stroke = 2.6.dp.toPx()
    // First leg draws over the first 40% of progress, second leg over the rest.
    val firstLeg = (progress / 0.4f).coerceAtMost(1f)
    drawLine(Color.White, start, lerp(start, mid, firstLeg), stroke, StrokeCap.Round)
    if (progress > 0.4f) {
        val secondLeg = ((progress - 0.4f) / 0.6f).coerceAtMost(1f)
        drawLine(Color.White, mid, lerp(mid, end, secondLeg), stroke, StrokeCap.Round)
    }
}

/** A mini punch clock: progress arc sweeps while a dot orbits the rim. */
private fun DrawScope.drawShiftMotif(phase: Float, trackColor: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f - 2.dp.toPx()
    drawCircle(trackColor, radius, center, style = Stroke(2.dp.toPx()))
    drawArc(
        color = AuroraIndigo,
        startAngle = -90f,
        sweepAngle = phase * 360f,
        useCenter = false,
        style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
    )
    val angle = Math.toRadians(phase * 360.0 - 90.0)
    val dot = Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius)
    drawCircle(AuroraAqua, 2.6.dp.toPx(), dot)
    drawCircle(AuroraIndigo, 1.6.dp.toPx(), center)
}

/** A watch face whose frame morphs between round and squircle while its hand turns. */
private fun DrawScope.drawClockStyleMotif(phase: Float, trackColor: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val half = size.minDimension / 2f - 2.dp.toPx()
    // 0→1→0 morph: circle → rounded square → circle.
    val morph = if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
    val cornerRadius = half * (1f - 0.55f * morph)
    drawRoundRect(
        color = AuroraPlum,
        topLeft = Offset(center.x - half, center.y - half),
        size = Size(half * 2, half * 2),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        style = Stroke(2.dp.toPx()),
    )
    rotate(phase * 360f, center) {
        drawLine(
            AuroraIndigo,
            center,
            Offset(center.x, center.y - half * 0.55f),
            2.dp.toPx(),
            StrokeCap.Round,
        )
    }
    drawCircle(trackColor, 1.4.dp.toPx(), center)
}

/** Three pay bars — regular, 125%, 150% — rising in a staggered rhythm. */
private fun DrawScope.drawCompensationMotif(phase: Float) {
    val barWidth = size.width * 0.2f
    val gap = size.width * 0.1f
    val heights = listOf(0.45f, 0.68f, 0.95f)
    val colors = listOf(AuroraIndigo, AuroraPlum, AuroraPeach)
    heights.forEachIndexed { index, target ->
        // Each bar grows during its own third of the loop, then holds.
        val local = ((phase * 3f) - index).coerceIn(0f, 1f)
        val height = size.height * target * local
        if (height > 0f) {
            drawRoundRect(
                color = colors[index],
                topLeft = Offset(index * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
            )
        }
    }
}

/** A four-point sparkle that breathes, with a twinkle orbiting its corner. */
private fun DrawScope.drawPremiumMotif(phase: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    // 0→1→0 breath over the loop.
    val breath = if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
    val radius = size.minDimension / 2f * (0.72f + 0.28f * breath)
    val waist = radius * 0.28f
    val star = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + waist * 0.4f, center.y - waist * 0.4f, center.x + radius, center.y)
        quadraticTo(center.x + waist * 0.4f, center.y + waist * 0.4f, center.x, center.y + radius)
        quadraticTo(center.x - waist * 0.4f, center.y + waist * 0.4f, center.x - radius, center.y)
        quadraticTo(center.x - waist * 0.4f, center.y - waist * 0.4f, center.x, center.y - radius)
        close()
    }
    drawPath(star, Brush.linearGradient(listOf(AuroraIndigo, AuroraPlum)))
    drawCircle(
        AuroraAqua.copy(alpha = 0.35f + 0.65f * breath),
        1.8.dp.toPx(),
        Offset(size.width * 0.88f, size.height * 0.16f),
    )
}

/** Three project dots pulsing one after the other. */
private fun DrawScope.drawTasksMotif(phase: Float) {
    val colors = listOf(AuroraIndigo, AuroraPlum, AuroraAqua)
    val baseRadius = size.minDimension * 0.14f
    val centerY = size.height / 2f
    colors.forEachIndexed { index, color ->
        val local = ((phase + 1f - index / 3f) % 1f)
        val pulse = if (local < 0.5f) local * 2f else (1f - local) * 2f
        drawCircle(
            color,
            baseRadius * (0.8f + 0.35f * pulse),
            Offset(size.width * (0.18f + index * 0.32f), centerY),
        )
    }
}

/** A 2x2 widget grid where the last tile pops in with a plus. */
private fun DrawScope.drawWidgetMotif(phase: Float) {
    val gap = size.width * 0.12f
    val tile = (size.width - gap) / 2f
    val corner = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
    val positions = listOf(
        Offset(0f, 0f),
        Offset(tile + gap, 0f),
        Offset(0f, tile + gap),
    )
    positions.forEach { drawRoundRect(AuroraIndigo.copy(alpha = 0.45f), it, Size(tile, tile), corner) }
    // The fourth tile scales in during the first half of the loop and holds.
    val pop = (phase * 2f).coerceAtMost(1f)
    if (pop > 0f) {
        val popSize = tile * pop
        val inset = (tile - popSize) / 2f
        val origin = Offset(tile + gap + inset, tile + gap + inset)
        drawRoundRect(AuroraAqua, origin, Size(popSize, popSize), corner)
        if (pop > 0.6f) {
            val c = Offset(tile + gap + tile / 2f, tile + gap + tile / 2f)
            val arm = popSize * 0.28f
            val stroke = 1.8.dp.toPx()
            drawLine(Color.White, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), stroke, StrokeCap.Round)
            drawLine(Color.White, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), stroke, StrokeCap.Round)
        }
    }
}

private fun lerp(start: Offset, end: Offset, fraction: Float): Offset =
    Offset(
        start.x + (end.x - start.x) * fraction,
        start.y + (end.y - start.y) * fraction,
    )

// ── Completion celebration ────────────────────────────────────────────────────

@Composable
private fun SetupCompleteCelebrationDialog(onDismiss: () -> Unit) {
    var animateIn by remember { mutableStateOf(false) }
    val motionEnabled = auroraMotionEnabled()

    LaunchedEffect(Unit) {
        if (motionEnabled) kotlinx.coroutines.delay(50)
        animateIn = true
    }

    val iconScale by animateFloatAsState(
        targetValue = if (!motionEnabled || animateIn) 1f else 0.72f,
        animationSpec = if (motionEnabled) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
        } else {
            tween(0)
        },
        label = "setup-celebration-icon-scale",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                Modifier
                    .size(56.dp)
                    .scale(iconScale)
                    .background(checklistGradient, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Celebration,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        title = {
            Text(
                stringResource(R.string.dashboard_setup_celebration_title),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Text(
                stringResource(R.string.dashboard_setup_celebration_body),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            ElmGradientButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.celebration_got_it), fontWeight = FontWeight.SemiBold)
            }
        },
    )
}
