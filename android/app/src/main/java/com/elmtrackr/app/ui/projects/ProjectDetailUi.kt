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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.projects.ProjectBillingCorrection
import com.elmtrackr.app.domain.projects.ProjectBillingStatusResolver
import com.elmtrackr.app.domain.projects.ProjectSummary
import com.elmtrackr.app.domain.projects.ProjectWorkAction
import com.elmtrackr.app.domain.HoursFormatter
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.projects.ProjectWorkStatusActions
import com.elmtrackr.app.domain.text.BidiText
import androidx.compose.ui.graphics.StrokeCap
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmOutlinedButton
import com.elmtrackr.app.ui.settings.SettingsDetailHeader
import com.elmtrackr.app.ui.theme.AuroraPeachDeep
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.common.appLocale

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
    onRecordBilling: () -> Unit = {},
    onRecordPayment: (ProjectBillingRecord) -> Unit = {},
    onCancelBilling: (ProjectBillingRecord) -> Unit = {},
    onRestoreBilling: (String) -> Unit = {},
    onDeletePayment: (String) -> Unit = {},
    /** Set when a correction was refused, so the reason can be shown. */
    correctionBlock: ProjectBillingCorrection.Block? = null,
    onDismissCorrectionBlock: () -> Unit = {},
) {
    val project = summary.project
    var confirmDelete by rememberSaveable(project.id) { mutableStateOf(false) }
    var confirmCancelBilling by rememberSaveable(project.id) { mutableStateOf<String?>(null) }

    correctionBlock?.let { block ->
        AlertDialog(
            onDismissRequest = onDismissCorrectionBlock,
            title = { Text(stringResource(R.string.project_billing_correct)) },
            text = { Text(correctionBlockedMessage(block)) },
            confirmButton = {
                TextButton(onClick = onDismissCorrectionBlock) {
                    Text(stringResource(R.string.project_billing_cancel_keep))
                }
            },
        )
    }

    confirmCancelBilling?.let { recordId ->
        val record = billingRecords.firstOrNull { it.id == recordId }
        AlertDialog(
            onDismissRequest = { confirmCancelBilling = null },
            title = { Text(stringResource(R.string.project_billing_cancel_confirm_title)) },
            text = { Text(stringResource(R.string.project_billing_cancel_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCancelBilling = null
                        record?.let(onCancelBilling)
                    },
                ) { Text(stringResource(R.string.project_billing_cancel_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancelBilling = null }) {
                    Text(stringResource(R.string.project_billing_cancel_keep))
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.project_delete_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.project_delete_confirm_text))
                    // Named, with counts, because the two outcomes are different in
                    // kind: the hours change how they are paid, the billing history
                    // goes away. A generic "this cannot be undone" would not let the
                    // user weigh either.
                    if (summary.time.shiftCount > 0) {
                        Text(
                            stringResource(
                                R.string.project_delete_shifts_kept,
                                summary.time.shiftCount,
                                HoursFormatter.decimal(summary.time.trackedMinutes, appLocale()),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (summary.hasBillingRecords || summary.hasPayments) {
                        Text(
                            stringResource(R.string.project_delete_billing_lost),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (summary.project.workStatus != ProjectWorkStatus.ARCHIVED) {
                        Text(
                            stringResource(R.string.project_delete_archive_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
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
                // The count of shifts those hours came from. It used to repeat the
                // hours as its value, under a label that already read "3 shifts" —
                // so the card stated the same figure twice and never said how many
                // shifts there were.
                ProjectInfoRow(
                    label = stringResource(R.string.project_detail_shifts),
                    value = pluralStringResource(
                        R.plurals.project_detail_shift_count,
                        summary.time.shiftCount,
                        summary.time.shiftCount,
                    ),
                )
                project.hourBudgetMinutes?.let { budget ->
                    ProjectInfoRow(
                        label = stringResource(R.string.project_detail_hour_budget),
                        value = formattedMinutes(budget),
                    )
                    summary.budgetUtilization?.let { used ->
                        Spacer(Modifier.height(Spacing.xs))
                        // Burn bar per the Aurora reference: rounded on a tinted
                        // track, and peach once the budget is spent.
                        LinearProgressIndicator(
                            progress = { used.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = if (used > 1f) AuroraPeachDeep else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round,
                            drawStopIndicator = {},
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
                            ProjectInfoRow(
                                label = stringResource(R.string.project_detail_billing_reference),
                                value = BidiText.isolate(it),
                                a11yValue = stringResource(R.string.project_a11y_reference, it),
                            )
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
                // How late, or how long until due. Days only, from the work
                // timezone's today, so it cannot read a day out.
                //
                // Labelled as the status rather than "Due" a second time: the row
                // above already carries that label and the date, so two "Due"
                // rows read as one fact stated twice rather than a date and how
                // far away it is.
                dueStatusText(summary.billing)?.let { text ->
                    ProjectInfoRow(
                        label = stringResource(R.string.project_detail_due_status),
                        value = text,
                        valueColor = if (summary.billing.isOverdue) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                if (billingRecords.any { it.isCancelled }) {
                    Spacer(Modifier.height(Spacing.xs))
                    ProjectNoteText(stringResource(R.string.project_detail_cancelled_billing))
                }
                Spacer(Modifier.height(Spacing.sm))
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

                // Actions. "Mark as billed" only while nothing is billed; the MVP
                // shows one active record at a time, and the schema does not stop
                // more being added later.
                Spacer(Modifier.height(Spacing.sm))
                if (ProjectBillingCorrection.canRecordBilling(billingRecords)) {
                    ElmGradientButton(onClick = onRecordBilling, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.project_billing_action), fontWeight = FontWeight.Bold)
                    }
                } else {
                    val record = ProjectBillingStatusResolver.activeRecords(billingRecords).first()
                    if (summary.billing.outstanding.isPositive) {
                        ElmGradientButton(
                            onClick = { onRecordPayment(record) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.project_payment_action), fontWeight = FontWeight.Bold) }
                    }
                    TextButton(
                        onClick = { confirmCancelBilling = record.id },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.project_billing_cancel_record)) }
                }
                billingRecords.firstOrNull { it.isCancelled }?.let { cancelled ->
                    TextButton(
                        onClick = { onRestoreBilling(cancelled.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.project_billing_restore)) }
                }
            }
        }

        // ── Payment history ───────────────────────────────────────────────────
        item {
            ProjectSectionCard(stringResource(R.string.project_payment_history)) {
                if (payments.isEmpty()) {
                    ProjectNoteText(stringResource(R.string.project_payment_history_empty))
                } else {
                    payments.sortedByDescending { it.paidOn }.forEach { payment ->
                        PaymentHistoryRow(
                            payment = payment,
                            onDelete = { onDeletePayment(payment.id) },
                        )
                    }
                }
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
                    ElmOutlinedButton(
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
            // Offered for every project. It used to be offered only for an untouched
            // draft, and everything else showed a card explaining that it could be
            // archived instead — which left a project used once permanently in the
            // list. The safety moved to the confirmation, which now names the
            // consequences instead of hiding the action.
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.project_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}
