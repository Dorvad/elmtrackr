package com.elmtrackr.app.ui.reports

import com.elmtrackr.app.domain.PayrollCalculator
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
    ) : ReportsUiState

    data class Error(val message: String) : ReportsUiState
}
