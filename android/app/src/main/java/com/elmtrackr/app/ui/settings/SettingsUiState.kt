package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.model.UserSettings

data class SettingsSaveFeedback(
    val message: UiText,
    val isError: Boolean = false,
)

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Ready(
        val settings: UserSettings,
        val profile: Profile? = null,
        val selectedTheme: String = "system",
        val isSaving: Boolean = false,
        val syncPendingCount: Int = 0,
        val syncFailedCount: Int = 0,
        val lastSyncStatus: String? = null,
        val isSyncing: Boolean = false,
        val validationErrors: Map<String, UiText> = emptyMap(),
        val passwordResetFeedback: UiText? = null,
        val saveFeedback: SettingsSaveFeedback? = null,
        val isDeletingAccount: Boolean = false,
        val accountActionFeedback: UiText? = null,
        val appLockEnabled: Boolean = false,
        val reduceMotionEnabled: Boolean = false,
        val compensationProfileCount: Int = 0,
        val defaultCompensationProfileName: String? = null,
    ) : SettingsUiState

    data class Error(val message: String) : SettingsUiState
}
