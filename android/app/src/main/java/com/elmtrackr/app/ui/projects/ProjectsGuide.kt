package com.elmtrackr.app.ui.projects

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.design.AuroraEaseOut
import com.elmtrackr.app.ui.design.AuroraMotion
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.auroraEnter
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.Spacing

/**
 * How Paid Projects works, shown once the first time the tab is opened.
 *
 * Four pages, one per question a first-time user actually has: what a project
 * *is*, how hours get onto it, how the numbers are worked out, and what happens
 * once the invoice goes out. Each page acts its answer out in a looping demo,
 * because the two things that confuse people here — that project time is a mode
 * of the ordinary clock-in rather than a separate timer, and that the effective
 * rate falls as hours rise — are both motion, and a static screenshot of either
 * shows the end state without the mechanism.
 *
 * **Not the same thing as `PaidProjectsUpdateWizard`.** That one runs on the
 * dashboard, announces the module to existing users, and ends by asking whether
 * to switch it on. It is answered before the tab has ever been seen. This is the
 * how-to on the other side of that decision, so someone who said yes there still
 * gets it here.
 *
 * A page, not a dialog. The guide teaches a screen the user is standing on, and
 * it needs the height to draw at a size worth reading — a dialog would take a
 * dimmed third of the display and put a scrim between the explanation and the
 * thing being explained. It is reachable again from the list header afterwards,
 * so skipping costs nothing.
 *
 * Every animation honours reduce-motion through [auroraMotionEnabled]: with
 * motion off each demo holds at its finished state and the pages cross-fade
 * instantly, so the guide still explains itself without moving.
 */
@Composable
internal fun ProjectsGuide(
    onFinish: () -> Unit,
    onCreateProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Survives rotation. Deliberately no further than that: the guide is short,
    // and a fresh process restarting it at page one is a smaller cost than
    // persisting a position nobody asked to keep.
    var page by rememberSaveable { mutableIntStateOf(0) }
    var movingForward by rememberSaveable { mutableIntStateOf(1) }
    val lastPage = guidePages.size - 1
    val motion = auroraMotionEnabled()
    val demo = guideDemoClock()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.screenGutter),
    ) {
        GuideTopBar(
            step = page + 1,
            total = guidePages.size,
            onSkip = onFinish,
        )

        // Pages advance toward the reading direction, so the slide mirrors in a
        // right-to-left layout.
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
            label = "projects-guide-page",
        ) { index ->
            GuidePageContent(guidePages[index], demo)
        }

        Spacer(Modifier.height(Spacing.md))
        GuideStepDots(current = page, total = guidePages.size)
        Spacer(Modifier.height(Spacing.md))

        if (page < lastPage) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                TextButton(
                    onClick = { movingForward = -1; page -= 1 },
                    enabled = page > 0,
                ) { Text(stringResource(R.string.projects_guide_back)) }
                Spacer(Modifier.weight(1f))
                ElmGradientButton(
                    onClick = { movingForward = 1; page += 1 },
                    compact = true,
                ) {
                    Text(stringResource(R.string.projects_guide_next), fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // The guide ends on the action it was teaching. Finishing first, then
            // opening the form, so a user who backs out of the form lands on the
            // list rather than back in the guide they just completed.
            ElmGradientButton(
                onClick = { onFinish(); onCreateProject() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.projects_guide_create), fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.projects_guide_done))
            }
        }

        Spacer(Modifier.height(Spacing.sm))
        Text(
            stringResource(R.string.projects_guide_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // Clears the system navigation bar: the tab's own bottom bar is hidden
        // while the guide is up, so nothing else is holding that space open.
        Spacer(Modifier.height(Spacing.s96))
    }
}

/**
 * The shared clock every demo on screen runs against: one slow 0→1 loop.
 *
 * Six seconds rather than the update wizard's four and a half. These demos are
 * teaching a sequence rather than trailing a feature, and each stage has to be
 * legible before the next one starts.
 *
 * Held at 1 with motion off, which is each demo's completed end state.
 */
