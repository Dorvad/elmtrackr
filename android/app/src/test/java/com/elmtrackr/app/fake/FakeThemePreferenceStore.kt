package com.elmtrackr.app.fake

import com.elmtrackr.app.ui.settings.ThemePreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeThemePreferenceStore : ThemePreferenceStore {

    private val _theme = MutableStateFlow("system")
    var saveCount = 0

    fun setTheme(theme: String) { _theme.value = theme }

    override fun observeTheme(): Flow<String> = _theme

    override suspend fun saveTheme(theme: String) {
        _theme.value = theme
        saveCount++
    }
}
