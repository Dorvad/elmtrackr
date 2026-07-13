package com.elmtrackr.app.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elmtrackr.app.notification.ActiveShiftRestorer
import com.elmtrackr.app.wear.WearSyncPublisher
import com.elmtrackr.app.widget.WidgetActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restores active-shift surfaces after a reboot or app update: the ongoing
 * "shift in progress" notification (plain notifications don't survive a
 * reboot), reminder scheduling, widgets, and the Wear snapshot. Without this
 * a user who restarts their phone mid-shift silently loses the persistent
 * notification until they next open the app.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> Unit
            else -> return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { ActiveShiftRestorer.restore(context.applicationContext) }
                runCatching { WidgetActions.refreshWidgets(context.applicationContext) }
                runCatching { WearSyncPublisher.refresh(context.applicationContext) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
