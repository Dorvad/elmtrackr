package com.elmtrackr.app.data.local.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AppLockPreferencesStore {
    val preferences: Flow<AppPreferenceValues>
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setReduceMotion(enabled: Boolean)
}
