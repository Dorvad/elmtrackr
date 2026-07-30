package com.elmtrackr.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.projects.ProjectDashboardSummary
import com.elmtrackr.app.ui.projects.MoneyByCurrencyRows
import com.elmtrackr.app.ui.projects.ProjectNoteText
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing

/**
 * The project block on the dashboard. Only shown when Paid Projects is on.
 *
 * Restrained on purpose: two counts, two balances, one cash figure. It sits
 * *alongside* the existing hourly content rather than replacing any of it, and it
 * never combines the two — the note at the bottom says why in the user's own
 * terms, because an unexplained "gross income" mixing work performed with cash
 * received would be the single most misleading number the app could show.
 */
@Composable
fun ProjectDashboardCard(
    summary: ProjectDashboardSummary,
    onOpenProjects: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Nothing to say yet: no projects, no money. The dashboard stays as it was.
    if (summary.isEmpty) return

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.project_dash_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenProjects) {
                    Text(stringResource(R.string.project_dash_open))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = stringResource(R.string.project_dash_active, summary.activeProjectCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (summary.unbilledProjectCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.project_dash_unbilled,
                            summary.unbilledProjectCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            // Balances. Each renders per currency; nothing is summed across them.
            MoneyByCurrencyRows(
                label = stringResource(R.string.project_dash_outstanding),
                amounts = summary.outstanding,
            )
            MoneyByCurrencyRows(
                label = stringResource(R.string.project_dash_overdue),
                amounts = summary.overdue,
                valueColor = MaterialTheme.colorScheme.error,
            )

            if (summary.receivedThisMonth.isNotEmpty) {
                Spacer(Modifier.height(Spacing.sm))
                // Cash received — a different basis from hourly earnings, and
                // labelled as such rather than merged with them.
                MoneyByCurrencyRows(
                    label = stringResource(R.string.project_dash_received),
                    amounts = summary.receivedThisMonth,
                    emphasis = true,
                )
                MoneyByCurrencyRows(
                    label = stringResource(R.string.project_dash_revenue_before_tax),
                    amounts = summary.receivedBeforeTaxThisMonth,
                )
                // Tax is shown apart because it is not the user's revenue.
                MoneyByCurrencyRows(
                    label = stringResource(R.string.project_dash_tax_collected),
                    amounts = summary.receivedTaxThisMonth,
                )
            }

            if (summary.needsAttentionCount > 0) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(
                        R.string.project_dash_attention,
                        summary.needsAttentionCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(Spacing.xs))
            ProjectNoteText(stringResource(R.string.project_dash_basis_note))
        }
    }
}
