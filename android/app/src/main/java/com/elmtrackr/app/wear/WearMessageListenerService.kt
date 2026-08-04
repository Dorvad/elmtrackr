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

    /**
     * Whether [nodeId] is a device currently paired with this one.
     *
     * This service is exported — it has to be, for Play Services to deliver to it —
     * and PUNCH_IN/PUNCH_OUT write shift rows. The sender was never checked: the only
     * use of `sourceNodeId` was addressing the reply. Play Services normally restricts
     * message delivery to same-signature apps, but nothing here depended on that
     * deliberately, and "normally" is not a control.
     *
     * A node lookup is the check the Wearable API actually supports; there is no
     * caller UID to verify for a message that arrived over the data layer. A node that
     * is not in the connected set cannot have been paired through the companion
     * relationship, so its punch is refused.
     *
     * Fails closed on a lookup error. A punch is not urgent enough to accept from an
     * unverifiable source, and the watch surfaces the failure.
     */
    private suspend fun isConnectedNode(nodeId: String): Boolean = runCatching {
        Wearable.getNodeClient(applicationContext).connectedNodes.await().any { it.id == nodeId }
    }.getOrDefault(false)

    private fun handlePunch(
        messageEvent: MessageEvent,
        action: suspend (Context) -> PunchResult,
    ) {
        scope.launch {
            val result = if (!isConnectedNode(messageEvent.sourceNodeId)) {
                PunchResult(success = false, errorCode = "unknown_sender")
            } else {
                runCatching {
                    action(applicationContext)
                }.getOrElse {
                    PunchResult(success = false, errorCode = "internal_error")
                }
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
