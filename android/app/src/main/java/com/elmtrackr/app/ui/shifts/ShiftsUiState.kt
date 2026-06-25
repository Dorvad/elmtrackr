package com.elmtrackr.app.ui.shifts

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings

sealed interface ShiftsUiState {
    data object Loading : ShiftsUiState
    data object Empty : ShiftsUiState

    data class Ready(
        val shifts: List<Shift>,
        val activeShift: Shift?,
        val featuresTravelRefunds: Boolean = false,
        val settings: UserSettings? = null,
    ) : ShiftsUiState

    data class Error(val message: String) : ShiftsUiState
}
