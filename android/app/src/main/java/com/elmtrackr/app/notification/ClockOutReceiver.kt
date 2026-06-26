package com.elmtrackr.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elmtrackr.app.ElmTrackrApp
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
                val app = context.applicationContext as ElmTrackrApp
                val userId = app.currentUserProvider.currentUserId()
                if (userId != null) {
                    val shift = app.shiftsRepository.getShiftById(shiftId)
                    val settings = app.settingsRepository.getSettings(userId)
                    if (shift != null && settings != null) {
                        val profiles = app.compensationProfilesRepository.getProfiles(userId)
                        val snapshot = ShiftCompensationHelper.buildClockOutSnapshot(shift, settings, profiles)
                        app.shiftsRepository.clockOut(shiftId, compensationSnapshot = snapshot)
                    } else {
                        app.shiftsRepository.clockOut(shiftId)
                    }
                } else {
                    app.shiftsRepository.clockOut(shiftId)
                }
                val nm = ActiveShiftNotificationManager(context.applicationContext)
                nm.cancelActiveShiftNotification()
                nm.cancelReminderNotification()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
