package com.elmtrackr.app.ui.dashboard

import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data class Ready(
        val activeShift: Shift?,
        val monthlyReport: MonthlyReport?,
        val settings: UserSettings?,
        val pendingSyncCount: Int = 0,
        val recentShifts: List<Shift> = emptyList(),
        val displayName: String? = null,
        val isRemoteConfigured: Boolean = true,
    ) : DashboardUiState

    data class Error(val message: String) : DashboardUiState
}
