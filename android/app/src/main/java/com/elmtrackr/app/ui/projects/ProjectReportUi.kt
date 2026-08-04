package com.elmtrackr.app.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.projects.ProjectBillingStatus
import com.elmtrackr.app.domain.projects.ProjectHealthResolver
import com.elmtrackr.app.domain.projects.ProjectReport
import com.elmtrackr.app.domain.projects.ProjectReportFilter
import com.elmtrackr.app.ui.theme.Spacing

/**
 * The project report.
 *
 * Every money figure goes through [MoneyByCurrencyRows], so a two-currency report
 * shows one row per currency and never a combined total. The five accounting
 * bases each get their own labelled line — contracted, billed, received, tax and
 * outstanding — because they are not interchangeable.
 */
@Composable
fun ProjectReportSection(
    report: ProjectReport,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (report.isEmpty) {
            ProjectSectionCard(stringResource(R.string.project_report_title)) {
                ProjectNoteText(stringResource(R.string.project_report_empty))
            }
            return@Column
        }

        // ── Time. Hours may be summed across projects and currencies. ─────────
        ProjectSectionCard(stringResource(R.string.project_report_section_time)) {
            ProjectInfoRow(
                label = stringResource(R.string.project_report_tracked_hours),
                value = formattedMinutes(report.trackedMinutes),
                emphasis = true,
            )
            ProjectInfoRow(
                label = stringResource(R.string.project_report_active_days),
                value = report.activeDays.toString(),
            )
            ProjectInfoRow(
                label = stringResource(R.string.project_report_average),
                value = formattedMinutes(report.averageMinutesPerProject),
            )
            if (report.minutesOverBudget > 0) {
                ProjectInfoRow(
                    label = stringResource(R.string.project_report_over_budget),
                    value = formattedMinutes(report.minutesOverBudget),
                    valueColor = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // ── Value. One line per basis, each grouped by currency. ──────────────
        ProjectSectionCard(stringResource(R.string.project_report_section_value)) {
            MoneyByCurrencyRows(
                label = stringResource(R.string.project_report_contracted),
                amounts = report.totals.contractedBase,
                emphasis = true,
            )
            MoneyByCurrencyRows(
                label = stringResource(R.string.project_report_tax),
                amounts = report.totals.contractedTax,
            )
            MoneyByCurrencyRows(
                label = stringResource(R.string.project_report_client_totals),
                amounts = report.totals.contractedClientTotal,
            )
            Spacer(Modifier.height(Spacing.sm))
            MoneyByCurrencyRows(
                label = stringResource(R.string.project_report_billed),
                amounts = report.totals.billed,
            )
            MoneyByCurrencyRows(
                label = stringResource(R.string.project_basis_cash),
                amounts = report.totals.received,
            )
            MoneyByCurrencyRows(
                label = stringResource(R.string.project_basis_tax),
                amounts = report.totals.receivedTax,
            )
            MoneyByCurrencyRows(
                label = stringResource(R.string.project_report_outstanding),
                amounts = report.totals.outstanding,
                emphasis = true,
            )
            if (report.hasMultipleCurrencies) {
                Spacer(Modifier.height(Spacing.xs))
                ProjectNoteText(stringResource(R.string.project_report_currency_note))
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // ── Revenue efficiency. Explicitly not profit. ────────────────────────
        if (report.efficiency.any { it.valuePerHour != null }) {
            ProjectSectionCard(stringResource(R.string.project_report_section_efficiency)) {
                report.efficiency.forEach { efficiency ->
                    efficiency.valuePerHour?.let { perHour ->
                        ProjectInfoRow(
                            label = if (report.hasMultipleCurrencies) {
                                stringResource(R.string.project_report_value_per_hour) +
                                    " · ${efficiency.currencyCode}"
                            } else {
                                stringResource(R.string.project_report_value_per_hour)
                            },
                            value = perHour.formatted(),
                            emphasis = true,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                ProjectNoteText(stringResource(R.string.project_report_efficiency_note))
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // ── Per project, with its health indicators and their reasons. ────────
        report.projects.forEach { insights ->
            ProjectSectionCard(insights.projectName) {
                insights.clientName?.let { ProjectNoteText(it) }
                ProjectInfoRow(
                    label = stringResource(R.string.project_report_tracked_hours),
                    value = formattedMinutes(insights.trackedMinutes),
                )
                ProjectInfoRow(
                    label = stringResource(R.string.project_report_contracted),
                    value = insights.baseFee.formatted(),
                )
                insights.effectiveHourlyRate?.let { rate ->
                    ProjectInfoRow(
                        // Labelled all-time here, unlike the project screens: this row
                        // sits in a monthly report next to period figures, and the rate
                        // is the one number on it that is not period-scoped.
                        label = stringResource(R.string.project_report_effective_rate_all_time),
                        value = rate.formatted(),
                    )
                }
                ProjectInfoRow(
                    label = stringResource(R.string.project_report_outstanding),
                    // The period balance, matching the report's own totals block.
                    // Showing the all-time balance here put a figure on the row that
                    // the TOTAL row below it did not include.
                    value = (insights.periodOutstanding ?: insights.outstandingTotal).formatted(),
                    valueColor = if (insights.daysOverdue != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.height(Spacing.xs))
                ProjectHealthChips(ProjectHealthResolver.flagsFor(insights))
            }
            Spacer(Modifier.height(Spacing.sm))
        }

        TextButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.project_report_export))
        }
    }
}

/**
 * Report filters.
 *
 * Only offers values that actually occur in the data, so the user cannot filter
 * to an empty result by picking a currency or client they never used.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectReportFilters(
    filter: ProjectReportFilter,
    availableClients: List<String>,
    availableCurrencies: List<String>,
    availableProjects: List<com.elmtrackr.app.domain.model.Project>,
    onFilterChange: (ProjectReportFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    ProjectSectionCard(stringResource(R.string.project_filter_title), modifier = modifier) {
        FilterRow(stringResource(R.string.project_filter_project)) {
            AnyChip(filter.projectId == null) { onFilterChange(filter.copy(projectId = null)) }
            availableProjects.forEach { project ->
                FilterChip(
                    selected = filter.projectId == project.id,
                    onClick = { onFilterChange(filter.copy(projectId = project.id)) },
                    label = { Text(project.name) },
                )
            }
        }

        if (availableClients.isNotEmpty()) {
            FilterRow(stringResource(R.string.project_filter_client)) {
                AnyChip(filter.clientName.isNullOrBlank()) {
                    onFilterChange(filter.copy(clientName = null))
                }
                availableClients.forEach { client ->
                    FilterChip(
                        selected = filter.clientName == client,
                        onClick = { onFilterChange(filter.copy(clientName = client)) },
                        label = { Text(client) },
                    )
                }
            }
        }

        FilterRow(stringResource(R.string.project_filter_work_status)) {
            AnyChip(filter.workStatus == null) { onFilterChange(filter.copy(workStatus = null)) }
            ProjectWorkStatus.entries.forEach { status ->
                FilterChip(
                    selected = filter.workStatus == status,
                    onClick = { onFilterChange(filter.copy(workStatus = status)) },
                    label = { Text(workStatusLabel(status)) },
                )
            }
        }

        FilterRow(stringResource(R.string.project_filter_billing_status)) {
            AnyChip(filter.billingStatus == null) {
                onFilterChange(filter.copy(billingStatus = null))
            }
            ProjectBillingStatus.entries.forEach { status ->
                FilterChip(
                    selected = filter.billingStatus == status,
                    onClick = { onFilterChange(filter.copy(billingStatus = status)) },
                    label = { Text(billingStatusLabel(status)) },
                )
            }
        }

        // Only worth offering when there is more than one currency to choose from.
        if (availableCurrencies.size > 1) {
            FilterRow(stringResource(R.string.project_filter_currency)) {
                AnyChip(filter.currencyCode == null) {
                    onFilterChange(filter.copy(currencyCode = null))
                }
                availableCurrencies.forEach { currency ->
                    FilterChip(
                        selected = filter.currencyCode == currency,
                        onClick = { onFilterChange(filter.copy(currencyCode = currency)) },
                        label = { Text(currency) },
                    )
                }
            }
        }

        FilterRow("") {
            FilterChip(
                selected = filter.includeArchived,
                onClick = { onFilterChange(filter.copy(includeArchived = !filter.includeArchived)) },
                label = { Text(stringResource(R.string.project_filter_include_archived)) },
            )
        }

        if (!filter.isEmpty) {
            TextButton(
                onClick = {
                    onFilterChange(ProjectReportFilter(from = filter.from, to = filter.to))
                },
            ) { Text(stringResource(R.string.project_filter_clear)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(label: String, content: @Composable () -> Unit) {
    if (label.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun AnyChip(selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(R.string.project_filter_any)) },
    )
}
