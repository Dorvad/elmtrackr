package com.elmtrackr.app.ui.reports

import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ReportScope
import com.elmtrackr.app.domain.leave.AbsenceListEntry
import com.elmtrackr.app.domain.leave.MonthlyLeaveEarnings
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.TaskMonthlyBreakdown
import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.model.WeeklyTotals
import com.elmtrackr.app.domain.DailyInsight
import com.elmtrackr.app.domain.ReportInsights
import java.time.ZoneId

sealed interface ReportsUiState {
    data object Loading : ReportsUiState
    data object Empty : ReportsUiState

    data class Ready(
        val year: Int,
        val month: Int,
        val report: MonthlyReport,
        val weeklyTotals: List<WeeklyTotals>,
        val paySummary: PayrollCalculator.MonthlyPaySummary? = null,
        val rawShifts: List<Shift> = emptyList(),
        /**
         * The pay weeks [rawShifts] belong to — the month plus the tail of the week
         * containing the 1st. Only [rawShifts] is displayed; this is what weekly
         * overtime accumulates over, and every figure derived on this screen has to
         * use it or two figures on one screen disagree.
         */
        val payContextShifts: List<Shift> = emptyList(),
        val settings: UserSettings? = null,
        val profiles: List<CompensationProfile> = emptyList(),
        val premiumProfiles: List<PremiumProfile> = emptyList(),
        val featuresTravelRefunds: Boolean = false,
        val insights: ReportInsights? = null,
        val dailyInsights: List<DailyInsight> = emptyList(),
        val previousMonthMinutes: Int = 0,
        val allShifts: List<Shift> = emptyList(),
        val refundClaims: List<RefundClaim> = emptyList(),
        val taskBreakdown: List<TaskMonthlyBreakdown> = emptyList(),
        val zone: ZoneId = ZoneId.systemDefault(),
        /**
         * The project report, or null while Paid Projects is off — which leaves the
         * hourly report exactly as it was.
         *
         * A separate field rather than folded into [report] or [paySummary]: project
         * money sits on different accounting bases from hourly earnings and is never
         * added to them.
         */
        val projectReport: com.elmtrackr.app.domain.projects.ProjectReport? = null,
        val projectFilter: com.elmtrackr.app.domain.projects.ProjectReportFilter =
            com.elmtrackr.app.domain.projects.ProjectReportFilter(),
        val projectClients: List<String> = emptyList(),
        val projectCurrencies: List<String> = emptyList(),
        val availableProjects: List<com.elmtrackr.app.domain.model.Project> = emptyList(),
        /**
         * Paid leave for this month, kept off [report]. Vacation and sick days are
         * not worked time, and `regular + overtime + weekend == total` is what the
         * distribution bar, the CSV totals row and the PDF header all assume.
         * Null when the month has no leave, so an hourly-only month is unchanged.
         */
        val paidLeave: MonthlyLeaveEarnings? = null,
        /** One row per affected day, so the report names the date the user entered. */
        val leaveEntries: List<AbsenceListEntry> = emptyList(),
        /** Which job the hours, pay, leave and exports cover. */
        val scope: ReportScope = ReportScope.AllJobs,
        /**
         * Completed hourly shifts this month across every job, so a one-job report
         * with nothing in it can say the other jobs have shifts and offer to show them.
         */
        val allJobsShiftCount: Int = 0,
    ) : ReportsUiState {
        /** The scoped job's name, or null for all jobs. */
        val scopedJobName: String?
            get() = (scope as? ReportScope.Job)?.let { job -> profiles.firstOrNull { it.id == job.profileId }?.name }

        /** The job picker is offered only when there is more than one job to pick. */
        val canChooseJob: Boolean get() = profiles.size > 1

        /**
         * Estimated leave gross per currency, alphabetical, zero buckets dropped.
         *
         * Read this rather than [MonthlyLeaveEarnings.paidLeaveGross] wherever money is
         * shown. That figure adds every allocation together, which is only a quantity of
         * anything while every workplace is paid in one currency; a month of leave at a
         * shekel job and a dollar job has two totals, not one.
         */
        val leaveGrossByCurrency: Map<String, Double>
            get() = leaveEntries
                .groupBy { it.currencyCode }
                .mapValues { (_, entries) -> entries.sumOf { it.allocation.estimatedGrossPay } }
                .filterValues { it > 0.0 }
                .toSortedMap()
    }

    data class Error(val message: String) : ReportsUiState
}
