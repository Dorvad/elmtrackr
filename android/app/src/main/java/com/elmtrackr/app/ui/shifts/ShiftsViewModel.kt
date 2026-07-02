package com.elmtrackr.app.ui.shifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.domain.compensation.ShiftCompensationHelper
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.RefundPolicy
import com.elmtrackr.app.domain.model.ReceiptUpload
import com.elmtrackr.app.domain.model.RefundAction
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.model.RefundProvider
import com.elmtrackr.app.domain.repository.RefundsRepository
import com.elmtrackr.app.domain.repository.RefundReceiptStorage
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.domain.repository.TasksRepository
import com.elmtrackr.app.domain.tasks.TaskHabitSuggestionBuilder
import com.elmtrackr.app.domain.time.WorkTimezone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
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
    private val tasksRepository: TasksRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val refundsRepository: RefundsRepository,
    private val refundReceiptStorage: RefundReceiptStorage?,
) : ViewModel() {

    private val _formTarget = MutableStateFlow<ShiftFormNavState?>(null)
    val formTarget: StateFlow<ShiftFormNavState?> = _formTarget.asStateFlow()

    private val _formErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val formErrors: StateFlow<Map<String, String>> = _formErrors.asStateFlow()

    private val _refundNotice = MutableStateFlow<String?>(null)
    val refundNotice: StateFlow<String?> = _refundNotice.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

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
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

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
                        combine(_selectedMonth, settingsRepository.observeSettings(userId)) { month, settings ->
                            month to settings
                        }.flatMapLatest { (month, settings) ->
                            val zone = settings?.let { WorkTimezone.zoneFor(it) } ?: ZoneId.of("UTC")
                            shiftsRepository.observeShiftsByMonthInZone(
                                userId,
                                month.year,
                                month.monthValue,
                                zone,
                            )
                        },
                        shiftsRepository.observeActiveShift(userId),
                        settingsRepository.observeSettings(userId),
                        compensationProfilesRepository.observeProfiles(userId),
                        tasksRepository.observeAllTasks(userId),
                    ) { shifts, activeShift, settings, profiles, tasks ->
                        if (shifts.isEmpty()) ShiftsUiState.Empty
                        else ShiftsUiState.Ready(
                            shifts = shifts,
                            activeShift = activeShift,
                            featuresTravelRefunds = settings?.featuresTravelRefunds ?: false,
                            settings = settings,
                            profiles = profiles,
                            tasks = tasks,
                        )
                    }
                }
        }.catch { e ->
            emit(ShiftsUiState.Error(e.message ?: "Unknown error"))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ShiftsUiState.Loading,
        )

    fun previousMonth() { _selectedMonth.value = _selectedMonth.value.minusMonths(1) }

    fun nextMonth() {
        if (_selectedMonth.value < YearMonth.now()) {
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
        _refundNotice.value = null
    }

    fun createShift(input: ShiftFormInput) {
        val errors = validate(input)
        if (errors.isNotEmpty()) { _formErrors.value = errors; return }
        viewModelScope.launch {
            val userId = currentUserProvider.currentUserId() ?: return@launch
            val settings = settingsRepository.getSettings(userId) ?: return@launch
            compensationProfilesRepository.ensureMigrated(userId)
            val profiles = compensationProfilesRepository.getProfiles(userId)
            val now = Instant.now()
            var shift = Shift(
                id = UUID.randomUUID().toString(),
                userId = userId,
                startTime = input.startTime,
                endTime = input.endTime,
                breakMinutes = input.breakMinutes,
                notes = input.notes.ifBlank { null },
                isSpecialDay = input.isSpecialDay,
                refundAction = input.refundAction,
                compensationProfileId = input.compensationProfileId ?: settings.defaultCompensationProfileId,
                createdAt = now,
                updatedAt = now,
            )
            val task = input.taskId?.let { tasksRepository.getTaskById(userId, it) }
            shift = TaskSnapshotApplier.applyToShift(shift, task)
            if (shift.isCompleted) {
                shift = shift.copy(
                    compensationSnapshot = ShiftCompensationHelper.buildClockOutSnapshot(
                        shift, settings, profiles,
                    ),
                )
            }
            shiftsRepository.createManualShift(shift)
            closeForm()
            _userMessage.value = "Shift added"
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
                isSpecialDay = input.isSpecialDay,
                refundAction = existing.refundAction,
                compensationProfileId = input.compensationProfileId ?: existing.compensationProfileId,
                updatedAt = Instant.now(),
            )
            updated = TaskSnapshotApplier.applyToShift(updated, task)
            val payAffecting = updated.startTime != existing.startTime ||
                updated.endTime != existing.endTime ||
                updated.breakMinutes != existing.breakMinutes ||
                updated.isSpecialDay != existing.isSpecialDay ||
                updated.compensationProfileId != existing.compensationProfileId ||
                updated.taskId != existing.taskId ||
                updated.taskHourlyRateSnapshot != existing.taskHourlyRateSnapshot
            val finalShift = if (updated.isCompleted && payAffecting) {
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
            _userMessage.value = "Shift updated"
        }
    }

    fun deleteShift(shiftId: String) {
        viewModelScope.launch {
            shiftsRepository.deleteShift(shiftId)
            _userMessage.value = "Shift deleted"
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
            _formErrors.value = mapOf("refund" to "Enter a valid refund amount")
            onComplete(false)
            return
        }
        viewModelScope.launch {
            _formErrors.value = _formErrors.value - "refund"
            _refundNotice.value = null
            val shift = shiftsRepository.getShiftById(shiftId)
            if (shift == null) {
                _formErrors.value = mapOf("refund" to "Shift not found")
                onComplete(false)
                return@launch
            }
            val eligibility = when (direction) {
                RefundDirection.TO_WORK -> RefundPolicy.checkToWorkEligibility(shift)
                RefundDirection.FROM_WORK -> RefundPolicy.checkFromWorkEligibility(shift)
            }
            if (!eligibility.eligible) {
                _formErrors.value = mapOf("refund" to "This ride is not eligible for a travel refund")
                onComplete(false)
                return@launch
            }
            val existing = refundsRepository.observeClaimsForShift(shiftId).first()
                .firstOrNull { it.direction == direction }
            val userId = currentUserProvider.currentUserId()
            if (userId == null) {
                _formErrors.value = mapOf("refund" to "Sign in before saving a claim")
                onComplete(false)
                return@launch
            }
            val oldReceiptPath = existing?.receiptPath
            val receiptPath = if (receipt != null) {
                val storage = refundReceiptStorage
                val uploaded = storage?.let {
                    runCatching { it.upload(userId, shiftId, direction, receipt) }.getOrNull()
                }
                if (uploaded == null) {
                    _refundNotice.value = "Claim saved without the new receipt. Receipt upload is unavailable; you can attach it later."
                    oldReceiptPath
                } else uploaded
            } else oldReceiptPath

            runCatching {
                if (existing == null) {
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
                } else {
                    refundsRepository.updateClaim(
                        existing.copy(
                            provider = provider,
                            amount = amount,
                            rideAt = rideAt,
                            notes = notes.ifBlank { null },
                            receiptPath = receiptPath,
                            updatedAt = Instant.now(),
                        ),
                    )
                }
                if (oldReceiptPath != null && oldReceiptPath != receiptPath) {
                    runCatching { refundReceiptStorage?.delete(oldReceiptPath) }
                }
                updateShiftRefundAction(shift, RefundAction.SUBMITTED)
            }.onSuccess {
                _formErrors.value = _formErrors.value - "refund"
                onComplete(true)
            }.onFailure {
                _formErrors.value = mapOf("refund" to (it.message ?: "Unable to save the refund claim"))
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

    private suspend fun updateShiftRefundAction(shift: Shift, action: RefundAction?) {
        val updated = shift.copy(refundAction = action, updatedAt = Instant.now())
        shiftsRepository.updateShift(updated)
        val target = _formTarget.value
        if (target is ShiftFormNavState.Edit && target.shift.id == shift.id) {
            _formTarget.value = ShiftFormNavState.Edit(updated)
        }
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
}
