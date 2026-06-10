package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.domain.model.UserSettings

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Ready(
        val settings: UserSettings,
        val isSaving: Boolean = false,
    ) : SettingsUiState

    data class Error(val message: String) : SettingsUiState
}
