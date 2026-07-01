package com.elmtrackr.wear.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.elmtrackr.wear.sync.WearPaths.PAYLOAD_KEY
import com.elmtrackr.wear.sync.WearPaths.SHIFT_STATE
import com.elmtrackr.wear.tile.WearTileRefreshWorker
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

private val Context.wearStateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wear_shift_state",
)

class WearStateRepository(
    private val context: Context,
) {
    private val cacheKey = stringPreferencesKey("snapshot_json")

    private val _snapshot = MutableStateFlow(WearShiftSnapshot.signedOut())
    val snapshot: StateFlow<WearShiftSnapshot> = _snapshot.asStateFlow()

    private val _confirmationMessage = MutableStateFlow<String?>(null)
    val confirmationMessage: StateFlow<String?> = _confirmationMessage.asStateFlow()

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
            for (item in items) {
                parseDataItem(item)?.let { applySnapshot(it) }
            }
            items.release()
        }
    }

    fun handleDataEvents(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            if (!item.uri.path.orEmpty().startsWith(SHIFT_STATE)) continue
            parseDataItem(item)?.let { snapshot ->
                kotlinx.coroutines.runBlocking {
                    applySnapshot(snapshot)
                }
            }
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
    }

    suspend fun showConfirmation(message: String) {
        _confirmationMessage.value = message
        delay(1_000)
        _confirmationMessage.value = null
    }

    fun dismissConfirmation() {
        _confirmationMessage.value = null
    }

    fun setPunchInProgress(inProgress: Boolean) {
        _isPunchInProgress.value = inProgress
    }

    private fun parseDataItem(item: com.google.android.gms.wearable.DataItem): WearShiftSnapshot? {
        val payload = DataMapItem.fromDataItem(item).dataMap.getString(PAYLOAD_KEY) ?: return null
        return WearSnapshotCodec.decode(payload)
    }
}
