package com.elmtrackr.app.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.projects.ProjectBillingStatusResolver
import com.elmtrackr.app.domain.projects.ProjectSummary
import com.elmtrackr.app.domain.projects.ProjectWorkAction
import com.elmtrackr.app.domain.projects.ProjectWorkStatusActions
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.settings.SettingsDetailHeader
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing

/**
 * Read-only view of one project: Overview, Time, Billing and Notes, plus the
 * work-status actions.
 *
 * Work status and billing status are presented as two separate facts. Nothing on
 * this screen infers one from the other.
 */
@Composable
fun ProjectDetailScreen(
    summary: ProjectSummary,
    billingRecords: List<ProjectBillingRecord>,
    payments: List<ProjectPayment>,
    onEdit: () -> Unit,
    onWorkAction: (ProjectWorkAction) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val project = summary.project
    var confirmDelete by rememberSaveable(project.id) { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.project_delete_confirm_title)) },
            text = { Text(stringResource(R.string.project_delete_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text(
                        stringResource(R.string.project_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.project_delete_keep))
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { SettingsDetailHeader(title = project.name, onBack = onBack) }

        // ── Overview ──────────────────────────────────────────────────────────
        item {
            ProjectSectionCard(stringResource(R.string.project_detail_section_overview)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    WorkStatusPill(project.workStatus)
                    BillingStatusPill(summary.billing.status)
                }
                Spacer(Modifier.height(Spacing.sm))
                ProjectInfoRow(
                    label = stringResource(R.string.project_detail_client),
                    value = project.clientName ?: stringResource(R.string.project_detail_not_set),
                )
                ProjectInfoRow(
                    label = stringResource(R.string.project_detail_currency),
                    value = project.currencyCode,
                )
                Spacer(Modifier.height(Spacing.xs))
                ProjectFeeBreakdown(summary.fee)
                Spacer(Modifier.height(Spacing.xs))
                project.startDate?.let {
                    ProjectInfoRow(stringResource(R.string.project_detail_start_date), it.formattedMedium())
                }
                project.deadline?.let {
                    ProjectInfoRow(
                        label = stringResource(R.string.project_detail_deadline),
                        value = it.formattedMedium(),
                    )
                }
                project.completionDate?.let {
                    ProjectInfoRow(stringResource(R.string.project_detail_completed_on), it.formattedMedium())
                }
            }
        }

        // ── Time ──────────────────────────────────────────────────────────────
        item {
            ProjectSectionCard(stringResource(R.string.project_detail_section_time)) {
                ProjectInfoRow(
                    label = stringResource(R.string.project_detail_hours_tracked),
                    value = formattedMinutes(summary.time.trackedMinutes),
                    emphasis = true,
                )
                ProjectInfoRow(
                    label = stringResource(R.string.project_detail_shift_count, summary.time.shiftCount),
                    value = formattedMinutes(summary.time.trackedMinutes),
                )
                project.hourBudgetMinutes?.let { budget ->
                    ProjectInfoRow(
                        label = stringResource(R.string.project_detail_hour_budget),
                        value = formattedMinutes(budget),
                    )
                    summary.budgetUtilization?.let { used ->
                        Spacer(Modifier.height(Spacing.xs))
                        LinearProgressIndicator(
                            progress = { used.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        ProjectNoteText(
                            stringResource(
                                R.string.project_detail_budget_used,
                                formattedMinutes(summary.time.trackedMinutes),
                                formattedMinutes(budget),
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                ProjectInfoRow(
                    label = stringResource(R.string.project_detail_effective_rate),
                    value = summary.effectiveHourlyRate?.formatted()
                        ?: stringResource(R.string.project_detail_no_rate_yet),
                    emphasis = true,
                )
                project.targetHourlyRate?.let {
                    ProjectInfoRow(
                        label = stringResource(R.string.project_detail_target_rate),
                        value = it.formatted(),
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                ProjectNoteText(stringResource(R.string.project_detail_effective_rate_note))
                if (!summary.time.hasTime) {
                    Spacer(Modifier.height(Spacing.xs))
                    ProjectNoteText(stringResource(R.string.project_detail_no_time_yet))
                }
                Spacer(Modifier.height(Spacing.xs))
                ProjectNoteText(stringResource(R.string.project_detail_time_pending))
            }
        }

        // ── Billing ───────────────────────────────────────────────────────────
        item {
            ProjectSectionCard(stringResource(R.string.project_detail_section_billing)) {
                ProjectInfoRow(
                    label = stringResource(R.string.project_detail_billing_status),
                    value = billingStatusLabel(summary.billing.status),
                    emphasis = true,
                )
                val active = ProjectBillingStatusResolver.activeRecords(billingRecords)
                if (active.isEmpty()) {
                    Spacer(Modifier.height(Spacing.xs))
                    ProjectNoteText(stringResource(R.string.project_detail_no_billing))
                } else {
                    active.forEach { record ->
                        record.externalReference?.let {
                            ProjectInfoRow(stringResource(R.string.project_detail_billing_reference), it)
                        }
                        ProjectInfoRow(
                            label = stringResource(R.string.project_detail_billed_on),
                            value = record.billedOn.formattedMedium(),
                        )
                        record.dueOn?.let { due ->
                            ProjectInfoRow(
                                label = stringResource(R.string.project_detail_due_on),
                                value = due.formattedMedium(),
                                valueColor = if (summary.billing.isOverdue) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
                if (billingRecords.any { it.isCancelled }) {
                    Spacer(Modifier.height(Spacing.xs))
                    ProjectNoteText(stringResource(R.string.project_detail_cancelled_billing))
                }
                Spacer(Modifier.height(Spacing.sm))
                ProjectInfoRow(
                    label = stringResource(R.string.project_detail_payments),
                    value = stringResource(R.string.project_detail_payment_count, payments.size),
                )
                ProjectInfoRow(
                    label = stringResource(R.string.project_detail_total_paid),
                    value = summary.billing.paid.formatted(),
                )
                ProjectInfoRow(
                    label = stringResource(R.string.project_detail_outstanding),
                    value = summary.billing.outstanding.formatted(),
                    emphasis = true,
                    valueColor = if (summary.billing.isOverdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (summary.billing.credit.isPositive) {
                    ProjectInfoRow(
                        label = stringResource(R.string.project_detail_credit),
                        value = summary.billing.credit.formatted(),
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                ProjectNoteText(stringResource(R.string.project_detail_billing_disclaimer))
            }
        }

        // ── Notes ─────────────────────────────────────────────────────────────
        item {
            ProjectSectionCard(stringResource(R.string.project_detail_section_notes)) {
                Text(
                    text = project.description ?: stringResource(R.string.project_detail_no_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                project.notes?.let { notes ->
                    Spacer(Modifier.height(Spacing.sm))
                    Text(text = notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ── Actions ───────────────────────────────────────────────────────────
        item {
            ProjectSectionCard(stringResource(R.string.project_actions_title)) {
                ProjectWorkStatusActions.available(project.workStatus).forEach { action ->
                    OutlinedButton(
                        onClick = { onWorkAction(action) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) { Text(stringResource(ProjectLabels.workAction(action))) }
                }
                Spacer(Modifier.height(Spacing.xs))
                ProjectNoteText(stringResource(R.string.project_actions_note))
            }
        }

        item {
            ElmGradientButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.project_detail_edit), fontWeight = FontWeight.Bold)
            }
        }

        item {
            // Permanent deletion is only offered for an unused draft; anything
            // with time, billing or payments is archived so reports keep it.
            if (summary.canDeletePermanently) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.project_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.project_delete_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}