@Composable
private fun guideDemoClock(): Float {
    if (!auroraMotionEnabled()) return 1f
    val transition = rememberInfiniteTransition(label = "projects-guide")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "projects-guide-t",
    )
    return t
}

@Composable
private fun GuideTopBar(step: Int, total: Int, onSkip: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.projects_guide_step, step, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        // Labelled rather than an X. On a screen that fills the tab, an X is
        // ambiguous between "close the guide" and "leave Projects", and a Skip
        // beside one was two controls a thumb apart doing the same thing.
        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.projects_guide_skip))
        }
    }
}

@Composable
private fun GuidePageContent(page: GuidePage, demo: Float) {
    val demoDescription = stringResource(page.demoA11y)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Reserve the tallest page's footprint so the buttons below do not
            // move as the user pages through.
            .heightIn(min = Layout.guidePageMinHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .auroraEnter(index = 0)
                .fillMaxWidth()
                .height(Layout.guideDemoHeight)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    RoundedCornerShape(CornerRadius.Large),
                )
                // One description for the whole drawing: a canvas is invisible to
                // a screen reader, and these carry the half of the explanation the
                // body text deliberately does not repeat.
                .semantics { contentDescription = demoDescription },
        ) {
            Canvas(
                Modifier.fillMaxSize().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            ) { page.demo(this, demo) }
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(
            stringResource(page.title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.auroraEnter(index = 1),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            stringResource(page.body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.auroraEnter(index = 2),
        )
        page.footnote?.let { footnote ->
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.auroraEnter(index = 3),
            )
        }
    }
}

@Composable
private fun GuideStepDots(current: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val active = index == current
            val width by animateFloatAsState(
                targetValue = if (active) DotActiveWidth else DotWidth,
                animationSpec = tween(AuroraMotion.FadeMillis, easing = AuroraEaseOut),
                label = "projects-guide-dot",
            )
            Box(
                Modifier
                    .padding(horizontal = Spacing.s2)
                    .size(width = width.dp, height = DotWidth.dp)
                    .background(
                        if (active) AuroraIndigo
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

/** Step-dot thickness, and how far the current one stretches. */
private const val DotWidth = 6f
private const val DotActiveWidth = 20f

private data class GuidePage(
    @StringRes val title: Int,
    @StringRes val body: Int,
    /** One extra line, for a page with a caveat worth stating outright. */
    @StringRes val footnote: Int? = null,
    /** What the drawing shows, for a screen reader. */
    @StringRes val demoA11y: Int,
    val demo: DrawScope.(Float) -> Unit,
)

/**
 * Four pages, in the order the work actually happens: set the project up, put
 * hours on it, read what those hours mean, then bill and collect.
 */
private val guidePages = listOf(
    GuidePage(
        title = R.string.projects_guide_setup_title,
        body = R.string.projects_guide_setup_body,
        footnote = R.string.projects_guide_setup_note,
        demoA11y = R.string.projects_guide_setup_a11y,
        demo = { t -> drawSetupDemo(t) },
    ),
    GuidePage(
        title = R.string.projects_guide_track_title,
        body = R.string.projects_guide_track_body,
        footnote = R.string.projects_guide_track_note,
        demoA11y = R.string.projects_guide_track_a11y,
        demo = { t -> drawTrackDemo(t) },
    ),
    GuidePage(
        title = R.string.projects_guide_rate_title,
        body = R.string.projects_guide_rate_body,
        footnote = R.string.projects_guide_rate_note,
        demoA11y = R.string.projects_guide_rate_a11y,
        demo = { t -> drawRateDemo(t) },
    ),
    GuidePage(
        title = R.string.projects_guide_billing_title,
        body = R.string.projects_guide_billing_body,
        footnote = R.string.projects_guide_billing_note,
        demoA11y = R.string.projects_guide_billing_a11y,
        demo = { t -> drawBillingDemo(t) },
    ),
)


