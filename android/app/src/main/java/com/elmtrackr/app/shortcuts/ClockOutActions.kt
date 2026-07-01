package com.elmtrackr.app.shortcuts

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.elmtrackr.app.MainActivity
import com.elmtrackr.app.R
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import com.elmtrackr.app.domain.compensation.ShiftCompensationHelper
import com.elmtrackr.app.notification.ActiveShiftNotificationManager
import com.elmtrackr.app.notification.NotificationChannels
import com.elmtrackr.app.security.AppLockActionGuard
import com.elmtrackr.app.widget.ElmTrackrWidgetUpdater
import kotlinx.coroutines.flow.first

object ClockOutActions {

    enum class Result {
        CLOCKED_OUT,
        NO_ACTIVE_SHIFT,
    }

    suspend fun clockOutActiveShift(context: Context): Result {
        if (AppLockActionGuard.blockIfLocked(context)) return Result.NO_ACTIVE_SHIFT
        val deps = AppEntryPoints.background(context)
        val userId = deps.currentUserProvider().currentUserId() ?: return Result.NO_ACTIVE_SHIFT
        val activeShift = deps.shiftsRepository().observeActiveShift(userId).first()
            ?: return Result.NO_ACTIVE_SHIFT

        val settings = deps.settingsRepository().getSettings(userId)
        if (settings != null) {
            val profiles = deps.compensationProfilesRepository().getProfiles(userId)
            val snapshot = ShiftCompensationHelper.buildClockOutSnapshot(activeShift, settings, profiles)
            deps.shiftsRepository().clockOut(activeShift.id, compensationSnapshot = snapshot)
        } else {
            deps.shiftsRepository().clockOut(activeShift.id)
        }

        ActiveShiftNotificationManager(context.applicationContext).cancelActiveShiftNotification()
        ElmTrackrWidgetUpdater.update(context.applicationContext, null)
        com.elmtrackr.app.wear.WearSyncPublisher.refresh(context.applicationContext)
        deps.dynamicShortcutsRefresher().refresh()
        return Result.CLOCKED_OUT
    }

    fun showShortcutFeedback(context: Context, result: Result) {
        val (title, message) = when (result) {
            Result.CLOCKED_OUT -> "Clocked out" to "Your shift has been ended."
            Result.NO_ACTIVE_SHIFT -> "No active shift" to "There is no active shift to clock out from."
        }
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

    private const val NOTIFICATION_ID = 1204
}
