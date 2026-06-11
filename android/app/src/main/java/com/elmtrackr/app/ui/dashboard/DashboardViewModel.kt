package com.elmtrackr.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.LOCAL_USER_ID
import com.elmtrackr.app.domain.repository.ReportsRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.domain.repository.SyncRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

class DashboardViewModel(
    private val shiftsRepository: ShiftsRepository,
    private val settingsRepository: SettingsRepository,
    private val reportsRepository: ReportsRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val today = LocalDate.now(ZoneOffset.UTC)

    val uiState: StateFlow<DashboardUiState> = combine(
        shiftsRepository.observeActiveShift(LOCAL_USER_ID),
        reportsRepository.observeMonthlyReport(LOCAL_USER_ID, today.year, today.monthValue),
        settingsRepository.observeSettings(LOCAL_USER_ID),
        syncRepository.observePendingCount(),
    ) { activeShift, report, settings, pendingCount ->
        DashboardUiState.Ready(
            activeShift = activeShift,
            monthlyReport = report,
            settings = settings,
            pendingSyncCount = pendingCount,
        ) as DashboardUiState
    }.catch { e ->
        emit(DashboardUiState.Error(e.message ?: "Unknown error"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState.Loading,
    )

    fun clockIn() {
        viewModelScope.launch { shiftsRepository.clockIn(LOCAL_USER_ID) }
    }

    fun clockOut(shiftId: String) {
        viewModelScope.launch { shiftsRepository.clockOut(shiftId) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                @Suppress("UNCHECKED_CAST")
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ElmTrackrApp
                DashboardViewModel(
                    app.shiftsRepository,
                    app.settingsRepository,
                    app.reportsRepository,
                    app.syncRepository,
                )
            }
        }
    }
}
