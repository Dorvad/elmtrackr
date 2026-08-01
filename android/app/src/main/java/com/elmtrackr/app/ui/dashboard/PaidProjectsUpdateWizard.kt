package com.elmtrackr.app.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius as GeometryCornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.design.AuroraEaseOut
import com.elmtrackr.app.ui.design.AuroraMotion
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.auroraEnter
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraAquaDeep
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPeachDeep
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * One-time v1.2 update wizard, shown over the main screen for existing users
 * ([DashboardViewModel] decides when). It opens on the new watch faces, then
 * walks through the Paid Projects module one page at a time, each page acting
 * out its feature with a looping demo animation rather than a static icon.
 * The wizard ends with a choice: turn the feature on ([onEnable]) or skip it
 * ([onDismiss]). Both paths retire the wizard permanently; the feature stays
 * available from Settings → Features either way.
 *
 * All motion — the page slides, the demo loops, the entrance rises — honours
 * the reduce-motion preference through [auroraMotionEnabled]: with motion off,
 * every demo freezes on its completed end state.
 */
@Composable
internal fun PaidProjectsUpdateWizard(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Survives rotation; deliberately not persisted further — the wizard shows
    // once, so a fresh process may start it from the first page again.
    var page by rememberSaveable { mutableIntStateOf(0) }
    var movingForward by rememberSaveable { mutableIntStateOf(1) }
    val lastPage = wizardPageCount - 1
    val demo = wizardDemoProgress()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // A tap outside must not permanently retire the announcement by
            // accident; skipping stays an explicit choice.
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(CornerRadius.Large),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
            ) {
                WizardHeader(onDismiss = onDismiss)
                Spacer(Modifier.height(Spacing.md))

                val motion = auroraMotionEnabled()
                // Pages advance toward the reading direction, so the slide
                // mirrors in a right-to-left layout.
                val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        if (!motion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val forward = (movingForward >= 0) != rtl
                            (
                                slideInHorizontally(
                                    tween(AuroraMotion.RiseMillis, easing = AuroraEaseOut),
                                ) { full -> if (forward) full / 3 else -full / 3 } +
                                    fadeIn(tween(AuroraMotion.FadeMillis, easing = AuroraEaseOut))
                                ) togetherWith (
                                slideOutHorizontally(
                                    tween(AuroraMotion.ContentCrossfadeMillis, easing = AuroraEaseOut),
                                ) { full -> if (forward) -full / 3 else full / 3 } +
                                    fadeOut(tween(AuroraMotion.ContentCrossfadeMillis, easing = AuroraEaseOut))
                                )
                        }
                    },
                    label = "paid-projects-wizard-page",
                ) { index ->
                    if (index == 0) {
                        WizardWatchFacesPage(demo)
                    } else {
                        WizardFeaturePageContent(featurePages[index - 1], demo)
                    }
                }

                Spacer(Modifier.height(Spacing.md))
                WizardStepDots(current = page, total = wizardPageCount)
                Spacer(Modifier.height(Spacing.md))

                Column(Modifier.padding(horizontal = Spacing.md)) {
                    if (page < lastPage) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            TextButton(
                                onClick = {
                                    if (page == 0) {
                                        onDismiss()
                                    } else {
                                        movingForward = -1
                                        page -= 1
                                    }
                                },
                            ) {
                                Text(
                                    stringResource(
                                        if (page == 0) R.string.projects_wizard_skip
                                        else R.string.projects_wizard_back,
                                    ),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            ElmGradientButton(
                                onClick = {
                                    movingForward = 1
                                    page += 1
                                },
                                compact = true,
                            ) {
                                Text(
                                    stringResource(R.string.projects_wizard_next),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    } else {
                        ElmGradientButton(onClick = onEnable) {
                            Text(
                                stringResource(R.string.projects_wizard_activate),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.projects_wizard_not_now))
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        stringResource(R.string.projects_wizard_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(Spacing.md))
            }
        }
    }
}

/**
 * The shared clock for every demo on screen: one slow 0→1 loop. With motion
 * off (reduce-motion, or animations disabled) it holds at 1 — each demo's
 * completed end state — so the pages still explain themselves unanimated.
 */
@Composable
private fun wizardDemoProgress(): Float {
    if (!auroraMotionEnabled()) return 1f
    val transition = rememberInfiniteTransition(label = "wizard-demo")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Restart),
        label = "wizard-demo-t",
    )
    return t
}

