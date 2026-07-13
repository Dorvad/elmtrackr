package com.elmtrackr.app.notification

import android.content.Context
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import com.elmtrackr.app.domain.time.WorkTimezone
import kotlinx.coroutines.flow.firstOrNull
import java.time.ZoneId

/**
 * Re-establishes the visible side effects of an active shift — the ongoing
 * notification and the scheduled reminder work — without launching the app.
 * WorkManager re-schedules its own persisted jobs after a reboot, but the
 * ongoing notification does not survive one, and timezone/DST changes leave
 * previously computed reminder delays pointing at the wrong wall-clock time.
 */
object ActiveShiftRestorer {

    suspend fun restore(context: Context) {
        val appContext = context.applicationContext
        val deps = AppEntryPoints.background(appContext)
        val userId = deps.currentUserProvider().currentUserId() ?: return
        val shift = deps.shiftsRepository().observeActiveShift(userId).firstOrNull() ?: return
        val settings = deps.settingsRepository().getSettings(userId)
        val zone = settings?.let { WorkTimezone.zoneFor(it) } ?: ZoneId.systemDefault()
        ActiveShiftNotificationManager(appContext).showActiveShiftNotification(shift, zone)
        OvertimeReminderScheduler.scheduleForActiveShift(appContext, shift, settings)
    }
}
