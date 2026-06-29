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
                val result = ClockOutActions.clockOutActiveShift(context.applicationContext)
                ClockOutActions.showShortcutFeedback(context.applicationContext, result)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_HEADLESS_CLOCK_OUT = "com.elmtrackr.ACTION_HEADLESS_CLOCK_OUT"
    }
}
