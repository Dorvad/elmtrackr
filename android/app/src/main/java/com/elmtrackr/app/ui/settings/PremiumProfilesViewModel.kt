package com.elmtrackr.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.data.repository.PremiumProfilesRepository
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.PremiumType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PremiumProfilesUiState {
    data object Loading : PremiumProfilesUiState
    data class Error(val message: String) : PremiumProfilesUiState
    data class Ready(
        val profiles: List<PremiumProfile>,
        val editor: PremiumProfileEditorState?,
        val saveMessage: String? = null,
    ) : PremiumProfilesUiState
}

data class PremiumProfileEditorState(
    val profileId: String?,
    val name: String,
    val multiplierText: String,
    val premiumType: PremiumType,
    val isDefault: Boolean,
    val isSaving: Boolean = false,
)

@HiltViewModel
class PremiumProfilesViewModel @Inject constructor(
    private val premiumProfilesRepository: PremiumProfilesRepository,
    private val currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PremiumProfilesUiState>(PremiumProfilesUiState.Loading)
    val uiState: StateFlow<PremiumProfilesUiState> = _uiState.asStateFlow()

    fun ensureLoaded() {
        viewModelScope.launch {
            _uiState.value = PremiumProfilesUiState.Loading
            runCatching {
                val userId = currentUserProvider.currentUserId() ?: error("Not signed in")
                premiumProfilesRepository.ensureDefaults(userId)
                val profiles = premiumProfilesRepository.getProfiles(userId)
                _uiState.value = PremiumProfilesUiState.Ready(profiles = profiles, editor = null)
            }.onFailure { error ->
                _uiState.value = PremiumProfilesUiState.Error(
                    error.message ?: "Unable to load premium profiles",
                )
            }
        }
    }

    fun openCreate() {
        updateReady { it.copy(editor = blankEditor()) }
    }

    fun openEdit(profile: PremiumProfile) {
        updateReady {
            it.copy(
                editor = PremiumProfileEditorState(
                    profileId = profile.id,
                    name = profile.name,
                    multiplierText = profile.multiplier.toString(),
                    premiumType = profile.premiumType,
                    isDefault = profile.isDefault,
                ),
            )
        }
    }

    fun dismissEditor() {
        updateReady { it.copy(editor = null, saveMessage = null) }
    }

    fun updateName(value: String) = updateEditor { it.copy(name = value) }

    fun updateMultiplier(value: String) {
        if (value.count { it == '.' } <= 1 && value.all { it.isDigit() || it == '.' }) {
            updateEditor { it.copy(multiplierText = value) }
        }
    }

    fun updatePremiumType(type: PremiumType) = updateEditor { it.copy(premiumType = type) }

    fun saveEditor() {
        val ready = _uiState.value as? PremiumProfilesUiState.Ready ?: return
        val editor = ready.editor ?: return
        val multiplier = editor.multiplierText.toDoubleOrNull()
        if (editor.name.isBlank()) {
            updateReady { it.copy(saveMessage = "Enter a profile name") }
            return
        }
        if (multiplier == null || multiplier <= 0.0) {
            updateReady { it.copy(saveMessage = "Enter a valid multiplier") }
            return
        }

        viewModelScope.launch {
            updateReady { it.copy(editor = editor.copy(isSaving = true), saveMessage = null) }
            runCatching {
                val userId = currentUserProvider.currentUserId() ?: error("Not signed in")
                val now = Instant.now()
                val profile = PremiumProfile(
                    id = editor.profileId ?: UUID.randomUUID().toString(),
                    userId = userId,
                    name = editor.name.trim(),
                    multiplier = multiplier,
                    premiumType = editor.premiumType,
                    isDefault = editor.isDefault || editor.profileId == null,
                    createdAt = now,
                    updatedAt = now,
                )
                premiumProfilesRepository.upsertProfile(profile)
                val profiles = premiumProfilesRepository.getProfiles(userId)
                _uiState.value = PremiumProfilesUiState.Ready(
                    profiles = profiles,
                    editor = null,
                    saveMessage = "Premium profile saved",
                )
            }.onFailure { error ->
                updateReady {
                    it.copy(
                        editor = editor.copy(isSaving = false),
                        saveMessage = error.message ?: "Unable to save premium profile",
                    )
                }
            }
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            runCatching {
                val userId = currentUserProvider.currentUserId() ?: error("Not signed in")
                premiumProfilesRepository.deleteProfile(userId, profileId)
                val profiles = premiumProfilesRepository.getProfiles(userId)
                updateReady { it.copy(profiles = profiles, editor = null, saveMessage = "Profile removed") }
            }.onFailure { error ->
                updateReady { it.copy(saveMessage = error.message ?: "Unable to delete profile") }
            }
        }
    }

    fun clearSaveMessage() {
        updateReady { it.copy(saveMessage = null) }
    }

    private fun blankEditor() = PremiumProfileEditorState(
        profileId = null,
        name = "",
        multiplierText = "1.5",
        premiumType = PremiumType.HIGHEST_ONLY,
        isDefault = false,
    )

    private fun updateReady(transform: (PremiumProfilesUiState.Ready) -> PremiumProfilesUiState.Ready) {
        val current = _uiState.value
        if (current is PremiumProfilesUiState.Ready) {
            _uiState.value = transform(current)
        }
    }

    private fun updateEditor(transform: (PremiumProfileEditorState) -> PremiumProfileEditorState) {
        updateReady { ready ->
            val editor = ready.editor ?: return@updateReady ready
            ready.copy(editor = transform(editor))
        }
    }
}
