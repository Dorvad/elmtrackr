package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ThemePreferenceStore {
    fun observeTheme(): Flow<String>
    suspend fun saveTheme(theme: String)
}

class AppThemePreferenceStore(
    private val appPreferences: AppPreferencesRepository,
) : ThemePreferenceStore {
    override fun observeTheme(): Flow<String> =
        appPreferences.preferences.map { it.selectedTheme }

    override suspend fun saveTheme(theme: String) =
        appPreferences.setSelectedTheme(theme)
}
