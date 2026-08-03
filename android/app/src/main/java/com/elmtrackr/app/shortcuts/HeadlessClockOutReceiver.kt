package com.elmtrackr.app.shortcuts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HeadlessClockOutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_HEADLESS_CLOCK_OUT) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // runCatching, matching ClockOutReceiver, BootCompletedReceiver
                // and WidgetDateChangeReceiver. This receiver was the outlier: a
                // bare try/finally with no catch and no CoroutineExceptionHandler
                // on its scope, so anything thrown here reached the default
                // handler and killed the process. SupervisorJob does not help —
                // it stops sibling cancellation, not an uncaught throw from a
                // root launch.
                //
                // Reachable: clockOut does `?: error("Shift not found")` when the
                // shift disappears between the read and the write, which a
                // concurrent pull tombstone pass can cause.
                val result = runCatching {
                    ClockOutActions.clockOutActiveShift(context.applicationContext)
                }.getOrNull()
                if (result != null) {
                    runCatching {
                        ClockOutActions.showShortcutFeedback(context.applicationContext, result)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_HEADLESS_CLOCK_OUT = "com.elmtrackr.ACTION_HEADLESS_CLOCK_OUT"
    }
}
