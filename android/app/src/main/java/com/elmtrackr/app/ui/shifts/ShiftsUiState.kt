package com.elmtrackr.app.ui.shifts

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import java.time.YearMonth

sealed interface ShiftsUiState {
    data object Loading : ShiftsUiState
    data class Empty(val month: YearMonth) : ShiftsUiState

    data class Ready(
        val month: YearMonth,
        val shifts: List<Shift>,
        val activeShift: Shift?,
        val featuresTravelRefunds: Boolean = false,
        val settings: UserSettings? = null,
        val profiles: List<com.elmtrackr.app.domain.model.CompensationProfile> = emptyList(),
        val tasks: List<com.elmtrackr.app.domain.model.Task> = emptyList(),
    ) : ShiftsUiState

    data class Error(val message: String) : ShiftsUiState
}
