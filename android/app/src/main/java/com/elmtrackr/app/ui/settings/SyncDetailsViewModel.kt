package com.elmtrackr.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.data.sync.SyncDetails
import com.elmtrackr.app.data.sync.SyncRepository
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

sealed interface SyncDetailsUiState {
    data object Loading : SyncDetailsUiState
    data class Ready(
        val details: SyncDetails,
        val isSyncing: Boolean,
        val isExporting: Boolean,
        val message: String? = null,
    ) : SyncDetailsUiState
    data class Error(val message: String) : SyncDetailsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class SyncDetailsViewModel(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val syncTrigger: SyncTrigger,
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    private val _isExporting = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SyncDetailsUiState> = authRepository.observeCurrentProfile()
        .flatMapLatest { profile ->
            if (profile == null) return@flatMapLatest flowOf(SyncDetailsUiState.Loading)
            combine(
                syncRepository.observeSyncDetails(profile.id),
                _isSyncing,
                _isExporting,
                _message,
            ) { details, syncing, exporting, message ->
                SyncDetailsUiState.Ready(
                    details = details,
                    isSyncing = syncing,
                    isExporting = exporting,
                    message = message,
                ) as SyncDetailsUiState
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncDetailsUiState.Loading)

    fun retryAll() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            _isSyncing.value = true
            _message.value = null
            val result = runCatching { syncRepository.syncAll(userId) }.getOrElse { error ->
                _message.value = error.message ?: "Sync failed"
                _isSyncing.value = false
                return@launch
            }
            if (result is com.elmtrackr.app.data.sync.SyncResult.Error) {
                _message.value = result.message
            } else {
                syncTrigger.schedule()
                _message.value = "Sync finished"
            }
            _isSyncing.value = false
        }
    }

    fun exportBackup(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            _isExporting.value = true
            _message.value = null
            runCatching { syncRepository.exportLocalBackup(userId) }
                .onSuccess { json ->
                    onReady(json)
                    _message.value = "Backup ready to share"
                }
                .onFailure { error ->
                    _message.value = error.message ?: "Could not export backup"
                }
            _isExporting.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                @Suppress("UNCHECKED_CAST")
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ElmTrackrApp
                SyncDetailsViewModel(app.authRepository, app.syncRepository, app.syncTrigger)
            }
        }
    }
}
