package com.elmtrackr.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.projects.ProjectDashboardSummary
import com.elmtrackr.app.ui.design.ElmCard
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.design.auroraRowClickable
import com.elmtrackr.app.ui.projects.MoneyByCurrencyRows
import com.elmtrackr.app.ui.projects.ProjectNoteText
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

    Column(modifier = modifier) {
        // Section header sits above the card, matching the other dashboard
        // sections, with the navigation link on the same line.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ElmSectionHeader(title = stringResource(R.string.project_dash_title))
            Text(
                text = stringResource(R.string.project_dash_open),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .auroraRowClickable(onClick = onOpenProjects)
                    .semantics { role = Role.Button },
            )
        }

        ElmCard {
            Column(Modifier.padding(Spacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CountBadge(
                        text = stringResource(R.string.project_dash_active, summary.activeProjectCount),
                        accent = MaterialTheme.colorScheme.primary,
                    )
                    if (summary.unbilledProjectCount > 0) {
                        CountBadge(
                            text = stringResource(
                                R.string.project_dash_unbilled,
                                summary.unbilledProjectCount,
                            ),
                            accent = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Spacer(Modifier.height(Spacing.xs))
                    Divider()
                    Spacer(Modifier.height(Spacing.xs))
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
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(Spacing.xs))
                ProjectNoteText(stringResource(R.string.project_dash_basis_note))
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** Small rounded count badge, e.g. "2 active". */
@Composable
private fun CountBadge(text: String, accent: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}
