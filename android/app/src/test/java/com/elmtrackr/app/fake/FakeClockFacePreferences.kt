package com.elmtrackr.app.fake

import com.elmtrackr.app.data.local.preferences.AppPreferenceValues
import com.elmtrackr.app.data.local.preferences.ClockFacePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeClockFacePreferences(
    initial: List<String> = emptyList(),
    initialPacks: Set<String> = emptySet(),
) : ClockFacePreferences {

    private val state = MutableStateFlow(
        AppPreferenceValues(recentClockFaces = initial, installedClockFacePacks = initialPacks),
    )

    override val preferences: Flow<AppPreferenceValues> = state.asStateFlow()

    val recentClockFaces: List<String> get() = state.value.recentClockFaces

    val installedClockFacePacks: Set<String> get() = state.value.installedClockFacePacks

    override suspend fun setRecentClockFaces(styleNames: List<String>) {
        state.value = state.value.copy(recentClockFaces = styleNames)
    }

    override suspend fun setInstalledClockFacePacks(packNames: Set<String>) {
        state.value = state.value.copy(installedClockFacePacks = packNames)
    }
}
