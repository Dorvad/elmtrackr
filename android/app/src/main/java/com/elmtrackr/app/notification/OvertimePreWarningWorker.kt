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

/**
 * Fires 30 minutes before the user's daily overtime threshold.
 */
@HiltWorker
class OvertimePreWarningWorker @AssistedInject constructor(
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

        if (ActiveShiftNotificationManager.isShiftOverThreshold(shift.startTime, threshold)) {
            return Result.success()
        }

        ActiveShiftNotificationManager(applicationContext).showOvertimePreWarning(shift)
        return Result.success()
    }
}
