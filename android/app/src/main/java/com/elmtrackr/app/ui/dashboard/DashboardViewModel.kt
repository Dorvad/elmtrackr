package com.elmtrackr.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.data.local.preferences.FeatureDiscoveryPreferences
import com.elmtrackr.app.data.local.preferences.SetupChecklistPreferences
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.data.repository.PremiumProfilesRepository
import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.employeePaidOnly
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.compensation.ShiftCompensationHelper
import com.elmtrackr.app.domain.dashboard.isRefundReminderDismissed
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.projects.PaidProjectsDiscovery
import com.elmtrackr.app.domain.projects.ProjectClockInOptions
import com.elmtrackr.app.domain.projects.ProjectDashboardSummary
import com.elmtrackr.app.domain.projects.ProjectDashboardSummaryBuilder
import com.elmtrackr.app.domain.repository.ProjectsRepository
import com.elmtrackr.app.domain.setup.SetupChecklist
import com.elmtrackr.app.domain.setup.SetupChecklistInputs
import com.elmtrackr.app.domain.setup.SetupStep
import com.elmtrackr.app.domain.RefundPolicy
import com.elmtrackr.app.review.ReviewPromptRecorder
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    private val projectsRepository: ProjectsRepository,
    private val featureDiscoveryPreferences: FeatureDiscoveryPreferences,
    private val reviewPromptRecorder: ReviewPromptRecorder,
) : ViewModel() {

    private val _refreshNonce = MutableStateFlow(0)
    private val _showFirstClockInCelebration = MutableStateFlow(false)
    private val _selectedTaskId = MutableStateFlow<String?>(null)
    private val _suggestedTaskId = MutableStateFlow<String?>(null)
    private val _showSuggestedNow = MutableStateFlow(false)
    private val _suggestionExplanation = MutableStateFlow<String?>(null)
    private val _selectedCompensationSource = MutableStateFlow(CompensationSource.EMPLOYEE)
    private val _selectedProjectId = MutableStateFlow<String?>(null)
    private val _projectClockInNote = MutableStateFlow("")

    val showFirstClockInCelebration: StateFlow<Boolean> = _showFirstClockInCelebration

    /**
     * Clockable projects, or empty when Paid Projects is off. Projects stay stored
     * while the feature is disabled, so this gates on the flag rather than on
     * whether any project exists.
     */
    private val clockableProjects: StateFlow<List<Project>> =
        authRepository.observeCurrentProfile()
            .flatMapLatest { profile ->
                if (profile == null) return@flatMapLatest flowOf(emptyList())
                settingsRepository.observeSettings(profile.id).flatMapLatest { settings ->
                    if (settings?.featuresPaidProjects != true) {
                        flowOf(emptyList())
                    } else {
                        projectsRepository.observeProjects(profile.id)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The project an active project shift belongs to, together with that project's
     * other shifts. Null for hourly work, which is what keeps the project panel
     * off the dashboard for an ordinary punch.
     *
     * Deliberately *not* projected here. The projected rate and budget move with
     * the clock, and a ticking flow in a ViewModel never reaches quiescence; the
     * card owns its own tick, next to the elapsed-time timer that already exists
     * for the same reason.
     */
    private val activeProjectState: StateFlow<ActiveProjectState?> =
        authRepository.observeCurrentProfile()
            .flatMapLatest { profile ->
                if (profile == null) return@flatMapLatest flowOf(null)
                shiftsRepository.observeActiveShift(profile.id).flatMapLatest { activeShift ->
                    val projectId = activeShift?.takeIf { it.isProjectTime }?.projectId
                        ?: return@flatMapLatest flowOf(null)
                    combine(
                        projectsRepository.observeProject(projectId),
                        shiftsRepository.observeShiftsForProject(profile.id, projectId),
                    ) { project, projectShifts ->
                        project?.let { ActiveProjectState(it, projectShifts) }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class ActiveProjectState(
        val project: Project,
        val projectShifts: List<Shift>,
    )

    /**
     * The dashboard's project block, or null while Paid Projects is off — which is
     * what leaves the existing dashboard exactly as it was.
     *
     * Nothing here is combined with the hourly pay summary. They are different
     * accounting bases and the card labels them separately.
     */
    private val projectDashboardSummary: StateFlow<ProjectDashboardSummary?> =
        authRepository.observeCurrentProfile()
            .flatMapLatest { profile ->
                if (profile == null) return@flatMapLatest flowOf(null)
                settingsRepository.observeSettings(profile.id).flatMapLatest { settings ->
                    if (settings?.featuresPaidProjects != true) {
                        flowOf(null)
                    } else {
                        val zone = WorkTimezone.zoneFor(settings)
                        combine(
                            projectsRepository.observeProjects(profile.id),
                            projectsRepository.observeAllBillingRecords(profile.id),
                            projectsRepository.observeAllPayments(profile.id),
                            shiftsRepository.observeShifts(profile.id),
                        ) { projects, records, payments, shifts ->
                            val today = LocalDate.now(zone)
                            val timeByProject = com.elmtrackr.app.domain.projects.ProjectMetrics
                                .timeSummaries(shifts.filter { it.isProjectTime })
                            ProjectDashboardSummaryBuilder.build(
                                summaries = projects.map { project ->
                                    com.elmtrackr.app.domain.projects.ProjectMetrics.summarize(
                                        project = project,
                                        shifts = emptyList(),
                                        billingRecords = records,
                                        payments = payments,
                                        today = today,
                                        timeSummary = timeByProject[project.id]
                                            ?: com.elmtrackr.app.domain.projects.ProjectTimeSummary(),
                                    )
                                },
                                billingRecords = records,
                                payments = payments,
                                today = today,
                            )
                        }
                    }
                }
            }
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val paidProjectsWizardDismissed = featureDiscoveryPreferences.preferences
        .map { it.paidProjectsDiscoveryDismissed }
        .distinctUntilChanged()

    private val refundReminderDismissedMonth = featureDiscoveryPreferences.preferences
        .map { it.refundReminderDismissedMonth }
        .distinctUntilChanged()

    private data class RawData(
        val activeShift: Shift?,
        val report: MonthlyReport?,
        val settings: UserSettings?,
        val monthShifts: List<Shift>,
        val recentShifts: List<Shift>,
        val profiles: List<com.elmtrackr.app.domain.model.CompensationProfile>,
        val activeTasks: List<Task>,
        val premiumProfiles: List<com.elmtrackr.app.domain.model.PremiumProfile> = emptyList(),
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
            }.combine(premiumProfilesRepository.observeProfiles(profile.id)) { raw, premiumProfiles ->
                raw.copy(premiumProfiles = premiumProfiles)
            }.combine(flowOf(profile)) { raw, currentProfile ->
                if (raw.settings == null) {
                    DashboardUiState.Loading
                } else {
                val completedMonthShifts = raw.monthShifts.filter { it.isCompleted }
                val workZone = WorkTimezone.zoneFor(raw.settings)
                // Same day-total definition as the widget/Wear surfaces, so the
                // dashboard progress ring agrees with them on multi-shift days —
                // except that project time is excluded here. The ring turns over at
                // the daily overtime threshold, and an overtime state is a pay
                // classification, so project hours must not trip it; the widget and
                // Wear surfaces show raw tracked hours and are unaffected.
                val todayCompletedMinutes = completedMonthShifts
                    .employeePaidOnly()
                    .filter { it.startTime.atZone(workZone).toLocalDate() == LocalDate.now(workZone) }
                    .sumOf { com.elmtrackr.app.domain.ShiftDurationCalculator.netMinutes(it) ?: 0 }
                val paySummary = raw.settings
                    .takeIf { settings ->
                        (settings.hourlyRate ?: 0.0) > 0.0 ||
                            raw.profiles.any { (it.baseHourlyRate ?: 0.0) > 0.0 }
                    }
                    ?.let { PayrollCalculator.sumMonthlyPay(completedMonthShifts, it, raw.profiles, raw.premiumProfiles) }
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
                    todayCompletedMinutes = todayCompletedMinutes,
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
        .combine(clockableProjects) { state, projects ->
            when (state) {
                is DashboardUiState.Ready ->
                    state.copy(projectOptions = ProjectClockInOptions.options(projects))
                else -> state
            }
        }
        .combine(
            combine(
                _selectedCompensationSource,
                _selectedProjectId,
                _projectClockInNote,
            ) { source, projectId, note -> Triple(source, projectId, note) },
        ) { state, (source, projectId, note) ->
            when (state) {
                is DashboardUiState.Ready -> state.copy(
                    // A selection the user can no longer act on is not shown as
                    // chosen: the selector falls back to hourly work.
                    selectedCompensationSource = if (state.canSelectProjectTime) {
                        source
                    } else {
                        CompensationSource.EMPLOYEE
                    },
                    selectedProjectId = projectId?.takeIf { id ->
                        state.projectOptions.any { it.projectId == id }
                    },
                    projectClockInNote = note,
                )
                else -> state
            }
        }
        .combine(activeProjectState) { state, active ->
            when (state) {
                is DashboardUiState.Ready -> state.copy(
                    activeProject = active?.project,
                    activeProjectShifts = active?.projectShifts.orEmpty(),
                )
                else -> state
            }
        }
        .combine(projectDashboardSummary) { state, projectSummary ->
            when (state) {
                is DashboardUiState.Ready -> state.copy(projectSummary = projectSummary)
                else -> state
            }
        }
        .combine(paidProjectsWizardDismissed) { state, dismissed ->
            when (state) {
                is DashboardUiState.Ready -> state.copy(
                    showPaidProjectsWizard = PaidProjectsDiscovery.shouldShow(
                        onboardingCompleted = state.settings.onboardingCompleted,
                        paidProjectsEnabled = state.settings.featuresPaidProjects,
                        dismissed = dismissed,
                    ),
                )
                else -> state
            }
        }
        // Compared in the work zone, like the refund count itself: a user whose
        // work month has already turned over should see the next month's reminder,
        // not the previous one's dismissal.
        .combine(refundReminderDismissedMonth) { state, dismissedMonth ->
            when (state) {
                is DashboardUiState.Ready -> state.copy(
                    refundReminderDismissed = isRefundReminderDismissed(
                        storedMonth = dismissedMonth,
                        month = YearMonth.now(WorkTimezone.zoneFor(state.settings)),
                    ),
                )
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
            // A first-ever punch from the widget or watch can't show the
            // celebration; it leaves a pending marker for this visit instead.
            val prefs = appPreferences.currentPreferences()
            if (prefs.firstClockInCelebrationPending && !prefs.firstClockInCelebrated) {
                appPreferences.setFirstClockInCelebrated(true)
                appPreferences.setFirstClockInCelebrationPending(false)
                _showFirstClockInCelebration.value = true
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
            // Falls back to employee-paid work if the feature is off or the picked
            // project is no longer clockable, so a punch is never lost and never
            // becomes project time with no project attached.
            val selection = ProjectClockInOptions.resolve(
                paidProjectsEnabled = settings.featuresPaidProjects == true,
                requested = _selectedCompensationSource.value,
                selectedProjectId = _selectedProjectId.value,
                projects = clockableProjects.value,
            )
            val shift = shiftsRepository.clockIn(
                userId = userId,
                compensationProfileId = settings.defaultCompensationProfileId ?: defaultProfile?.id,
                taskId = taskParams.taskId,
                taskNameSnapshot = taskParams.taskNameSnapshot,
                taskIconSnapshot = taskParams.taskIconSnapshot,
                taskHourlyRateSnapshot = taskParams.taskHourlyRateSnapshot,
                compensationSource = selection.compensationSource,
                projectId = selection.projectId,
                projectNameSnapshot = selection.projectNameSnapshot,
            )
            _projectClockInNote.value.trim().takeIf { it.isNotEmpty() }?.let { note ->
                shiftsRepository.updateShift(shift.copy(notes = note))
            }
            _projectClockInNote.value = ""
            reviewPromptRecorder.noteClockIn()
            selected?.let { tasksRepository.markTaskUsed(userId, it.id) }
            if (isFirstClockIn) {
                appPreferences.setFirstClockInCelebrated(true)
                appPreferences.setFirstClockInCelebrationPending(false)
                _showFirstClockInCelebration.value = true
            }
        }
    }

    fun dismissFirstClockInCelebration() {
        _showFirstClockInCelebration.value = false
    }

    /**
     * Retires the v1.2 update wizard for good without touching the feature
     * flag — Paid Projects stays available from Settings → Features.
     */
    fun dismissPaidProjectsWizard() {
        viewModelScope.launch {
            featureDiscoveryPreferences.setPaidProjectsDiscoveryDismissed(true)
        }
    }

    /**
     * Turns Paid Projects on straight from the update wizard and stops the
     * wizard coming back. Only this flag changes: every other setting, and all
     * existing hours and pay data, is left exactly as it was.
     */
    fun enablePaidProjectsFromWizard() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            val existing = settingsRepository.getSettings(userId) ?: return@launch
            if (!existing.featuresPaidProjects) {
                settingsRepository.saveSettings(
                    existing.copy(featuresPaidProjects = true, updatedAt = Instant.now()),
                )
            }
            featureDiscoveryPreferences.setPaidProjectsDiscoveryDismissed(true)
        }
    }

    /**
     * Silences the refund reminder for the current work month only. Next month's
     * unresolved claims raise it again without the user having to re-enable
     * anything — the alternative, a permanent dismissal, quietly turns a recurring
     * reminder into a one-off.
     */
    fun dismissRefundReminder() {
        viewModelScope.launch {
            val zone = authRepository.getCurrentProfile()?.id
                ?.let { settingsRepository.getSettings(it) }
                ?.let { WorkTimezone.zoneFor(it) }
                ?: ZoneId.systemDefault()
            featureDiscoveryPreferences.setRefundReminderDismissedMonth(
                YearMonth.now(zone).toString(),
            )
        }
    }

    fun clockOut(shiftId: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            val shift = shiftsRepository.getShiftById(shiftId) ?: return@launch
            val settings = settingsRepository.getSettings(userId) ?: return@launch
            val profiles = compensationProfilesRepository.getProfiles(userId)
            // A project shift is paid by the project's fee, so it takes no wage
            // snapshot — one would record rules no calculation will ever read.
            val snapshot = if (shift.isEmployeePaid) {
                ShiftCompensationHelper.buildClockOutSnapshot(shift, settings, profiles)
            } else {
                null
            }
            // Same guard as the notification/Wear paths: the shift can vanish
            // (deleted on another device) between lookup and clock-out.
            val clockedOut = runCatching {
                shiftsRepository.clockOut(shiftId, compensationSnapshot = snapshot)
            }.getOrNull()
            if (clockedOut != null) {
                reviewPromptRecorder.noteShiftCompleted()
            } else {
                reviewPromptRecorder.noteDiscouragingEvent()
            }
            if (clockedOut != null && clockedOut.isProjectTime) {
                _projectShiftSummary.value = buildProjectShiftSummary(userId, clockedOut)
            }
        }
    }

    /**
     * Non-blocking summary shown after a project shift ends: how much time was
     * added and what the project's effective rate is now. Null when there is
     * nothing to say — no project, or a project whose fee is not set yet.
     */
    private val _projectShiftSummary = MutableStateFlow<ProjectShiftSummary?>(null)
    val projectShiftSummary: StateFlow<ProjectShiftSummary?> = _projectShiftSummary

    fun dismissProjectShiftSummary() {
        _projectShiftSummary.value = null
    }

    private suspend fun buildProjectShiftSummary(userId: String, shift: Shift): ProjectShiftSummary? {
        val projectId = shift.projectId ?: return null
        val project = projectsRepository.getProject(userId, projectId) ?: return null
        val projectShifts = shiftsRepository.observeShiftsForProject(userId, projectId).first()
        val time = com.elmtrackr.app.domain.projects.ProjectMetrics.timeSummary(projectShifts, projectId)
        return ProjectShiftSummary(
            projectId = projectId,
            projectName = project.name,
            addedMinutes = com.elmtrackr.app.domain.ShiftDurationCalculator.netMinutes(shift) ?: 0,
            effectiveHourlyRate = com.elmtrackr.app.domain.projects.ProjectMetrics.effectiveHourlyRate(
                project.fee,
                time.trackedMinutes,
            ),
        )
    }

    data class ProjectShiftSummary(
        val projectId: String,
        val projectName: String,
        val addedMinutes: Int,
        val effectiveHourlyRate: com.elmtrackr.app.domain.money.Money?,
    )

    fun editActiveShiftStartTime(shiftId: String, newStartTime: Instant) {
        viewModelScope.launch {
            // A future start would run the elapsed timer backwards.
            if (newStartTime.isAfter(Instant.now())) return@launch
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

    // ── Project time selection ────────────────────────────────────────────────

    /**
     * Chooses what the next clock-in records. Switching back to hourly work drops
     * the project selection and note, so a stale project cannot leak into an
     * employee-paid shift.
     */
    fun selectCompensationSource(source: CompensationSource) {
        _selectedCompensationSource.value = source
        if (source.isEmployeePaid) {
            _selectedProjectId.value = null
            _projectClockInNote.value = ""
        } else if (_selectedProjectId.value == null) {
            _selectedProjectId.value = ProjectClockInOptions.options(clockableProjects.value)
                .firstOrNull()
                ?.projectId
        }
    }

    fun selectClockInProject(projectId: String) {
        _selectedProjectId.value = projectId
        _selectedCompensationSource.value = CompensationSource.PROJECT
    }

    fun setProjectClockInNote(note: String) {
        _projectClockInNote.value = note
    }
}
