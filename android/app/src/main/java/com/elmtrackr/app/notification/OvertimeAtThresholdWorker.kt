package com.elmtrackr.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elmtrackr.app.ElmTrackrApp

/**
 * Fires when the user's daily overtime threshold is reached.
 * Starts the hourly overtime reminder chain.
 */
class OvertimeAtThresholdWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ElmTrackrApp
        val contextData = OvertimeReminderSupport.loadContext(app) ?: return Result.success()
        val shift = contextData.shift
        val threshold = contextData.thresholdMinutes.toLong()

        if (!ActiveShiftNotificationManager.isShiftOverThreshold(shift.startTime, threshold)) {
            return Result.success()
        }

        ActiveShiftNotificationManager(applicationContext).showOvertimeAtThreshold(shift)
        OvertimeReminderScheduler.scheduleHourly(
            applicationContext,
            shift,
            contextData.thresholdMinutes,
        )
        return Result.success()
    }
}
