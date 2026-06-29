package com.elmtrackr.app.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.elmtrackr.app.MainActivity
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.Shift
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

    fun showActiveShiftNotification(shift: Shift) {
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
            .setContentTitle(context.getString(R.string.notif_active_shift_title))
            .setContentText(context.getString(R.string.notif_active_shift_text, formatStartTime(shift.startTime)))
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setWhen(shift.startTime.toEpochMilli())
            .setUsesChronometer(true)   // counts up by default — no setChronometerCountsDown needed
            .setShowWhen(true)
            .addAction(0, context.getString(R.string.notif_clock_out_action), clockOutPendingIntent)
            .build()

        @Suppress("MissingPermission")
        notifManager.notify(NOTIFICATION_ID_ACTIVE, notification)
    }

    fun showOvertimePreWarning(shift: Shift) {
        showReminderNotification(
            titleRes = R.string.notif_overtime_pre_warning_title,
            textRes = R.string.notif_overtime_pre_warning_text,
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

    fun showOvertimeHourlyReminder(shift: Shift, hoursInOvertime: Long) {
        showReminderNotification(
            titleRes = R.string.notif_overtime_hourly_title,
            textRes = R.string.notif_overtime_hourly_text,
            shift = shift,
            textArg = hoursInOvertime,
        )
    }

    private fun showReminderNotification(
        titleRes: Int,
        textRes: Int,
        shift: Shift,
        textArg: Any? = formatStartTime(shift.startTime),
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
            is Long -> context.getString(textRes, textArg)
            is String -> context.getString(textRes, textArg)
            else -> context.getString(textRes, textArg)
        }

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(titleRes))
            .setContentText(contentText)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.notif_clock_out_action), clockOutPendingIntent)
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

    private fun formatStartTime(startTime: Instant): String =
        startTime.atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm"))
}