/** Progress of a sub-scene running between [from] and [to] on the demo clock. */
private fun stage(t: Float, from: Float, to: Float): Float =
    ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x)

/** Gradient banner carrying the release title and the explicit close action. */
@Composable
private fun WizardHeader(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to AuroraIndigo,
                        0.55f to AuroraPlum,
                        1.00f to AuroraAquaDeep,
                    ),
                ),
            ),
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.projects_wizard_close_a11y),
                tint = Color.White,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.lg, bottom = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .auroraEnter(index = 0)
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(CornerRadius.Small)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.projects_wizard_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.auroraEnter(index = 1),
            )
        }
    }
}

// ── Feature pages: each one acts out its feature in a looping demo ────────────

@Composable
private fun WizardFeaturePageContent(page: WizardFeaturePage, demo: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            // Reserve the tallest page's footprint so the dialog does not
            // jump in height while paging.
            .heightIn(min = 208.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .auroraEnter(index = 0)
                .fillMaxWidth()
                .height(104.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(CornerRadius.Medium),
                ),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) { page.demo(this, demo) }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            stringResource(page.title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.auroraEnter(index = 1),
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(page.body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.auroraEnter(index = 2),
        )
    }
}

/**
 * A project card assembling itself: name and client appear, then the agreed
 * fee, the tax stacked on top, and finally the client total — the three
 * amounts that always stay in step.
 */
private fun DrawScope.drawProjectCardDemo(t: Float) {
    val cardW = size.width * 0.66f
    val cardLeft = (size.width - cardW) / 2f
    val appear = easeOut(stage(t, 0f, 0.15f))
    drawRoundRect(
        AuroraIndigo.copy(alpha = 0.07f * appear),
        Offset(cardLeft, 0f),
        Size(cardW, size.height),
        GeometryCornerRadius(10.dp.toPx()),
    )
    drawRoundRect(
        AuroraIndigo.copy(alpha = 0.25f * appear),
        Offset(cardLeft, 0f),
        Size(cardW, size.height),
        GeometryCornerRadius(10.dp.toPx()),
        style = Stroke(1.dp.toPx()),
    )
    val inset = cardLeft + 12.dp.toPx()
    fun bar(y: Float, w: Float, h: Float, color: Color) {
        if (w <= 0f) return
        drawRoundRect(color, Offset(inset, y), Size(w, h), GeometryCornerRadius(h / 2f))
    }
    // Project name, then the client line beneath it.
    bar(10.dp.toPx(), cardW * 0.42f * appear, 5.dp.toPx(), AuroraIndigo.copy(alpha = 0.8f))
    bar(20.dp.toPx(), cardW * 0.30f * appear, 3.5.dp.toPx(), AuroraIndigo.copy(alpha = 0.25f))
    // Fee, tax on top, then the divider and the client total.
    val feeT = easeOut(stage(t, 0.2f, 0.45f))
    val taxT = easeOut(stage(t, 0.45f, 0.65f))
    val totalT = easeOut(stage(t, 0.65f, 0.9f))
    bar(34.dp.toPx(), cardW * 0.48f * feeT, 6.dp.toPx(), AuroraIndigo)
    bar(45.dp.toPx(), cardW * 0.16f * taxT, 6.dp.toPx(), AuroraPlum)
    if (totalT > 0f) {
        drawLine(
            AuroraIndigo.copy(alpha = 0.2f),
            Offset(inset, 57.dp.toPx()),
            Offset(inset + cardW * 0.64f, 57.dp.toPx()),
            1.dp.toPx(),
        )
    }
    bar(63.dp.toPx(), cardW * 0.64f * totalT, 7.dp.toPx(), AuroraAqua)
}

/**
 * Clocking in against a project: a tap ripple starts the mini clock, its arc
 * sweeps, and the tracked time streams across into the project's hour bank —
 * kept apart from wages.
 */
