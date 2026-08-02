package com.elmtrackr.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.data.local.preferences.WearSyncPreferences
import com.elmtrackr.app.wear.WearConnectionStatus
import com.elmtrackr.app.wear.WearSyncPublisher
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class WearSettingsUiState(
    /** Null while the first node query is still in flight. */
    val status: WearConnectionStatus? = null,
    val refreshing: Boolean = false,
    val syncing: Boolean = false,
    val lastSyncSentAtMs: Long? = null,
)

/**
 * Backs Settings → Wear OS: reports which watches are reachable and whether the
 * ElmTrackr watch app is installed on them, and drives the sync toggle and the
 * manual "sync now" push.
 *
 * Every Wearable call is wrapped: on phones without Play Services (or with the
 * Wearable module missing) the queries fail and the screen degrades to a clear
 * "unavailable" state instead of crashing.
 */
@HiltViewModel
class WearSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wearSyncPreferences: WearSyncPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WearSettingsUiState())
    val uiState: StateFlow<WearSettingsUiState> = _uiState.asStateFlow()

    /** Null until the preference loads — the toggle renders disabled meanwhile. */
    val syncEnabled: StateFlow<Boolean?> = wearSyncPreferences.preferences
        .map { it.wearSyncEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        if (_uiState.value.refreshing) return
        _uiState.value = _uiState.value.copy(refreshing = true)
        viewModelScope.launch {
            val status = queryStatus()
            _uiState.value = _uiState.value.copy(status = status, refreshing = false)
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            wearSyncPreferences.setWearSyncEnabled(enabled)
            if (enabled) {
                // Bring the watch straight up to date rather than waiting for
                // the next punch.
                WearSyncPublisher.refresh(context)
                _uiState.value = _uiState.value.copy(lastSyncSentAtMs = System.currentTimeMillis())
            } else {
                // Blank the watch so it does not keep showing a frozen shift.
                WearSyncPublisher.clearWatchData(context)
            }
        }
    }

    fun syncNow() {
        if (_uiState.value.syncing) return
        _uiState.value = _uiState.value.copy(syncing = true)
        viewModelScope.launch {
            WearSyncPublisher.refresh(context)
            _uiState.value = _uiState.value.copy(
                syncing = false,
                lastSyncSentAtMs = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun queryStatus(): WearConnectionStatus {
        val nodes = runCatching {
            Wearable.getNodeClient(context).connectedNodes.await()
        }.getOrElse { return WearConnectionStatus.unavailable() }
        val capabilityNodeIds = runCatching {
            Wearable.getCapabilityClient(context)
                .getCapability(WearSyncPublisher.WATCH_APP_CAPABILITY, CapabilityClient.FILTER_ALL)
                .await()
                .nodes
                .map { it.id }
                .toSet()
        }.getOrDefault(emptySet())
        return WearConnectionStatus.from(
            connectedNodes = nodes.map { Triple(it.id, it.displayName, it.isNearby) },
            capabilityNodeIds = capabilityNodeIds,
        )
    }
}
