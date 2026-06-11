package com.elmtrackr.app

import android.app.Application
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.Intent
import android.graphics.drawable.Icon
import com.elmtrackr.app.data.auth.SupabaseClientProvider
import com.elmtrackr.app.data.local.ElmTrackrDatabase
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.data.remote.SupabaseProfileDataSource
import com.elmtrackr.app.data.remote.SupabaseRefundsDataSource
import com.elmtrackr.app.data.remote.SupabaseSettingsDataSource
import com.elmtrackr.app.data.remote.SupabaseShiftsDataSource
import com.elmtrackr.app.data.repository.LocalRefundsRepository
import com.elmtrackr.app.data.repository.LocalReportsRepository
import com.elmtrackr.app.data.repository.LocalSettingsRepository
import com.elmtrackr.app.data.repository.LocalShiftsRepository
import com.elmtrackr.app.data.repository.SupabaseAuthRepository
import com.elmtrackr.app.data.repository.SyncRepositoryImpl
import com.elmtrackr.app.domain.LOCAL_USER_ID
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.SyncRepository
import com.elmtrackr.app.notification.ActiveShiftNotificationManager
import com.elmtrackr.app.notification.LongShiftReminderWorker
import com.elmtrackr.app.notification.NotificationChannels
import com.elmtrackr.app.widget.ElmTrackrWidgetUpdater
import com.elmtrackr.app.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ElmTrackrApp : Application() {

    val database: ElmTrackrDatabase by lazy { ElmTrackrDatabase.getInstance(this) }

    val appPreferences: AppPreferencesRepository by lazy { AppPreferencesRepository(this) }

    private val syncScheduler: SyncScheduler by lazy { SyncScheduler(this) }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val syncRepository: SyncRepository by lazy {
        val client = SupabaseClientProvider.get()
        SyncRepositoryImpl(
            shiftDao = database.shiftDao(),
            refundClaimDao = database.refundClaimDao(),
            settingsDao = database.settingsDao(),
            profileDao = database.profileDao(),
            remoteShifts = client?.let { SupabaseShiftsDataSource(it) },
            remoteRefunds = client?.let { SupabaseRefundsDataSource(it) },
            remoteSettings = client?.let { SupabaseSettingsDataSource(it) },
            remoteProfile = client?.let { SupabaseProfileDataSource(it) },
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

    // SupabaseAuthRepository self-checks BuildConfig: returns NotConfigured state
    // gracefully when SUPABASE_URL / SUPABASE_ANON_KEY are absent (e.g. CI).
    val authRepository: AuthRepository by lazy {
        SupabaseAuthRepository(database.profileDao(), appPreferences)
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        syncScheduler.schedulePeriodic()
        startActiveShiftObserver()
    }

    // ── Active-shift notification observer ───────────────────────────────────

    private fun startActiveShiftObserver() {
        val notifManager = ActiveShiftNotificationManager(this)
        applicationScope.launch {
            shiftsRepository.observeActiveShift(LOCAL_USER_ID)
                .catch { /* never crash the app due to notification failures */ }
                .collect { shift ->
                    if (shift != null) {
                        notifManager.showActiveShiftNotification(shift)
                        val settings = settingsRepository.getSettings(LOCAL_USER_ID)
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

    // ── Dynamic app shortcuts ─────────────────────────────────────────────────

    private fun updateDynamicShortcuts(clockedIn: Boolean) {
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
        val (id, shortLabel, longLabel) = if (clockedIn)
            Triple("clock_out", getString(R.string.shortcut_clock_out_short), getString(R.string.shortcut_clock_out_long))
        else
            Triple("clock_in", getString(R.string.shortcut_clock_in_short), getString(R.string.shortcut_clock_in_long))

        val shortcut = ShortcutInfo.Builder(this, id)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                },
            )
            .build()

        shortcutManager.dynamicShortcuts = listOf(shortcut)
    }
}
