package com.elmtrackr.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant
import kotlin.math.roundToInt

enum class FeatureFlag { TRAVEL_REFUNDS, PAID_PROJECTS, INSIGHTS, CLOCK_STYLES }

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val syncRepository: SyncRepository,
    private val authRepository: AuthRepository,
    private val themeStore: ThemePreferenceStore,
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _isSyncing = MutableStateFlow(false)
    private val _validationErrors = MutableStateFlow<Map<String, String>>(emptyMap())

    private data class CoreData(
        val settings: UserSettings?,
        val pendingCount: Int,
        val lastSyncStatus: String?,
    )

    private data class Extras(
        val profile: Profile?,
        val theme: String,
        val isSaving: Boolean,
        val isSyncing: Boolean,
        val validationErrors: Map<String, String>,
    )

    private val coreData = authRepository.observeCurrentProfile().flatMapLatest { profile ->
        if (profile == null) flowOf(CoreData(null, 0, null))
        else combine(
            settingsRepository.observeSettings(profile.id),
            syncRepository.observePendingCount(profile.id),
            syncRepository.observeLastSyncStatus(),
        ) { settings, pending, lastSync -> CoreData(settings, pending, lastSync) }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        coreData,
        combine(
            authRepository.observeCurrentProfile(),
            themeStore.observeTheme(),
            _isSaving,
            _isSyncing,
            _validationErrors,
        ) { profile, theme, saving, syncing, errors -> Extras(profile, theme, saving, syncing, errors) },
    ) { core, extras ->
        if (core.settings == null) SettingsUiState.Loading
        else SettingsUiState.Ready(
            settings = core.settings,
            profile = extras.profile,
            selectedTheme = extras.theme,
            pendingCount = core.pendingCount,
            lastSyncStatus = core.lastSyncStatus,
            isSaving = extras.isSaving,
            isSyncing = extras.isSyncing,
            validationErrors = extras.validationErrors,
        )
    }.catch { e ->
        emit(SettingsUiState.Error(e.message ?: "Unknown error"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState.Loading,
    )

    fun saveSettings(
        displayName: String,
        dailyOtHours: Double,
        weeklyOtHours: Double,
        hourlyRate: Double?,
        timezone: String,
        clockStyle: ClockStyle,
        currency: CurrencyCode = CurrencyCode.ILS,
    ) {
        val errors = validate(dailyOtHours, weeklyOtHours, hourlyRate)
        if (errors.isNotEmpty()) { _validationErrors.value = errors; return }
        _validationErrors.value = emptyMap()
        viewModelScope.launch {
            _isSaving.value = true
            val currentProfile = authRepository.getCurrentProfile()
                ?: run { _isSaving.value = false; return@launch }
            val existing = settingsRepository.getSettings(currentProfile.id)
                ?: run { _isSaving.value = false; return@launch }
            settingsRepository.saveSettings(
                existing.copy(
                    dailyOvertimeThresholdMinutes = (dailyOtHours * 60).roundToInt(),
                    weeklyOvertimeThresholdMinutes = (weeklyOtHours * 60).roundToInt(),
                    hourlyRate = hourlyRate,
                    timezone = timezone.trim(),
                    clockStyle = clockStyle,
                    currency = currency,
                    updatedAt = Instant.now(),
                )
            )
            val existingProfile = currentProfile
            if (existingProfile.fullName != displayName.trim().ifBlank { null }) {
                val newName = displayName.trim().ifBlank { null }
                authRepository.saveProfile(
                    existingProfile.copy(fullName = newName, updatedAt = Instant.now()),
                    existingProfile.id,
                )
            }
            _isSaving.value = false
        }
    }

    fun updateFeatureFlag(feature: FeatureFlag, enabled: Boolean) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            val existing = settingsRepository.getSettings(userId) ?: return@launch
            val updated = when (feature) {
                FeatureFlag.TRAVEL_REFUNDS -> existing.copy(featuresTravelRefunds = enabled)
                FeatureFlag.PAID_PROJECTS -> existing.copy(featuresPaidProjects = enabled)
                FeatureFlag.INSIGHTS -> existing.copy(featuresInsights = enabled)
                FeatureFlag.CLOCK_STYLES -> existing.copy(featuresClockStyles = enabled)
            }
            settingsRepository.saveSettings(updated.copy(updatedAt = Instant.now()))
        }
    }

    fun updateWeekendDays(days: List<Int>) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            val existing = settingsRepository.getSettings(userId) ?: return@launch
            settingsRepository.saveSettings(existing.copy(weekendDays = days, updatedAt = Instant.now()))
        }
    }

    fun saveTheme(theme: String) {
        viewModelScope.launch { themeStore.saveTheme(theme) }
    }

    fun triggerSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            val userId = authRepository.getCurrentProfile()?.id
            if (userId != null) syncRepository.syncAll(userId)
            _isSyncing.value = false
        }
    }

    fun resetPassword() {
        viewModelScope.launch {
            val profile = authRepository.getCurrentProfile() ?: return@launch
            authRepository.resetPassword(profile.email)
        }
    }

    fun ensureSettingsExist() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            if (settingsRepository.getSettings(userId) == null) {
                settingsRepository.createDefaultSettings(userId)
            }
        }
    }

    internal fun validate(
        dailyOtHours: Double,
        weeklyOtHours: Double,
        hourlyRate: Double?,
    ): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (dailyOtHours <= 0.0) errors["dailyOt"] = "Must be positive"
        else if (dailyOtHours > 24.0) errors["dailyOt"] = "Cannot exceed 24 hours"
        if (weeklyOtHours <= 0.0) errors["weeklyOt"] = "Must be positive"
        else if (weeklyOtHours > 168.0) errors["weeklyOt"] = "Cannot exceed 168 hours"
        else if (dailyOtHours > 0 && weeklyOtHours < dailyOtHours) {
            errors["weeklyOt"] = "Must be ≥ daily threshold"
        }
        if (hourlyRate != null && hourlyRate < 0.0) errors["hourlyRate"] = "Must be zero or positive"
        return errors
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                @Suppress("UNCHECKED_CAST")
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ElmTrackrApp
                SettingsViewModel(
                    settingsRepository = app.settingsRepository,
                    syncRepository = app.syncRepository,
                    authRepository = app.authRepository,
                    themeStore = AppThemePreferenceStore(app.appPreferences),
                )
            }
        }
    }
}
