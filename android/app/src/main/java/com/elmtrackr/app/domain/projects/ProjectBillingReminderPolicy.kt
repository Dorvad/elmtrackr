package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.money.Money
import java.time.LocalDate

/** Which billing reminder a project earns today. */
enum class BillingReminderKind {
    /** Due soon, nothing received yet or only part. */
    UPCOMING,

    /** Due today. */
    DUE_TODAY,

    /** The due date has passed and money is still owed. */
    OVERDUE,
}

/**
 * One notification to show.
 *
 * Carries the figures the notification text needs, so the worker does no money
 * arithmetic of its own.
 */
data class BillingReminder(
    val projectId: String,
    val projectName: String,
    val clientName: String?,
    val billingRecordId: String,
    val kind: BillingReminderKind,
    val outstanding: Money,
    val dueOn: LocalDate,
    /** Days until due; negative once overdue, zero on the due date. */
    val daysUntilDue: Long,
)

/**
 * Decides which projects should notify the user about money owed, and when.
 *
 * Pure and date-only: reminders are scheduled from a [LocalDate] in the user's
 * work timezone, never from an instant. A due date is a calendar fact — comparing
 * timestamps makes a project overdue in the evening in one zone and the next
 * morning in another.
 *
 * Restrained by design. At most one reminder per project per run, and the
 * upcoming reminder fires on exactly one day rather than every day in the
 * run-up, so a project with a distant due date does not nag daily.
 */
object ProjectBillingReminderPolicy {

    /** How far ahead the single "due soon" reminder fires. */
    const val UPCOMING_LEAD_DAYS = 3L

    /**
     * How often an overdue project is allowed to re-notify. A weekly nudge is
     * enough to be useful without becoming noise the user swipes away unread.
     */
    const val OVERDUE_REPEAT_DAYS = 7L

    /**
     * Reminders due on [today] for one project.
     *
     * Returns null — no notification — unless all of these hold:
     *  - a billing record exists and is not cancelled;
     *  - it has a due date;
     *  - money is still outstanding.
     *
     * A settled project never notifies, however late it was paid, and a cancelled
     * billing record never notifies at all.
     */
    fun reminderFor(
        project: Project,
        billingRecords: List<ProjectBillingRecord>,
        payments: List<ProjectPayment>,
        today: LocalDate,
    ): BillingReminder? {
        val state = ProjectBillingStatusResolver.resolve(
            currencyCode = project.currencyCode,
            billingRecords = billingRecords,
            payments = payments,
            today = today,
        )
        val recordId = state.activeBillingRecordId ?: return null
        val dueOn = state.dueOn ?: return null
        if (!state.outstanding.isPositive) return null

        val daysUntilDue = state.daysUntilDue ?: return null
        val kind = when {
            daysUntilDue < 0 -> {
                // Weekly after the due date, counted from the due date itself, so
                // the cadence does not depend on when the worker happened to run.
                val daysLate = -daysUntilDue
                if (daysLate % OVERDUE_REPEAT_DAYS != 0L) return null
                BillingReminderKind.OVERDUE
            }
            daysUntilDue == 0L -> BillingReminderKind.DUE_TODAY
            daysUntilDue == UPCOMING_LEAD_DAYS -> BillingReminderKind.UPCOMING
            else -> return null
        }

        return BillingReminder(
            projectId = project.id,
            projectName = project.name,
            clientName = project.clientName,
            billingRecordId = recordId,
            kind = kind,
            outstanding = state.outstanding,
            dueOn = dueOn,
            daysUntilDue = daysUntilDue,
        )
    }

    /**
     * Every reminder to raise on [today].
     *
     * @param paidProjectsEnabled the feature flag. False produces no reminders at
     * all, which is what suppresses them while the module is off — the billing
     * data itself is untouched and reminders resume when it is switched back on.
     * @param notificationsEnabled the user's notification preference.
     */
    fun remindersFor(
        paidProjectsEnabled: Boolean,
        notificationsEnabled: Boolean,
        projects: List<Project>,
        billingRecords: List<ProjectBillingRecord>,
        payments: List<ProjectPayment>,
        today: LocalDate,
    ): List<BillingReminder> {
        if (!paidProjectsEnabled || !notificationsEnabled) return emptyList()
        val recordsByProject = billingRecords.groupBy { it.projectId }
        val paymentsByProject = payments.groupBy { it.projectId }
        return projects.mapNotNull { project ->
            reminderFor(
                project = project,
                billingRecords = recordsByProject[project.id].orEmpty(),
                payments = paymentsByProject[project.id].orEmpty(),
                today = today,
            )
        }
    }
}
