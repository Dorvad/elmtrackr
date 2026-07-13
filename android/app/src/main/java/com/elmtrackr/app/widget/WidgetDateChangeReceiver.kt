package com.elmtrackr.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elmtrackr.app.wear.WearSyncPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Repaints the widgets (and pushes a fresh Wear snapshot) when the clock or
 * timezone is changed, and on date rollover where the platform still delivers
 * ACTION_DATE_CHANGED to manifest receivers (best-effort on API 26+). Without
 * this, an idle widget — the refresh worker only runs while a shift is
 * active — keeps a stale date label until something else touches it.
 */
class WidgetDateChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> Unit
            else -> return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { WidgetActions.refreshWidgets(context.applicationContext) }
                runCatching { WearSyncPublisher.refresh(context.applicationContext) }
                // Reminder delays were computed against the previous clock and
                // zone; recompute them so an "at 17:30" rule still fires at
                // 17:30 after a timezone or DST change mid-shift.
                runCatching {
                    com.elmtrackr.app.notification.ActiveShiftRestorer.restore(context.applicationContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
