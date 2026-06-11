package com.elmtrackr.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val CHANNEL_ACTIVE_SHIFT = "active_shift"
    const val CHANNEL_REMINDERS = "reminders"

    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ACTIVE_SHIFT,
                "Active Shift",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shown while you are clocked in"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Long-shift and other reminders"
            },
        )
    }
}
