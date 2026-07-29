package com.elmtrackr.app.ui.projects

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.projects.ProjectBillingStatus
import com.elmtrackr.app.domain.projects.ProjectFormError
import com.elmtrackr.app.domain.projects.ProjectStatusFilter
import com.elmtrackr.app.domain.projects.ProjectWorkAction
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPeachDeep
import com.elmtrackr.app.ui.theme.AuroraPlum

/**
 * Enum-to-string-resource mapping, following the PremiumTypeLabels /
 * StackingPolicyLabels pattern. Keeps every user-facing status word in
 * strings_projects.xml so Hebrew stays in lockstep with English.
 */
object ProjectLabels {

    @StringRes
    fun workStatus(status: ProjectWorkStatus): Int = when (status) {
        ProjectWorkStatus.DRAFT -> R.string.project_status_draft
        ProjectWorkStatus.ACTIVE -> R.string.project_status_active
        ProjectWorkStatus.PAUSED -> R.string.project_status_paused
        ProjectWorkStatus.COMPLETED -> R.string.project_status_completed
        ProjectWorkStatus.CANCELLED -> R.string.project_status_cancelled
        ProjectWorkStatus.ARCHIVED -> R.string.project_status_archived
    }

    @StringRes
    fun billingStatus(status: ProjectBillingStatus): Int = when (status) {
        ProjectBillingStatus.NOT_BILLED -> R.string.project_billing_not_billed
        ProjectBillingStatus.BILLED -> R.string.project_billing_billed
        ProjectBillingStatus.PARTIALLY_PAID -> R.string.project_billing_partially_paid
        ProjectBillingStatus.PAID -> R.string.project_billing_paid
        ProjectBillingStatus.OVERDUE -> R.string.project_billing_overdue
    }

    @StringRes
    fun statusFilter(filter: ProjectStatusFilter): Int = when (filter) {
        ProjectStatusFilter.ALL -> R.string.project_filter_all
        ProjectStatusFilter.ACTIVE -> R.string.project_status_active
        ProjectStatusFilter.DRAFT -> R.string.project_status_draft
        ProjectStatusFilter.PAUSED -> R.string.project_status_paused
        ProjectStatusFilter.COMPLETED -> R.string.project_status_completed
        ProjectStatusFilter.CANCELLED -> R.string.project_status_cancelled
        ProjectStatusFilter.ARCHIVED -> R.string.project_status_archived
    }

    @StringRes
    fun workAction(action: ProjectWorkAction): Int = when (action) {
        ProjectWorkAction.SAVE_AS_DRAFT -> R.string.project_action_save_draft
        ProjectWorkAction.START -> R.string.project_action_start
        ProjectWorkAction.PAUSE -> R.string.project_action_pause
        ProjectWorkAction.RESUME -> R.string.project_action_resume
        ProjectWorkAction.COMPLETE -> R.string.project_action_complete
        ProjectWorkAction.CANCEL -> R.string.project_action_cancel
        ProjectWorkAction.ARCHIVE -> R.string.project_action_archive
        ProjectWorkAction.UNARCHIVE -> R.string.project_action_unarchive
    }

    @StringRes
    fun formError(error: ProjectFormError): Int = when (error) {
        ProjectFormError.REQUIRED -> R.string.project_error_required
        ProjectFormError.NOT_A_NUMBER -> R.string.project_error_not_a_number
        ProjectFormError.MUST_BE_POSITIVE -> R.string.project_error_positive
        ProjectFormError.TAX_RATE_OUT_OF_RANGE -> R.string.project_error_tax_rate
        ProjectFormError.INVALID_CURRENCY -> R.string.project_error_currency
        ProjectFormError.DEADLINE_BEFORE_START -> R.string.project_error_deadline
    }

    /** Accent per work status, reusing the Aurora palette. */
    fun workStatusColor(status: ProjectWorkStatus): Color = when (status) {
        ProjectWorkStatus.ACTIVE -> AuroraAqua
        ProjectWorkStatus.DRAFT -> AuroraIndigo
        ProjectWorkStatus.PAUSED -> AuroraPeachDeep
        ProjectWorkStatus.COMPLETED -> AuroraPlum
        ProjectWorkStatus.CANCELLED, ProjectWorkStatus.ARCHIVED -> AuroraIndigo
    }
}

@Composable
fun workStatusLabel(status: ProjectWorkStatus): String =
    stringResource(ProjectLabels.workStatus(status))

@Composable
fun billingStatusLabel(status: ProjectBillingStatus): String =
    stringResource(ProjectLabels.billingStatus(status))
