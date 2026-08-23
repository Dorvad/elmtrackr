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
    val premiumProfiles: List<com.elmtrackr.app.domain.model.PremiumProfile> = emptyList(),
    val tasks: List<com.elmtrackr.app.domain.model.Task> = emptyList(),
        /**
         * [shifts] plus the leading tail of the pay week containing the 1st.
         *
         * Pay-week context only — never listed, counted or displayed. Weekly overtime
         * accumulates over a week, so a week straddling the 1st has to see the minutes
         * worked on its far side; without them the first week card of a month restarted
         * the weekly allowance at zero and showed less pay than Reports did for the
         * same days. Reports already loads the previous month for this
         * (`ReportsViewModel.payContext`), and the dashboard already uses this same
         * query.
         */
        val payContextShifts: List<Shift> = emptyList(),
    ) : ShiftsUiState

    data class Error(val message: String) : ShiftsUiState
}
