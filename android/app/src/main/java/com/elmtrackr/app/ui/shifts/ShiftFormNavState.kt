package com.elmtrackr.app.ui.shifts

import com.elmtrackr.app.domain.model.Shift

sealed interface ShiftFormNavState {
    data object Create : ShiftFormNavState
    data class Edit(val shift: Shift) : ShiftFormNavState
}
