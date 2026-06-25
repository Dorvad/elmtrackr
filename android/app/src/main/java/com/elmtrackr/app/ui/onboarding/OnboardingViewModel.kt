package com.elmtrackr.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    private val markOnboardingCompleted: suspend () -> Unit,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Welcome)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    val initialSettings = authRepository.observeCurrentProfile()
        .flatMapLatest { profile ->
            if (profile == null) flowOf(null) else settingsRepository.observeSettings(profile.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val initialProfile = authRepository.observeCurrentProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun completeOnboarding(input: OnboardingInput) {
        val errors = validate(input)
        if (errors.isNotEmpty()) {
            _uiState.value = OnboardingUiState.ValidationError(errors)
            return
        }
        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Saving
            try {
                val profile = authRepository.getCurrentProfile()
                    ?: error("Sign in before completing onboarding")
                val existing = settingsRepository.getSettings(profile.id)
                val base = existing ?: settingsRepository.createDefaultSettings(profile.id)
                val source = base.copy(
                    timezone = input.timezone,
                    dailyOvertimeThresholdMinutes = (input.dailyOvertimeHours * 60).roundToInt(),
                    weeklyOvertimeThresholdMinutes = (input.weeklyOvertimeHours * 60).roundToInt(),
                    weekendDays = input.weekendDays,
                    hourlyRate = input.hourlyRate,
                    currency = input.currency,
                )
                settingsRepository.saveSettings(
                    source.copy(
                        featuresTravelRefunds = input.featuresTravelRefunds,
                        featuresPaidProjects = input.featuresPaidProjects,
                        featuresInsights = input.featuresInsights,
                        featuresClockStyles = input.featuresClockStyles,
                        clockStyle = input.clockStyle,
                        onboardingCompleted = true,
                        onboardingCompletedAt = Instant.now(),
                        updatedAt = Instant.now(),
                    )
                )
                if (input.displayName.isNotBlank()) {
                    authRepository.saveProfile(
                        profile.copy(fullName = input.displayName),
                        profile.id,
                    )
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
        if (input.displayName.isBlank()) {
            errors["displayName"] = "Enter the name you want shown in the app"
        }
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
        if (input.weekendDays.isEmpty()) {
            errors["weekendDays"] = "Select at least one weekend day"
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
