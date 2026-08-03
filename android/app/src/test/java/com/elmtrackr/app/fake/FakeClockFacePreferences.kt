package com.elmtrackr.app.fake

import com.elmtrackr.app.data.local.preferences.AppPreferenceValues
import com.elmtrackr.app.data.local.preferences.ClockFacePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeClockFacePreferences(
    initial: List<String> = emptyList(),
) : ClockFacePreferences {

    private val state = MutableStateFlow(AppPreferenceValues(recentClockFaces = initial))

    override val preferences: Flow<AppPreferenceValues> = state.asStateFlow()

    val recentClockFaces: List<String> get() = state.value.recentClockFaces

    override suspend fun setRecentClockFaces(styleNames: List<String>) {
        state.value = state.value.copy(recentClockFaces = styleNames)
    }
}
