package com.elmtrackr.app.fake

import com.elmtrackr.app.data.local.preferences.AppLockPreferencesStore
import com.elmtrackr.app.data.local.preferences.AppPreferenceValues
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAppLockPreferencesStore(
    initial: AppPreferenceValues = AppPreferenceValues(),
) : AppLockPreferencesStore {

    private val state = MutableStateFlow(initial)

    override val preferences: Flow<AppPreferenceValues> = state.asStateFlow()

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        state.value = state.value.copy(appLockEnabled = enabled)
    }
}
