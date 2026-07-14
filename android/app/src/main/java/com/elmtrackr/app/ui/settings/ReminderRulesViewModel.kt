package com.elmtrackr.app.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.notification.OvertimeReminderScheduler
import com.elmtrackr.app.notification.ReminderRule
import com.elmtrackr.app.notification.ReminderRulesCodec
import com.elmtrackr.app.notification.ReminderRulesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the reminder-schedule sentence editor. Edits persist immediately
 * (they live outside the settings save flow) and reschedule any pending
 * reminder work for the active shift.
 */
@HiltViewModel
class ReminderRulesViewModel @Inject constructor(
    private val app: Application,
    private val currentUserProvider: CurrentUserProvider,
    private val shiftsRepository: ShiftsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val rules: StateFlow<List<ReminderRule>> = ReminderRulesStore.rulesFlow(app)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderRulesCodec.DEFAULT_RULES)

    fun addRule() {
        val current = rules.value
        if (current.size >= ReminderRulesCodec.MAX_RULES) return
        // New notifications default to a timed reminder (a specific time, every
        // day); the editor can switch it to a before/after-overtime rule.
        persist(current + ReminderRule.newAtTime())
    }

    fun updateRule(updated: ReminderRule) {
        persist(rules.value.map { if (it.id == updated.id) updated else it })
    }

    fun removeRule(id: String) {
        persist(rules.value.filterNot { it.id == id })
    }

    private fun persist(newRules: List<ReminderRule>) {
        viewModelScope.launch {
            ReminderRulesStore.save(app, newRules)
            rescheduleForActiveShift()
        }
    }

    private suspend fun rescheduleForActiveShift() {
        val userId = currentUserProvider.currentUserId() ?: return
        val shift = shiftsRepository.observeActiveShift(userId).firstOrNull() ?: return
        val settings = settingsRepository.getSettings(userId)
        OvertimeReminderScheduler.scheduleForActiveShift(app, shift, settings)
    }
}
