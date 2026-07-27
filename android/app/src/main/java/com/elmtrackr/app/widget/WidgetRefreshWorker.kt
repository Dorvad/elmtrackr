package com.elmtrackr.app.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val currentUserProvider: CurrentUserProvider,
    private val shiftsRepository: ShiftsRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userId = currentUserProvider.currentUserId() ?: return Result.success()
        val widgetContext = WidgetContextLoader.load(shiftsRepository, settingsRepository, userId)
        val activeShift = widgetContext.activeShift
        if (activeShift == null) {
            WidgetTimerScheduler.cancel(applicationContext)
            return Result.success()
        }

        // update() re-schedules the chain aligned to the next minute rollover.
        ElmTrackrWidgetUpdater.update(applicationContext, widgetContext)
        return Result.success()
    }
}
