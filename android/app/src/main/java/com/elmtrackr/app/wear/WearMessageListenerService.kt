package com.elmtrackr.app.wear

import android.content.Context
import android.util.Log
import com.elmtrackr.app.monitoring.CrashReporting
import com.elmtrackr.wear.sync.PunchResult
import com.elmtrackr.wear.sync.WearMessages
import com.elmtrackr.wear.sync.WearSnapshotCodec
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearMessageListenerService : WearableListenerService() {

    // Play Services starts this service on the watch's behalf, so the work here
    // runs in the background with nobody looking. A SupervisorJob alone does not
    // stop an unhandled failure from reaching the thread's default handler, which
    // on Android kills the phone app — over a watch message. Report and swallow.
    private val scope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.IO +
            CoroutineExceptionHandler { _, throwable ->
                if (throwable is CancellationException) return@CoroutineExceptionHandler
                Log.e(TAG, "Unhandled failure while serving a watch message", throwable)
                runCatching { CrashReporting.report(throwable) }
            },
    )

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearMessages.PUNCH_IN -> handlePunch(messageEvent) { ctx ->
                val command = punchCommand(messageEvent)
                WearActions.clockIn(ctx, command?.epochMillis?.takeIf { it > 0L }, command?.userId.orEmpty())
            }
            WearMessages.PUNCH_OUT -> handlePunch(messageEvent) { ctx ->
                val command = punchCommand(messageEvent)
                WearActions.clockOut(ctx, command?.epochMillis?.takeIf { it > 0L }, command?.userId.orEmpty())
            }
            WearMessages.REFRESH -> scope.launch {
                // refresh() reads the database before it publishes, and only the
                // publish half guarded itself. A Room failure here used to be an
                // uncaught exception on a background coroutine — a phone crash
                // triggered by the watch coming into range.
                runCatching { WearSyncPublisher.refresh(applicationContext) }
                    .onFailure {
                        if (it is CancellationException) throw it
                        Log.w(TAG, "Could not answer the watch's refresh request", it)
                    }
            }
        }
    }

    private fun punchCommand(messageEvent: MessageEvent) =
        WearSnapshotCodec.decodePunchCommand(messageEvent.data)

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

    private companion object {
        const val TAG = "WearMessageListener"
    }
}
