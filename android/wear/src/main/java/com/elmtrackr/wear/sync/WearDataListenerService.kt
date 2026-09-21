package com.elmtrackr.wear.sync

import com.elmtrackr.wear.ElmTrackrWearApp
import com.elmtrackr.wear.sync.WearMessages.REFRESH
import com.elmtrackr.wear.wearBackgroundExceptionHandler
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WearDataListenerService : WearableListenerService() {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + wearBackgroundExceptionHandler(TAG),
    )

    private val repository: WearStateRepository?
        get() = ElmTrackrWearApp.from(this)?.wearStateRepository

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val target = repository ?: return
        val snapshots = target.takeChangedSnapshots(dataEvents)
        val crashReportingConsents = target.takeChangedCrashReportingConsents(dataEvents)
        val app = ElmTrackrWearApp.from(this) ?: return
        scope.launch {
            crashReportingConsents.forEach { target.applyCrashReportingConsent(it) }
            snapshots.forEach { target.applyIncomingPhoneSnapshot(it) }
            // Tile-only punches queue events without opening the launcher.
            // Drain after applying so a stale signed-out snapshot cannot
            // replace work that just replayed onto the phone.
            app.wearActionClient.syncPendingWithPhone()
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != REFRESH) return
        val target = repository ?: return
        scope.launch {
            target.refreshFromDataLayer()
            ElmTrackrWearApp.from(this@WearDataListenerService)
                ?.wearActionClient
                ?.syncPendingWithPhone()
        }
    }

    override fun onPeerConnected(peer: Node) {
        replayWhenPhoneIsReachable(askPhone = true)
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.nodes.isEmpty()) return
        replayWhenPhoneIsReachable(askPhone = true)
    }

    private fun replayWhenPhoneIsReachable(askPhone: Boolean = false) {
        val app = ElmTrackrWearApp.from(this) ?: return
        scope.launch {
            if (askPhone) {
                app.wearActionClient.requestRefreshFromPhone()
            } else {
                app.wearActionClient.syncPendingWithPhone()
            }
        }
    }

    private companion object {
        const val TAG = "WearDataListener"
    }
}
