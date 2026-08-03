package com.elmtrackr.app.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.projects.ProjectHealthFlag
import com.elmtrackr.app.domain.projects.ProjectHealthIndicator
import com.elmtrackr.app.ui.theme.auroraSemantics
import kotlin.math.roundToInt

/**
 * Health indicators, each rendered with the reason it appeared.
 *
 * There is no score anywhere here. Every chip is a named condition and every
 * chip has an explanation underneath built from the figures that triggered it —
 * that is the whole point of [ProjectHealthFlag] carrying them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectHealthChips(
    flags: List<ProjectHealthFlag>,
    modifier: Modifier = Modifier,
    showExplanations: Boolean = true,
) {
    if (flags.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            flags.forEach { flag ->
                StatusPill(
                    text = stringResource(healthLabel(flag.indicator)),
                    accent = healthColor(flag.indicator),
                )
            }
        }
        if (showExplanations) {
            flags.forEach { flag ->
                healthExplanation(flag)?.let { ProjectNoteText(it) }
            }
        }
    }
}

fun healthLabel(indicator: ProjectHealthIndicator): Int = when (indicator) {
    ProjectHealthIndicator.ON_TRACK -> R.string.project_health_on_track
    ProjectHealthIndicator.NEAR_HOUR_BUDGET -> R.string.project_health_near_budget
    ProjectHealthIndicator.OVER_HOUR_BUDGET -> R.string.project_health_over_budget
    ProjectHealthIndicator.RATE_BELOW_TARGET -> R.string.project_health_rate_below
    ProjectHealthIndicator.PAYMENT_DUE_SOON -> R.string.project_health_due_soon
    ProjectHealthIndicator.PAYMENT_OVERDUE -> R.string.project_health_overdue
    ProjectHealthIndicator.COMPLETED_NOT_BILLED -> R.string.project_health_not_billed
}

/**
 * Why this indicator is showing, built from the flag's own figures.
 *
 * Returns null only when a figure the text needs is somehow absent, which keeps
 * a half-finished sentence off the screen rather than printing a placeholder.
 */
@Composable
fun healthExplanation(flag: ProjectHealthFlag): String? = when (flag.indicator) {
    ProjectHealthIndicator.ON_TRACK ->
        stringResource(R.string.project_health_on_track_why)

    ProjectHealthIndicator.NEAR_HOUR_BUDGET -> flag.budgetUsage?.let { usage ->
        stringResource(R.string.project_health_near_budget_why, (usage * 100).roundToInt())
    }

    ProjectHealthIndicator.OVER_HOUR_BUDGET -> flag.minutesOverBudget?.let { minutes ->
        stringResource(R.string.project_health_over_budget_why, formattedMinutes(minutes))
    }

    // The shortfall is negative; the sentence reads "N below", so it is shown
    // without the sign rather than as "-N below".
    ProjectHealthIndicator.RATE_BELOW_TARGET -> flag.rateShortfall?.let { shortfall ->
        stringResource(R.string.project_health_rate_below_why, shortfall.absoluteValue.formatted())
    }

    ProjectHealthIndicator.PAYMENT_DUE_SOON -> {
        val outstanding = flag.outstanding
        val days = flag.daysUntilDue
        if (outstanding == null || days == null) {
            null
        } else {
            stringResource(R.string.project_health_due_soon_why, outstanding.formatted(), days.toInt())
        }
    }

    ProjectHealthIndicator.PAYMENT_OVERDUE -> {
        val outstanding = flag.outstanding
        val days = flag.daysOverdue
        if (outstanding == null || days == null) {
            null
        } else {
            stringResource(R.string.project_health_overdue_why, outstanding.formatted(), days.toInt())
        }
    }

    ProjectHealthIndicator.COMPLETED_NOT_BILLED ->
        stringResource(R.string.project_health_not_billed_why)
}

@Composable
private fun healthColor(indicator: ProjectHealthIndicator): Color = when (indicator) {
    ProjectHealthIndicator.ON_TRACK -> auroraSemantics.successInk
    ProjectHealthIndicator.NEAR_HOUR_BUDGET,
    ProjectHealthIndicator.PAYMENT_DUE_SOON,
    ProjectHealthIndicator.COMPLETED_NOT_BILLED -> MaterialTheme.colorScheme.tertiary
    ProjectHealthIndicator.OVER_HOUR_BUDGET,
    ProjectHealthIndicator.RATE_BELOW_TARGET,
    ProjectHealthIndicator.PAYMENT_OVERDUE -> MaterialTheme.colorScheme.error
}
