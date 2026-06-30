package com.elmtrackr.app.ui.dashboard

import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.model.UserSettings

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data class Ready(
        val activeShift: Shift?,
        val monthlyReport: MonthlyReport?,
        val settings: UserSettings,
        val profiles: List<com.elmtrackr.app.domain.model.CompensationProfile> = emptyList(),
        val activeTasks: List<Task> = emptyList(),
        val selectedTaskId: String? = null,
        val suggestedTaskId: String? = null,
        val showSuggestedNow: Boolean = false,
        val suggestionExplanation: String? = null,
        val recentShifts: List<Shift> = emptyList(),
        val displayName: String? = null,
        val unresolvedRefundCount: Int = 0,
        val paySummary: PayrollCalculator.MonthlyPaySummary? = null,
    ) : DashboardUiState

    data class Error(val message: String) : DashboardUiState
}
