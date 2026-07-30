package com.elmtrackr.app.ui.projects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.projects.ProjectSummary
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing

/**
 * One project in the list.
 *
 * Collapsed, the card answers "what is this, how is it going, and am I owed
 * money" — name, client, both statuses, client total and outstanding. Hours,
 * effective rate and deadline sit behind "More detail", so a long list stays
 * scannable instead of turning into a wall of figures.
 */
@Composable
fun ProjectCard(
    summary: ProjectSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val project = summary.project
    // Keyed by project id so expanding one card and rotating keeps that card
    // open, rather than whichever card now sits in the same list position.
    var expanded by rememberSaveable(project.id) { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                    )
                    project.clientName?.let { client ->
                        Text(
                            text = client,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = summary.fee.clientTotal.formatted(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = project.currencyCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                WorkStatusPill(project.workStatus)
                BillingStatusPill(summary.billing.status)
            }

            if (summary.billing.outstanding.isPositive) {
                Spacer(Modifier.height(Spacing.xs))
                ProjectInfoRow(
                    // Short form: a card row is the one place the full glossary
                    // term does not fit beside its amount.
                    label = stringResource(R.string.project_detail_outstanding_short),
                    value = summary.billing.outstanding.formatted(),
                    emphasis = true,
                    valueColor = if (summary.billing.isOverdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    ProjectInfoRow(
                        label = stringResource(R.string.project_detail_hours_tracked),
                        value = formattedMinutes(summary.time.trackedMinutes),
                    )
                    ProjectInfoRow(
                        label = stringResource(R.string.project_detail_effective_rate),
                        value = summary.effectiveHourlyRate?.formatted()
                            ?: stringResource(R.string.project_detail_no_rate_yet),
                    )
                    if (summary.billing.paid.isPositive) {
                        ProjectInfoRow(
                            label = stringResource(R.string.project_detail_total_paid),
                            value = summary.billing.paid.formatted(),
                        )
                    }
                    project.deadline?.let { deadline ->
                        ProjectInfoRow(
                            label = stringResource(R.string.project_detail_deadline),
                            value = deadline.formattedMedium(),
                            valueColor = if (summary.billing.isOverdue) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }

            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.projects_show_less else R.string.projects_show_more,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(Spacing.xs))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.height(18.dp),
                )
            }
        }
    }
}
