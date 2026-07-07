package com.elmtrackr.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CompensationSettingsViewModel @Inject constructor(
    private val compensationProfilesRepository: CompensationProfilesRepository,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _saveMessage = MutableStateFlow<String?>(null)
    private val _bootstrapComplete = MutableStateFlow(false)
    private val _selectedProfileId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CompensationSettingsUiState> =
        authRepository.observeCurrentProfile().flatMapLatest { profile ->
            if (profile == null) return@flatMapLatest flowOf(CompensationSettingsUiState.Loading)
            combine(
                compensationProfilesRepository.observeProfiles(profile.id),
                settingsRepository.observeSettings(profile.id),
                _isSaving,
                _saveMessage,
                _bootstrapComplete,
                _selectedProfileId,
            ) { values ->
                val profiles = values[0] as List<CompensationProfile>
                val settings = values[1] as com.elmtrackr.app.domain.model.UserSettings?
                val saving = values[2] as Boolean
                val message = values[3] as String?
                val bootstrapComplete = values[4] as Boolean
                val selectedId = values[5] as String?
                val selectedProfile = profiles.firstOrNull { it.id == selectedId }
                    ?: profiles.firstOrNull { it.isDefault }
                    ?: profiles.firstOrNull()
                when {
                    settings == null -> CompensationSettingsUiState.Loading
                    selectedProfile == null && !bootstrapComplete -> CompensationSettingsUiState.Loading
                    selectedProfile == null -> CompensationSettingsUiState.Error(
                        "No compensation profile found. Open Payroll settings or complete onboarding.",
                    )
                    else -> CompensationSettingsUiState.Ready(
                        profiles = profiles,
                        profile = selectedProfile,
                        settings = settings,
                        presets = RegionPresets.all,
                        currencyOptions = RegionPresets.currencyOptions,
                        timezoneOptions = RegionPresets.timezoneOptions,
                        isSaving = saving,
                        saveMessage = message,
                    )
                }
            }
        }.catch { e ->
            emit(CompensationSettingsUiState.Error(e.message ?: "Unknown error"))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CompensationSettingsUiState.Loading,
        )

    fun ensureLoaded() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            compensationProfilesRepository.ensureMigrated(userId)
            val profiles = compensationProfilesRepository.getProfiles(userId)
            if (_selectedProfileId.value == null) {
                _selectedProfileId.value = profiles.firstOrNull { it.isDefault }?.id
                    ?: profiles.firstOrNull()?.id
            }
            _bootstrapComplete.value = true
        }
    }

    fun selectProfile(profileId: String) {
        _selectedProfileId.value = profileId
        _saveMessage.value = null
    }

    fun createProfile(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            _isSaving.value = true
            _saveMessage.value = null
            try {
                val userId = authRepository.getCurrentProfile()?.id
                    ?: error("Sign in to create a compensation profile")
                val existing = compensationProfilesRepository.getProfiles(userId)
                val template = existing.firstOrNull { it.isDefault } ?: existing.firstOrNull()
                    ?: error("No compensation profile found")
                val now = Instant.now()
                val created = compensationProfilesRepository.upsertProfile(
                    template.copy(
                        id = "",
                        name = trimmed,
                        isDefault = false,
                        remoteId = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                _selectedProfileId.value = created.id
                _saveMessage.value = "Profile created"
            } catch (e: Exception) {
                _saveMessage.value = e.message ?: "Create failed"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveProfile(
        name: String,
        regionCode: RegionCode,
        currencyCode: String,
        timezone: String,
        hourlyRate: Double?,
        stackingPolicy: StackingPolicy,
        rules: CompensationRules,
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            _saveMessage.value = null
            try {
                val userId = authRepository.getCurrentProfile()?.id
                    ?: error("Sign in to save compensation rules")
                val profiles = compensationProfilesRepository.getProfiles(userId)
                val existing = profiles.firstOrNull { it.id == _selectedProfileId.value }
                    ?: profiles.firstOrNull { it.isDefault }
                    ?: error("No compensation profile found")
                val updated = existing.copy(
                    name = name.trim().ifBlank { "Main job" },
                    regionCode = regionCode,
                    currencyCode = currencyCode,
                    timezone = timezone,
                    baseHourlyRate = hourlyRate,
                    stackingPolicy = stackingPolicy,
                    rules = rules,
                    updatedAt = Instant.now(),
                )
                val saved = compensationProfilesRepository.upsertProfile(updated)
                if (saved.isDefault) {
                    val settings = settingsRepository.getSettings(userId) ?: error("Settings not found")
                    settingsRepository.saveSettings(
                        settings.apply(CompensationResolver.profileToLegacySettingsUpdates(saved))
                            .copy(updatedAt = Instant.now()),
                    )
                }
                _saveMessage.value = "Compensation rules saved"
            } catch (e: Exception) {
                _saveMessage.value = e.message ?: "Save failed"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearSaveMessage() {
        _saveMessage.value = null
    }
}

sealed interface CompensationSettingsUiState {
    data object Loading : CompensationSettingsUiState
    data class Ready(
        val profiles: List<CompensationProfile>,
        val profile: CompensationProfile,
        val settings: com.elmtrackr.app.domain.model.UserSettings,
        val presets: List<com.elmtrackr.app.domain.compensation.RegionPreset>,
        val currencyOptions: List<Pair<String, String>>,
        val timezoneOptions: List<String>,
        val isSaving: Boolean,
        val saveMessage: String?,
    ) : CompensationSettingsUiState
    data class Error(val message: String) : CompensationSettingsUiState
}
