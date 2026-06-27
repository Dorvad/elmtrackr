package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.UserSettings

data class SettingsSaveFeedback(
    val message: String,
    val isError: Boolean = false,
)

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Ready(
        val settings: UserSettings,
        val profile: Profile? = null,
        val selectedTheme: String = "system",
        val pendingCount: Int = 0,
        val lastSyncStatus: String? = null,
        val isRemoteConfigured: Boolean = true,
        val isOnline: Boolean = true,
        val syncError: String? = null,
        val isSaving: Boolean = false,
        val isSyncing: Boolean = false,
        val validationErrors: Map<String, String> = emptyMap(),
        val passwordResetFeedback: String? = null,
        val saveFeedback: SettingsSaveFeedback? = null,
        val isDeletingAccount: Boolean = false,
        val accountActionFeedback: String? = null,
    ) : SettingsUiState

    data class Error(val message: String) : SettingsUiState
}
