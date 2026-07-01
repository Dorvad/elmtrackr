package com.elmtrackr.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant

/**
 * Fires every hour after overtime starts while the user remains clocked in.
 * Re-schedules itself until clock-out or the feature is disabled.
 */
@HiltWorker
class OvertimeHourlyReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val currentUserProvider: CurrentUserProvider,
    private val shiftsRepository: ShiftsRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val contextData = OvertimeReminderSupport.loadContext(
            currentUserProvider,
            shiftsRepository,
            settingsRepository,
        ) ?: return Result.success()
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
