package com.elmtrackr.app.ui.dashboard

import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.tasks.TaskSuggestionReason
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.projects.ProjectClockInOption
import com.elmtrackr.app.domain.setup.SetupStepState

/** Getting-started checklist as rendered on the dashboard. */
data class SetupChecklistUiState(
    val steps: List<SetupStepState>,
    val completedCount: Int,
    val totalCount: Int,
    val showCelebration: Boolean,
)

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data class Ready(
        val activeShift: Shift?,
        val monthlyReport: MonthlyReport?,
        val settings: UserSettings,
        val profiles: List<com.elmtrackr.app.domain.model.CompensationProfile> = emptyList(),
        /**
         * The work profile the next clock-in books to.
         *
         * Only meaningful when [profiles] holds more than one: with a single job
         * there is nothing to choose, and the selector stays off screen rather
         * than occupying a row above the clock to state the obvious.
         */
        val selectedWorkProfileId: String? = null,
        /**
         * Tasks belonging to [selectedWorkProfileId], not every task the user has.
         *
         * A task is something you do at one job, so scoping the picker is what
         * stops it growing without bound as jobs are added — the selector above it
         * has already narrowed the question.
         */
        val activeTasks: List<Task> = emptyList(),
        val selectedTaskId: String? = null,
        val suggestedTaskId: String? = null,
        val showSuggestedNow: Boolean = false,
        val suggestionReason: TaskSuggestionReason? = null,
        val recentShifts: List<Shift> = emptyList(),
        val displayName: String? = null,
        val unresolvedRefundCount: Int = 0,
        /**
         * Whether the end-of-month refund reminder was dismissed *for the current
         * month*. Persisted, so it survives a relaunch, and re-arms next month.
         */
        val refundReminderDismissed: Boolean = false,
        val todayCompletedMinutes: Int = 0,
        val paySummary: PayrollCalculator.MonthlyPaySummary? = null,
        /**
         * Clockable projects. Empty when Paid Projects is disabled, which is what
         * hides the compensation-source selector entirely.
         */
        val projectOptions: List<ProjectClockInOption> = emptyList(),
        /** What the next clock-in will record. */
        val selectedCompensationSource: CompensationSource = CompensationSource.EMPLOYEE,
        val selectedProjectId: String? = null,
        /** Optional note carried into the project shift on clock-in. */
        val projectClockInNote: String = "",
        /**
         * The project an active *project* shift belongs to. Null for an employee
         * shift, for no active shift, or when the project row can no longer be
         * read. The live figures are projected in the card, which owns the tick.
         */
        val activeProject: Project? = null,
        /** That project's shifts, for its banked-hours total. */
        val activeProjectShifts: List<Shift> = emptyList(),
        /**
         * The restrained project block. Null while Paid Projects is off, which
         * leaves the dashboard exactly as it was for an hourly-only user.
         */
        val projectSummary: com.elmtrackr.app.domain.projects.ProjectDashboardSummary? = null,
        /**
         * The one-time v1.2 update wizard for existing users: onboarding done,
         * Paid Projects off, and the wizard not yet dismissed.
         */
        val showPaidProjectsWizard: Boolean = false,
    ) : DashboardUiState {
        /** The selector only appears when there is a project to pick. */
        val canSelectProjectTime: Boolean get() = projectOptions.isNotEmpty()

        val activeShiftIsProjectTime: Boolean get() = activeShift?.isProjectTime == true
    }

    data class Error(val message: String) : DashboardUiState
}
