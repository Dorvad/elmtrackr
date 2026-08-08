package com.elmtrackr.wear.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.wear.tiles.TileService
import com.elmtrackr.wear.sync.WearPaths.PAYLOAD_KEY
import com.elmtrackr.wear.sync.WearPaths.SHIFT_STATE
import com.elmtrackr.wear.tile.ElmTrackrTileService
import com.elmtrackr.wear.tile.WearTileRefreshWorker
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

private val Context.wearStateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wear_shift_state",
)

/** Transient full-screen punch feedback: a success checkmark or a failure cross. */
data class WearConfirmation(
    val message: String,
    val isSuccess: Boolean = true,
)

class WearStateRepository(
    private val context: Context,
) {
    // Repository is application-lifetime; used for applying data-layer events
    // after their buffer has been released.
    private val applyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cacheKey = stringPreferencesKey("snapshot_json")

    private val _snapshot = MutableStateFlow(WearShiftSnapshot.signedOut())
    val snapshot: StateFlow<WearShiftSnapshot> = _snapshot.asStateFlow()

    private val _confirmation = MutableStateFlow<WearConfirmation?>(null)
    val confirmation: StateFlow<WearConfirmation?> = _confirmation.asStateFlow()

    private val _isPunchInProgress = MutableStateFlow(false)
    val isPunchInProgress: StateFlow<Boolean> = _isPunchInProgress.asStateFlow()

    suspend fun bootstrap() {
        loadCached()
        refreshFromDataLayer()
    }

    suspend fun loadCached() {
        val json = context.wearStateDataStore.data.first()[cacheKey] ?: return
        WearSnapshotCodec.decode(json)?.let { applySnapshot(it, persist = false) }
    }

    suspend fun refreshFromDataLayer() {
        runCatching {
            val dataClient = Wearable.getDataClient(context)
            val uri = android.net.Uri.parse("wear://*$SHIFT_STATE")
            val items = dataClient.getDataItems(uri).await()
            // Multiple nodes can each hold a data item; applying them in
            // iteration order lets a stale phone snapshot overwrite a fresh
            // one. Apply only the newest.
            val newest = items.mapNotNull { parseDataItem(it) }
                .maxByOrNull { it.updatedAtEpochMillis }
            items.release()
            newest?.let { applySnapshot(it) }
        }
    }

    /**
     * Parses the callback-scoped buffer synchronously (it is invalid once the
     * listener returns) and applies the snapshots asynchronously — blocking
     * the data-layer dispatch thread with runBlocking would stall delivery of
     * subsequent events during a burst of phone-side updates.
     */
    fun handleDataEvents(events: DataEventBuffer) {
        val snapshots = events.mapNotNull { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@mapNotNull null
            val item = event.dataItem
            if (!item.uri.path.orEmpty().startsWith(SHIFT_STATE)) return@mapNotNull null
            parseDataItem(item)
        }
        if (snapshots.isEmpty()) return
        applyScope.launch {
            snapshots.forEach { applySnapshot(it) }
        }
    }

    suspend fun applySnapshot(snapshot: WearShiftSnapshot, persist: Boolean = true) {
        _snapshot.value = snapshot
        if (persist) {
            context.wearStateDataStore.edit { prefs ->
                prefs[cacheKey] = WearSnapshotCodec.encode(snapshot)
            }
        }
        if (snapshot.isActive) {
            WearTileRefreshWorker.schedule(context)
        } else {
            WearTileRefreshWorker.cancel(context)
        }
        ElmTrackrComplicationBridge.requestUpdateAll(context)
        // Re-render the tile immediately on every state change. The refresh
        // worker only drives the once-a-minute count-up while a shift runs;
        // without this, a punch made FROM the tile keeps showing the old
        // face for up to a minute, and a sign-in/out for up to an hour.
        runCatching {
            TileService.getUpdater(context).requestUpdate(ElmTrackrTileService::class.java)
        }
    }

    private var confirmationDismissJob: Job? = null

    fun showConfirmation(message: String, isSuccess: Boolean = true) {
        _confirmation.value = WearConfirmation(message, isSuccess)
        // The auto-dismiss runs on the repository's own application-lifetime
        // scope, not the caller's: a ViewModel scope dies when the user drops
        // their wrist, and a cancelled dismiss used to leave the overlay stuck
        // on the next launch. 1.6s is long enough for the result-mark draw-in.
        confirmationDismissJob?.cancel()
        confirmationDismissJob = applyScope.launch {
            delay(1_600)
            _confirmation.value = null
        }
    }

    fun dismissConfirmation() {
        confirmationDismissJob?.cancel()
        _confirmation.value = null
    }

    fun setPunchInProgress(inProgress: Boolean) {
        _isPunchInProgress.value = inProgress
    }

    private fun parseDataItem(item: com.google.android.gms.wearable.DataItem): WearShiftSnapshot? {
        val payload = DataMapItem.fromDataItem(item).dataMap.getString(PAYLOAD_KEY) ?: return null
        return WearSnapshotCodec.decode(payload)
    }
}
