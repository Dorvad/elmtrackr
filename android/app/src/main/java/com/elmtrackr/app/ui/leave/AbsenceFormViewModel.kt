package com.elmtrackr.app.ui.leave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.common.UserFacingError
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.leave.LeaveConflict
import com.elmtrackr.app.domain.leave.LeaveEstimate
import com.elmtrackr.app.domain.leave.LeaveEstimateGap
import com.elmtrackr.app.domain.leave.LeaveWorkdayPlanner
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import com.elmtrackr.app.domain.model.LeaveCalculationSnapshot
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.model.Workplace
import com.elmtrackr.app.domain.repository.AbsenceDayInput
import com.elmtrackr.app.domain.repository.AbsenceDraft
import com.elmtrackr.app.domain.repository.LeaveRepository
import com.elmtrackr.app.domain.repository.WorkplacesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One candidate date, its estimate, and whether it counts. */
data class AbsenceDayRow(
    val date: LocalDate,
    val selected: Boolean,
    val sickDayOrdinal: Int? = null,
    val multiplier: Double? = null,
    val estimatedGross: Double? = null,
    val gap: LeaveEstimateGap? = null,
    val snapshot: LeaveCalculationSnapshot? = null,
    /** What the user typed for a day the engine could not value. */
    val manualAmount: String = "",
    val observedCount: Int = 0,
)

sealed interface AbsenceFormUiState {
    data object Loading : AbsenceFormUiState

    /** The user has no workplace yet, so there is nothing to report leave against. */
    data object NoWorkplace : AbsenceFormUiState

    data class Ready(
        val type: AbsenceType,
        val workplaces: List<Workplace>,
        val selectedWorkplaceId: String,
        val isRange: Boolean,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val days: List<AbsenceDayRow>,
        val fullDay: Boolean,
        val partialAmount: String,
        val partialUnit: LeaveBalanceUnit,
        val notes: String,
        val currencyCode: String,
        val conflicts: List<LeaveConflict> = emptyList(),
        val mergeOffer: LeaveConflict.AdjacentSickPeriod? = null,
        val validationError: UiText? = null,
        val isEstimating: Boolean = false,
        val isSaving: Boolean = false,
        val editingEventId: String? = null,
    ) : AbsenceFormUiState {
        val selectedDays: List<AbsenceDayRow> get() = days.filter { it.selected }
        val estimatedTotal: Double get() = selectedDays.sumOf { it.estimatedGross ?: 0.0 }
        val needsInput: Boolean get() = selectedDays.any { it.gap != null }

        /**
         * Only a duplicate blocks. A shift on the same date is a warning, because
         * working the morning and taking the afternoon off is ordinary.
         */
        val blockingConflicts: List<LeaveConflict.DuplicateLeave>
            get() = conflicts.filterIsInstance<LeaveConflict.DuplicateLeave>()
                .filter { conflict -> selectedDays.any { it.date == conflict.date } }

        val canSave: Boolean
            get() = !isSaving && selectedDays.isNotEmpty() && blockingConflicts.isEmpty()

        /** Hidden when there is nothing to choose between. */
        val showWorkplacePicker: Boolean get() = workplaces.size > 1
    }

    data class Error(val message: String) : AbsenceFormUiState
}