private fun DrawScope.drawProjectTimeDemo(t: Float) {
    val clockCenter = Offset(size.width * 0.22f, size.height * 0.5f)
    val radius = size.height * 0.32f
    // The clock-in tap.
    val tap = stage(t, 0f, 0.18f)
    if (tap > 0f && tap < 1f) {
        drawCircle(
            AuroraIndigo.copy(alpha = (1f - tap) * 0.5f),
            radius * (0.6f + tap * 1.1f),
            clockCenter,
            style = Stroke(2.dp.toPx()),
        )
    }
    drawCircle(AuroraIndigo.copy(alpha = 0.15f), radius, clockCenter, style = Stroke(3.dp.toPx()))
    val sweep = easeOut(stage(t, 0.15f, 0.9f))
    if (sweep > 0f) {
        drawArc(
            AuroraIndigo,
            startAngle = -90f,
            sweepAngle = 320f * sweep,
            useCenter = false,
            topLeft = Offset(clockCenter.x - radius, clockCenter.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
        )
        val angle = Math.toRadians(-90.0 + 320.0 * sweep)
        drawCircle(
            AuroraIndigo,
            3.dp.toPx(),
            clockCenter + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius),
        )
    }
    // Hours streaming from the clock toward the project.
    val laneY = clockCenter.y
    val laneStart = clockCenter.x + radius + 8.dp.toPx()
    val laneEnd = size.width * 0.56f
    drawLine(
        AuroraIndigo.copy(alpha = 0.2f),
        Offset(laneStart, laneY),
        Offset(laneEnd, laneY),
        1.5.dp.toPx(),
        StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
    )
    if (t > 0.2f) {
        repeat(2) { k ->
            val flow = (stage(t, 0.2f, 0.95f) * 2f + k * 0.5f) % 1f
            drawCircle(
                AuroraAqua.copy(alpha = 1f - flow * 0.6f),
                2.5.dp.toPx(),
                Offset(laneStart + (laneEnd - laneStart) * flow, laneY),
            )
        }
    }
    // The project's hour bank filling up.
    val bankLeft = size.width * 0.62f
    val bankW = size.width * 0.34f
    drawRoundRect(
        AuroraPlum.copy(alpha = 0.10f),
        Offset(bankLeft, size.height * 0.12f),
        Size(bankW, size.height * 0.76f),
        GeometryCornerRadius(8.dp.toPx()),
    )
    // Briefcase mark on the bank.
    val caseCenter = Offset(bankLeft + bankW / 2f, size.height * 0.34f)
    drawRoundRect(
        AuroraPlum,
        Offset(caseCenter.x - 7.dp.toPx(), caseCenter.y - 4.dp.toPx()),
        Size(14.dp.toPx(), 9.dp.toPx()),
        GeometryCornerRadius(2.5.dp.toPx()),
        style = Stroke(1.8.dp.toPx()),
    )
    drawLine(
        AuroraPlum,
        Offset(caseCenter.x - 3.dp.toPx(), caseCenter.y - 4.dp.toPx()),
        Offset(caseCenter.x - 3.dp.toPx(), caseCenter.y - 6.5.dp.toPx()),
        1.8.dp.toPx(), StrokeCap.Round,
    )
    drawLine(
        AuroraPlum,
        Offset(caseCenter.x + 3.dp.toPx(), caseCenter.y - 4.dp.toPx()),
        Offset(caseCenter.x + 3.dp.toPx(), caseCenter.y - 6.5.dp.toPx()),
        1.8.dp.toPx(), StrokeCap.Round,
    )
    val trackLeft = bankLeft + 8.dp.toPx()
    val trackW = bankW - 16.dp.toPx()
    val trackY = size.height * 0.62f
    drawRoundRect(
        AuroraPlum.copy(alpha = 0.18f),
        Offset(trackLeft, trackY),
        Size(trackW, 6.dp.toPx()),
        GeometryCornerRadius(3.dp.toPx()),
    )
    val banked = easeOut(stage(t, 0.25f, 0.95f))
    if (banked > 0f) {
        drawRoundRect(
            AuroraAqua,
            Offset(trackLeft, trackY),
            Size(trackW * banked, 6.dp.toPx()),
            GeometryCornerRadius(3.dp.toPx()),
        )
    }
}

