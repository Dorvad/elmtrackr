package com.elmtrackr.app.ui.reports

import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.model.WeeklyTotals
import com.elmtrackr.app.domain.DailyInsight
import com.elmtrackr.app.domain.ReportInsights

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
        val featuresTravelRefunds: Boolean = false,
        val insights: ReportInsights? = null,
        val dailyInsights: List<DailyInsight> = emptyList(),
        val previousMonthMinutes: Int = 0,
        val allShifts: List<Shift> = emptyList(),
        val refundClaims: List<RefundClaim> = emptyList(),
    ) : ReportsUiState

    data class Error(val message: String) : ReportsUiState
}
