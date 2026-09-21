package com.elmtrackr.wear.sync

import android.content.Context
import com.elmtrackr.wear.runCatchingCancellable
import com.elmtrackr.wear.sync.WearMessages.PUNCH_IN
import com.elmtrackr.wear.sync.WearMessages.PUNCH_OUT
import com.elmtrackr.wear.sync.WearMessages.PUNCH_RESULT
import com.elmtrackr.wear.sync.WearMessages.REFRESH
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class WearActionClient(
    private val context: Context,
    private val wearStateRepository: WearStateRepository,
) : MessageClient.OnMessageReceivedListener {

    private val appContext = context.applicationContext
    private val punchMutex = Mutex()

    suspend fun punchIn(): PunchResult = punch(isPunchIn = true)

    suspend fun punchOut(): PunchResult = punch(isPunchIn = false)

    suspend fun requestRefreshFromPhone() {
        runCatchingCancellable {
            val phone = findPhoneNode() ?: return
            Wearable.getMessageClient(appContext)
                .sendMessage(phone.id, REFRESH, ByteArray(0))
                .await()
            // The phone answers REFRESH asynchronously: it reads its DB and pushes
            // a new data item, which lands here as a DATA_CHANGED event a beat
            // later. A single immediate read almost always loses that race.
            var phoneSnapshot: WearShiftSnapshot? = null
            var attempt = 0
            while (attempt < REFRESH_POLL_ATTEMPTS) {
                phoneSnapshot = wearStateRepository.refreshFromDataLayer()
                if (phoneSnapshot?.signedIn == true) break
                delay(REFRESH_POLL_INTERVAL_MS)
                attempt++
            }
            if (phoneSnapshot?.signedIn == true) {
                syncPendingWithPhone()
            }
        }
    }

    private suspend fun punch(isPunchIn: Boolean): PunchResult = punchMutex.withLock {
        wearStateRepository.setPunchInProgress(true)
        try {
            val now = System.currentTimeMillis()
            val path = if (isPunchIn) PUNCH_IN else PUNCH_OUT
            val phoneResult = sendPunchToPhone(path, now, wearStateRepository.snapshot.value.userId)
            if (phoneResult.success) {
                wearStateRepository.refreshFromDataLayer()
                return phoneResult
            }
            if (phoneResult.errorCode == "timeout") {
                val latest = wearStateRepository.readNewestPhoneSnapshot()
                if (latest != null && WearLocalShift.livePunchSettledByPhone(isPunchIn, now, latest)) {
                    wearStateRepository.applySnapshot(latest)
                    return PunchResult(success = true)
                }
            }
            if (WearLocalShift.shouldFallbackToLocal(phoneResult.errorCode)) {
                return wearStateRepository.applyLocalPunch(isPunchIn, now)
            }
            phoneResult
        } finally {
            wearStateRepository.setPunchInProgress(false)
        }
    }

    /**
     * Drain the local punch log onto the phone. Stops on the first real
     * failure so later events cannot invert IN/OUT order. A lost ACK that
     * already took effect on the phone is treated as settled so the queue
     * cannot jam behind `no_active_shift`.
     *
     * Public so tile-only punches still replay when the phone reconnects
     * without the launcher opening.
     */
    suspend fun syncPendingWithPhone() {
        val drained = punchMutex.withLock {
            val events = wearStateRepository.pendingEvents()
            if (events.isEmpty()) return@withLock false
            for (event in events) {
                val phoneBeforeReplay = wearStateRepository.readNewestPhoneSnapshot()
                if (event.userId.isBlank() || phoneBeforeReplay?.userId?.let { it != event.userId } == true) {
                    wearStateRepository.removeEvent(event.id)
                    continue
                }
                if (WearLocalShift.shouldDeferReplay(event, phoneBeforeReplay)) {
                    return@withLock false
                }
                val path = if (event.isPunchIn) PUNCH_IN else PUNCH_OUT
                val result = sendPunchToPhone(path, event.epochMillis, event.userId)
                val phone = wearStateRepository.readNewestPhoneSnapshot()
                if (result.errorCode == "user_mismatch") {
                    wearStateRepository.removeEvent(event.id)
                    continue
                }
                if (!WearLocalShift.replayEventSettled(event, result, phone)) {
                    return@withLock false
                }
                wearStateRepository.removeEvent(event.id)
            }
            true
        }
        if (drained) {
            wearStateRepository.refreshFromDataLayer()
        }
    }

    private suspend fun sendPunchToPhone(
        path: String,
        epochMillis: Long,
        userId: String = "",
    ): PunchResult {
        return try {
            val phone = findPhoneNode()
                ?: return PunchResult(success = false, errorCode = "phone_unreachable")
            val messageClient = Wearable.getMessageClient(appContext)
            // Await registration: a fast phone can reply before an un-awaited
            // listener is live, losing the result and forcing the timeout path.
            messageClient.addListener(this).await()
            try {
                pendingResult = null
                val payload = WearSnapshotCodec.encodePunchCommand(WearPunchCommand(epochMillis, userId))
                messageClient.sendMessage(phone.id, path, payload).await()
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
