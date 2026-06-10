package com.elmtrackr.app.ui.shifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.LOCAL_USER_ID
import com.elmtrackr.app.domain.repository.ShiftsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShiftsViewModel(
    private val shiftsRepository: ShiftsRepository,
) : ViewModel() {

    val uiState: StateFlow<ShiftsUiState> = combine(
        shiftsRepository.observeShifts(LOCAL_USER_ID),
        shiftsRepository.observeActiveShift(LOCAL_USER_ID),
    ) { shifts, activeShift ->
        if (shifts.isEmpty()) ShiftsUiState.Empty
        else ShiftsUiState.Ready(shifts = shifts, activeShift = activeShift)
    }.catch { e ->
        emit(ShiftsUiState.Error(e.message ?: "Unknown error"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShiftsUiState.Loading,
    )

    fun deleteShift(shiftId: String) {
        viewModelScope.launch { shiftsRepository.deleteShift(shiftId) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                @Suppress("UNCHECKED_CAST")
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ElmTrackrApp
                ShiftsViewModel(app.shiftsRepository)
            }
        }
    }
}
