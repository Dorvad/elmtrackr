package com.elmtrackr.app.wear

import android.content.Context
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.widget.WidgetContext
import com.elmtrackr.app.widget.WidgetShiftState
import com.elmtrackr.app.widget.WidgetStateMapper
import com.elmtrackr.wear.sync.WearMessages
import com.elmtrackr.wear.sync.WearPaths
import com.elmtrackr.wear.sync.WearShiftSnapshot
import com.elmtrackr.wear.sync.WearSnapshotCodec
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId

object WearSyncPublisher {

    suspend fun publishSnapshot(context: Context, snapshot: WearShiftSnapshot) {
        runCatching {
            val dataClient = Wearable.getDataClient(context.applicationContext)
            val putRequest = PutDataMapRequest.create(WearPaths.SHIFT_STATE).apply {
                dataMap.putString(WearPaths.PAYLOAD_KEY, WearSnapshotCodec.encode(snapshot))
                dataMap.putLong("updatedAt", snapshot.updatedAtEpochMillis)
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(putRequest).await()
            nudgeConnectedNodes(context)
        }
    }

    suspend fun publishFromWidgetContext(context: Context, widgetContext: WidgetContext) {
        val app = context.applicationContext as ElmTrackrApp
        val userId = app.currentUserProvider.currentUserId()
        if (userId == null) {
            publishSnapshot(context, WearShiftSnapshot.signedOut())
            return
        }
        val state = WidgetStateMapper.map(widgetContext)
        publishSnapshot(context, state.toWearSnapshot(signedIn = true))
    }

    suspend fun publishFromShiftState(context: Context, state: WidgetShiftState, signedIn: Boolean) {
        publishSnapshot(context, state.toWearSnapshot(signedIn = signedIn))
    }

    suspend fun refresh(context: Context) {
        val app = context.applicationContext as ElmTrackrApp
        val userId = app.currentUserProvider.currentUserId()
        if (userId == null) {
            publishSnapshot(context, WearShiftSnapshot.signedOut())
            return
        }
        val shift = app.shiftsRepository.observeActiveShift(userId).first()
        val lastCompleted = app.shiftsRepository
            .observeRecentCompletedShifts(userId, limit = 1)
            .first()
            .firstOrNull()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayShifts = app.shiftsRepository
            .observeShiftsByMonth(userId, today.year, today.monthValue)
            .first()
        val settings = app.settingsRepository.getSettings(userId)
        publishFromWidgetContext(
            context,
            WidgetContext(
                activeShift = shift,
                lastCompletedShift = lastCompleted,
                todayShifts = todayShifts,
                settings = settings,
            ),
        )
    }

    private suspend fun nudgeConnectedNodes(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            val nodeClient = Wearable.getNodeClient(appContext)
            val messageClient = Wearable.getMessageClient(appContext)
            val nodes = nodeClient.connectedNodes.await()
            for (node in nodes) {
                messageClient.sendMessage(node.id, WearMessages.REFRESH, ByteArray(0)).await()
            }
        }
    }
}
