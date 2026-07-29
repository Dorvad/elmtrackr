package com.elmtrackr.app.fake

import com.elmtrackr.app.data.local.preferences.AppPreferenceValues
import com.elmtrackr.app.data.local.preferences.FeatureDiscoveryPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeFeatureDiscoveryPreferences(
    initial: AppPreferenceValues = AppPreferenceValues(),
) : FeatureDiscoveryPreferences {

    private val state = MutableStateFlow(initial)

    override val preferences: Flow<AppPreferenceValues> = state.asStateFlow()

    val paidProjectsDiscoveryDismissed: Boolean
        get() = state.value.paidProjectsDiscoveryDismissed

    override suspend fun setPaidProjectsDiscoveryDismissed(dismissed: Boolean) {
        state.value = state.value.copy(paidProjectsDiscoveryDismissed = dismissed)
    }
}
