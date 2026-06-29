package com.elmtrackr.app.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import java.time.Instant
import java.util.concurrent.TimeUnit

object OvertimeReminderScheduler {
    const val PRE_WARNING_WORK = "overtime_pre_warning"
    const val AT_THRESHOLD_WORK = "overtime_at_threshold"
    const val HOURLY_WORK = "overtime_hourly"
    private const val LEGACY_LONG_SHIFT_WORK = "long_shift_reminder"

    fun scheduleForActiveShift(context: Context, shift: Shift, settings: UserSettings?) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_LONG_SHIFT_WORK)

        if (settings?.featuresOvertimeReminders != true) {
            cancelAll(context)
            return
        }

        val threshold = settings.dailyOvertimeThresholdMinutes
            .takeIf { it > 0 }
            ?: OvertimeReminderPolicy.FALLBACK_THRESHOLD_MINUTES.toInt()
        val now = Instant.now()

        val preWarningDelay = OvertimeReminderPolicy.preWarningDelayMinutes(threshold, shift.startTime, now)
        if (preWarningDelay >= 0) {
            enqueuePreWarning(context, preWarningDelay)
        } else {
            workManager.cancelUniqueWork(PRE_WARNING_WORK)
        }

        enqueueAtThreshold(
            context,
            OvertimeReminderPolicy.atThresholdDelayMinutes(threshold, shift.startTime, now),
        )

        if (ActiveShiftNotificationManager.isShiftOverThreshold(shift.startTime, threshold.toLong())) {
            scheduleHourly(context, shift, threshold, now)
        } else {
            workManager.cancelUniqueWork(HOURLY_WORK)
        }
    }

    fun scheduleHourly(
        context: Context,
        shift: Shift,
        thresholdMinutes: Int,
        now: Instant = Instant.now(),
    ) {
        enqueueHourly(
            context,
            OvertimeReminderPolicy.nextHourlyDelayMinutes(thresholdMinutes, shift.startTime, now),
        )
    }

    fun cancelAll(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PRE_WARNING_WORK)
        workManager.cancelUniqueWork(AT_THRESHOLD_WORK)
        workManager.cancelUniqueWork(HOURLY_WORK)
        workManager.cancelUniqueWork(LEGACY_LONG_SHIFT_WORK)
    }

    private fun enqueuePreWarning(context: Context, delayMinutes: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            PRE_WARNING_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<OvertimePreWarningWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .addTag(PRE_WARNING_WORK)
                .build(),
        )
    }

    private fun enqueueAtThreshold(context: Context, delayMinutes: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            AT_THRESHOLD_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<OvertimeAtThresholdWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .addTag(AT_THRESHOLD_WORK)
                .build(),
        )
    }

    private fun enqueueHourly(context: Context, delayMinutes: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            HOURLY_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<OvertimeHourlyReminderWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .addTag(HOURLY_WORK)
                .build(),
        )
    }
}