/**
 * Billing and payments: the billed amount stands as the outstanding balance,
 * two payments land and shrink it, and the record closes with a check.
 */
private fun DrawScope.drawBillingDemo(t: Float) {
    // The invoice document.
    val docW = size.width * 0.20f
    val docH = size.height * 0.72f
    val docLeft = size.width * 0.05f
    val docTop = size.height * 0.14f
    val appear = easeOut(stage(t, 0f, 0.18f))
    drawRoundRect(
        AuroraIndigo.copy(alpha = 0.08f * appear),
        Offset(docLeft, docTop),
        Size(docW, docH),
        GeometryCornerRadius(6.dp.toPx()),
    )
    drawRoundRect(
        AuroraIndigo.copy(alpha = 0.3f * appear),
        Offset(docLeft, docTop),
        Size(docW, docH),
        GeometryCornerRadius(6.dp.toPx()),
        style = Stroke(1.2.dp.toPx()),
    )
    repeat(3) { i ->
        drawLine(
            AuroraIndigo.copy(alpha = 0.3f * appear),
            Offset(docLeft + 6.dp.toPx(), docTop + (12 + i * 9).dp.toPx()),
            Offset(docLeft + docW - 6.dp.toPx(), docTop + (12 + i * 9).dp.toPx()),
            2.dp.toPx(), StrokeCap.Round,
        )
    }
    // Outstanding balance, shrinking as the two payments arrive.
    val trackLeft = size.width * 0.34f
    val trackW = size.width * 0.60f
    val trackY = size.height * 0.28f
    drawRoundRect(
        AuroraIndigo.copy(alpha = 0.12f),
        Offset(trackLeft, trackY),
        Size(trackW, 9.dp.toPx()),
        GeometryCornerRadius(4.5.dp.toPx()),
    )
    val outstanding = 1f -
        0.45f * easeOut(stage(t, 0.3f, 0.45f)) -
        0.55f * easeOut(stage(t, 0.55f, 0.7f))
    if (outstanding > 0.005f) {
        drawRoundRect(
            AuroraPeachDeep,
            Offset(trackLeft, trackY),
            Size(trackW * outstanding, 9.dp.toPx()),
            GeometryCornerRadius(4.5.dp.toPx()),
        )
    }
    // The two payments dropping onto the balance.
    listOf(0.3f to 0.30f, 0.55f to 0.62f).forEach { (at, x) ->
        val drop = stage(t, at - 0.1f, at + 0.05f)
        if (drop > 0f && drop < 1f) {
            drawCircle(
                AuroraAqua,
                4.dp.toPx(),
                Offset(trackLeft + trackW * x, trackY - (1f - drop) * 16.dp.toPx()),
            )
        }
    }
    // Payments received so far, resting on the ledger line below.
    val ledgerY = size.height * 0.68f
    listOf(0.35f, 0.6f).forEachIndexed { i, at ->
        val landed = stage(t, at, at + 0.08f)
        if (landed > 0f) {
            val cx = trackLeft + (i * 0.16f + 0.08f) * trackW
            drawCircle(AuroraAqua.copy(alpha = 0.9f), 5.dp.toPx() * landed, Offset(cx, ledgerY))
            drawCircle(Color.White.copy(alpha = 0.8f), 1.8.dp.toPx() * landed, Offset(cx, ledgerY))
        }
    }
    // Paid: the check draws itself once the balance reaches zero.
    val check = easeOut(stage(t, 0.82f, 0.97f))
    if (check > 0f) {
        val c = Offset(trackLeft + trackW * 0.82f, ledgerY)
        drawCircle(AuroraAqua.copy(alpha = 0.2f), 12.dp.toPx(), c)
        val p1 = c + Offset(-5.dp.toPx(), 0.5.dp.toPx())
        val p2 = c + Offset(-1.5.dp.toPx(), 4.dp.toPx())
        val p3 = c + Offset(6.dp.toPx(), -4.dp.toPx())
        val firstLeg = (check * 2f).coerceAtMost(1f)
        drawLine(
            AuroraAqua, p1,
            Offset(p1.x + (p2.x - p1.x) * firstLeg, p1.y + (p2.y - p1.y) * firstLeg),
            2.5.dp.toPx(), StrokeCap.Round,
        )
        val secondLeg = ((check - 0.5f) * 2f).coerceIn(0f, 1f)
        if (secondLeg > 0f) {
            drawLine(
                AuroraAqua, p2,
                Offset(p2.x + (p3.x - p2.x) * secondLeg, p2.y + (p3.y - p2.y) * secondLeg),
                2.5.dp.toPx(), StrokeCap.Round,
            )
        }
    }
}

