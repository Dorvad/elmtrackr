package com.elmtrackr.wear.tile

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.wear.tiles.TileService
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elmtrackr.wear.ElmTrackrWearApp
import com.elmtrackr.wear.runCatchingCancellable
import com.elmtrackr.wear.sync.ElmTrackrComplicationBridge
import com.elmtrackr.wear.sync.WearLocalShift
import java.util.concurrent.TimeUnit

class WearTileRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Returning success on a foreign context rather than retrying: a
        // context that is not this app's will not become one on a later run,
        // so a retry would just re-run the same no-op with backoff.
        val app = ElmTrackrWearApp.from(applicationContext) ?: return Result.success()
        app.wearStateRepository.rollLocalDayIfNeeded()
        app.wearStateRepository.refreshFromDataLayer()
        app.wearActionClient.syncPendingWithPhone()
        runCatchingCancellable { ElmTrackrComplicationBridge.requestUpdateAll(applicationContext) }
        // The complication bridge doesn't cover the tile — without this the
        // tile's count-up freezes at whatever it showed when last rendered.
        runCatchingCancellable {
            TileService.getUpdater(applicationContext).requestUpdate(ElmTrackrTileService::class.java)
        }
        // Re-arming is the one step that must not be skipped: drop it and the
        // count-up stops for the rest of the shift. The same loop also retries
        // queued wrist punches after a failed drain, even when the local face is
        // clocked out and no further phone snapshot is expected.
        val keepRunning = WearLocalShift.shouldKeepBackgroundRefreshRunning(
            app.wearStateRepository.snapshot.value,
            hasPendingReplay = app.wearStateRepository.pendingEvents().isNotEmpty(),
        )
        if (keepRunning) {
            runCatchingCancellable { schedule(applicationContext) }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "elmtrackr_wear_tile_refresh"

        // Self-chaining one-shot: doWork re-schedules while a shift is active.
        // A PeriodicWorkRequest can't do this — WorkManager silently clamps
        // periods below 15 minutes, freezing the tile count-up between runs.
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WearTileRefreshWorker>()
                    .setInitialDelay(60, TimeUnit.SECONDS)
                    .build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
