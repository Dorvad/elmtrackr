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
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

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
        val active = shiftsRepository.observeActiveShift(userId).first()
        if (active == null) {
            WidgetTimerScheduler.cancel(applicationContext)
            return Result.success()
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayShifts = shiftsRepository
            .observeShiftsByMonth(userId, today.year, today.monthValue)
            .first()
        val lastCompleted = shiftsRepository
            .observeRecentCompletedShifts(userId, limit = 1)
            .first()
            .firstOrNull()
        val settings = settingsRepository.getSettings(userId)
        ElmTrackrWidgetUpdater.update(
            applicationContext,
            WidgetContext(
                activeShift = active,
                lastCompletedShift = lastCompleted,
                todayShifts = todayShifts,
                settings = settings,
            ),
        )
        WidgetTimerScheduler.schedule(applicationContext)
        return Result.success()
    }
}
