package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.UserSettings

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Ready(
        val settings: UserSettings,
        val profile: Profile? = null,
        val selectedTheme: String = "system",
        val pendingCount: Int = 0,
        val lastSyncStatus: String? = null,
        val isSaving: Boolean = false,
        val isSyncing: Boolean = false,
        val validationErrors: Map<String, String> = emptyMap(),
    ) : SettingsUiState

    data class Error(val message: String) : SettingsUiState
}
