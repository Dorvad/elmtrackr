package com.elmtrackr.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The "you have entered overtime" alert, scheduled through [AlarmManager] rather
 * than WorkManager.
 *
 * Every reminder used `setInitialDelay`, which Doze can defer by hours. For a
 * pre-warning that is tolerable — the worker checks whether it woke up late and
 * stays quiet. For the at-threshold alert it is not: that notification is the point
 * of the whole feature, and one delivered an hour into unpaid overtime has already
 * failed the user.
 *
 * ## Why not `USE_EXACT_ALARM`
 *
 * Two permissions can drive an exact alarm. `USE_EXACT_ALARM` is granted
 * automatically but Play restricts it to alarm-clock and calendar apps; a
 * time-tracker claiming it invites a policy rejection. `SCHEDULE_EXACT_ALARM` is
 * the user-grantable one, so that is what is declared — and because it can be
 * absent or revoked, [schedule] degrades rather than failing:
 *
 *  1. exact and Doze-exempt, when the permission is held;
 *  2. inexact but still Doze-exempt (`setAndAllowWhileIdle`) otherwise — which is
 *     already better than `setInitialDelay`, since it fires in a Doze maintenance
 *     window instead of waiting for one.
 *
 * The WorkManager rule for this trigger is not enqueued when the alarm is set, so
 * the user cannot get the notification twice.
 */
object OvertimeThresholdAlarm {

    private const val ACTION = "com.elmtrackr.app.ACTION_OVERTIME_THRESHOLD"
    private const val REQUEST_CODE = 4711

    /**
     * Sets the alert for the moment [shiftStart] plus [thresholdMinutes] is reached.
     *
     * Returns false when the moment has already passed, so the caller falls back to
     * the WorkManager path and the notification is not simply lost. A past-due
     * threshold means the shift was already in overtime when this was scheduled —
     * usually a re-arm after a reboot — and WorkManager fires that immediately.
     */
    fun schedule(
        context: Context,
        shiftStart: Instant,
        thresholdMinutes: Int,
        now: Instant = Instant.now(),
    ): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        val fireAt = shiftStart.plusSeconds(thresholdMinutes * 60L)
        if (!fireAt.isAfter(now)) return false

        val triggerAtMillis = System.currentTimeMillis() + (fireAt.toEpochMilli() - now.toEpochMilli())
        // Non-null with UPDATE_CURRENT; only FLAG_NO_CREATE can return null, and that
        // is the cancel path.
        val pendingIntent = pendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return false

        // canScheduleExactAlarms exists from API 31; below that an exact alarm needs
        // no permission at all.
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        // A SecurityException is still possible if the permission is revoked between
        // the check and the call, so the inexact path is also the recovery path.
        return runCatching {
            if (canBeExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
            true
        }.getOrElse {
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
                true
            }.getOrDefault(false)
        }
    }

    /** Cancelled alongside the reminder work, so clock-out silences this too. */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        // NO_CREATE so cancelling when nothing is pending does not mint an intent.
        val pendingIntent = pendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun pendingIntent(context: Context, extraFlags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, OvertimeThresholdAlarmReceiver::class.java).setAction(ACTION),
        // Immutable, like every other PendingIntent in the app: nothing downstream
        // needs to fill in extras, and a mutable one is a handle for another app.
        extraFlags or PendingIntent.FLAG_IMMUTABLE,
    )
}

/**
 * Fires the at-threshold notification.
 *
 * Reloads the shift rather than carrying it in the intent, so an alarm that
 * outlives its shift — clocked out on another device, reminders switched off —
 * finds nothing to announce and stays quiet. That check is
 * [OvertimeReminderSupport.loadContext] returning null.
 */
class OvertimeThresholdAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        // runCatching around the whole body: this is a root launch in a receiver
        // scope, and an unhandled throw here would reach the thread's default
        // handler and kill the process, exactly as it could in
        // HeadlessClockOutReceiver before that was fixed.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching {
                    val deps = AppEntryPoints.background(context.applicationContext)
                    val loaded = OvertimeReminderSupport.loadContext(
                        deps.currentUserProvider(),
                        deps.shiftsRepository(),
                        deps.settingsRepository(),
                    ) ?: return@runCatching
                    ActiveShiftNotificationManager(context.applicationContext)
                        .showOvertimeAtThreshold(loaded.shift)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