/**
 * The effective-rate chart drawing itself across the page, crossing the dashed
 * target-rate line, with the live point glowing at its tip.
 */
private fun DrawScope.drawRateChartDemo(t: Float) {
    val left = size.width * 0.06f
    val right = size.width * 0.94f
    val base = size.height * 0.88f
    val top = size.height * 0.08f
    drawLine(AuroraIndigo.copy(alpha = 0.18f), Offset(left, base), Offset(right, base), 1.5.dp.toPx(), StrokeCap.Round)
    drawLine(AuroraIndigo.copy(alpha = 0.18f), Offset(left, base), Offset(left, top), 1.5.dp.toPx(), StrokeCap.Round)
    // The target hourly rate to beat.
    val targetY = top + (base - top) * 0.34f
    drawLine(
        AuroraPlum.copy(alpha = 0.45f),
        Offset(left, targetY),
        Offset(right, targetY),
        1.5.dp.toPx(),
        StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx())),
    )
    // The rate line: dips while hours pile up, recovers as payments land.
    val points = listOf(
        Offset(0.00f, 0.78f), Offset(0.18f, 0.58f), Offset(0.36f, 0.66f),
        Offset(0.55f, 0.42f), Offset(0.75f, 0.48f), Offset(1.00f, 0.20f),
    ).map { Offset(left + (right - left) * it.x, top + (base - top) * it.y) }
    val reveal = easeOut(stage(t, 0.1f, 0.9f))
    if (reveal <= 0f) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val visible = Path()
    measure.getSegment(0f, measure.length * reveal, visible, true)
    drawPath(
        visible,
        Brush.linearGradient(listOf(AuroraIndigo, AuroraAqua), start = points.first(), end = points.last()),
        style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
    )
    val tip = measure.getPosition(measure.length * reveal)
    drawCircle(AuroraAqua.copy(alpha = 0.2f), 8.dp.toPx(), tip)
    drawCircle(AuroraAqua, 3.5.dp.toPx(), tip)
}

// ── Watch-face showcase: the faces run their own mini demos ───────────────────

/**
 * Opening page: the three watch faces that ship with v1.2, each tile playing
 * its face in miniature — the train rides, the tonearm tracks, the moon waxes.
 */
