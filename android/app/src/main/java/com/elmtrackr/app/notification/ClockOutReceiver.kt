package com.elmtrackr.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import com.elmtrackr.app.domain.compensation.ShiftCompensationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the "Clock Out" action from the active-shift notification.
 * Writes to Room first (offline-safe), then cancels both notifications.
 * The active-shift observer in ElmTrackrApp will also cancel the notification
 * when the Room flow emits null, but we cancel eagerly here for instant feedback.
 */
class ClockOutReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val shiftId = intent.getStringExtra(ActiveShiftNotificationManager.EXTRA_SHIFT_ID)
            ?: return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val deps = AppEntryPoints.background(context)
                val userId = deps.currentUserProvider().currentUserId()
                // A stale notification can outlive its shift (clocked out or
                // deleted elsewhere). Only clock out when the shift still
                // exists, and never let a failure keep the notification pinned.
                val shift = runCatching { deps.shiftsRepository().getShiftById(shiftId) }.getOrNull()
                if (shift != null) {
                    runCatching {
                        val settings = userId?.let { deps.settingsRepository().getSettings(it) }
                        if (userId != null && settings != null) {
                            val profiles = deps.compensationProfilesRepository().getProfiles(userId)
                            val snapshot = ShiftCompensationHelper.buildClockOutSnapshot(shift, settings, profiles)
                            deps.shiftsRepository().clockOut(shiftId, compensationSnapshot = snapshot)
                        } else {
                            deps.shiftsRepository().clockOut(shiftId)
                        }
                    }
                }
            } finally {
                runCatching {
                    val nm = ActiveShiftNotificationManager(context.applicationContext)
                    nm.cancelActiveShiftNotification()
                    nm.cancelReminderNotification()
                    OvertimeReminderScheduler.cancelAll(context.applicationContext)
                }
                pendingResult.finish()
            }
        }
    }
}
