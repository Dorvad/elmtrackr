package com.elmtrackr.app

import android.app.Application
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.Intent
import android.graphics.drawable.Icon
import com.elmtrackr.app.data.auth.SupabaseClientProvider
import com.elmtrackr.app.data.local.ElmTrackrDatabase
import com.elmtrackr.app.data.local.LegacyDataAdopter
import com.elmtrackr.app.data.local.LocalUserDataCleaner
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.data.remote.RemoteCompensationProfilesDataSource
import com.elmtrackr.app.data.remote.SupabaseProfileDataSource
import com.elmtrackr.app.data.remote.SupabaseRefundsDataSource
import com.elmtrackr.app.data.remote.SupabaseRefundReceiptStorage
import com.elmtrackr.app.data.remote.SupabaseSettingsDataSource
import com.elmtrackr.app.data.remote.SupabaseShiftsDataSource
import com.elmtrackr.app.data.receipts.RefundReceiptPhotoCleanupWorker
import com.elmtrackr.app.data.repository.LocalCompensationProfilesRepository
import com.elmtrackr.app.data.repository.LocalRefundsRepository
import com.elmtrackr.app.data.repository.LocalReportsRepository
import com.elmtrackr.app.data.repository.LocalSettingsRepository
import com.elmtrackr.app.data.repository.LocalShiftsRepository
import com.elmtrackr.app.data.repository.SupabaseAuthRepository
import com.elmtrackr.app.data.repository.SyncRepositoryImpl
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.PreferencesCurrentUserProvider
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.SyncRepository
import com.elmtrackr.app.notification.ActiveShiftNotificationManager
import com.elmtrackr.app.notification.LongShiftReminderWorker
import com.elmtrackr.app.notification.NotificationChannels
import com.elmtrackr.app.shortcuts.HeadlessTrampolineActivity
import com.elmtrackr.app.widget.ElmTrackrWidgetUpdater
import com.elmtrackr.app.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

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

    private val syncScheduler: SyncScheduler by lazy { SyncScheduler(this) }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val syncRepository: SyncRepository by lazy {
        val client = SupabaseClientProvider.get()
        SyncRepositoryImpl(
            shiftDao = database.shiftDao(),
            refundClaimDao = database.refundClaimDao(),
            settingsDao = database.settingsDao(),
            profileDao = database.profileDao(),
            compensationProfileDao = database.compensationProfileDao(),
            remoteShifts = client?.let { SupabaseShiftsDataSource(it) },
            remoteRefunds = client?.let { SupabaseRefundsDataSource(it) },
            remoteSettings = client?.let { SupabaseSettingsDataSource(it) },
            remoteProfile = client?.let { SupabaseProfileDataSource(it) },
            remoteCompensationProfiles = client?.let { RemoteCompensationProfilesDataSource(it) },
        )
    }

    val compensationProfilesRepository: LocalCompensationProfilesRepository by lazy {
        LocalCompensationProfilesRepository(
            database.compensationProfileDao(),
            settingsRepository,
        )
    }

    val shiftsRepository: LocalShiftsRepository by lazy {
        LocalShiftsRepository(database.shiftDao(), syncScheduler)
    }

    val settingsRepository: LocalSettingsRepository by lazy {
        LocalSettingsRepository(database.settingsDao(), syncScheduler)
    }

    val reportsRepository: LocalReportsRepository by lazy {
        LocalReportsRepository(database.shiftDao(), database.settingsDao())
    }

    val refundsRepository: LocalRefundsRepository by lazy {
        LocalRefundsRepository(database.refundClaimDao(), syncScheduler)
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
        )
    }

    // SupabaseAuthRepository self-checks BuildConfig: returns NotConfigured state
    // gracefully when SUPABASE_URL / SUPABASE_ANON_KEY are absent (e.g. CI).
    val authRepository: AuthRepository by lazy {
        SupabaseAuthRepository(database.profileDao(), appPreferences, localUserDataCleaner) { userId ->
            legacyDataAdopter.adoptFor(userId)
            applicationScope.launch {
                val settings = settingsRepository.getSettings(userId)
                if (settings?.onboardingCompleted == true) {
                    compensationProfilesRepository.ensureMigrated(userId)
                }
                syncRepository.syncAll(userId)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        syncScheduler.schedulePeriodic()
        RefundReceiptPhotoCleanupWorker.schedule(this)
        startActiveShiftObserver()
    }

    // â”€â”€ Active-shift notification observer â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun startActiveShiftObserver() {
        val notifManager = ActiveShiftNotificationManager(this)
        applicationScope.launch {
            currentUserProvider.userId
                .filterNotNull()
                .flatMapLatest { userId -> shiftsRepository.observeActiveShift(userId) }
                .catch { /* never crash the app due to notification failures */ }
                .collect { shift ->
                    if (shift != null) {
                        notifManager.showActiveShiftNotification(shift)
                        val userId = currentUserProvider.currentUserId() ?: return@collect
                        val settings = settingsRepository.getSettings(userId)
                        val delayMinutes = settings?.dailyOvertimeThresholdMinutes?.toLong()
                            ?: LongShiftReminderWorker.FALLBACK_THRESHOLD_MINUTES
                        scheduleReminder(delayMinutes)
                        updateDynamicShortcuts(clockedIn = true)
                    } else {
                        notifManager.cancelActiveShiftNotification()
                        cancelReminder()
                        updateDynamicShortcuts(clockedIn = false)
                    }
                    ElmTrackrWidgetUpdater.update(this@ElmTrackrApp, shift)
                }
        }
    }

    private fun scheduleReminder(delayMinutes: Long) {
        WorkManager.getInstance(this).enqueueUniqueWork(
            LongShiftReminderWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<LongShiftReminderWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .addTag(LongShiftReminderWorker.WORK_NAME)
                .build(),
        )
    }

    private fun cancelReminder() {
        WorkManager.getInstance(this).cancelUniqueWork(LongShiftReminderWorker.WORK_NAME)
    }

    fun refreshDynamicShortcuts() {
        applicationScope.launch {
            val userId = currentUserProvider.currentUserId()
            val activeShift = userId?.let { shiftsRepository.observeActiveShift(it).first() }
            updateDynamicShortcuts(clockedIn = activeShift != null)
        }
    }
    // Dynamic app shortcuts.

    private fun updateDynamicShortcuts(clockedIn: Boolean) {
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
        val shortcutId = "shortcut_clock_out"
        if (!clockedIn) {
            shortcutManager.removeDynamicShortcuts(listOf(shortcutId))
            return
        }

        val shortcut = ShortcutInfo.Builder(this, shortcutId)
            .setShortLabel(getString(R.string.shortcut_clock_out_short))
            .setLongLabel(getString(R.string.shortcut_clock_out_long))
            .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(
                Intent(this, HeadlessTrampolineActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                },
            )
            .build()

        shortcutManager.dynamicShortcuts = listOf(shortcut)
    }
}