@Composable
private fun WizardWatchFacesPage(demo: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .heightIn(min = 208.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.projects_wizard_page_faces_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.auroraEnter(index = 0),
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(R.string.projects_wizard_page_faces_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.auroraEnter(index = 1),
        )
        Spacer(Modifier.height(Spacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            WizardFaceTile(
                name = R.string.clock_style_metro,
                dark = false,
                modifier = Modifier
                    .weight(1f)
                    .auroraEnter(index = 2),
            ) { drawMetroTile(demo) }
            WizardFaceTile(
                name = R.string.clock_style_vinyl,
                dark = true,
                modifier = Modifier
                    .weight(1f)
                    .auroraEnter(index = 3),
            ) { drawVinylTile(demo) }
            WizardFaceTile(
                name = R.string.clock_style_luna,
                dark = false,
                modifier = Modifier
                    .weight(1f)
                    .auroraEnter(index = 4),
            ) { drawLunaTile(demo) }
        }
    }
}

@Composable
private fun WizardFaceTile(
    @StringRes name: Int,
    dark: Boolean,
    modifier: Modifier = Modifier,
    draw: DrawScope.() -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    if (dark) Color(0xFF11162A)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(CornerRadius.Small),
                ),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                onDraw = draw,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(name),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The Metro tile: the train rides the line, lighting stations as it passes. */
private fun DrawScope.drawMetroTile(t: Float) {
    val p0 = Offset(size.width * 0.06f, size.height * 0.78f)
    val c0 = Offset(size.width * 0.40f, size.height * 0.82f)
    val p1 = Offset(size.width * 0.56f, size.height * 0.58f)
    val c1 = Offset(size.width * 0.74f, size.height * 0.34f)
    val p2 = Offset(size.width * 0.92f, size.height * 0.26f)
    fun quad(a: Offset, c: Offset, b: Offset, s: Float): Offset {
        val inv = 1f - s
        return Offset(
            inv * inv * a.x + 2f * inv * s * c.x + s * s * b.x,
            inv * inv * a.y + 2f * inv * s * c.y + s * s * b.y,
        )
    }
    fun quadTangent(a: Offset, c: Offset, b: Offset, s: Float): Offset = Offset(
        2f * (1f - s) * (c.x - a.x) + 2f * s * (b.x - c.x),
        2f * (1f - s) * (c.y - a.y) + 2f * s * (b.y - c.y),
    )
    val line = Path().apply {
        moveTo(p0.x, p0.y)
        quadraticTo(c0.x, c0.y, p1.x, p1.y)
        quadraticTo(c1.x, c1.y, p2.x, p2.y)
    }
    drawPath(line, AuroraIndigo.copy(alpha = 0.2f), style = Stroke(3f, cap = StrokeCap.Round))
    val ride = easeOut(stage(t, 0.05f, 0.95f))
    // Stations light as the train passes; the terminus stays a ring until reached.
    listOf(0.18f to quad(p0, c0, p1, 0.36f), 0.42f to quad(p0, c0, p1, 0.84f)).forEach { (at, stop) ->
        if (ride >= at) drawCircle(AuroraIndigo, 3.5f, stop)
        else drawCircle(AuroraIndigo.copy(alpha = 0.35f), 3.5f, stop, style = Stroke(2f))
    }
    if (ride >= 0.99f) drawCircle(AuroraIndigo, 3.5f, p2)
    else drawCircle(AuroraIndigo.copy(alpha = 0.35f), 3.5f, p2, style = Stroke(2f))
    val (pos, tan) = if (ride < 0.5f) {
        quad(p0, c0, p1, ride * 2f) to quadTangent(p0, c0, p1, ride * 2f)
    } else {
        quad(p1, c1, p2, (ride - 0.5f) * 2f) to quadTangent(p1, c1, p2, (ride - 0.5f) * 2f)
    }
    withTransform({
        translate(pos.x, pos.y)
        rotate(Math.toDegrees(atan2(tan.y, tan.x).toDouble()).toFloat(), Offset.Zero)
    }) {
        drawRoundRect(
            AuroraIndigo,
            Offset(-7.dp.toPx(), -3.5.dp.toPx()),
            Size(14.dp.toPx(), 7.dp.toPx()),
            GeometryCornerRadius(3.5.dp.toPx()),
        )
    }
}

/** The Vinyl tile: the label marker spins while the tonearm tracks inward. */
private fun DrawScope.drawVinylTile(t: Float) {
    val disc = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension * 0.42f
    drawCircle(VinylTileDisc, radius, disc)
    val needleRadius = radius * (0.88f - 0.5f * easeOut(stage(t, 0.05f, 0.95f)))
    // Grooves already played glow aqua behind the needle.
    repeat(3) { i ->
        val grooveRadius = radius * (0.5f + i * 0.16f)
        drawCircle(
            if (grooveRadius >= needleRadius) AuroraAqua.copy(alpha = 0.4f)
            else Color.White.copy(alpha = 0.1f),
            grooveRadius, disc, style = Stroke(1.5f),
        )
    }
    drawCircle(AuroraIndigo, radius * 0.32f, disc)
    val marker = Math.toRadians(t * 720.0 - 90.0)
    drawCircle(
        Color.White.copy(alpha = 0.85f),
        1.8f,
        disc + Offset(cos(marker).toFloat() * radius * 0.2f, sin(marker).toFloat() * radius * 0.2f),
    )
    drawCircle(Color(0xFF11162A), 2.5f, disc)
    val pivot = Offset(size.width - 4.dp.toPx(), 4.dp.toPx())
    val needleAngle = -Math.PI.toFloat() / 3f
    val needle = disc + Offset(cos(needleAngle) * needleRadius, sin(needleAngle) * needleRadius)
    drawLine(Color.White.copy(alpha = 0.6f), pivot, needle, 2f, StrokeCap.Round)
    drawCircle(AuroraAqua.copy(alpha = 0.7f), 3f, needle)
}

/** The Luna tile: the moon waxes from new to full, then the sparkle lands. */
private fun DrawScope.drawLunaTile(t: Float) {
    val moon = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension * 0.36f
    val wax = stage(t, 0.05f, 0.9f)
    drawCircle(AuroraIndigo.copy(alpha = 0.08f + 0.1f * wax), radius + 5.dp.toPx(), moon)
    drawCircle(LunaTileSurface, radius, moon)
    drawCircle(LunaTileEdge.copy(alpha = 0.1f), radius, moon, style = Stroke(1.5f))
    val k = cos(Math.PI.toFloat() * wax)
    val terminatorRx = radius * abs(k)
    if (wax > 0.01f) {
        val lit = Path().apply {
            arcTo(Rect(moon.x - radius, moon.y - radius, moon.x + radius, moon.y + radius), -90f, 180f, false)
            arcTo(
                Rect(moon.x - terminatorRx, moon.y - radius, moon.x + terminatorRx, moon.y + radius),
                90f, if (k > 0f) -180f else 180f, false,
            )
            close()
        }
        drawPath(lit, LunaTileLit)
    }
    val sparkle = easeOut(stage(t, 0.9f, 1f))
    if (sparkle > 0f) {
        val star = Offset(moon.x + radius + 6.dp.toPx(), moon.y - radius + 2.dp.toPx())
        val arm = 2.5.dp.toPx() * sparkle
        drawLine(AuroraIndigo, star - Offset(arm, 0f), star + Offset(arm, 0f), 1.5f, StrokeCap.Round)
        drawLine(AuroraIndigo, star - Offset(0f, arm), star + Offset(0f, arm), 1.5f, StrokeCap.Round)
    }
}

/** Progress dots; the active step stretches into a pill. */
@Composable
private fun WizardStepDots(current: Int, total: Int) {
    val stepLabel = stringResource(R.string.projects_wizard_step_a11y, current + 1, total)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = stepLabel },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val active = index == current
            val dotWidth by animateDpAsState(
                targetValue = if (active) 20.dp else 7.dp,
                animationSpec = tween(AuroraMotion.ContentCrossfadeMillis, easing = AuroraEaseOut),
                label = "wizard-dot-width",
            )
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(dotWidth)
                    .background(
                        if (active) AuroraIndigo
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

private data class WizardFeaturePage(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val demo: DrawScope.(t: Float) -> Unit,
)

private val featurePages = listOf(
    WizardFeaturePage(
        title = R.string.projects_wizard_page_projects_title,
        body = R.string.projects_wizard_page_projects_body,
        demo = { t -> drawProjectCardDemo(t) },
    ),
    WizardFeaturePage(
        title = R.string.projects_wizard_page_time_title,
        body = R.string.projects_wizard_page_time_body,
        demo = { t -> drawProjectTimeDemo(t) },
    ),
    WizardFeaturePage(
        title = R.string.projects_wizard_page_billing_title,
        body = R.string.projects_wizard_page_billing_body,
        demo = { t -> drawBillingDemo(t) },
    ),
    WizardFeaturePage(
        title = R.string.projects_wizard_page_insights_title,
        body = R.string.projects_wizard_page_insights_body,
        demo = { t -> drawRateChartDemo(t) },
    ),
)

/** The watch-face opener plus the feature pages. */
private val wizardPageCount = featurePages.size + 1

private val VinylTileDisc = Color(0xFF211D3E)
private val LunaTileSurface = Color(0xFFF1F0FB)
private val LunaTileEdge = Color(0xFF181530)
private val LunaTileLit = Color(0xFFC4B8FA)
