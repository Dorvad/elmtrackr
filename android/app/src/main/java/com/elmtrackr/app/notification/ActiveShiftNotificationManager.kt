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
            .setContentTitle("Clocked in")
            .setContentText("Since ${formatStartTime(shift.startTime)}")
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .addAction(0, "Clock Out", clockOutPendingIntent)
            .build()

        @Suppress("MissingPermission")
        notifManager.notify(NOTIFICATION_ID_ACTIVE, notification)
    }

    fun showLongShiftReminder(shift: Shift) {
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

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("You're still clocked in")
            .setContentText("Since ${formatStartTime(shift.startTime)}. Tap to review.")
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .addAction(0, "Clock Out", clockOutPendingIntent)
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
