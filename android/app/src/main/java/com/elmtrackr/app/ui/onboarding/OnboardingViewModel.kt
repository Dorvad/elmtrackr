package com.elmtrackr.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.R
import com.elmtrackr.app.data.local.preferences.AppLockPreferencesStore
import com.elmtrackr.app.data.local.preferences.FeatureDiscoveryPreferences
import com.elmtrackr.app.data.local.preferences.OnboardingPreferences
import com.elmtrackr.app.data.local.preferences.SetupChecklistPreferences
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.setup.SetupStep
import com.elmtrackr.app.security.AppLockController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    private val featureDiscoveryPreferences: FeatureDiscoveryPreferences,
    private val setupChecklistPreferences: SetupChecklistPreferences,
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

    /**
     * Current app-lock state, so a replay seeds the Security toggle with the
     * truth instead of always-off. Null until the preference has loaded.
     */
    val initialAppLockEnabled: StateFlow<Boolean?> = appLockPreferences.preferences
        .map { it.appLockEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun completeOnboarding(input: OnboardingInput) {
        val errors = validate(input)
        if (errors.isNotEmpty()) {
            _uiState.value = OnboardingUiState.ValidationError(errors)
            return
        }
        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Saving
            val profile = authRepository.getCurrentProfile()
            if (profile == null) {
                _uiState.value = OnboardingUiState.ValidationError(
                    mapOf("save" to UiText.Res(R.string.onboarding_sign_in_first)),
                )
                return@launch
            }
            try {
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
                        // The wizard now has an explicit Paid Projects step and
                        // seeds it from stored settings on replay, so the answer
                        // is always the user's own — guarding it behind
                        // preserveExisting would make the step a no-op on replay.
                        featuresPaidProjects = input.featuresPaidProjects,
                        featuresInsights = input.featuresInsights,
                        // Still no wizard steps for these two, so a replay must
                        // not silently reset the user's existing choices (e.g. a
                        // custom clock face) to the wizard defaults.
                        featuresClockStyles = if (input.preserveExisting) base.featuresClockStyles else input.featuresClockStyles,
                        clockStyle = if (input.preserveExisting) base.clockStyle else input.clockStyle,
                        // Only written while the module is on, so declining
                        // Paid Projects cannot wipe defaults saved earlier.
                        projectsDefaultRegionCode = if (input.featuresPaidProjects) {
                            input.projectsDefaultRegionCode
                        } else {
                            base.projectsDefaultRegionCode
                        },
                        projectsDefaultCurrencyCode = if (input.featuresPaidProjects) {
                            input.projectsDefaultCurrencyCode
                        } else {
                            base.projectsDefaultCurrencyCode
                        },
                        projectsTaxLabel = if (input.featuresPaidProjects) {
                            input.projectsTaxLabel
                        } else {
                            base.projectsTaxLabel
                        },
                        projectsTaxRateBasisPoints = if (input.featuresPaidProjects) {
                            input.projectsTaxRateBasisPoints
                        } else {
                            base.projectsTaxRateBasisPoints
                        },
                        projectsTaxInclusive = if (input.featuresPaidProjects) {
                            input.projectsTaxInclusive
                        } else {
                            base.projectsTaxInclusive
                        },
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
                // Applied in both directions so a replay can turn the lock off,
                // not just on; skipped when unchanged so a plain first run never
                // touches the lock preference.
                val appLockCurrently = appLockPreferences.preferences.first().appLockEnabled
                if (input.enableAppLock != appLockCurrently) {
                    appLockPreferences.setAppLockEnabled(input.enableAppLock)
                    AppLockController.configure(
                        enabled = input.enableAppLock,
                        initiallyUnlocked = true,
                    )
                    if (input.enableAppLock) AppLockController.unlock()
                }
                // The wizard's Paid Projects step just asked the question, so the
                // dashboard discovery modal must not ask it again — whatever the
                // answer was.
                featureDiscoveryPreferences.setPaidProjectsDiscoveryDismissed(true)
                // An hourly rate entered here configures the default compensation
                // profile; the setup checklist should not ask for it a second time.
                if (input.hourlyRate != null) {
                    setupChecklistPreferences.markSetupStepVisited(SetupStep.COMPENSATION.key)
                }
                appPreferences.setOnboardingCompleted(true)
                _uiState.value = OnboardingUiState.Completed
            } catch (e: Exception) {
                _uiState.value = OnboardingUiState.ValidationError(
                    mapOf("save" to UiText.Res(R.string.onboarding_save_failed)),
                )
            }
        }
    }

    private fun validate(input: OnboardingInput): Map<String, UiText> {
        val errors = mutableMapOf<String, UiText>()
        // A blank display name is accepted. It feeds the dashboard greeting and
        // nothing else, so refusing to finish setup without one made a cosmetic
        // field a gate on using the app. The setup checklist asks for it later.
        // Same bounds the screen enforces (daily ≤ 24 h, weekly ≤ 168 h) so the
        // two layers cannot drift apart.
        if (input.dailyOvertimeHours <= 0 || input.dailyOvertimeHours > 24) {
            errors["dailyOT"] = UiText.Res(R.string.onboarding_ot_error)
        }
        if (input.weeklyOvertimeHours <= 0 || input.weeklyOvertimeHours > 168) {
            errors["weeklyOT"] = UiText.Res(R.string.onboarding_ot_error)
        } else if (input.weeklyOvertimeHours < input.dailyOvertimeHours) {
            errors["weeklyOT"] = UiText.Res(R.string.onboarding_ot_error)
        }
        if (input.hourlyRate != null && input.hourlyRate <= 0) {
            errors["hourlyRate"] = UiText.Res(R.string.onboarding_pay_rate_error)
        }
        if (input.weekendDays.isEmpty()) {
            errors["weekendDays"] = UiText.Res(R.string.onboarding_weekend_days_error)
        }
        return errors
    }
}
