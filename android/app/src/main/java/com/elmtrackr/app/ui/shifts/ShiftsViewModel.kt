package com.elmtrackr.app.ui.shifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.LOCAL_USER_ID
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class ShiftsViewModel(
    private val shiftsRepository: ShiftsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _formTarget = MutableStateFlow<ShiftFormNavState?>(null)
    val formTarget: StateFlow<ShiftFormNavState?> = _formTarget.asStateFlow()

    private val _formErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val formErrors: StateFlow<Map<String, String>> = _formErrors.asStateFlow()

    val featuresTravelRefunds: StateFlow<Boolean> = settingsRepository
        .observeSettings(LOCAL_USER_ID)
        .map { it?.featuresTravelRefunds ?: false }
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val uiState: StateFlow<ShiftsUiState> = combine(
        shiftsRepository.observeShifts(LOCAL_USER_ID),
        shiftsRepository.observeActiveShift(LOCAL_USER_ID),
        settingsRepository.observeSettings(LOCAL_USER_ID),
    ) { shifts, activeShift, settings ->
        if (shifts.isEmpty()) ShiftsUiState.Empty
        else ShiftsUiState.Ready(
            shifts = shifts,
            activeShift = activeShift,
            featuresTravelRefunds = settings?.featuresTravelRefunds ?: false,
        )
    }.catch { e ->
        emit(ShiftsUiState.Error(e.message ?: "Unknown error"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShiftsUiState.Loading,
    )

    fun showCreateForm() {
        _formErrors.value = emptyMap()
        _formTarget.value = ShiftFormNavState.Create
    }

    fun showEditForm(shiftId: String) {
        viewModelScope.launch {
            val shift = shiftsRepository.getShiftById(shiftId) ?: return@launch
            _formErrors.value = emptyMap()
            _formTarget.value = ShiftFormNavState.Edit(shift)
        }
    }

    fun closeForm() {
        _formTarget.value = null
        _formErrors.value = emptyMap()
    }

    fun createShift(input: ShiftFormInput) {
        val errors = validate(input)
        if (errors.isNotEmpty()) { _formErrors.value = errors; return }
        viewModelScope.launch {
            val now = Instant.now()
            val shift = Shift(
                id = UUID.randomUUID().toString(),
                userId = LOCAL_USER_ID,
                startTime = input.startTime,
                endTime = input.endTime,
                breakMinutes = input.breakMinutes,
                notes = input.notes.ifBlank { null },
                isSpecialDay = input.isSpecialDay,
                refundAction = input.refundAction,
                createdAt = now,
                updatedAt = now,
            )
            shiftsRepository.createManualShift(shift)
            closeForm()
        }
    }

    fun saveEditedShift(shiftId: String, input: ShiftFormInput) {
        val errors = validate(input)
        if (errors.isNotEmpty()) { _formErrors.value = errors; return }
        viewModelScope.launch {
            val existing = shiftsRepository.getShiftById(shiftId) ?: return@launch
            shiftsRepository.updateShift(
                existing.copy(
                    startTime = input.startTime,
                    endTime = input.endTime,
                    breakMinutes = input.breakMinutes,
                    notes = input.notes.ifBlank { null },
                    isSpecialDay = input.isSpecialDay,
                    refundAction = input.refundAction,
                    updatedAt = Instant.now(),
                )
            )
            closeForm()
        }
    }

    fun deleteShift(shiftId: String) {
        viewModelScope.launch { shiftsRepository.deleteShift(shiftId) }
    }

    internal fun validate(input: ShiftFormInput): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (input.endTime != null && !input.endTime.isAfter(input.startTime)) {
            errors["endTime"] = "End time must be after start time"
        }
        if (input.breakMinutes < 0) {
            errors["breakMinutes"] = "Break minutes must be zero or positive"
        }
        return errors
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                @Suppress("UNCHECKED_CAST")
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ElmTrackrApp
                ShiftsViewModel(app.shiftsRepository, app.settingsRepository)
            }
        }
    }
}
