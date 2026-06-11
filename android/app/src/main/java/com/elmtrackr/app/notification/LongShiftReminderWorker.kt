package com.elmtrackr.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.LOCAL_USER_ID
import kotlinx.coroutines.flow.firstOrNull

/**
 * Fires once after a delay equal to the user's daily OT threshold.
 * If the user is still clocked in at that point, shows a reminder notification.
 * Scheduled by ElmTrackrApp when clock-in is detected; cancelled on clock-out.
 */
class LongShiftReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "long_shift_reminder"
        const val FALLBACK_THRESHOLD_MINUTES = 480L
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as ElmTrackrApp
        val activeShift = app.shiftsRepository
            .observeActiveShift(LOCAL_USER_ID)
            .firstOrNull() ?: return Result.success()

        val settings = app.settingsRepository.getSettings(LOCAL_USER_ID)
        val thresholdMinutes = settings?.dailyOvertimeThresholdMinutes?.toLong()
            ?: FALLBACK_THRESHOLD_MINUTES

        if (ActiveShiftNotificationManager.isShiftOverThreshold(activeShift.startTime, thresholdMinutes)) {
            ActiveShiftNotificationManager(applicationContext).showLongShiftReminder(activeShift)
        }

        return Result.success()
    }
}
