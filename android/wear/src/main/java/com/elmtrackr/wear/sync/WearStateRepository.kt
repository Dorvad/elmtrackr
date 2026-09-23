package com.elmtrackr.wear.sync

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.wear.tiles.TileService
import com.elmtrackr.wear.ongoing.WearOngoingShift
import com.elmtrackr.wear.runCatchingCancellable
import com.elmtrackr.wear.sync.WearPaths.CRASH_REPORTING_CONSENT
import com.elmtrackr.wear.sync.WearPaths.ENABLED_KEY
import com.elmtrackr.wear.sync.WearPaths.PAYLOAD_KEY
import com.elmtrackr.wear.sync.WearPaths.SHIFT_STATE
import com.elmtrackr.wear.tile.ElmTrackrTileService
import com.elmtrackr.wear.tile.WearTileRefreshWorker
import com.elmtrackr.wear.wearBackgroundExceptionHandler
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.UUID

// A corrupt or unreadable cache file must never be fatal. Without the
// corruption handler the `data` flow rethrows CorruptionException on every
// read, and the read happens during Application.onCreate — so a single bad
// write (battery pull mid-flush) would turn every subsequent launch into a
// crash until the user cleared the app's data.
private val Context.wearStateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wear_shift_state",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

// Kept separate from the display snapshot cache: the snapshot may be safely
// reset on corruption, but an unreplayed punch log is user work that must never
// be converted to "no pending work" by that cache's recovery handler.
private val Context.wearPunchLogDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wear_punch_log",
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
    private val applyScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + wearBackgroundExceptionHandler(TAG),
    )

    private val cacheKey = stringPreferencesKey("snapshot_json")
    private val punchLogKey = stringPreferencesKey("punch_log_json")
    private val punchLogMutex = Mutex()

    private val _snapshot = MutableStateFlow(WearShiftSnapshot.signedOut())
    val snapshot: StateFlow<WearShiftSnapshot> = _snapshot.asStateFlow()

    private val _confirmation = MutableStateFlow<WearConfirmation?>(null)
    val confirmation: StateFlow<WearConfirmation?> = _confirmation.asStateFlow()

    private val _isPunchInProgress = MutableStateFlow(false)
    val isPunchInProgress: StateFlow<Boolean> = _isPunchInProgress.asStateFlow()

    suspend fun bootstrap() {
        loadCached()
        rollLocalDayIfNeeded()
        refreshFromDataLayer()
    }

    suspend fun loadCached() {
        // Runs on the startup path (Application.onCreate and the view model's
        // init). Disk I/O, so it can fail for reasons that have nothing to do
        // with this app being correct — a full filesystem, a locked file during
        // a restore. Falling back to the signed-out snapshot shows the sign-in
        // face; throwing here would take the process down before the first frame.
        runCatchingCancellable {
            val json = context.wearStateDataStore.data.first()[cacheKey]
            val cached = json?.let(WearSnapshotCodec::decode)
            if (cached != null) {
                val rolled = WearLocalShift.rollToLocalDay(cached, System.currentTimeMillis())
                applySnapshot(rolled, persist = rolled != cached)
            }
        }.onFailure { Log.w(TAG, "Could not read the cached shift snapshot", it) }
    }

    /**
     * Newest phone snapshot on the data layer, or null if none. Does not write
     * DataStore — replay uses this to decide whether a lost ACK already landed.
     */
    suspend fun readNewestPhoneSnapshot(): WearShiftSnapshot? =
        runCatchingCancellable {
            val dataClient = Wearable.getDataClient(context)
            val uri = android.net.Uri.parse("wear://*$SHIFT_STATE")
            val items = dataClient.getDataItems(uri).await()
            // Multiple nodes can each hold a data item; applying them in
            // iteration order lets a stale phone snapshot overwrite a fresh
            // one. Apply only the newest.
            val newest = items.mapNotNull { parseDataItem(it) }
                .maxByOrNull { it.updatedAtEpochMillis }
            items.release()
            newest
        }.onFailure { Log.w(TAG, "Could not read the phone snapshot from the data layer", it) }
            .getOrNull()

    suspend fun refreshFromDataLayer(): WearShiftSnapshot? {
        val newest = readNewestPhoneSnapshot() ?: return null
        val pending = pendingEvents().isNotEmpty()
        if (WearLocalShift.shouldApplyPhoneSnapshot(_snapshot.value, newest, pending)) {
            applySnapshot(newest)
        } else {
            applySnapshot(WearLocalShift.mergeConsent(_snapshot.value, newest))
        }
        return newest
    }

    suspend fun rollLocalDayIfNeeded(nowMillis: Long = System.currentTimeMillis()) {
        val current = _snapshot.value
        val rolled = WearLocalShift.rollToLocalDay(current, nowMillis)
        if (rolled != current) applySnapshot(rolled)
    }

    /**
     * Parses the callback-scoped buffer synchronously (it is invalid once the
     * listener returns). Callers apply on their own coroutine so a replay of
     * queued wrist punches can run in the same turn and cannot be overwritten
     * by a stale snapshot applied later.
     */
    fun takeChangedSnapshots(events: DataEventBuffer): List<WearShiftSnapshot> =
        events.mapNotNull { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@mapNotNull null
            val item = event.dataItem
            if (!item.uri.path.orEmpty().startsWith(SHIFT_STATE)) return@mapNotNull null
            parseDataItem(item)
        }

    fun takeChangedCrashReportingConsents(events: DataEventBuffer): List<Boolean> =
        events.mapNotNull { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@mapNotNull null
            val item = event.dataItem
            if (!item.uri.path.orEmpty().startsWith(CRASH_REPORTING_CONSENT)) return@mapNotNull null
            val dataMap = DataMapItem.fromDataItem(item).dataMap
            if (!dataMap.containsKey(ENABLED_KEY)) return@mapNotNull null
            dataMap.getBoolean(ENABLED_KEY)
        }

    suspend fun applyIncomingPhoneSnapshot(incoming: WearShiftSnapshot) {
        val pending = pendingEvents().isNotEmpty()
        if (WearLocalShift.shouldApplyPhoneSnapshot(_snapshot.value, incoming, pending)) {
            applySnapshot(incoming)
        } else {
            applySnapshot(WearLocalShift.mergeConsent(_snapshot.value, incoming))
        }
    }

    suspend fun applyCrashReportingConsent(enabled: Boolean) {
        val current = _snapshot.value
        if (current.crashReportingEnabled != enabled) {
            applySnapshot(current.copy(crashReportingEnabled = enabled))
        }
    }

    suspend fun applySnapshot(snapshot: WearShiftSnapshot, persist: Boolean = true) {
        val rolled = WearLocalShift.rollToLocalDay(snapshot, System.currentTimeMillis())
        _snapshot.value = rolled
        if (persist) {
            runCatchingCancellable {
                context.wearStateDataStore.edit { prefs ->
                    prefs[cacheKey] = WearSnapshotCodec.encode(rolled)
                }
            }.onFailure { Log.w(TAG, "Could not cache the shift snapshot", it) }
        }
        // Each of the three surfaces below is refreshed independently and each
        // is wrapped on its own. They talk to system components that are not
        // guaranteed to be present or initialised on every watch — the tile
        // host, the watch-face complication registry, and WorkManager — and
        // this method runs on the startup path. One missing surface must
        // degrade that surface, not take the app down with it.
        val keepRefreshWorkerRunning = WearLocalShift.shouldKeepBackgroundRefreshRunning(
            rolled,
            hasPendingReplay = pendingEvents().isNotEmpty(),
        )
        runCatchingCancellable {
            if (keepRefreshWorkerRunning) {
                WearTileRefreshWorker.schedule(context)
            } else {
                WearTileRefreshWorker.cancel(context)
            }
        }.onFailure { Log.w(TAG, "Could not reschedule the tile refresh worker", it) }

        runCatchingCancellable {
            ElmTrackrComplicationBridge.requestUpdateAll(context)
        }.onFailure { Log.w(TAG, "Could not request a complication update", it) }

        // The fourth surface: the Wear OS ongoing-activity indicator on the watch
        // face and the chip in recents, both of which the quality guidelines
        // require while a shift runs. Posted while active, cancelled otherwise.
        // Guards itself, including the POST_NOTIFICATIONS check.
        WearOngoingShift.sync(context, rolled)

        // Re-render the tile immediately on every state change. The refresh
        // worker only drives the once-a-minute count-up while a shift runs;
        // without this, a punch made FROM the tile keeps showing the old
        // face for up to a minute, and a sign-in/out for up to an hour.
        runCatchingCancellable {
            TileService.getUpdater(context).requestUpdate(ElmTrackrTileService::class.java)
        }.onFailure { Log.w(TAG, "Could not request a tile update", it) }
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

    /**
     * Records a punch on the watch itself when the phone cannot take it —
     * unpaired, unsigned, unreachable, or opted out of Wear sync. The event is
     * queued so a later signed-in phone can replay it with the original wrist
     * time rather than "whenever the two devices next saw each other".
     */
    suspend fun applyLocalPunch(isPunchIn: Boolean, nowMillis: Long = System.currentTimeMillis()): PunchResult {
        if (isPunchIn && _snapshot.value.isActive) return PunchResult(success = true)
        if (!isPunchIn && !_snapshot.value.isActive) return PunchResult(success = true)
        val queued = appendEvent(
            WearPunchEvent(
                id = UUID.randomUUID().toString(),
                isPunchIn = isPunchIn,
                epochMillis = nowMillis,
                userId = _snapshot.value.userId,
            ),
        )
        if (!queued) return PunchResult(success = false, errorCode = "local_queue_failed")
        val next = if (isPunchIn) {
            WearLocalShift.punchIn(_snapshot.value, nowMillis)
        } else {
            WearLocalShift.punchOut(_snapshot.value, nowMillis)
        }
        applySnapshot(next)
        return PunchResult(success = true)
    }

    suspend fun pendingEvents(): List<WearPunchEvent> =
        punchLogMutex.withLock { loadPunchLogLocked()?.events.orEmpty() }

    suspend fun removeEvent(eventId: String) {
        punchLogMutex.withLock {
            val current = loadPunchLogLocked() ?: return@withLock
            val remaining = current.events.filterNot { it.id == eventId }
            persistPunchLogLocked(WearPunchEventLog(remaining))
        }
    }

    private suspend fun appendEvent(event: WearPunchEvent): Boolean =
        punchLogMutex.withLock {
            val current = loadPunchLogLocked() ?: return@withLock false
            persistPunchLogLocked(WearPunchEventLog(current.events + event))
        }

    private suspend fun loadPunchLogLocked(): WearPunchEventLog? {
        val json = runCatchingCancellable {
            context.wearPunchLogDataStore.data.first()[punchLogKey]
        }.onFailure { Log.w(TAG, "Could not read the watch punch log", it) }
            .getOrNull()
            ?: runCatchingCancellable {
                // Legacy location from before the queue was split away from the
                // lossy snapshot cache. Read only when the new queue is empty.
                context.wearStateDataStore.data.first()[punchLogKey]
            }.onFailure { Log.w(TAG, "Could not read the legacy watch punch log", it) }
                .getOrNull()
        if (json == null) return WearPunchEventLog()
        return WearSnapshotCodec.decodePunchLog(json)
            ?: run {
                Log.w(TAG, "Could not decode the watch punch log")
                null
            }
    }

    private suspend fun persistPunchLogLocked(log: WearPunchEventLog): Boolean =
        runCatchingCancellable {
            context.wearPunchLogDataStore.edit { prefs ->
                prefs[punchLogKey] = WearSnapshotCodec.encodePunchLog(log)
            }
        }.onFailure { Log.w(TAG, "Could not persist the watch punch log", it) }
            .isSuccess

    private fun parseDataItem(item: com.google.android.gms.wearable.DataItem): WearShiftSnapshot? {
        val payload = DataMapItem.fromDataItem(item).dataMap.getString(PAYLOAD_KEY) ?: return null
        return WearSnapshotCodec.decode(payload)
    }

    private companion object {
        const val TAG = "WearStateRepository"
    }
}
