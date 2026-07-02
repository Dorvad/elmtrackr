package com.elmtrackr.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.data.local.preferences.AppLockPreferencesStore
import com.elmtrackr.app.data.local.preferences.OnboardingPreferences
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.security.AppLockController
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
import java.util.UUID
import kotlin.math.roundToInt
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val compensationProfilesRepository: CompensationProfilesRepository,
    private val appPreferences: OnboardingPreferences,
    private val appLockPreferences: AppLockPreferencesStore,
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
                val presetProfile = CompensationResolver.createFromPreset(
                    userId = profile.id,
                    regionCode = input.regionCode,
                    currencyCode = input.currencyCode,
                    timezone = input.timezone,
                    baseHourlyRate = input.hourlyRate,
                )
                val mergedRules = presetProfile.rules.copy(
                    dailyStandardMinutes = (input.dailyOvertimeHours * 60).roundToInt(),
                    weeklyStandardMinutes = (input.weeklyOvertimeHours * 60).roundToInt(),
                    weekendDays = input.weekendDays,
                )
                val existingProfiles = compensationProfilesRepository.getProfiles(profile.id)
                val existingDefault = existingProfiles.firstOrNull { it.isDefault }
                    ?: existingProfiles.firstOrNull()
                val compensationProfile = if (input.preserveExisting && existingDefault != null) {
                    compensationProfilesRepository.upsertProfile(
                        existingDefault.copy(
                            regionCode = input.regionCode,
                            currencyCode = input.currencyCode,
                            timezone = input.timezone,
                            baseHourlyRate = input.hourlyRate,
                            rules = mergedRules,
                            isDefault = true,
                        ),
                    )
                } else {
                    compensationProfilesRepository.upsertProfile(
                        presetProfile.copy(
                            id = UUID.randomUUID().toString(),
                            rules = mergedRules,
                        ),
                    )
                }
                val source = base.copy(
                    timezone = input.timezone,
                    dailyOvertimeThresholdMinutes = (input.dailyOvertimeHours * 60).roundToInt(),
                    weeklyOvertimeThresholdMinutes = (input.weeklyOvertimeHours * 60).roundToInt(),
                    weekendDays = input.weekendDays,
                    hourlyRate = input.hourlyRate,
                    currency = input.currency,
                    regionCode = input.regionCode,
                    currencyCode = input.currencyCode,
                    defaultCompensationProfileId = compensationProfile.id,
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
                    ),
                )
                if (input.displayName.isNotBlank()) {
                    authRepository.saveProfile(
                        profile.copy(fullName = input.displayName),
                        profile.id,
                    )
                }
                if (input.enableAppLock) {
                    appLockPreferences.setAppLockEnabled(true)
                    AppLockController.configure(enabled = true, initiallyUnlocked = true)
                    AppLockController.unlock()
                }
                appPreferences.setOnboardingCompleted(true)
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
}
