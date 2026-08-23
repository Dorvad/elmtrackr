package com.elmtrackr.app.ui.shifts

import androidx.lifecycle.ViewModel
import com.elmtrackr.app.R
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.data.repository.PremiumProfilesRepository
import com.elmtrackr.app.domain.compensation.ShiftCompensationHelper
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.model.ReceiptUpload
import com.elmtrackr.app.domain.model.RefundAction
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.model.RefundProvider
import com.elmtrackr.app.domain.repository.RefundsRepository
import com.elmtrackr.app.domain.repository.RefundReceiptStorage
import com.elmtrackr.app.domain.projects.ProjectClockInOptions
import com.elmtrackr.app.domain.projects.ShiftCompensationReassignment
import com.elmtrackr.app.domain.repository.ProjectsRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.domain.repository.TasksRepository
import com.elmtrackr.app.domain.tasks.TaskHabitSuggestionBuilder
import com.elmtrackr.app.domain.MonthlyReportBuilder
import com.elmtrackr.app.domain.time.WorkTimezone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.elmtrackr.app.domain.tasks.TaskSnapshotApplier
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShiftsViewModel @Inject constructor(
    private val shiftsRepository: ShiftsRepository,
    private val settingsRepository: SettingsRepository,
    private val compensationProfilesRepository: CompensationProfilesRepository,
    private val premiumProfilesRepository: PremiumProfilesRepository,
    private val tasksRepository: TasksRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val refundsRepository: RefundsRepository,
    private val refundReceiptStorage: RefundReceiptStorage?,
    private val projectsRepository: ProjectsRepository,
) : ViewModel() {

    private val _formTarget = MutableStateFlow<ShiftFormNavState?>(null)
    val formTarget: StateFlow<ShiftFormNavState?> = _formTarget.asStateFlow()

    private val _formErrors = MutableStateFlow<Map<String, UiText>>(emptyMap())
    val formErrors: StateFlow<Map<String, UiText>> = _formErrors.asStateFlow()

    // Receipt-upload notices go through _userMessage: the screen renders that as a
    // snackbar, whereas the old dedicated flow was collected by nothing, so a claim
    // saved without its receipt told the user nothing and they believed it attached.

    private val _userMessage = MutableStateFlow<UiText?>(null)
    val userMessage: StateFlow<UiText?> = _userMessage.asStateFlow()

    fun consumeUserMessage() {
        _userMessage.value = null
    }

    val refundClaims: StateFlow<List<RefundClaim>> = _formTarget
        .flatMapLatest { target ->
            val shiftId = (target as? ShiftFormNavState.Edit)?.shift?.id
            if (shiftId == null) flowOf(emptyList()) else refundsRepository.observeClaimsForShift(shiftId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    private val _refreshNonce = MutableStateFlow(0)

    // Latest work timezone seen by the pipeline; month navigation and the
    // "current month" cap must agree with the zone the data is queried in.
    @Volatile private var workZone: ZoneId = ZoneId.systemDefault()
    @Volatile private var monthNavigated = false
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    /**
     * Every project, for the shift form. Not filtered to clockable ones: a shift
     * already linked to a completed or archived project must still show which
     * project that is, so the form can keep the link instead of dropping it.
     *
     * Empty while Paid Projects is off, which hides the compensation controls
     * without touching the stored project links.
     */
    val formProjects: StateFlow<List<com.elmtrackr.app.domain.model.Project>> =
        currentUserProvider.userId
            .filterNotNull()
            .flatMapLatest { userId ->
                settingsRepository.observeSettings(userId).flatMapLatest { settings ->
                    if (settings?.featuresPaidProjects != true) {
                        flowOf(emptyList())
                    } else {
                        projectsRepository.observeProjects(userId)
                    }
                }
            }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val featuresTravelRefunds: StateFlow<Boolean> = currentUserProvider.userId
        .filterNotNull()
        .flatMapLatest { settingsRepository.observeSettings(it) }
        .map { it?.featuresTravelRefunds ?: false }
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val uiState: StateFlow<ShiftsUiState> = _refreshNonce
        .flatMapLatest {
            currentUserProvider.userId
                .filterNotNull()
                .flatMapLatest { userId ->
                    combine(
                        _selectedMonth,
                        settingsRepository.observeSettings(userId),
                    ) { month, settings ->
                        month to settings
                    }.flatMapLatest { (month, settings) ->
                        val zone = settings?.let { WorkTimezone.zoneFor(it) } ?: ZoneId.of("UTC")
                        workZone = zone
                        if (!monthNavigated) {
                            // Land on the current month in the WORK zone; near
                            // midnight at a month boundary the device zone can
                            // disagree by a whole month.
                            val current = YearMonth.now(zone)
                            if (_selectedMonth.value != current) _selectedMonth.value = current
                        }
                        flow {
                            emit(ShiftsUiState.Loading)
                            combine(
                                // The month plus the leading tail of the pay week that
                                // contains the 1st, so the week cards and per-row pay
                                // see the same weekly-overtime context Reports does.
                                // One query and a handful of extra rows; filtered back
                                // to the month for everything that is displayed.
                                shiftsRepository.observeShiftsForPayContext(
                                    userId,
                                    month.year,
                                    month.monthValue,
                                    zone,
                                    settings?.let { MonthlyReportBuilder.defaultWeekStartDay(it) } ?: 0,
                                ),
                                shiftsRepository.observeActiveShift(userId),
                                compensationProfilesRepository.observeProfiles(userId),
                                premiumProfilesRepository.observeProfiles(userId),
                                tasksRepository.observeAllTasks(userId),
                            ) { contextShifts, activeShift, profiles, premiumProfiles, tasks ->
                                val shifts = if (settings == null) {
                                    contextShifts.filter {
                                        YearMonth.from(it.startTime.atZone(zone)) == month
                                    }
                                } else {
                                    MonthlyReportBuilder.filterByMonth(
                                        contextShifts, month.year, month.monthValue, settings,
                                    )
                                }
                                if (shifts.isEmpty()) {
                                    ShiftsUiState.Empty(month)
                                } else {
                                    ShiftsUiState.Ready(
                                        month = month,
                                        shifts = shifts,
                                        activeShift = activeShift,
                                        featuresTravelRefunds = settings?.featuresTravelRefunds ?: false,
                                        settings = settings,
                                        profiles = profiles,
                                        premiumProfiles = premiumProfiles,
                                        tasks = tasks,
                                        payContextShifts = contextShifts,
                                    )
                                }
                            }.collect { emit(it) }
                        }
                    }
                }
        }.catch { e ->
            emit(ShiftsUiState.Error(e.message ?: "Unknown error"))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ShiftsUiState.Loading,
        )

    fun previousMonth() {
        monthNavigated = true
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        monthNavigated = true
        if (_selectedMonth.value < YearMonth.now(workZone)) {
            _selectedMonth.value = _selectedMonth.value.plusMonths(1)
        }
    }

    fun retry() {
        _refreshNonce.value++
    }

    fun showCreateForm() {
        _formErrors.value = emptyMap()
        _formTarget.value = ShiftFormNavState.Create
    }

    suspend fun suggestTaskForStart(startTime: Instant): String? {
        val userId = currentUserProvider.currentUserId() ?: return null
        val tasks = tasksRepository.getActiveTasks(userId)
        if (tasks.isEmpty()) return null
        val settings = settingsRepository.getSettings(userId)
        val zone = settings?.let { WorkTimezone.zoneFor(it) } ?: java.time.ZoneId.systemDefault()
        val recent = shiftsRepository.observeRecentCompletedShifts(userId, 60).first()
        return TaskHabitSuggestionBuilder.suggest(tasks, recent, now = startTime, zoneId = zone)?.task?.id
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
            val userId = currentUserProvider.currentUserId() ?: return@launch
            val settings = settingsRepository.getSettings(userId) ?: return@launch
            compensationProfilesRepository.ensureMigrated(userId)
            premiumProfilesRepository.ensureDefaults(userId)
            val profiles = compensationProfilesRepository.getProfiles(userId)
            val now = Instant.now()
            var shift = Shift(
                id = UUID.randomUUID().toString(),
                userId = userId,
                startTime = input.startTime,
                endTime = input.endTime,
                breakMinutes = input.breakMinutes,
                notes = input.notes.ifBlank { null },
                isSpecialDay = input.premiumProfileId != null,
                premiumProfileId = input.premiumProfileId,
                forceRegularRate = input.forceRegularRate,
                refundAction = input.refundAction,
                compensationProfileId = input.compensationProfileId ?: settings.defaultCompensationProfileId,
                createdAt = now,
                updatedAt = now,
            )
            val task = input.taskId?.let { tasksRepository.getTaskById(userId, it) }
            shift = TaskSnapshotApplier.applyToShift(shift, task)
            shift = ShiftCompensationReassignment.apply(
                shift = shift,
                requested = input.compensationSource,
                project = resolveClockableProject(userId, settings, input),
            )
            // A project shift earns the project fee, so no wage snapshot is taken.
            if (shift.isCompleted && shift.isEmployeePaid) {
                shift = shift.copy(
                    compensationSnapshot = ShiftCompensationHelper.buildClockOutSnapshot(
                        shift, settings, profiles,
                    ),
                )
            }
            shiftsRepository.createManualShift(shift)
            closeForm()
            _userMessage.value = UiText.Res(R.string.shifts_feedback_added)
        }
    }

    fun saveEditedShift(shiftId: String, input: ShiftFormInput) {
        val errors = validate(input)
        if (errors.isNotEmpty()) { _formErrors.value = errors; return }
        viewModelScope.launch {
            val userId = currentUserProvider.currentUserId() ?: return@launch
            val settings = settingsRepository.getSettings(userId) ?: return@launch
            val profiles = compensationProfilesRepository.getProfiles(userId)
            val existing = shiftsRepository.getShiftById(shiftId) ?: return@launch
            val task = input.taskId?.let { tasksRepository.getTaskById(userId, it) }
            var updated = existing.copy(
                startTime = input.startTime,
                endTime = input.endTime,
                breakMinutes = input.breakMinutes,
                notes = input.notes.ifBlank { null },
                isSpecialDay = input.premiumProfileId != null,
                premiumProfileId = input.premiumProfileId,
                forceRegularRate = input.forceRegularRate,
                refundAction = existing.refundAction,
                compensationProfileId = input.compensationProfileId ?: existing.compensationProfileId,
                updatedAt = Instant.now(),
            )
            updated = TaskSnapshotApplier.applyToShift(updated, task)
            // Reassigning between wages and project time is the one edit that
            // changes which side pays for the hours, so it always recalculates.
            // Billing records and payments are untouched: a billed amount is a
            // frozen snapshot, and only the tracked hours move.
            updated = ShiftCompensationReassignment.apply(
                shift = updated,
                requested = input.compensationSource,
                project = resolveFormProject(userId, settings, input, existing),
            )
            val payAffecting = updated.compensationSource != existing.compensationSource ||
                updated.projectId != existing.projectId ||
                updated.startTime != existing.startTime ||
                updated.endTime != existing.endTime ||
                updated.breakMinutes != existing.breakMinutes ||
                updated.isSpecialDay != existing.isSpecialDay ||
                updated.premiumProfileId != existing.premiumProfileId ||
                updated.forceRegularRate != existing.forceRegularRate ||
                updated.compensationProfileId != existing.compensationProfileId ||
                updated.taskId != existing.taskId ||
                updated.taskHourlyRateSnapshot != existing.taskHourlyRateSnapshot
            val finalShift = if (updated.isCompleted && payAffecting && updated.isEmployeePaid) {
                updated.copy(
                    compensationSnapshot = ShiftCompensationHelper.buildClockOutSnapshot(
                        updated, settings, profiles,
                    ),
                )
            } else {
                updated
            }
            shiftsRepository.updateShift(finalShift)
            closeForm()
            _userMessage.value = UiText.Res(R.string.shifts_feedback_updated)
        }
    }

    /**
     * The project a form input points at, or null when project time is not
     * available: the feature is off, no id was given, or the project is not open
     * for time tracking.
     */
    private suspend fun resolveClockableProject(
        userId: String,
        settings: com.elmtrackr.app.domain.model.UserSettings,
        input: ShiftFormInput,
    ): com.elmtrackr.app.domain.model.Project? {
        if (settings.featuresPaidProjects != true) return null
        if (!input.compensationSource.isProjectTime) return null
        val projectId = input.projectId ?: return null
        return projectsRepository.getProject(userId, projectId)
            ?.takeIf { ProjectClockInOptions.isClockable(it) }
    }

    /**
     * Like [resolveClockableProject], but a shift that is *already* linked to a
     * project keeps that link even after the project stops being clockable.
     * Completing or archiving a project must not silently convert its recorded
     * hours into paid wages the next time an unrelated field on the shift is
     * edited.
     */
    private suspend fun resolveFormProject(
        userId: String,
        settings: com.elmtrackr.app.domain.model.UserSettings,
        input: ShiftFormInput,
        existing: Shift,
    ): com.elmtrackr.app.domain.model.Project? {
        resolveClockableProject(userId, settings, input)?.let { return it }
        if (!input.compensationSource.isProjectTime) return null
        val unchanged = input.projectId != null && input.projectId == existing.projectId
        if (!unchanged) return null
        return projectsRepository.getProject(userId, existing.projectId!!)
    }

    fun deleteShift(shiftId: String) {
        viewModelScope.launch {
            shiftsRepository.deleteShift(shiftId)
            _userMessage.value = UiText.Res(R.string.shifts_feedback_deleted)
        }
    }

    fun saveRefundClaim(
        shiftId: String,
        direction: RefundDirection,
        provider: RefundProvider,
        amount: Double,
        rideAt: Instant,
        notes: String,
        receipt: ReceiptUpload?,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (amount <= 0) {
            _formErrors.value = mapOf("refund" to UiText.Res(R.string.shifts_error_refund_amount))
            onComplete(false)
            return
        }
        viewModelScope.launch {
            _formErrors.value = _formErrors.value - "refund"
            val shift = shiftsRepository.getShiftById(shiftId)
            if (shift == null) {
                _formErrors.value = mapOf("refund" to UiText.Res(R.string.shifts_error_refund_shift_missing))
                onComplete(false)
                return@launch
            }
            val userId = currentUserProvider.currentUserId()
            if (userId == null) {
                _formErrors.value = mapOf("refund" to UiText.Res(R.string.shifts_error_refund_sign_in))
                onComplete(false)
                return@launch
            }
            val receiptPath = if (receipt != null) {
                val storage = refundReceiptStorage
                val uploaded = storage?.let {
                    runCatching { it.upload(userId, shiftId, direction, receipt) }.getOrNull()
                }
                if (uploaded == null) {
                    // Surfaced, not swallowed: the claim is about to be saved without
                    // its receipt, and the user must know so they can re-attach it.
                    _userMessage.value = UiText.Res(R.string.shifts_refund_saved_without_receipt)
                }
                uploaded
            } else null

            runCatching {
                // Every save is a new ride: a shift may carry several rides in
                // each direction, so nothing is looked up or overwritten here.
                refundsRepository.addClaim(
                    shiftLocalId = shiftId,
                    userId = userId,
                    direction = direction,
                    provider = provider,
                    amount = amount,
                    notes = notes.ifBlank { null },
                    rideAt = rideAt,
                    receiptPath = receiptPath,
                )
                updateShiftRefundAction(shift, RefundAction.SUBMITTED)
            }.onSuccess {
                _formErrors.value = _formErrors.value - "refund"
                onComplete(true)
            }.onFailure {
                _formErrors.value = mapOf("refund" to UiText.Res(R.string.shifts_error_refund_save_failed))
                onComplete(false)
            }
        }
    }

    fun deleteRefundClaim(claimId: String) {
        viewModelScope.launch {
            val claim = refundsRepository.getClaimById(claimId) ?: return@launch
            refundsRepository.deleteClaim(claimId)
            claim.receiptPath?.let { path -> runCatching { refundReceiptStorage?.delete(path) } }
            val remaining = refundsRepository.observeClaimsForShift(claim.shiftId).first()
            val shift = shiftsRepository.getShiftById(claim.shiftId)
            if (shift != null) {
                updateShiftRefundAction(
                    shift,
                    if (remaining.isEmpty()) null else RefundAction.SUBMITTED,
                )
            }
        }
    }

    fun updateRefundAction(shiftId: String, action: RefundAction?) {
        viewModelScope.launch {
            val shift = shiftsRepository.getShiftById(shiftId) ?: return@launch
            updateShiftRefundAction(shift, action)
        }
    }

    suspend fun receiptUrl(path: String): String? =
        refundReceiptStorage?.let { storage -> runCatching { storage.createSignedUrl(path) }.getOrNull() }

    fun createCompensationProfile(name: String, onComplete: (String?) -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            onComplete(null)
            return
        }
        viewModelScope.launch {
            val userId = currentUserProvider.currentUserId() ?: run {
                onComplete(null)
                return@launch
            }
            compensationProfilesRepository.ensureMigrated(userId)
            val existing = compensationProfilesRepository.getProfiles(userId)
            val template = existing.firstOrNull { it.isDefault } ?: existing.firstOrNull()
            if (template == null) {
                onComplete(null)
                return@launch
            }
            val now = Instant.now()
            val created = compensationProfilesRepository.upsertProfile(
                template.copy(
                    id = "",
                    name = trimmed,
                    isDefault = false,
                    remoteId = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            onComplete(created.id)
        }
    }

    private suspend fun updateShiftRefundAction(shift: Shift, action: RefundAction?) {
        val updated = shift.copy(refundAction = action, updatedAt = Instant.now())
        shiftsRepository.updateShift(updated)
        val target = _formTarget.value
        if (target is ShiftFormNavState.Edit && target.shift.id == shift.id) {
            _formTarget.value = ShiftFormNavState.Edit(updated)
        }
    }

    internal fun validate(input: ShiftFormInput): Map<String, UiText> {
        val errors = mutableMapOf<String, UiText>()
        if (input.endTime != null && !input.endTime.isAfter(input.startTime)) {
            errors["endTime"] = UiText.Res(R.string.shifts_error_end_after_start)
        }
        if (input.breakMinutes < 0) {
            errors["breakMinutes"] = UiText.Res(R.string.shifts_error_break_negative)
        } else if (input.endTime != null && input.endTime.isAfter(input.startTime)) {
            val grossMinutes = java.time.Duration.between(input.startTime, input.endTime).toMinutes()
            if (input.breakMinutes > grossMinutes) {
                errors["breakMinutes"] = UiText.Res(R.string.shifts_error_break_too_long)
            }
        }
        return errors
    }
}
