package com.elmtrackr.wear.sync

import com.elmtrackr.wear.ElmTrackrWearApp
import com.elmtrackr.wear.sync.WearMessages.REFRESH
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WearDataListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repository: WearStateRepository?
        get() = ElmTrackrWearApp.from(this)?.wearStateRepository

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        repository?.handleDataEvents(dataEvents)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != REFRESH) return
        val target = repository ?: return
        scope.launch {
            target.refreshFromDataLayer()
        }
    }
}
