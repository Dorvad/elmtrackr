package com.elmtrackr.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.elmtrackr.app.R
import com.elmtrackr.app.language.withAppLocale

object NotificationChannels {
    const val CHANNEL_ACTIVE_SHIFT = "active_shift"
    const val CHANNEL_REMINDERS = "reminders"

    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val localized = context.withAppLocale()

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ACTIVE_SHIFT,
                localized.getString(R.string.notif_channel_active_shift),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = localized.getString(R.string.notif_channel_active_shift_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                localized.getString(R.string.notif_channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = localized.getString(R.string.notif_channel_reminders_desc)
            },
        )
    }
}
