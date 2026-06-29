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
import com.elmtrackr.app.data.remote.SupabaseRefundReceiptStorage
import com.elmtrackr.app.data.receipts.RefundReceiptPhotoCleanupWorker
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
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.notification.ActiveShiftNotificationManager
import com.elmtrackr.app.notification.OvertimeReminderScheduler
import com.elmtrackr.app.notification.NotificationChannels
import com.elmtrackr.app.shortcuts.HeadlessTrampolineActivity
import com.elmtrackr.app.widget.ElmTrackrWidgetUpdater
import com.elmtrackr.app.wear.WearSyncPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
        LocalTasksRepository(database.taskDao())
    }

    val compensationProfilesRepository: LocalCompensationProfilesRepository by lazy {
        LocalCompensationProfilesRepository(
            database.compensationProfileDao(),
            settingsRepository,
        )
    }

    val shiftsRepository: LocalShiftsRepository by lazy {
        LocalShiftsRepository(database.shiftDao())
    }

    val settingsRepository: LocalSettingsRepository by lazy {
        LocalSettingsRepository(database.settingsDao())
    }

    val reportsRepository: LocalReportsRepository by lazy {
        LocalReportsRepository(database.shiftDao(), database.settingsDao())
    }

    val refundsRepository: LocalRefundsRepository by lazy {
        LocalRefundsRepository(database.refundClaimDao())
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
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        RefundReceiptPhotoCleanupWorker.schedule(this)
        startActiveShiftObserver()
        startWearSignOutObserver()
    }

    private fun startWearSignOutObserver() {
        applicationScope.launch {
            currentUserProvider.userId.collect { userId ->
                if (userId == null) {
                    WearSyncPublisher.publishSnapshot(
                        this@ElmTrackrApp,
                        com.elmtrackr.wear.sync.WearShiftSnapshot.signedOut(),
                    )
                }
            }
        }
    }

    private fun startActiveShiftObserver() {
        val notifManager = ActiveShiftNotificationManager(this)
        applicationScope.launch {
            currentUserProvider.userId
                .filterNotNull()
                .flatMapLatest { userId: String ->
                    combine(
                        shiftsRepository.observeActiveShift(userId),
                        settingsRepository.observeSettings(userId),
                    ) { active: Shift?, settings ->
                        Triple(userId, active, settings)
                    }
                }
                .catch { /* never crash the app due to notification failures */ }
                .collect { payload: Triple<String, Shift?, UserSettings?> ->
                    val userId = payload.first
                    val shift = payload.second
                    val settings = payload.third
                    if (shift != null) {
                        notifManager.showActiveShiftNotification(shift)
                        OvertimeReminderScheduler.scheduleForActiveShift(this@ElmTrackrApp, shift, settings)
                        updateDynamicShortcuts(clockedIn = true)
                    } else {
                        notifManager.cancelActiveShiftNotification()
                        notifManager.cancelReminderNotification()
                        OvertimeReminderScheduler.cancelAll(this@ElmTrackrApp)
                        updateDynamicShortcuts(clockedIn = false)
                    }
                    val lastCompleted = shiftsRepository
                        .observeRecentCompletedShifts(userId, limit = 1)
                        .first()
                        .firstOrNull()
                    val zone = java.time.ZoneId.systemDefault()
                    val today = java.time.LocalDate.now(zone)
                    val todayShifts = shiftsRepository
                        .observeShiftsByMonth(userId, today.year, today.monthValue)
                        .first()
                    ElmTrackrWidgetUpdater.update(
                        this@ElmTrackrApp,
                        shift,
                        lastCompleted,
                        todayShifts,
                        settings,
                    )
                    WearSyncPublisher.publishFromWidgetContext(
                        this@ElmTrackrApp,
                        com.elmtrackr.app.widget.WidgetContext(
                            activeShift = shift,
                            lastCompletedShift = lastCompleted,
                            todayShifts = todayShifts,
                            settings = settings,
                        ),
                    )
                }
        }
    }

    fun refreshDynamicShortcuts() {
        applicationScope.launch {
            val userId = currentUserProvider.currentUserId()
            val activeShift = userId?.let { shiftsRepository.observeActiveShift(it).first() }
            updateDynamicShortcuts(clockedIn = activeShift != null)
        }
    }

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
