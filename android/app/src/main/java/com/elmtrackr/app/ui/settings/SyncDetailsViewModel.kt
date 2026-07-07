package com.elmtrackr.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.R
import com.elmtrackr.app.data.sync.BackupImportException
import com.elmtrackr.app.data.sync.SyncDetails
import com.elmtrackr.app.data.sync.SyncRepository
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

sealed interface SyncDetailsUiState {
    data object Loading : SyncDetailsUiState
    data class Ready(
        val details: SyncDetails,
        val isSyncing: Boolean,
        val isExporting: Boolean,
        val isImporting: Boolean = false,
        val message: UiText? = null,
    ) : SyncDetailsUiState
    data class Error(val message: String) : SyncDetailsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SyncDetailsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val syncTrigger: SyncTrigger,
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    private val _isExporting = MutableStateFlow(false)
    private val _isImporting = MutableStateFlow(false)
    private val _message = MutableStateFlow<UiText?>(null)

    val uiState: StateFlow<SyncDetailsUiState> = authRepository.observeCurrentProfile()
        .flatMapLatest { profile ->
            if (profile == null) return@flatMapLatest flowOf(SyncDetailsUiState.Loading)
            combine(
                syncRepository.observeSyncDetails(profile.id),
                _isSyncing,
                _isExporting,
                _isImporting,
                _message,
            ) { details, syncing, exporting, importing, message ->
                SyncDetailsUiState.Ready(
                    details = details,
                    isSyncing = syncing,
                    isExporting = exporting,
                    isImporting = importing,
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
                _message.value = error.message?.let { UiText.Raw(it) } ?: UiText.Res(R.string.sync_failed)
                _isSyncing.value = false
                return@launch
            }
            when (result) {
                is com.elmtrackr.app.data.sync.SyncResult.Error ->
                    _message.value = UiText.Raw(result.message)
                com.elmtrackr.app.data.sync.SyncResult.AuthExpired ->
                    _message.value = UiText.Res(R.string.sync_session_expired)
                else -> {
                    syncTrigger.schedule()
                    _message.value = UiText.Res(R.string.sync_finished)
                }
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
                    _message.value = UiText.Res(R.string.sync_backup_ready)
                }
                .onFailure { error ->
                    _message.value = error.message?.let { UiText.Raw(it) } ?: UiText.Res(R.string.sync_export_failed)
                }
            _isExporting.value = false
        }
    }

    /**
     * [readJson] runs on a background dispatcher and should return the backup
     * file's contents, or null when it can't be read.
     */
    fun importBackup(readJson: suspend () -> String?) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            _isImporting.value = true
            _message.value = null
            runCatching {
                val json = withContext(Dispatchers.IO) { readJson() }
                    ?: throw BackupImportException("Could not read the selected file.")
                syncRepository.importLocalBackup(userId, json)
            }
                .onSuccess { summary ->
                    _message.value = when {
                        summary.importedTotal == 0 && summary.skipped > 0 ->
                            UiText.Res(R.string.sync_import_nothing_new)
                        summary.importedTotal == 0 -> UiText.Res(R.string.sync_import_empty)
                        summary.skipped > 0 ->
                            UiText.Res(R.string.sync_import_count_skipped, summary.importedTotal, summary.skipped)
                        else -> UiText.Res(R.string.sync_import_count, summary.importedTotal)
                    }
                    if (summary.importedTotal > 0) syncTrigger.schedule()
                }
                .onFailure { error ->
                    _message.value = error.message?.let { UiText.Raw(it) } ?: UiText.Res(R.string.sync_import_failed)
                }
            _isImporting.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
