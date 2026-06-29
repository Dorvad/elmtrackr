package com.elmtrackr.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elmtrackr.app.ElmTrackrApp
import java.time.Instant

/**
 * Fires every hour after overtime starts while the user remains clocked in.
 * Re-schedules itself until clock-out or the feature is disabled.
 */
class OvertimeHourlyReminderWorker(
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

        val hoursInOvertime = OvertimeReminderPolicy.overtimeHoursElapsed(
            contextData.thresholdMinutes,
            shift.startTime,
            Instant.now(),
        ).coerceAtLeast(1)
        ActiveShiftNotificationManager(applicationContext).showOvertimeHourlyReminder(shift, hoursInOvertime)
        OvertimeReminderScheduler.scheduleHourly(
            applicationContext,
            shift,
            contextData.thresholdMinutes,
        )
        return Result.success()
    }
}
