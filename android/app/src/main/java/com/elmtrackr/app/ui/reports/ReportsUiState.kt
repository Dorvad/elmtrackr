package com.elmtrackr.app.ui.reports

import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.WeeklyTotals

sealed interface ReportsUiState {
    data object Loading : ReportsUiState
    data object Empty : ReportsUiState

    data class Ready(
        val year: Int,
        val month: Int,
        val report: MonthlyReport,
        val weeklyTotals: List<WeeklyTotals>,
    ) : ReportsUiState

    data class Error(val message: String) : ReportsUiState
}
