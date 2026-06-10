package com.elmtrackr.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.LOCAL_USER_ID
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        settingsRepository.observeSettings(LOCAL_USER_ID)
            .map { settings ->
                if (settings != null) SettingsUiState.Ready(settings)
                else SettingsUiState.Loading
            }
            .catch { e -> emit(SettingsUiState.Error(e.message ?: "Unknown error")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState.Loading,
            )

    fun saveSettings(settings: UserSettings) {
        viewModelScope.launch {
            settingsRepository.saveSettings(settings.copy(updatedAt = Instant.now()))
        }
    }

    /** Creates default settings if none exist yet. Safe to call on screen entry. */
    fun ensureSettingsExist() {
        viewModelScope.launch {
            if (settingsRepository.getSettings(LOCAL_USER_ID) == null) {
                settingsRepository.createDefaultSettings(LOCAL_USER_ID)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                @Suppress("UNCHECKED_CAST")
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ElmTrackrApp
                SettingsViewModel(app.settingsRepository)
            }
        }
    }
}
