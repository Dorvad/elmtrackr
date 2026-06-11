package com.elmtrackr.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.LOCAL_USER_ID
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    private val markOnboardingCompleted: suspend () -> Unit,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Welcome)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun completeOnboarding(input: OnboardingInput) {
        val errors = validate(input)
        if (errors.isNotEmpty()) {
            _uiState.value = OnboardingUiState.ValidationError(errors)
            return
        }
        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Saving
            try {
                val base = settingsRepository.createDefaultSettings(LOCAL_USER_ID)
                settingsRepository.saveSettings(
                    base.copy(
                        timezone = input.timezone,
                        dailyOvertimeThresholdMinutes = input.dailyOvertimeHours * 60,
                        weeklyOvertimeThresholdMinutes = input.weeklyOvertimeHours * 60,
                        weekendDays = input.weekendDays,
                        hourlyRate = input.hourlyRate,
                        featuresTravelRefunds = input.featuresTravelRefunds,
                        featuresPaidProjects = input.featuresPaidProjects,
                        featuresInsights = input.featuresInsights,
                        featuresClockStyles = input.featuresClockStyles,
                        onboardingCompleted = true,
                        onboardingCompletedAt = Instant.now(),
                        updatedAt = Instant.now(),
                    )
                )
                if (input.displayName.isNotBlank()) {
                    val profile = authRepository.getCurrentProfile()
                    if (profile != null) {
                        authRepository.saveProfile(
                            profile.copy(fullName = input.displayName),
                            profile.id,
                        )
                    }
                }
                markOnboardingCompleted()
                _uiState.value = OnboardingUiState.Completed
            } catch (e: Exception) {
                _uiState.value = OnboardingUiState.ValidationError(
                    mapOf("save" to (e.message ?: "Save failed. Please try again.")),
                )
            }
        }
    }

    private fun validate(input: OnboardingInput): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (input.dailyOvertimeHours <= 0) {
            errors["dailyOT"] = "Daily overtime threshold must be at least 1 hour"
        }
        if (input.weeklyOvertimeHours <= 0) {
            errors["weeklyOT"] = "Weekly overtime threshold must be at least 1 hour"
        } else if (input.weeklyOvertimeHours < input.dailyOvertimeHours) {
            errors["weeklyOT"] = "Weekly threshold must be ≥ daily threshold"
        }
        if (input.hourlyRate != null && input.hourlyRate <= 0) {
            errors["hourlyRate"] = "Hourly rate must be a positive number"
        }
        return errors
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                @Suppress("UNCHECKED_CAST")
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ElmTrackrApp
                OnboardingViewModel(
                    settingsRepository = app.settingsRepository,
                    markOnboardingCompleted = { app.appPreferences.setOnboardingCompleted(true) },
                    authRepository = app.authRepository,
                )
            }
        }
    }
}
