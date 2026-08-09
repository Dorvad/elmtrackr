package com.elmtrackr.wear.sync

import android.content.Context
import com.elmtrackr.wear.sync.WearMessages.PUNCH_IN
import com.elmtrackr.wear.sync.WearMessages.PUNCH_OUT
import com.elmtrackr.wear.sync.WearMessages.PUNCH_RESULT
import com.elmtrackr.wear.sync.WearMessages.REFRESH
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
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
            // The phone answers REFRESH asynchronously: it reads its DB and pushes
            // a new data item, which lands here as a DATA_CHANGED event a beat
            // later. A single immediate read almost always loses that race and
            // leaves the watch on the sign-in screen even though the phone is now
            // signed in, so poll briefly until the signed-in state arrives.
            repeat(REFRESH_POLL_ATTEMPTS) {
                wearStateRepository.refreshFromDataLayer()
                if (wearStateRepository.snapshot.value.signedIn) return
                delay(REFRESH_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun sendPunch(path: String): PunchResult {
        wearStateRepository.setPunchInProgress(true)
        return try {
            val phone = findPhoneNode()
                ?: return PunchResult(success = false, errorCode = "phone_unreachable")
            val messageClient = Wearable.getMessageClient(appContext)
            // Await registration: a fast phone can reply before an un-awaited
            // listener is live, losing the result and forcing the timeout path.
            messageClient.addListener(this).await()
            try {
                pendingResult = null
                messageClient.sendMessage(phone.id, path, ByteArray(0)).await()
                waitForPunchResult()
            } finally {
                runCatching { messageClient.removeListener(this) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The node lookup and sendMessage throw ApiException when Play
            // Services is unavailable or the phone drops mid-flight. A punch
            // must fail with feedback, never crash the watch app.
            PunchResult(success = false, errorCode = "phone_unreachable")
        } finally {
            wearStateRepository.setPunchInProgress(false)
        }
    }

    @Volatile
    private var pendingResult: PunchResult? = null

    private suspend fun waitForPunchResult(): PunchResult {
        // 10s: the phone-side punch includes a Room write and a Supabase push;
        // 5s produced false "failed" feedback on slow networks, and a retry
        // after a false failure is how duplicate punches happen.
        repeat(40) {
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

    private companion object {
        // ~3s total: covers a phone DB read plus data-layer round trip without
        // making a genuinely signed-out watch feel stuck on the sign-in screen.
        const val REFRESH_POLL_ATTEMPTS = 12
        const val REFRESH_POLL_INTERVAL_MS = 250L
    }
}
