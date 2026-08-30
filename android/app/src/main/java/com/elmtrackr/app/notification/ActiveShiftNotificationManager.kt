package com.elmtrackr.app.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.elmtrackr.app.MainActivity
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.language.withAppLocale
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ActiveShiftNotificationManager(private val context: Context) {

    companion object {
        const val NOTIFICATION_ID_ACTIVE = 1001
        const val NOTIFICATION_ID_REMINDER = 1002
        const val ACTION_CLOCK_OUT = "com.elmtrackr.app.ACTION_CLOCK_OUT"
        const val EXTRA_SHIFT_ID = "shift_id"

        fun isShiftOverThreshold(startTime: Instant, thresholdMinutes: Long): Boolean =
            Duration.between(startTime, Instant.now()).toMinutes() >= thresholdMinutes
    }

    private val notifManager = NotificationManagerCompat.from(context)

    // Strings resolve in the in-app language even on Android 12 and below,
    // where background contexts otherwise use the system locale. A getter, not
    // a stored context: this manager lives inside an application-scoped
    // singleton, so a context captured at construction would keep the language
    // the process started in — the running-shift notification stayed in the
    // previous language after a switch until the app was killed.
    private val localizedContext: Context get() = context.withAppLocale()

    fun showActiveShiftNotification(shift: Shift, zone: ZoneId = ZoneId.systemDefault()) {
        if (!notifManager.areNotificationsEnabled()) return

        val tapIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_ACTIVE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val clockOutPendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID_ACTIVE,
            Intent(context, ClockOutReceiver::class.java).apply {
                action = ACTION_CLOCK_OUT
                putExtra(EXTRA_SHIFT_ID, shift.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_ACTIVE_SHIFT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(localizedContext.getString(R.string.notif_active_shift_title))
            .setContentText(localizedContext.getString(R.string.notif_active_shift_text, formatStartTime(shift.startTime, zone)))
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setWhen(shift.startTime.toEpochMilli())
            .setUsesChronometer(true)   // counts up by default — no setChronometerCountsDown needed
            .setShowWhen(true)
            .addAction(0, localizedContext.getString(R.string.notif_clock_out_action), clockOutPendingIntent)
            .build()

        @Suppress("MissingPermission")
        notifManager.notify(NOTIFICATION_ID_ACTIVE, notification)
    }

    fun showOvertimePreWarning(shift: Shift, minutesBefore: Int = 30) {
        showReminderNotification(
            titleRes = R.string.notif_overtime_pre_warning_title_min,
            textRes = R.string.notif_overtime_pre_warning_text,
            shift = shift,
            titleArg = minutesBefore,
        )
    }

    fun showScheduledTimeReminder(shift: Shift) {
        showReminderNotification(
            titleRes = R.string.notif_scheduled_reminder_title,
            textRes = R.string.notif_scheduled_reminder_text,
            shift = shift,
        )
    }

    fun showOvertimeAtThreshold(shift: Shift) {
        showReminderNotification(
            titleRes = R.string.notif_overtime_at_threshold_title,
            textRes = R.string.notif_overtime_at_threshold_text,
            shift = shift,
        )
    }

    fun showOvertimeHourlyReminder(shift: Shift, durationLabel: String) {
        showReminderNotification(
            titleRes = R.string.notif_overtime_hourly_title,
            textRes = R.string.notif_overtime_hourly_text,
            shift = shift,
            textArg = durationLabel,
        )
    }

    private fun showReminderNotification(
        titleRes: Int,
        textRes: Int,
        shift: Shift,
        textArg: Any? = formatStartTime(shift.startTime, ZoneId.systemDefault()),
        titleArg: Any? = null,
    ) {
        if (!notifManager.areNotificationsEnabled()) return

        val tapIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_REMINDER,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val clockOutPendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID_REMINDER,
            Intent(context, ClockOutReceiver::class.java).apply {
                action = ACTION_CLOCK_OUT
                putExtra(EXTRA_SHIFT_ID, shift.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val contentText = when (textArg) {
            is Long -> localizedContext.getString(textRes, textArg)
            is String -> localizedContext.getString(textRes, textArg)
            else -> localizedContext.getString(textRes, textArg)
        }

        val contentTitle = when (titleArg) {
            null -> localizedContext.getString(titleRes)
            else -> localizedContext.getString(titleRes, titleArg)
        }

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .addAction(0, localizedContext.getString(R.string.notif_clock_out_action), clockOutPendingIntent)
            .build()

        @Suppress("MissingPermission")
        notifManager.notify(NOTIFICATION_ID_REMINDER, notification)
    }

    fun cancelActiveShiftNotification() {
        notifManager.cancel(NOTIFICATION_ID_ACTIVE)
    }

    fun cancelReminderNotification() {
        notifManager.cancel(NOTIFICATION_ID_REMINDER)
    }

    private fun formatStartTime(startTime: Instant, zone: ZoneId): String =
        startTime.atZone(zone)
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm"))
}
