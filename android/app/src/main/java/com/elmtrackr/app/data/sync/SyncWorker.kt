package com.elmtrackr.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elmtrackr.app.ElmTrackrApp

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ElmTrackrApp ?: return Result.failure()
        val userId = app.currentUserProvider.currentUserId() ?: return Result.success()
        return when (val result = app.syncRepository.syncAll(userId)) {
            is SyncResult.Success, SyncResult.NotConfigured -> {
                if (app.syncRepository.hasPendingWork(userId)) {
                    app.syncScheduler.schedule()
                }
                Result.success()
            }
            is SyncResult.Error -> Result.retry()
        }
    }
}