@HiltViewModel
class AbsenceFormViewModel @Inject constructor(
    private val leaveRepository: LeaveRepository,
    private val workplacesRepository: WorkplacesRepository,
    private val currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AbsenceFormUiState>(AbsenceFormUiState.Loading)
    val uiState: StateFlow<AbsenceFormUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<UiText?>(null)
    val userMessage: StateFlow<UiText?> = _userMessage.asStateFlow()

    private val _closed = MutableStateFlow(false)
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

    private var userId: String? = null
    private var workedDates: List<LocalDate> = emptyList()

    /**
     * What [start] was last called with.
     *
     * This ViewModel is resolved from the Shifts destination's store, so it
     * outlives the form being closed. Without this key, reporting a vacation and
     * then opening sick leave would reuse the vacation state, and the one-shot
     * [closed] flag would still be true and shut the new form immediately.
     */
    private var startedKey: Pair<AbsenceType, String?>? = null

    /** Cancelled and restarted on each edit, so a slow estimate cannot land after a newer one. */
    private var estimateJob: Job? = null

    fun start(type: AbsenceType, editingEventId: String? = null, today: LocalDate = LocalDate.now()) {
        val key = type to editingEventId
        if (startedKey == key && _uiState.value is AbsenceFormUiState.Ready) return
        startedKey = key
        estimateJob?.cancel()
        _closed.value = false
        _userMessage.value = null
        _uiState.value = AbsenceFormUiState.Loading
        viewModelScope.launch {
            val user = currentUserProvider.currentUserId()
            if (user == null) {
                _uiState.value = AbsenceFormUiState.NoWorkplace
                return@launch
            }
            userId = user
            // Created on demand rather than during the database upgrade, so a user
            // who has been tracking shifts for a year gets their existing job and
            // its history rather than a blank one.
            workplacesRepository.ensureDefaultWorkplace(user)
            val workplaces = workplacesRepository.getWorkplaces(user).filter { !it.isArchived }
            if (workplaces.isEmpty()) {
                _uiState.value = AbsenceFormUiState.NoWorkplace
                return@launch
            }
            val workplaceId = workplaces.firstOrNull { it.isDefault }?.id ?: workplaces.first().id
            workplacesRepository.ensurePolicy(user, workplaceId)

            val existing = editingEventId?.let { leaveRepository.getEvent(user, it) }
            val allocations = editingEventId?.let { leaveRepository.getAllocationsForEvent(it) } ?: emptyList()
            val start = existing?.startDate ?: today
            val end = existing?.endDate ?: today

            workedDates = leaveRepository.workedDatesForWorkplace(user, workplaceId)
            val firstAllocation = allocations.firstOrNull()

            _uiState.value = AbsenceFormUiState.Ready(
                type = existing?.type ?: type,
                workplaces = workplaces,
                selectedWorkplaceId = firstAllocation?.workplaceId ?: workplaceId,
                isRange = start != end,
                startDate = start,
                endDate = end,
                days = if (allocations.isEmpty()) {
                    // null, not emptyList: null means "propose from the work pattern",
                    // while an empty list of days to keep would tick nothing at all.
                    proposeDays(start, end, null)
                } else {
                    // Editing keeps exactly the days that were reported, ticked. The
                    // proposal is for a new entry; re-proposing here could silently
                    // add or drop a day the user had already decided about.
                    proposeDays(start, end, allocations.map { it.affectedDate })
                },
                fullDay = firstAllocation?.let { it.entitlementUnits == 1.0 } ?: true,
                partialAmount = firstAllocation
                    ?.takeIf { it.entitlementUnits != 1.0 }
                    ?.entitlementUnits
                    ?.let { formatUnits(it) }
                    ?: "",
                partialUnit = firstAllocation?.unit ?: LeaveBalanceUnit.DAYS,
                notes = existing?.notes.orEmpty(),
                currencyCode = workplaces.firstOrNull { it.id == workplaceId }?.currencyCode ?: "ILS",
                editingEventId = editingEventId,
            )
            refresh()
        }
    }

    fun selectWorkplace(workplaceId: String) {
        update { state ->
            state.copy(
                selectedWorkplaceId = workplaceId,
                currencyCode = state.workplaces.firstOrNull { it.id == workplaceId }?.currencyCode
                    ?: state.currencyCode,
            )
        }
        val user = userId ?: return
        viewModelScope.launch {
            workplacesRepository.ensurePolicy(user, workplaceId)
            // The weekday pattern belongs to the job, so switching jobs re-proposes
            // which days the absence hits.
            workedDates = leaveRepository.workedDatesForWorkplace(user, workplaceId)
            update { it.copy(days = proposeDays(it.startDate, it.endDate, null)) }
            refresh()
        }
    }

    fun setRange(isRange: Boolean) {
        update { state ->
            val end = if (isRange) state.endDate else state.startDate
            state.copy(isRange = isRange, endDate = end, days = proposeDays(state.startDate, end, null))
        }
        refresh()
    }

    fun setStartDate(date: LocalDate) {
        update { state ->
            // An end before the start is not a range the user meant; the end follows.
            val end = if (!state.isRange || state.endDate.isBefore(date)) date else state.endDate
            state.copy(startDate = date, endDate = end, days = proposeDays(date, end, null))
        }
        refresh()
    }

    fun setEndDate(date: LocalDate) {
        update { state ->
            val start = if (date.isBefore(state.startDate)) date else state.startDate
            state.copy(startDate = start, endDate = date, days = proposeDays(start, date, null))
        }
        refresh()
    }

    fun toggleDay(date: LocalDate) {
        update { state ->
            state.copy(
                days = state.days.map { day ->
                    if (day.date == date) day.copy(selected = !day.selected) else day
                },
            )
        }
        refresh()
    }

    fun setFullDay(fullDay: Boolean) {
        update { it.copy(fullDay = fullDay) }
        refresh()
    }

    fun setPartialAmount(amount: String) {
        update { it.copy(partialAmount = amount) }
        refresh()
    }

    fun setPartialUnit(unit: LeaveBalanceUnit) {
        update { it.copy(partialUnit = unit) }
        refresh()
    }

    fun setNotes(notes: String) = update { it.copy(notes = notes) }

    fun setManualAmount(date: LocalDate, amount: String) {
        update { state ->
            state.copy(
                days = state.days.map { day ->
                    if (day.date == date) day.copy(manualAmount = amount) else day
                },
            )
        }
        refresh()
    }

    fun dismissMergeOffer() = update { it.copy(mergeOffer = null) }

    fun save() {
        val user = userId ?: return
        val state = _uiState.value as? AbsenceFormUiState.Ready ?: return
        if (state.selectedDays.isEmpty()) {
            update { it.copy(validationError = UiText.Res(R.string.leave_form_no_days_selected)) }
            return
        }
        if (!state.canSave) return
        update { it.copy(isSaving = true, validationError = null) }
        viewModelScope.launch {
            runCatching {
                val draft = state.toDraft()
                if (state.editingEventId == null) {
                    leaveRepository.saveAbsence(user, draft)
                } else {
                    leaveRepository.updateAbsence(user, state.editingEventId, draft)
                }
            }.onSuccess {
                _userMessage.value = UiText.Res(R.string.leave_form_saved)
                _closed.value = true
            }.onFailure { error ->
                update {
                    it.copy(
                        isSaving = false,
                        // Never the exception text: repository failures carry
                        // English developer strings, and an exception with no
                        // message rendered as an empty error beside a re-enabled
                        // save button — the user was told nothing at all.
                        validationError = UserFacingError.message(error, R.string.leave_form_save_failed),
                    )
                }
            }
        }
    }

    fun delete() {
        val user = userId ?: return
        val eventId = (_uiState.value as? AbsenceFormUiState.Ready)?.editingEventId ?: return
        viewModelScope.launch {
            leaveRepository.deleteAbsence(user, eventId)
            _userMessage.value = UiText.Res(R.string.leave_form_deleted)
            _closed.value = true
        }
    }

    /** Merges into the neighbouring illness, only ever on the user's say-so. */
    fun mergeWithAdjacent() {
        val user = userId ?: return
        val state = _uiState.value as? AbsenceFormUiState.Ready ?: return
        val offer = state.mergeOffer ?: return
        val editing = state.editingEventId
        viewModelScope.launch {
            if (editing == null) {
                // Nothing is saved yet, so extending this entry over the existing
                // period is the merge: the ordinals then run through both.
                update {
                    it.copy(
                        mergeOffer = null,
                        startDate = minOf(it.startDate, offer.existingStart),
                        endDate = maxOf(it.endDate, offer.existingEnd),
                        isRange = true,
                    )
                }
                update { it.copy(days = proposeDays(it.startDate, it.endDate, null)) }
                refresh()
            } else {
                leaveRepository.mergeSickPeriods(user, offer.existingEventId, editing)
                _userMessage.value = UiText.Res(R.string.leave_form_saved)
                _closed.value = true
            }
        }
    }

    fun consumeUserMessage() {
        _userMessage.value = null
    }

    // ── Estimating ────────────────────────────────────────────────────────────

    /**
     * Re-prices the selected days and re-checks for conflicts.
     *
     * Through the repository, which is the same path the save takes. A preview
     * computed a second way is a preview that can disagree with what gets stored.
     */
    private fun refresh() {
        val user = userId ?: return
        estimateJob?.cancel()
        val state = _uiState.value as? AbsenceFormUiState.Ready ?: return
        if (state.selectedDays.isEmpty()) {
            update { it.copy(conflicts = emptyList(), isEstimating = false) }
            return
        }
        update { it.copy(isEstimating = true) }
        estimateJob = viewModelScope.launch {
            val draft = state.toDraft()
            val previews = runCatching { leaveRepository.previewAbsence(user, draft) }.getOrDefault(emptyList())
            val conflicts = runCatching {
                leaveRepository.detectConflicts(user, draft, excludeEventId = state.editingEventId)
            }.getOrDefault(emptyList())
            val byDate = previews.associateBy { it.date }
            update { current ->
                current.copy(
                    isEstimating = false,
                    conflicts = conflicts,
                    mergeOffer = current.mergeOffer
                        ?: conflicts.filterIsInstance<LeaveConflict.AdjacentSickPeriod>().firstOrNull(),
                    days = current.days.map { day ->
                        val estimate = byDate[day.date]?.estimate
                        when (estimate) {
                            is LeaveEstimate.Ready -> day.copy(
                                sickDayOrdinal = estimate.snapshot.sickDayOrdinal,
                                multiplier = estimate.snapshot.multiplier,
                                estimatedGross = estimate.snapshot.estimatedGrossPay,
                                snapshot = estimate.snapshot,
                                gap = null,
                            )

                            is LeaveEstimate.NeedsInput -> day.copy(
                                estimatedGross = null,
                                snapshot = null,
                                gap = estimate.gap,
                                // The ordinal is known from the range even when the money
                                // is not, and it is the thing the user most wants to see
                                // on a sick period.
                                sickDayOrdinal = ordinalFor(day.date),
                            )

                            null -> day.copy(estimatedGross = null, snapshot = null, gap = null)
                        }
                    },
                )
            }
        }
    }

    private fun AbsenceFormUiState.Ready.toDraft(): AbsenceDraft {
        val units = if (fullDay) 1.0 else partialAmount.toDoubleOrNull() ?: 1.0
        val unit = if (fullDay) LeaveBalanceUnit.DAYS else partialUnit
        return AbsenceDraft(
            type = type,
            startDate = startDate,
            endDate = endDate,
            notes = notes.trim().ifBlank { null },
            days = selectedDays.map { day ->
                AbsenceDayInput(
                    workplaceId = selectedWorkplaceId,
                    date = day.date,
                    entitlementUnits = units,
                    unit = unit,
                    manualDailyAmount = day.manualAmount.toDoubleOrNull(),
                )
            },
        )
    }

    private fun ordinalFor(date: LocalDate): Int? {
        val state = _uiState.value as? AbsenceFormUiState.Ready ?: return null
        if (state.type != AbsenceType.SICK) return null
        return (date.toEpochDay() - state.startDate.toEpochDay() + 1).toInt()
    }

    /**
     * [keepSelected] preserves an explicit choice across a change that only moved
     * the range; pass null to re-propose from the work pattern.
     */
    private fun proposeDays(
        start: LocalDate,
        end: LocalDate,
        keepSelected: List<LocalDate>?,
    ): List<AbsenceDayRow> {
        val previouslySelected = keepSelected
            ?: (_uiState.value as? AbsenceFormUiState.Ready)
                ?.days
                ?.filter { it.selected }
                ?.map { it.date }
        return LeaveWorkdayPlanner.propose(start, end, workedDates).map { proposal ->
            AbsenceDayRow(
                date = proposal.date,
                selected = previouslySelected?.contains(proposal.date) ?: proposal.selected,
                observedCount = proposal.observedCount,
            )
        }
    }

    private fun formatUnits(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private inline fun update(block: (AbsenceFormUiState.Ready) -> AbsenceFormUiState.Ready) {
        val current = _uiState.value as? AbsenceFormUiState.Ready ?: return
        _uiState.value = block(current)
    }
}
