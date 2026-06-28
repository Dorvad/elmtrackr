package com.elmtrackr.app.wear

import android.content.Context
import com.elmtrackr.wear.sync.PunchResult
import com.elmtrackr.wear.sync.WearMessages
import com.elmtrackr.wear.sync.WearSnapshotCodec
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearMessageListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearMessages.PUNCH_IN -> handlePunch(messageEvent) { WearActions.clockIn(it) }
            WearMessages.PUNCH_OUT -> handlePunch(messageEvent) { WearActions.clockOut(it) }
            WearMessages.REFRESH -> scope.launch { WearSyncPublisher.refresh(applicationContext) }
        }
    }

    private fun handlePunch(
        messageEvent: MessageEvent,
        action: suspend (Context) -> PunchResult,
    ) {
        scope.launch {
            val result = runCatching {
                action(applicationContext)
            }.getOrElse {
                PunchResult(success = false, errorCode = "internal_error")
            }
            runCatching {
                Wearable.getMessageClient(this@WearMessageListenerService)
                    .sendMessage(
                        messageEvent.sourceNodeId,
                        WearMessages.PUNCH_RESULT,
                        WearSnapshotCodec.encodePunchResult(result),
                    )
                    .await()
            }
        }
    }
}
