package com.elmtrackr.wear.sync

import android.content.Context
import com.elmtrackr.wear.sync.WearMessages.PUNCH_IN
import com.elmtrackr.wear.sync.WearMessages.PUNCH_OUT
import com.elmtrackr.wear.sync.WearMessages.PUNCH_RESULT
import com.elmtrackr.wear.sync.WearMessages.REFRESH
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

class WearActionClient(
    private val context: Context,
    private val wearStateRepository: WearStateRepository,
) : MessageClient.OnMessageReceivedListener {

    private val appContext = context.applicationContext

    suspend fun punchIn(): PunchResult = sendPunch(PUNCH_IN)

    suspend fun punchOut(): PunchResult = sendPunch(PUNCH_OUT)

    suspend fun requestRefreshFromPhone() {
        runCatching {
            val phone = findPhoneNode() ?: return
            Wearable.getMessageClient(appContext)
                .sendMessage(phone.id, REFRESH, ByteArray(0))
                .await()
            wearStateRepository.refreshFromDataLayer()
        }
    }

    private suspend fun sendPunch(path: String): PunchResult {
        wearStateRepository.setPunchInProgress(true)
        return try {
            val phone = findPhoneNode()
                ?: return PunchResult(success = false, errorCode = "phone_unreachable")
            val messageClient = Wearable.getMessageClient(appContext)
            messageClient.addListener(this)
            try {
                pendingResult = null
                messageClient.sendMessage(phone.id, path, ByteArray(0)).await()
                waitForPunchResult()
            } finally {
                messageClient.removeListener(this)
            }
        } finally {
            wearStateRepository.setPunchInProgress(false)
        }
    }

    @Volatile
    private var pendingResult: PunchResult? = null

    private suspend fun waitForPunchResult(): PunchResult {
        repeat(20) {
            pendingResult?.let { return it }
            delay(250)
        }
        wearStateRepository.refreshFromDataLayer()
        return PunchResult(success = false, errorCode = "timeout")
    }

    override fun onMessageReceived(messageEvent: com.google.android.gms.wearable.MessageEvent) {
        if (messageEvent.path != PUNCH_RESULT) return
        pendingResult = WearSnapshotCodec.decodePunchResult(messageEvent.data)
    }

    private suspend fun findPhoneNode(): Node? {
        val nodes = Wearable.getNodeClient(appContext).connectedNodes.await()
        return nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
    }
}
