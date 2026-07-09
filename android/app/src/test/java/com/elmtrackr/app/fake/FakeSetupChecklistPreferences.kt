package com.elmtrackr.app.fake

import com.elmtrackr.app.data.local.preferences.AppPreferenceValues
import com.elmtrackr.app.data.local.preferences.SetupChecklistPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSetupChecklistPreferences(
    initial: AppPreferenceValues = AppPreferenceValues(),
) : SetupChecklistPreferences {

    private val state = MutableStateFlow(initial)

    override val preferences: Flow<AppPreferenceValues> = state.asStateFlow()

    val current: AppPreferenceValues get() = state.value

    override suspend fun setSetupChecklistDismissed(dismissed: Boolean) {
        state.value = state.value.copy(setupChecklistDismissed = dismissed)
    }

    override suspend fun markSetupStepVisited(stepKey: String) {
        state.value = state.value.copy(
            setupChecklistVisitedSteps = state.value.setupChecklistVisitedSteps + stepKey,
        )
    }

    override suspend fun setSetupChecklistCelebrated(celebrated: Boolean) {
        state.value = state.value.copy(setupChecklistCelebrated = celebrated)
    }
}
