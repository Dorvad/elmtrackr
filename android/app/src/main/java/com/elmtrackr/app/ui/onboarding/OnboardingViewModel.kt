package com.elmtrackr.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.domain.LOCAL_USER_ID
import com.elmtrackr.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    private val appPreferences: AppPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Welcome)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun completeOnboarding(hourlyRate: Double?, weekendDays: List<Int>) {
        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Saving
            val base = settingsRepository.createDefaultSettings(LOCAL_USER_ID)
            settingsRepository.saveSettings(
                base.copy(
                    hourlyRate = hourlyRate,
                    weekendDays = weekendDays,
                    onboardingCompleted = true,
                    onboardingCompletedAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
            )
            appPreferences.setOnboardingCompleted(true)
            _uiState.value = OnboardingUiState.Completed
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                @Suppress("UNCHECKED_CAST")
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ElmTrackrApp
                OnboardingViewModel(app.settingsRepository, app.appPreferences)
            }
        }
    }
}
