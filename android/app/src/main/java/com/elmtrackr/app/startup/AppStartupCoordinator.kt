package com.elmtrackr.app.startup

import android.app.Application
import com.elmtrackr.app.data.auth.SupabaseClientProvider
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.data.receipts.RefundReceiptPhotoCleanupWorker
import com.elmtrackr.app.data.sync.SyncScheduler
import com.elmtrackr.app.di.ApplicationScope
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.notification.NotificationChannels
import com.elmtrackr.app.notification.ProjectBillingReminderWorker
import com.elmtrackr.app.security.AppLockController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppStartupCoordinator @Inject constructor(
    private val appPreferences: AppPreferencesRepository,
    private val syncScheduler: SyncScheduler,
    private val currentUserProvider: CurrentUserProvider,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    fun onCreate(application: Application) {
        applicationScope.launch {
            val prefs = appPreferences.currentPreferences()
            AppLockController.configure(
                enabled = prefs.appLockEnabled,
                initiallyUnlocked = !prefs.appLockEnabled,
            )
        }
        NotificationChannels.createAll(application)
        RefundReceiptPhotoCleanupWorker.schedule(application)
        // Enqueued unconditionally; the worker itself checks the feature flag and
        // the notification permission on each run, and posts nothing when either
        // is off. That keeps the decision in one tested place instead of splitting
        // it between here and the worker.
        ProjectBillingReminderWorker.schedule(application)
        if (SupabaseClientProvider.isConfigured()) {
            syncScheduler.schedulePeriodic()
        }
        applicationScope.launch {
            val sideEffects = AppEntryPoints.background(application)
                .activeShiftSideEffectsCoordinator()
            sideEffects.start()
            var hadSignedInUser = false
            currentUserProvider.userId.collect { userId ->
                if (userId != null) {
                    hadSignedInUser = true
                } else if (hadSignedInUser) {
                    sideEffects.publishSignedOutWearSnapshot()
                    hadSignedInUser = false
                }
            }
        }
    }
}
