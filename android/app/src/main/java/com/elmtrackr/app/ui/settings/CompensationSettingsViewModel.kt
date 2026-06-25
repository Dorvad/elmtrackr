package com.elmtrackr.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
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

@OptIn(ExperimentalCoroutinesApi::class)
class CompensationSettingsViewModel(
    private val compensationProfilesRepository: CompensationProfilesRepository,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _saveMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CompensationSettingsUiState> =
        authRepository.observeCurrentProfile().flatMapLatest { profile ->
            if (profile == null) return@flatMapLatest flowOf(CompensationSettingsUiState.Loading)
            combine(
                compensationProfilesRepository.observeProfiles(profile.id),
                settingsRepository.observeSettings(profile.id),
                _isSaving,
                _saveMessage,
            ) { profiles, settings, saving, message ->
                val defaultProfile = profiles.firstOrNull { it.isDefault } ?: profiles.firstOrNull()
                if (defaultProfile == null || settings == null) {
                    CompensationSettingsUiState.Loading
                } else {
                    CompensationSettingsUiState.Ready(
                        profile = defaultProfile,
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
                val existing = compensationProfilesRepository.getProfiles(userId)
                    .firstOrNull { it.isDefault } ?: error("No compensation profile found")
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
                val settings = settingsRepository.getSettings(userId) ?: error("Settings not found")
                settingsRepository.saveSettings(
                    settings.apply(CompensationResolver.profileToLegacySettingsUpdates(saved))
                        .copy(updatedAt = Instant.now()),
                )
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                @Suppress("UNCHECKED_CAST")
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ElmTrackrApp
                CompensationSettingsViewModel(
                    compensationProfilesRepository = app.compensationProfilesRepository,
                    settingsRepository = app.settingsRepository,
                    authRepository = app.authRepository,
                )
            }
        }
    }
}

sealed interface CompensationSettingsUiState {
    data object Loading : CompensationSettingsUiState
    data class Ready(
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
