package com.elmtrackr.app.data.receipts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RefundReceiptPhotoCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        PhotoFileManager(applicationContext).cleanupOrphans()
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "refund-receipt-photo-cleanup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefundReceiptPhotoCleanupWorker>(24, TimeUnit.HOURS)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
