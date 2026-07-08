package com.elmtrackr.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.data.local.preferences.SetupChecklistPreferences
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.data.repository.PremiumProfilesRepository
import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.compensation.ShiftCompensationHelper
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.setup.SetupChecklist
import com.elmtrackr.app.domain.setup.SetupChecklistInputs
import com.elmtrackr.app.domain.setup.SetupStep
import com.elmtrackr.app.domain.RefundPolicy
import com.elmtrackr.app.widget.WidgetPinInspector
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.ReportsRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.domain.repository.TasksRepository
import com.elmtrackr.app.domain.tasks.TaskClockInHelper
import com.elmtrackr.app.domain.tasks.TaskHabitSuggestionBuilder
import com.elmtrackr.app.domain.tasks.TaskSorting
import com.elmtrackr.app.domain.time.WorkTimezone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val shiftsRepository: ShiftsRepository,
    private val settingsRepository: SettingsRepository,
    private val reportsRepository: ReportsRepository,
    private val authRepository: AuthRepository,
    private val compensationProfilesRepository: CompensationProfilesRepository,
    private val tasksRepository: TasksRepository,
    private val appPreferences: com.elmtrackr.app.data.local.preferences.AppPreferencesStore,
    private val premiumProfilesRepository: PremiumProfilesRepository,
    private val setupPreferences: SetupChecklistPreferences,
    private val widgetPinInspector: WidgetPinInspector,
) : ViewModel() {

    private val _refreshNonce = MutableStateFlow(0)
    private val _showFirstClockInCelebration = MutableStateFlow(false)
    private val _selectedTaskId = MutableStateFlow<String?>(null)
    private val _suggestedTaskId = MutableStateFlow<String?>(null)
    private val _showSuggestedNow = MutableStateFlow(false)
    private val _suggestionExplanation = MutableStateFlow<String?>(null)

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
                    settingsRepository.observeSettings(profile.id),
                ) { activeShift, settings -> activeShift to settings }
                    .flatMapLatest { (activeShift, settings) ->
                        if (settings == null) {
                            flowOf(RawData(null, null, null, emptyList(), emptyList(), emptyList(), emptyList()))
                        } else {
                            val zone = WorkTimezone.zoneFor(settings)
                            val today = LocalDate.now(zone)
                            shiftsRepository.observeShiftsByMonthInZone(
                                    profile.id,
                                    today.year,
                                    today.monthValue,
                                    zone,
                                ).map { monthShifts ->
                                val report = MonthlyReportBuilder.buildMonthlyReport(
                                    year = today.year,
                                    month = today.monthValue,
                                    shifts = monthShifts,
                                    settings = settings,
                                )
                                RawData(activeShift, report, settings, monthShifts, emptyList(), emptyList(), emptyList())
                            }
                        }
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
                    suggestedTaskId = _suggestedTaskId.value,
                    showSuggestedNow = _showSuggestedNow.value,
                    suggestionExplanation = _suggestionExplanation.value,
                    recentShifts = raw.recentShifts,
                    displayName = currentProfile.fullName,
                    unresolvedRefundCount = if (raw.settings.featuresTravelRefunds == true) {
                        val zone = WorkTimezone.zoneFor(raw.settings)
                        RefundPolicy.countUnresolved(raw.monthShifts, zone)
                    } else 0,
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
        .combine(_suggestedTaskId) { state, suggestedTaskId ->
            when (state) {
                is DashboardUiState.Ready -> state.copy(suggestedTaskId = suggestedTaskId)
                else -> state
            }
        }
        .combine(_showSuggestedNow) { state, showSuggestedNow ->
            when (state) {
                is DashboardUiState.Ready -> state.copy(showSuggestedNow = showSuggestedNow)
                else -> state
            }
        }
        .combine(_suggestionExplanation) { state, explanation ->
            when (state) {
                is DashboardUiState.Ready -> state.copy(suggestionExplanation = explanation)
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
                    _suggestedTaskId.value = null
                    _showSuggestedNow.value = false
                    _suggestionExplanation.value = null
                    return@collect
                }
                val current = _selectedTaskId.value
                if (current == null || tasks.none { it.id == current }) {
                    val suggestion = TaskHabitSuggestionBuilder.suggest(tasks, shifts)
                    _selectedTaskId.value = suggestion?.task?.id
                        ?: TaskSorting.byRecency(tasks).firstOrNull()?.id
                    _suggestedTaskId.value = suggestion?.task?.id
                    _showSuggestedNow.value = suggestion?.showSuggestedNow == true
                    _suggestionExplanation.value = suggestion?.explanation
                }
            }
        }
    }

    fun selectTask(taskId: String) {
        _selectedTaskId.value = taskId
        _showSuggestedNow.value = false
        _suggestionExplanation.value = null
    }

    fun clockIn() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            val settings = settingsRepository.getSettings(userId) ?: return@launch
            val isFirstClockIn = !shiftsRepository.hasAnyShifts(userId) &&
                !appPreferences.currentPreferences().firstClockInCelebrated
            val defaultProfile = compensationProfilesRepository.ensureMigrated(userId)
            val tasks = tasksRepository.getActiveTasks(userId)
            val selected = tasks.firstOrNull { it.id == _selectedTaskId.value }
                ?: TaskSorting.byRecency(tasks).firstOrNull()
            val taskParams = TaskClockInHelper.paramsFromTask(selected)
            shiftsRepository.clockIn(
                userId = userId,
                compensationProfileId = settings.defaultCompensationProfileId ?: defaultProfile?.id,
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

    // ── Getting-started checklist ────────────────────────────────────────────

    private val _widgetPinned = MutableStateFlow(widgetPinInspector.hasPinnedWidget())

    /** Re-reads widget placement; called on dashboard resume so a freshly pinned widget completes the step. */
    fun refreshWidgetPinState() {
        _widgetPinned.value = widgetPinInspector.hasPinnedWidget()
    }

    val setupChecklist: StateFlow<SetupChecklistUiState?> =
        authRepository.observeCurrentProfile().flatMapLatest { profile ->
            if (profile == null) return@flatMapLatest flowOf(null)
            combine(
                settingsRepository.observeSettings(profile.id),
                shiftsRepository.observeRecentCompletedShifts(profile.id, 1),
                premiumProfilesRepository.observeProfiles(profile.id),
                compensationProfilesRepository.observeProfiles(profile.id),
                tasksRepository.observeActiveTasks(profile.id),
            ) { settings, completedShifts, premiumProfiles, compensationProfiles, tasks ->
                if (settings == null) return@combine null
                ChecklistSignals(
                    clockStyleCustomized = settings.clockStyle != ClockStyle.CLASSIC,
                    hasCompletedShift = completedShifts.isNotEmpty(),
                    hasCustomPremiumProfile = premiumProfiles.any { !it.isDefault },
                    compensationProfileCount = compensationProfiles.size,
                    hasAnyTask = tasks.isNotEmpty(),
                )
            }.combine(setupPreferences.preferences) { signals, prefs ->
                signals?.let { it to prefs }
            }.combine(_widgetPinned) { pair, widgetPinned ->
                val (signals, prefs) = pair ?: return@combine null
                SetupChecklist.build(
                    SetupChecklistInputs(
                        hasCompletedShift = signals.hasCompletedShift,
                        clockStyleCustomized = signals.clockStyleCustomized,
                        compensationProfileCount = signals.compensationProfileCount,
                        hasCustomPremiumProfile = signals.hasCustomPremiumProfile,
                        hasAnyTask = signals.hasAnyTask,
                        hasPinnedWidget = widgetPinned,
                        widgetPinSupported = widgetPinInspector.isPinSupported(),
                        visitedStepKeys = prefs.setupChecklistVisitedSteps,
                        dismissed = prefs.setupChecklistDismissed,
                        celebrated = prefs.setupChecklistCelebrated,
                    ),
                )?.let { checklist ->
                    SetupChecklistUiState(
                        steps = checklist.steps,
                        completedCount = checklist.completedCount,
                        totalCount = checklist.totalCount,
                        showCelebration = checklist.showCelebration,
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private data class ChecklistSignals(
        val clockStyleCustomized: Boolean,
        val hasCompletedShift: Boolean,
        val hasCustomPremiumProfile: Boolean,
        val compensationProfileCount: Int,
        val hasAnyTask: Boolean,
    )

    /** Marks a teach-by-visiting step as seen when the user follows its call to action. */
    fun markSetupStepVisited(step: SetupStep) {
        viewModelScope.launch { setupPreferences.markSetupStepVisited(step.key) }
    }

    fun dismissSetupChecklist() {
        viewModelScope.launch { setupPreferences.setSetupChecklistDismissed(true) }
    }

    fun markSetupChecklistCelebrated() {
        viewModelScope.launch { setupPreferences.setSetupChecklistCelebrated(true) }
    }

    /** Asks the launcher to pin the main clock in/out widget. */
    fun requestPinWidget() {
        widgetPinInspector.requestPinWidget()
    }
}
