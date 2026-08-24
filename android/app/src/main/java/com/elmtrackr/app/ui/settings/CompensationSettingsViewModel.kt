package com.elmtrackr.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.ui.common.UserFacingError
import com.elmtrackr.app.R
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.leave.LeavePresets
import com.elmtrackr.app.domain.model.LeavePolicy
import com.elmtrackr.app.domain.model.SickLeavePolicy
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.WorkplacesRepository
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
    private val workplacesRepository: WorkplacesRepository,
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _saveMessage = MutableStateFlow<CompensationSaveMessage?>(null)
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
                // The sick-pay ladder lives on the workplace's leave policy rather
                // than on the compensation rules, so the screen needs both to show
                // one coherent "what this job pays" form.
                workplacesRepository.observeWorkplaces(profile.id),
                workplacesRepository.observePolicies(profile.id),
            ) { values ->
                val profiles = values[0] as List<CompensationProfile>
                val settings = values[1] as com.elmtrackr.app.domain.model.UserSettings?
                val saving = values[2] as Boolean
                val message = values[3] as CompensationSaveMessage?
                val bootstrapComplete = values[4] as Boolean
                val selectedId = values[5] as String?

                @Suppress("UNCHECKED_CAST")
                val workplaces = values[6] as List<com.elmtrackr.app.domain.model.Workplace>

                @Suppress("UNCHECKED_CAST")
                val policies = values[7] as List<LeavePolicy>
                val selectedProfile = profiles.firstOrNull { it.id == selectedId }
                    ?: profiles.firstOrNull { it.isDefault }
                    ?: profiles.firstOrNull()
                when {
                    settings == null -> CompensationSettingsUiState.Loading
                    selectedProfile == null && !bootstrapComplete -> CompensationSettingsUiState.Loading
                    selectedProfile == null -> CompensationSettingsUiState.Error(
                        UiText.Res(R.string.settings_error_no_comp_profile),
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
                        sickLeave = sickLeaveFor(selectedProfile, workplaces, policies),
                    )
                }
            }
        }.catch { e ->
            emit(CompensationSettingsUiState.Error(UserFacingError.message(e, R.string.error_generic)))
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
                        // Cleared, then replaced below with a workplace of its own.
                        // Copying the template's would have made the new job share
                        // the old one's leave entitlement and balances.
                        workplaceId = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                attachWorkplace(userId, created, trimmed)
                _selectedProfileId.value = created.id
                _saveMessage.value = CompensationSaveMessage(UiText.Res(R.string.settings_feedback_profile_created), isError = false)
            } catch (e: Exception) {
                _saveMessage.value = CompensationSaveMessage(UiText.Res(R.string.settings_feedback_create_failed), isError = true)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteProfile() {
        viewModelScope.launch {
            _isSaving.value = true
            _saveMessage.value = null
            try {
                val userId = authRepository.getCurrentProfile()?.id
                    ?: error("Sign in to delete a compensation profile")
                val profiles = compensationProfilesRepository.getProfiles(userId)
                val target = profiles.firstOrNull { it.id == _selectedProfileId.value }
                    ?: error("No compensation profile selected")
                if (profiles.size <= 1) {
                    _saveMessage.value = CompensationSaveMessage(
                        UiText.Res(R.string.settings_feedback_delete_last_profile),
                        isError = true,
                    )
                    return@launch
                }
                compensationProfilesRepository.deleteProfile(userId, target.id)
                // Archived, never deleted: leave already reported against this job
                // stays readable, and its shifts keep pointing at a real row. A job
                // that ended is not a job that never happened.
                target.workplaceId?.let { workplacesRepository.archiveWorkplace(userId, it) }
                val remaining = profiles.filter { it.id != target.id }
                var nextSelected = remaining.firstOrNull { it.isDefault } ?: remaining.first()
                if (target.isDefault) {
                    // The default profile also backs the legacy settings fields, so
                    // promote the next profile and mirror it there, as saveProfile does.
                    val promoted = compensationProfilesRepository.upsertProfile(
                        nextSelected.copy(isDefault = true, updatedAt = Instant.now()),
                    )
                    nextSelected = promoted
                    settingsRepository.getSettings(userId)?.let { settings ->
                        settingsRepository.saveSettings(
                            settings.apply(CompensationResolver.profileToLegacySettingsUpdates(promoted))
                                .copy(updatedAt = Instant.now()),
                        )
                    }
                }
                _selectedProfileId.value = nextSelected.id
                _saveMessage.value = CompensationSaveMessage(UiText.Res(R.string.settings_feedback_profile_deleted), isError = false)
            } catch (e: Exception) {
                _saveMessage.value = CompensationSaveMessage(UiText.Res(R.string.settings_feedback_delete_failed), isError = true)
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * The sick-pay arrangement in force for [profile], or the region preset when
     * its workplace has no stored policy yet.
     *
     * Falling back to the preset rather than to an empty ladder is what keeps the
     * screen honest: a workplace with no policy row still *behaves* as the preset,
     * because that is what `ensurePolicy` creates the first time an absence is
     * priced against it. An empty form would misdescribe the current state.
     */
    private fun sickLeaveFor(
        profile: CompensationProfile?,
        workplaces: List<com.elmtrackr.app.domain.model.Workplace>,
        policies: List<LeavePolicy>,
    ): SickLeavePolicy {
        val region = profile?.regionCode ?: RegionCode.IL
        val preset = LeavePresets.forRegion(region).sick
        val workplaceId = profile?.workplaceId
            ?: workplaces.firstOrNull { it.isDefault }?.id
            ?: workplaces.firstOrNull()?.id
            ?: return preset
        return policies
            .filter { it.workplaceId == workplaceId }
            // The one in force is the active policy, and among several the one that
            // started most recently — the ordering `resolvePolicy` applies.
            .sortedByDescending { it.effectiveFrom }
            .firstOrNull { it.isActive }
            ?.rules
            ?.sick
            ?: preset
    }

    fun saveProfile(values: CompensationFormValues) {
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
                    name = values.name.trim().ifBlank { "Main job" },
                    regionCode = values.regionCode,
                    currencyCode = values.currencyCode,
                    timezone = values.timezone,
                    baseHourlyRate = values.hourlyRate,
                    stackingPolicy = values.stackingPolicy,
                    rules = values.rules,
                    color = values.color,
                    icon = values.icon,
                    updatedAt = Instant.now(),
                )
                val saved = compensationProfilesRepository.upsertProfile(updated)
                val withWorkplace = attachWorkplace(userId, saved, saved.name)
                saveSickLeave(userId, withWorkplace, values.sickLeave)
                if (saved.isDefault) {
                    val settings = settingsRepository.getSettings(userId) ?: error("Settings not found")
                    settingsRepository.saveSettings(
                        settings.apply(CompensationResolver.profileToLegacySettingsUpdates(saved))
                            .copy(updatedAt = Instant.now()),
                    )
                }
                _saveMessage.value = CompensationSaveMessage(UiText.Res(R.string.settings_feedback_rules_saved), isError = false)
            } catch (e: Exception) {
                _saveMessage.value = CompensationSaveMessage(UiText.Res(R.string.settings_feedback_save_failed), isError = true)
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Ensures [profile] owns a workplace, and keeps its name in step.
     *
     * One workplace per work profile is what makes leave per-job: the policy, the
     * entitlement and the payslip balances hang off the workplace, so two profiles
     * sharing one would share their sick and vacation arrangements too. The user
     * never sees the workplace — it is named after the profile and follows it.
     *
     * The very first profile is the exception: it adopts the workplace
     * `ensureDefaultWorkplace` already created, along with the shifts and profiles
     * that predate workplaces, rather than creating a second one beside it.
     */
    private suspend fun attachWorkplace(
        userId: String,
        profile: CompensationProfile,
        name: String,
    ): CompensationProfile {
        val existingId = profile.workplaceId
        if (existingId != null) {
            val workplace = workplacesRepository.getWorkplace(userId, existingId)
            if (workplace != null && workplace.name != name && name.isNotBlank()) {
                workplacesRepository.upsertWorkplace(workplace.copy(name = name, updatedAt = Instant.now()))
            }
            return profile
        }
        // No workplace yet. The default one is unclaimed only while this is the
        // user's first profile; otherwise this job gets one of its own.
        val default = workplacesRepository.ensureDefaultWorkplace(userId)
        val claimed = default?.takeIf { wp ->
            compensationProfilesRepository.getProfiles(userId).none {
                it.id != profile.id && it.workplaceId == wp.id
            }
        }
        val label = name.ifBlank { profile.name }
        val workplace = if (claimed != null) {
            // The claimed default was named by `ensureDefaultWorkplace` before this
            // profile existed. Adopting it without taking the profile's name left
            // the workplace called "Main job" while the job on screen said
            // something else.
            if (claimed.name == label) {
                claimed
            } else {
                workplacesRepository.upsertWorkplace(
                    claimed.copy(name = label, updatedAt = Instant.now()),
                )
            }
        } else {
            workplacesRepository.upsertWorkplace(
                com.elmtrackr.app.domain.model.Workplace(
                    id = "",
                    userId = userId,
                    name = label,
                    regionCode = profile.regionCode,
                    currencyCode = profile.currencyCode,
                    timezone = profile.timezone,
                    isDefault = false,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
        }
        return compensationProfilesRepository.upsertProfile(
            profile.copy(workplaceId = workplace.id, updatedAt = Instant.now()),
        )
    }

    /**
     * Writes the sick-pay arrangement onto the workplace's leave policy.
     *
     * A profile with no workplace of its own uses the default one — the same
     * workplace the leave repository already prices its absences against.
     * `updatePolicyRules` closes the outgoing policy rather than editing it, so an
     * absence reported last month keeps the explanation it was shown at the time;
     * that is also why this writes nothing when the arrangement has not changed.
     *
     * Only the sick block is replaced. Vacation, the standard day and the balance
     * unit carry over from whatever is in force.
     */
    private suspend fun saveSickLeave(
        userId: String,
        profile: CompensationProfile,
        sickLeave: SickLeavePolicy,
    ) {
        val workplaceId = profile.workplaceId
            ?: workplacesRepository.ensureDefaultWorkplace(userId)?.id
            ?: return
        val current = workplacesRepository.ensurePolicy(userId, workplaceId)
        if (current.rules.sick == sickLeave) return
        workplacesRepository.updatePolicyRules(
            userId = userId,
            workplaceId = workplaceId,
            rules = current.rules.copy(sick = sickLeave),
        )
    }

    fun clearSaveMessage() {
        _saveMessage.value = null
    }
}

/**
 * Everything the compensation form edits, saved as one unit.
 *
 * Gathered into a value rather than passed as a widening parameter list: the
 * sick-pay arrangement would have been an eighth positional argument, and eight
 * anonymous arguments is where a caller starts transposing two of them.
 */
data class CompensationFormValues(
    val name: String,
    val regionCode: RegionCode,
    val currencyCode: String,
    val timezone: String,
    val hourlyRate: Double?,
    val stackingPolicy: StackingPolicy,
    val rules: CompensationRules,
    val sickLeave: SickLeavePolicy,
    /** Visual identity, shown wherever the profile appears. */
    val color: String,
    val icon: String,
)

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
        val saveMessage: CompensationSaveMessage?,
        /**
         * The sick-pay arrangement in force for [profile]'s workplace, or the
         * region preset when that workplace has no policy row yet — which is what
         * it will be given the first time an absence is priced against it.
         */
        val sickLeave: SickLeavePolicy = SickLeavePolicy(),
    ) : CompensationSettingsUiState
    data class Error(val message: UiText) : CompensationSettingsUiState
}

data class CompensationSaveMessage(
    val text: UiText,
    val isError: Boolean,
)
