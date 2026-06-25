package com.elmtrackr.app.shortcuts

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.MainActivity
import com.elmtrackr.app.R
import com.elmtrackr.app.notification.ActiveShiftNotificationManager
import com.elmtrackr.app.notification.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HeadlessClockOutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_HEADLESS_CLOCK_OUT) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as ElmTrackrApp
                val userId = app.currentUserProvider.currentUserId()
                val activeShift = userId?.let { app.shiftsRepository.observeActiveShift(it).first() }
                if (activeShift != null) {
                    app.shiftsRepository.clockOut(activeShift.id)
                    ActiveShiftNotificationManager(context.applicationContext).cancelActiveShiftNotification()
                    showShortcutNotification(context, "Clocked out", "Your shift has been ended.")
                    app.refreshDynamicShortcuts()
                } else {
                    showShortcutNotification(context, "No active shift", "There is no active shift to clock out from.")
                    app.refreshDynamicShortcuts()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showShortcutNotification(context: Context, title: String, message: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val tapIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_HEADLESS_CLOCK_OUT = "com.elmtrackr.ACTION_HEADLESS_CLOCK_OUT"
        private const val NOTIFICATION_ID = 1204
    }
}
