package com.elmtrackr.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elmtrackr.app.ElmTrackrApp
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ElmTrackrApp
        val userId = app.currentUserProvider.currentUserId() ?: return Result.success()
        val active = app.shiftsRepository.observeActiveShift(userId).first()
        if (active == null) {
            WidgetTimerScheduler.cancel(applicationContext)
            return Result.success()
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayShifts = app.shiftsRepository
            .observeShiftsByMonth(userId, today.year, today.monthValue)
            .first()
        val lastCompleted = app.shiftsRepository
            .observeRecentCompletedShifts(userId, limit = 1)
            .first()
            .firstOrNull()
        val settings = app.settingsRepository.getSettings(userId)
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
