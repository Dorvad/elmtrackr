package com.elmtrackr.app.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.WorkOutline
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.elmtrackr.app.ui.theme.AuroraAquaDeep
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPeachDeep
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing

/**
 * One-time update wizard telling an existing user that Paid Projects exists.
 *
 * Existing users are never sent back through onboarding, so this pop-up is how
 * they find the module. It walks through the module's features one page at a
 * time and ends with a choice: turn the feature on ([onEnable]) or skip it
 * ([onDismiss]). Both paths retire the wizard permanently
 * ([SettingsViewModel.dismissPaidProjectsDiscovery]); the feature stays
 * available from Settings → Features either way.
 *
 * Page transitions honour the reduce-motion preference through
 * [auroraMotionEnabled], the same switch the rest of the app uses.
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
    val lastPage = wizardPages.size - 1

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
                    WizardPageContent(wizardPages[index])
                }

                Spacer(Modifier.height(Spacing.md))
                WizardStepDots(current = page, total = wizardPages.size)
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

@Composable
private fun WizardPageContent(page: WizardPage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            // Reserve the tallest page's footprint so the dialog does not
            // jump in height while paging.
            .heightIn(min = 176.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .auroraEnter(index = 0)
                .size(64.dp)
                .background(page.tint.copy(alpha = 0.14f), RoundedCornerShape(CornerRadius.Medium)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                tint = page.tint,
                modifier = Modifier.size(32.dp),
            )
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
            val width by animateDpAsState(
                targetValue = if (active) 20.dp else 7.dp,
                animationSpec = tween(AuroraMotion.ContentCrossfadeMillis, easing = AuroraEaseOut),
                label = "wizard-dot-width",
            )
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(width)
                    .background(
                        if (active) AuroraIndigo
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

private data class WizardPage(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val icon: ImageVector,
    val tint: Color,
)

private val wizardPages = listOf(
    WizardPage(
        title = R.string.projects_wizard_page_projects_title,
        body = R.string.projects_wizard_page_projects_body,
        icon = Icons.Outlined.WorkOutline,
        tint = AuroraPlum,
    ),
    WizardPage(
        title = R.string.projects_wizard_page_time_title,
        body = R.string.projects_wizard_page_time_body,
        icon = Icons.Filled.Schedule,
        tint = AuroraIndigo,
    ),
    WizardPage(
        title = R.string.projects_wizard_page_billing_title,
        body = R.string.projects_wizard_page_billing_body,
        icon = Icons.AutoMirrored.Filled.ReceiptLong,
        tint = AuroraPeachDeep,
    ),
    WizardPage(
        title = R.string.projects_wizard_page_insights_title,
        body = R.string.projects_wizard_page_insights_body,
        icon = Icons.AutoMirrored.Filled.ShowChart,
        tint = AuroraAquaDeep,
    ),
)
