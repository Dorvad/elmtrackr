package com.elmtrackr.app.startup

import android.app.Application
import com.elmtrackr.app.data.auth.SupabaseClientProvider
import com.elmtrackr.app.data.local.ElmTrackrDatabase
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.data.receipts.RefundReceiptPhotoCleanupWorker
import com.elmtrackr.app.data.sync.SyncScheduler
import com.elmtrackr.app.di.ApplicationScope
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.notification.NotificationChannels
import com.elmtrackr.app.security.AppLockController
import com.elmtrackr.app.sideeffects.ActiveShiftSideEffectsCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppStartupCoordinator @Inject constructor(
    private val appPreferences: AppPreferencesRepository,
    private val database: ElmTrackrDatabase,
    private val syncScheduler: SyncScheduler,
    private val activeShiftSideEffects: ActiveShiftSideEffectsCoordinator,
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
            database.shiftDao()
        }
        NotificationChannels.createAll(application)
        RefundReceiptPhotoCleanupWorker.schedule(application)
        if (SupabaseClientProvider.isConfigured()) {
            syncScheduler.schedulePeriodic()
        }
        activeShiftSideEffects.start()
        applicationScope.launch {
            currentUserProvider.userId.collect { userId ->
                if (userId == null) {
                    activeShiftSideEffects.publishSignedOutWearSnapshot()
                }
            }
        }
    }
}
