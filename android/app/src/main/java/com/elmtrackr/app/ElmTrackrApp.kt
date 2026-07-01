package com.elmtrackr.app

import android.app.Application
import com.elmtrackr.app.data.auth.SupabaseClientProvider
import com.elmtrackr.app.data.local.ElmTrackrDatabase
import com.elmtrackr.app.data.local.LegacyDataAdopter
import com.elmtrackr.app.data.local.LocalUserDataCleaner
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.data.remote.SupabaseRefundReceiptStorage
import com.elmtrackr.app.data.remote.SupabaseCompensationProfilesDataSource
import com.elmtrackr.app.data.remote.SupabaseRefundClaimsDataSource
import com.elmtrackr.app.data.remote.SupabaseShiftsDataSource
import com.elmtrackr.app.data.remote.SupabaseTasksDataSource
import com.elmtrackr.app.data.remote.SupabaseUserSettingsDataSource
import com.elmtrackr.app.data.receipts.RefundReceiptPhotoCleanupWorker
import com.elmtrackr.app.data.sync.PreferenceSyncCursorStore
import com.elmtrackr.app.data.sync.SyncRepository
import com.elmtrackr.app.data.sync.SyncRepositoryImpl
import com.elmtrackr.app.data.sync.SyncScheduler
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.data.repository.LocalCompensationProfilesRepository
import com.elmtrackr.app.data.repository.LocalRefundsRepository
import com.elmtrackr.app.data.repository.LocalReportsRepository
import com.elmtrackr.app.data.repository.LocalSettingsRepository
import com.elmtrackr.app.data.repository.LocalShiftsRepository
import com.elmtrackr.app.data.repository.LocalTasksRepository
import com.elmtrackr.app.data.repository.SupabaseAuthRepository
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.PreferencesCurrentUserProvider
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.notification.NotificationChannels
import com.elmtrackr.app.security.AppLockController
import com.elmtrackr.app.sideeffects.ActiveShiftSideEffectsCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ElmTrackrApp : Application() {

    val database: ElmTrackrDatabase by lazy { ElmTrackrDatabase.getInstance(this) }

    val appPreferences: AppPreferencesRepository by lazy { AppPreferencesRepository(this) }

    val currentUserProvider: CurrentUserProvider by lazy {
        PreferencesCurrentUserProvider(appPreferences)
    }

    private val legacyDataAdopter: LegacyDataAdopter by lazy {
        LegacyDataAdopter(database, appPreferences)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tasksRepository: LocalTasksRepository by lazy {
        LocalTasksRepository(database.taskDao(), syncTrigger)
    }

    val compensationProfilesRepository: LocalCompensationProfilesRepository by lazy {
        LocalCompensationProfilesRepository(
            database.compensationProfileDao(),
            settingsRepository,
            syncTrigger,
        )
    }

    val syncScheduler: SyncScheduler by lazy { SyncScheduler(this) }

    val syncTrigger: SyncTrigger by lazy {
        if (SupabaseClientProvider.isConfigured()) syncScheduler else com.elmtrackr.app.data.sync.NoOpSyncTrigger
    }

    val syncCursorStore by lazy { PreferenceSyncCursorStore(this) }

    val remoteTasksDataSource by lazy {
        SupabaseClientProvider.get()?.let { SupabaseTasksDataSource(it) }
    }

    val remoteShiftsDataSource by lazy {
        SupabaseClientProvider.get()?.let { SupabaseShiftsDataSource(it) }
    }

    val remoteRefundClaimsDataSource by lazy {
        SupabaseClientProvider.get()?.let { SupabaseRefundClaimsDataSource(it) }
    }

    val remoteUserSettingsDataSource by lazy {
        SupabaseClientProvider.get()?.let { SupabaseUserSettingsDataSource(it) }
    }

    val remoteCompensationProfilesDataSource by lazy {
        SupabaseClientProvider.get()?.let { SupabaseCompensationProfilesDataSource(it) }
    }

    val syncRepository: SyncRepository by lazy {
        SyncRepositoryImpl(
            shiftDao = database.shiftDao(),
            refundClaimDao = database.refundClaimDao(),
            settingsDao = database.settingsDao(),
            compensationProfileDao = database.compensationProfileDao(),
            taskDao = database.taskDao(),
            syncCursorStore = syncCursorStore,
            remoteTasks = remoteTasksDataSource,
            remoteShifts = remoteShiftsDataSource,
            remoteRefundClaims = remoteRefundClaimsDataSource,
            remoteSettings = remoteUserSettingsDataSource,
            remoteCompensationProfiles = remoteCompensationProfilesDataSource,
        )
    }

    val shiftsRepository: LocalShiftsRepository by lazy {
        LocalShiftsRepository(database.shiftDao(), syncTrigger)
    }

    val settingsRepository: LocalSettingsRepository by lazy {
        LocalSettingsRepository(database.settingsDao(), syncTrigger)
    }

    val reportsRepository: LocalReportsRepository by lazy {
        LocalReportsRepository(database.shiftDao(), database.settingsDao())
    }

    val refundsRepository: LocalRefundsRepository by lazy {
        LocalRefundsRepository(database.refundClaimDao(), syncTrigger)
    }

    val refundReceiptStorage by lazy {
        SupabaseClientProvider.get()?.let { SupabaseRefundReceiptStorage(it) }
    }

    val localUserDataCleaner: LocalUserDataCleaner by lazy {
        LocalUserDataCleaner(
            database.shiftDao(),
            database.settingsDao(),
            database.profileDao(),
            database.refundClaimDao(),
            database.compensationProfileDao(),
            database.taskDao(),
        )
    }

    private val activeShiftSideEffects: ActiveShiftSideEffectsCoordinator by lazy {
        ActiveShiftSideEffectsCoordinator(
            app = this,
            scope = applicationScope,
            shiftsRepository = shiftsRepository,
            settingsRepository = settingsRepository,
            observeUserId = currentUserProvider.userId,
        )
    }

    // SupabaseAuthRepository self-checks BuildConfig: returns NotConfigured state
    // gracefully when SUPABASE_URL / SUPABASE_ANON_KEY are absent (e.g. CI).
    val authRepository: AuthRepository by lazy {
        SupabaseAuthRepository(database.profileDao(), appPreferences, localUserDataCleaner) { userId ->
            legacyDataAdopter.adoptFor(userId)
            applicationScope.launch {
                runCatching {
                    val settings = settingsRepository.getSettings(userId)
                    if (settings?.onboardingCompleted == true) {
                        compensationProfilesRepository.ensureMigrated(userId)
                    }
                }
            }
            if (SupabaseClientProvider.isConfigured()) {
                syncTrigger.schedule()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(Dispatchers.IO) {
            val prefs = appPreferences.currentPreferences()
            AppLockController.configure(
                enabled = prefs.appLockEnabled,
                initiallyUnlocked = !prefs.appLockEnabled,
            )
            database
        }
        NotificationChannels.createAll(this)
        RefundReceiptPhotoCleanupWorker.schedule(this)
        if (SupabaseClientProvider.isConfigured()) {
            syncScheduler.schedulePeriodic()
        }
        activeShiftSideEffects.start()
        startWearSignOutObserver()
    }

    private fun startWearSignOutObserver() {
        applicationScope.launch {
            currentUserProvider.userId.collect { userId ->
                if (userId == null) {
                    activeShiftSideEffects.publishSignedOutWearSnapshot()
                }
            }
        }
    }

    fun refreshDynamicShortcuts() {
        applicationScope.launch {
            val userId = currentUserProvider.currentUserId()
            val activeShift = userId?.let { shiftsRepository.observeActiveShift(it).first() }
            activeShiftSideEffects.refreshShortcuts(activeShift != null)
        }
    }
}
