package com.elmtrackr.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.data.local.preferences.AppLockPreferencesStore
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.model.AuthResult
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.data.sync.SyncHealth
import com.elmtrackr.app.data.sync.SyncRepository
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.security.AppLockController
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

enum class FeatureFlag { TRAVEL_REFUNDS, PAID_PROJECTS, INSIGHTS, CLOCK_STYLES, OVERTIME_REMINDERS }

data class SettingsFeatureFlags(
    val travelRefunds: Boolean,
    val paidProjects: Boolean,
    val insights: Boolean,
    val clockStyles: Boolean,
    val overtimeReminders: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val compensationProfilesRepository: CompensationProfilesRepository,
    private val themeStore: ThemePreferenceStore,
    private val syncRepository: SyncRepository,
    private val syncTrigger: SyncTrigger,
    private val appPreferences: AppLockPreferencesStore,
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _isSyncing = MutableStateFlow(false)
    private val _validationErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _passwordResetFeedback = MutableStateFlow<String?>(null)
    private val _saveFeedback = MutableStateFlow<SettingsSaveFeedback?>(null)
    private val _isDeletingAccount = MutableStateFlow(false)
    private val _accountActionFeedback = MutableStateFlow<String?>(null)

    private data class CoreData(
        val settings: UserSettings?,
    )

    private data class Extras(
        val profile: Profile?,
        val theme: String,
        val isSaving: Boolean,
        val isSyncing: Boolean,
        val syncHealth: SyncHealth?,
        val lastSyncStatus: String?,
        val validationErrors: Map<String, String>,
        val passwordResetFeedback: String?,
        val saveFeedback: SettingsSaveFeedback?,
        val isDeletingAccount: Boolean,
        val accountActionFeedback: String?,
        val appLockEnabled: Boolean,
        val reduceMotionEnabled: Boolean,
    )

    private val syncHealthFlow = authRepository.observeCurrentProfile().flatMapLatest { profile ->
        if (profile == null) flowOf(null)
        else syncRepository.observeSyncHealth(profile.id)
    }

    private val lastSyncFlow = syncRepository.observeLastSyncStatus()

    private val coreData = authRepository.observeCurrentProfile().flatMapLatest { profile ->
        if (profile == null) flowOf(CoreData(null))
        else settingsRepository.observeSettings(profile.id).flatMapLatest { settings ->
            flowOf(CoreData(settings))
        }
    }

    private val extras = combine(
        combine(
            combine(
                authRepository.observeCurrentProfile(),
                themeStore.observeTheme(),
                appPreferences.preferences,
            ) { profile, theme, prefs ->
                Triple(profile, theme, prefs)
            },
            combine(
                _isSaving,
                _isSyncing,
                syncHealthFlow,
            ) { saving, syncing, health ->
                Triple(saving, syncing, health)
            },
        ) { profileMeta, syncMeta ->
            SyncExtras(
                profile = profileMeta.first,
                theme = profileMeta.second,
                appLockEnabled = profileMeta.third.appLockEnabled,
                reduceMotionEnabled = profileMeta.third.reduceMotionEnabled,
                isSaving = syncMeta.first,
                isSyncing = syncMeta.second,
                syncHealth = syncMeta.third,
            )
        },
        combine(
            lastSyncFlow,
            combine(
                combine(
                    _validationErrors,
                    _passwordResetFeedback,
                ) { errors, resetFeedback ->
                    Pair(errors, resetFeedback)
                },
                combine(
                    _saveFeedback,
                    _isDeletingAccount,
                    _accountActionFeedback,
                ) { saveFeedback, deleting, accountFeedback ->
                    Triple(saveFeedback, deleting, accountFeedback)
                },
            ) { (errors, resetFeedback), (saveFeedback, deleting, accountFeedback) ->
                AccountExtras(errors, resetFeedback, saveFeedback, deleting, accountFeedback)
            },
        ) { lastSync, account -> lastSync to account },
    ) { syncExtras, lastAccount ->
        val (lastSync, account) = lastAccount
        Extras(
            syncExtras.profile,
            syncExtras.theme,
            syncExtras.isSaving,
            syncExtras.isSyncing,
            syncExtras.syncHealth,
            lastSync,
            account.errors,
            account.resetFeedback,
            account.saveFeedback,
            account.deleting,
            account.accountFeedback,
            syncExtras.appLockEnabled,
            syncExtras.reduceMotionEnabled,
        )
    }

    private data class SyncExtras(
        val profile: Profile?,
        val theme: String,
        val appLockEnabled: Boolean,
        val reduceMotionEnabled: Boolean,
        val isSaving: Boolean,
        val isSyncing: Boolean,
        val syncHealth: SyncHealth?,
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        coreData,
        extras,
    ) { core, extras ->
        if (core.settings == null) SettingsUiState.Loading
        else SettingsUiState.Ready(
            settings = core.settings,
            profile = extras.profile,
            selectedTheme = extras.theme,
            isSaving = extras.isSaving,
            syncPendingCount = extras.syncHealth?.pendingCount ?: 0,
            syncFailedCount = extras.syncHealth?.failedCount ?: 0,
            lastSyncStatus = extras.lastSyncStatus,
            isSyncing = extras.isSyncing,
            validationErrors = extras.validationErrors,
            passwordResetFeedback = extras.passwordResetFeedback,
            saveFeedback = extras.saveFeedback,
            isDeletingAccount = extras.isDeletingAccount,
            accountActionFeedback = extras.accountActionFeedback,
            appLockEnabled = extras.appLockEnabled,
            reduceMotionEnabled = extras.reduceMotionEnabled,
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
        weekendDays: List<Int>,
        featureFlags: SettingsFeatureFlags? = null,
    ) {
        val errors = validate(dailyOtHours, weeklyOtHours, hourlyRate)
        if (errors.isNotEmpty()) {
            _validationErrors.value = errors
            _saveFeedback.value = SettingsSaveFeedback(
                message = "Fix the highlighted fields before saving",
                isError = true,
            )
            return
        }
        _validationErrors.value = emptyMap()
        viewModelScope.launch {
            _isSaving.value = true
            val currentProfile = authRepository.getCurrentProfile()
                ?: run {
                    _isSaving.value = false
                    _saveFeedback.value = SettingsSaveFeedback("Could not save settings", isError = true)
                    return@launch
                }
            val existing = settingsRepository.getSettings(currentProfile.id)
                ?: run {
                    _isSaving.value = false
                    _saveFeedback.value = SettingsSaveFeedback("Could not save settings", isError = true)
                    return@launch
                }
            val flags = featureFlags ?: SettingsFeatureFlags(
                travelRefunds = existing.featuresTravelRefunds,
                paidProjects = existing.featuresPaidProjects,
                insights = existing.featuresInsights,
                clockStyles = existing.featuresClockStyles,
                overtimeReminders = existing.featuresOvertimeReminders,
            )
            val normalizedTimezone = IanaTimezones.normalize(timezone.trim())
            val savedSettings = existing.copy(
                dailyOvertimeThresholdMinutes = (dailyOtHours * 60).roundToInt(),
                weeklyOvertimeThresholdMinutes = (weeklyOtHours * 60).roundToInt(),
                hourlyRate = hourlyRate,
                timezone = normalizedTimezone,
                clockStyle = clockStyle,
                currency = currency,
                weekendDays = weekendDays,
                featuresTravelRefunds = flags.travelRefunds,
                featuresPaidProjects = flags.paidProjects,
                featuresInsights = flags.insights,
                featuresClockStyles = flags.clockStyles,
                featuresOvertimeReminders = flags.overtimeReminders,
                updatedAt = Instant.now(),
            )
            settingsRepository.saveSettings(savedSettings)
            compensationProfilesRepository.ensureMigrated(currentProfile.id)
            val profiles = compensationProfilesRepository.getProfiles(currentProfile.id)
            val defaultProfile = profiles.firstOrNull { it.isDefault } ?: profiles.firstOrNull()
            if (defaultProfile != null) {
                val updatedProfile = defaultProfile.copy(
                    baseHourlyRate = hourlyRate,
                    currencyCode = currency.name,
                    timezone = normalizedTimezone,
                    rules = defaultProfile.rules.copy(
                        dailyStandardMinutes = (dailyOtHours * 60).roundToInt(),
                        weeklyStandardMinutes = (weeklyOtHours * 60).roundToInt(),
                        weekendDays = weekendDays,
                    ),
                )
                val savedProfile = compensationProfilesRepository.upsertProfile(updatedProfile)
                settingsRepository.saveSettings(
                    savedSettings.apply(CompensationResolver.profileToLegacySettingsUpdates(savedProfile)),
                )
            }
            val existingProfile = currentProfile
            if (existingProfile.fullName != displayName.trim().ifBlank { null }) {
                val newName = displayName.trim().ifBlank { null }
                authRepository.saveProfile(
                    existingProfile.copy(fullName = newName, updatedAt = Instant.now()),
                    existingProfile.id,
                )
            }
            _isSaving.value = false
            _saveFeedback.value = SettingsSaveFeedback("Settings saved")
        }
    }

    fun clearSaveFeedback() {
        _saveFeedback.value = null
    }

    fun clearAccountActionFeedback() {
        _accountActionFeedback.value = null
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _isDeletingAccount.value = true
            when (val result = authRepository.deleteAccount()) {
                is AuthResult.Success ->
                    _accountActionFeedback.value = "Account deleted"
                is AuthResult.NotConfigured ->
                    _accountActionFeedback.value = "Local data cleared"
                is AuthResult.Error ->
                    _accountActionFeedback.value = result.message
            }
            _isDeletingAccount.value = false
        }
    }

    private data class AccountExtras(
        val errors: Map<String, String>,
        val resetFeedback: String?,
        val saveFeedback: SettingsSaveFeedback?,
        val deleting: Boolean,
        val accountFeedback: String?,
    )

    fun updateFeatureFlag(feature: FeatureFlag, enabled: Boolean) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            val existing = settingsRepository.getSettings(userId) ?: return@launch
            val updated = when (feature) {
                FeatureFlag.TRAVEL_REFUNDS -> existing.copy(featuresTravelRefunds = enabled)
                FeatureFlag.PAID_PROJECTS -> existing.copy(featuresPaidProjects = enabled)
                FeatureFlag.INSIGHTS -> existing.copy(featuresInsights = enabled)
                FeatureFlag.CLOCK_STYLES -> existing.copy(featuresClockStyles = enabled)
                FeatureFlag.OVERTIME_REMINDERS -> existing.copy(featuresOvertimeReminders = enabled)
            }
            settingsRepository.saveSettings(updated.copy(updatedAt = Instant.now()))
        }
    }

    fun updateWeekendDays(days: List<Int>) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            val existing = settingsRepository.getSettings(userId) ?: return@launch
            val savedSettings = existing.copy(weekendDays = days, updatedAt = Instant.now())
            settingsRepository.saveSettings(savedSettings)
            compensationProfilesRepository.ensureMigrated(userId)
            val profiles = compensationProfilesRepository.getProfiles(userId)
            val defaultProfile = profiles.firstOrNull { it.isDefault } ?: profiles.firstOrNull()
            if (defaultProfile != null) {
                val updatedProfile = defaultProfile.copy(
                    rules = defaultProfile.rules.copy(weekendDays = days),
                )
                val savedProfile = compensationProfilesRepository.upsertProfile(updatedProfile)
                settingsRepository.saveSettings(
                    savedSettings.apply(CompensationResolver.profileToLegacySettingsUpdates(savedProfile)),
                )
            }
        }
    }

    fun saveTheme(theme: String) {
        viewModelScope.launch { themeStore.saveTheme(theme) }
    }

    fun resetPassword() {
        viewModelScope.launch {
            val profile = authRepository.getCurrentProfile() ?: return@launch
            when (val result = authRepository.resetPassword(profile.email)) {
                is AuthResult.Success ->
                    _passwordResetFeedback.value = "Reset link sent to ${profile.email}. Check your inbox."
                is AuthResult.NotConfigured ->
                    _passwordResetFeedback.value = "Supabase is not configured."
                is AuthResult.Error ->
                    _passwordResetFeedback.value = result.message
            }
        }
    }

    fun clearPasswordResetFeedback() {
        _passwordResetFeedback.value = null
    }

    fun syncNow() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            _isSyncing.value = true
            runCatching { syncRepository.syncAll(userId) }
            _isSyncing.value = false
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setAppLockEnabled(enabled)
            AppLockController.configure(enabled, initiallyUnlocked = true)
            if (enabled) {
                AppLockController.unlock()
            }
        }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setReduceMotion(enabled)
        }
    }

    fun ensureSettingsExist() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            if (settingsRepository.getSettings(userId) == null) {
                settingsRepository.createDefaultSettings(userId)
            }
            compensationProfilesRepository.ensureMigrated(userId)
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
}
