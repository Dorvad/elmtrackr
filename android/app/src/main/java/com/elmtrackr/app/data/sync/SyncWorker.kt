package com.elmtrackr.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elmtrackr.app.domain.CurrentUserProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val syncScheduler: SyncScheduler,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userId = currentUserProvider.currentUserId() ?: return Result.success()
        return when (val result = syncRepository.syncAll(userId)) {
            is SyncResult.Success, SyncResult.NotConfigured -> {
                if (syncRepository.hasPendingWork(userId)) {
                    syncScheduler.schedule()
                }
                Result.success()
            }
            is SyncResult.Error -> Result.retry()
        }
    }
}
