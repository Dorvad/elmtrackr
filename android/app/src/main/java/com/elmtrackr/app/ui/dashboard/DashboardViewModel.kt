package com.elmtrackr.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.compensation.ShiftCompensationHelper
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.RefundPolicy
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.ReportsRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.domain.repository.TasksRepository
import com.elmtrackr.app.domain.tasks.TaskClockInHelper
import com.elmtrackr.app.domain.tasks.TaskHabitSuggestionBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val shiftsRepository: ShiftsRepository,
    private val settingsRepository: SettingsRepository,
    private val reportsRepository: ReportsRepository,
    private val authRepository: AuthRepository,
    private val compensationProfilesRepository: CompensationProfilesRepository,
    private val tasksRepository: TasksRepository,
    private val appPreferences: com.elmtrackr.app.data.local.preferences.AppPreferencesStore,
) : ViewModel() {

    private val today = LocalDate.now(ZoneOffset.UTC)
    private val _refreshNonce = MutableStateFlow(0)
    private val _showFirstClockInCelebration = MutableStateFlow(false)
    private val _selectedTaskId = MutableStateFlow<String?>(null)
    private val _habitSuggested = MutableStateFlow(false)

    val showFirstClockInCelebration: StateFlow<Boolean> = _showFirstClockInCelebration

    private data class RawData(
        val activeShift: Shift?,
        val report: MonthlyReport?,
        val settings: UserSettings?,
        val monthShifts: List<Shift>,
        val recentShifts: List<Shift>,
        val profiles: List<com.elmtrackr.app.domain.model.CompensationProfile>,
        val activeTasks: List<Task>,
    )

    val uiState: StateFlow<DashboardUiState> = _refreshNonce
        .flatMapLatest {
            authRepository.observeCurrentProfile().flatMapLatest { profile ->
            if (profile == null) return@flatMapLatest flowOf(DashboardUiState.Loading)
            combine(
                combine(
                    shiftsRepository.observeActiveShift(profile.id),
                    reportsRepository.observeMonthlyReport(profile.id, today.year, today.monthValue),
                    settingsRepository.observeSettings(profile.id),
                    shiftsRepository.observeShiftsByMonth(profile.id, today.year, today.monthValue),
                ) { activeShift, report, settings, monthShifts ->
                    RawData(activeShift, report, settings, monthShifts, emptyList(), emptyList(), emptyList())
                },
                compensationProfilesRepository.observeProfiles(profile.id),
            ) { raw, profiles ->
                raw.copy(profiles = profiles)
            }.combine(shiftsRepository.observeRecentCompletedShifts(profile.id, 5)) { raw, recentShifts ->
                raw.copy(recentShifts = recentShifts)
            }.combine(tasksRepository.observeActiveTasks(profile.id)) { raw, activeTasks ->
                raw.copy(activeTasks = activeTasks)
            }.combine(flowOf(profile)) { raw, currentProfile ->
                if (raw.settings == null) {
                    DashboardUiState.Loading
                } else {
                val completedMonthShifts = raw.monthShifts.filter { it.isCompleted }
                val paySummary = raw.settings
                    .takeIf { settings ->
                        (settings.hourlyRate ?: 0.0) > 0.0 ||
                            raw.profiles.any { (it.baseHourlyRate ?: 0.0) > 0.0 }
                    }
                    ?.let { PayrollCalculator.sumMonthlyPay(completedMonthShifts, it, raw.profiles) }
                DashboardUiState.Ready(
                    activeShift = raw.activeShift,
                    monthlyReport = raw.report,
                    settings = raw.settings,
                    profiles = raw.profiles,
                    activeTasks = raw.activeTasks,
                    selectedTaskId = _selectedTaskId.value,
                    habitSuggested = _habitSuggested.value,
                    recentShifts = raw.recentShifts,
                    displayName = currentProfile.fullName,
                    unresolvedRefundCount = if (raw.settings.featuresTravelRefunds == true)
                        RefundPolicy.countUnresolved(raw.monthShifts) else 0,
                    paySummary = paySummary,
                ) as DashboardUiState
                }
            }
        }
        }
        .combine(_selectedTaskId) { state, selectedTaskId ->
            when (state) {
                is DashboardUiState.Ready -> state.copy(selectedTaskId = selectedTaskId)
                else -> state
            }
        }
        .combine(_habitSuggested) { state, habitSuggested ->
            when (state) {
                is DashboardUiState.Ready -> state.copy(habitSuggested = habitSuggested)
                else -> state
            }
        }
        .catch { e ->
        emit(DashboardUiState.Error(e.message ?: "Unknown error"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState.Loading,
    )

    init {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            val settings = settingsRepository.getSettings(userId) ?: return@launch
            if (settings.onboardingCompleted) {
                compensationProfilesRepository.ensureMigrated(userId)
            }
        }
        viewModelScope.launch {
            authRepository.observeCurrentProfile().flatMapLatest { profile ->
                if (profile == null) return@flatMapLatest flowOf(emptyList<Task>() to emptyList<Shift>())
                combine(
                    tasksRepository.observeActiveTasks(profile.id),
                    shiftsRepository.observeRecentCompletedShifts(profile.id, 60),
                ) { tasks, shifts -> tasks to shifts }
            }.collect { (tasks, shifts) ->
                if (tasks.isEmpty()) {
                    _selectedTaskId.value = null
                    _habitSuggested.value = false
                    return@collect
                }
                val current = _selectedTaskId.value
                if (current == null || tasks.none { it.id == current }) {
                    val suggestion = TaskHabitSuggestionBuilder.suggest(tasks, shifts)
                    _selectedTaskId.value = suggestion?.task?.id ?: tasks.maxByOrNull { it.createdAt }?.id
                    _habitSuggested.value = suggestion?.isHabitBased == true
                }
            }
        }
    }

    fun selectTask(taskId: String) {
        _selectedTaskId.value = taskId
        _habitSuggested.value = false
    }

    fun clockIn() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            val settings = settingsRepository.getSettings(userId) ?: return@launch
            val isFirstClockIn = !shiftsRepository.hasAnyShifts(userId) &&
                !appPreferences.currentPreferences().firstClockInCelebrated
            compensationProfilesRepository.ensureMigrated(userId)
            val tasks = tasksRepository.getActiveTasks(userId)
            val selected = tasks.firstOrNull { it.id == _selectedTaskId.value }
                ?: tasks.maxByOrNull { it.createdAt }
            val taskParams = TaskClockInHelper.paramsFromTask(selected)
            shiftsRepository.clockIn(
                userId = userId,
                compensationProfileId = settings.defaultCompensationProfileId,
                taskId = taskParams.taskId,
                taskNameSnapshot = taskParams.taskNameSnapshot,
                taskIconSnapshot = taskParams.taskIconSnapshot,
                taskHourlyRateSnapshot = taskParams.taskHourlyRateSnapshot,
            )
            selected?.let { tasksRepository.markTaskUsed(userId, it.id) }
            if (isFirstClockIn) {
                appPreferences.setFirstClockInCelebrated(true)
                _showFirstClockInCelebration.value = true
            }
        }
    }

    fun dismissFirstClockInCelebration() {
        _showFirstClockInCelebration.value = false
    }

    fun clockOut(shiftId: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            val shift = shiftsRepository.getShiftById(shiftId) ?: return@launch
            val settings = settingsRepository.getSettings(userId) ?: return@launch
            val profiles = compensationProfilesRepository.getProfiles(userId)
            val snapshot = ShiftCompensationHelper.buildClockOutSnapshot(shift, settings, profiles)
            shiftsRepository.clockOut(shiftId, compensationSnapshot = snapshot)
        }
    }

    fun editActiveShiftStartTime(shiftId: String, newStartTime: Instant) {
        viewModelScope.launch {
            val shift = shiftsRepository.getShiftById(shiftId) ?: return@launch
            if (!shift.isActive) return@launch
            shiftsRepository.updateShift(shift.copy(startTime = newStartTime, updatedAt = Instant.now()))
        }
    }

    fun retry() {
        _refreshNonce.value++
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
                    app.authRepository,
                    app.compensationProfilesRepository,
                    app.tasksRepository,
                    app.appPreferences,
                )
            }
        }
    }
}
