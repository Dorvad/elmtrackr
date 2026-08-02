package com.elmtrackr.app.wear

import android.content.Context
import com.elmtrackr.app.data.local.preferences.AppPreferenceKeys
import com.elmtrackr.app.data.local.preferences.appPreferencesDataStore
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import com.elmtrackr.app.language.withAppLocale
import com.elmtrackr.app.widget.WidgetContext
import com.elmtrackr.app.widget.WidgetContextLoader
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

object WearSyncPublisher {

    /** Capability the watch app declares; lets the phone detect the installed app. */
    const val WATCH_APP_CAPABILITY = "elmtrackr_wear_app"

    suspend fun isSyncEnabled(context: Context): Boolean = runCatching {
        context.applicationContext.appPreferencesDataStore.data.first()
            .let { it[AppPreferenceKeys.WEAR_SYNC_ENABLED] ?: true }
    }.getOrDefault(true)

    suspend fun publishSnapshot(context: Context, snapshot: WearShiftSnapshot) {
        // The one choke point every publish path goes through, so the Settings
        // toggle silences the watch mirror everywhere at once.
        if (!isSyncEnabled(context)) return
        publishSnapshotIgnoringPreference(context, snapshot)
    }

    /**
     * Blanks the watch after the user turns sync off — deliberately bypasses the
     * preference gate, because leaving yesterday's shift frozen on the watch face
     * would look like an active connection.
     */
    suspend fun clearWatchData(context: Context) {
        publishSnapshotIgnoringPreference(context, WearShiftSnapshot.signedOut())
    }

    private suspend fun publishSnapshotIgnoringPreference(
        context: Context,
        snapshot: WearShiftSnapshot,
    ) {
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
        val deps = AppEntryPoints.background(context)
        val userId = deps.currentUserProvider().currentUserId()
        if (userId == null) {
            publishSnapshot(context, WearShiftSnapshot.signedOut())
            return
        }
        val locale = context.withAppLocale().resources.configuration.locales[0]
            ?: java.util.Locale.getDefault()
        val state = WidgetStateMapper.map(widgetContext, locale)
        publishSnapshot(context, state.toWearSnapshot(signedIn = true))
    }

    suspend fun publishFromShiftState(context: Context, state: WidgetShiftState, signedIn: Boolean) {
        publishSnapshot(context, state.toWearSnapshot(signedIn = signedIn))
    }

    suspend fun refresh(context: Context) {
        val deps = AppEntryPoints.background(context)
        val userId = deps.currentUserProvider().currentUserId()
        if (userId == null) {
            publishSnapshot(context, WearShiftSnapshot.signedOut())
            return
        }
        val widgetContext = WidgetContextLoader.load(deps, userId)
        publishFromWidgetContext(context, widgetContext)
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
