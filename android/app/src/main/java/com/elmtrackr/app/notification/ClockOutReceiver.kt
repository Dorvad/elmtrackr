package com.elmtrackr.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import com.elmtrackr.app.domain.compensation.ShiftCompensationHelper
import com.elmtrackr.app.security.AppLockActionGuard
import com.elmtrackr.app.wear.WearSyncPublisher
import com.elmtrackr.app.widget.WidgetActions
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
            // Same guard as the widget/shortcut/Wear punch paths: the notification
            // action must not clock out shifts while the app lock is engaged. It
            // runs inside the coroutine because resolving the persisted lock
            // preference suspends — and outside the try/finally below, because a
            // blocked punch must leave the shift running and its notification
            // pinned rather than cleaning both up.
            if (AppLockActionGuard.blockIfLocked(context)) {
                pendingResult.finish()
                return@launch
            }
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
                // Eager repaint, matching the widget/shortcut/Wear clock-out
                // paths; otherwise other surfaces wait for the debounced
                // app-scope observer.
                runCatching { WidgetActions.refreshWidgets(context.applicationContext) }
                runCatching { WearSyncPublisher.refresh(context.applicationContext) }
                pendingResult.finish()
            }
        }
    }
}
