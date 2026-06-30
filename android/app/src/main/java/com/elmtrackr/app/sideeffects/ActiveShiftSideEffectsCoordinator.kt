package com.elmtrackr.app.sideeffects

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.domain.time.WorkTimezone
import com.elmtrackr.app.notification.ActiveShiftNotificationManager
import com.elmtrackr.app.notification.OvertimeReminderScheduler
import com.elmtrackr.app.shortcuts.HeadlessTrampolineActivity
import com.elmtrackr.app.widget.ElmTrackrWidgetUpdater
import com.elmtrackr.app.widget.WidgetContext
import com.elmtrackr.app.wear.WearSyncPublisher
import com.elmtrackr.wear.sync.WearShiftSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Splits active-shift side effects into focused observers so notification work
 * is not blocked by widget/Wear refreshes (and vice versa).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveShiftSideEffectsCoordinator(
    private val app: Application,
    private val scope: CoroutineScope,
    private val shiftsRepository: ShiftsRepository,
    private val settingsRepository: SettingsRepository,
    private val observeUserId: kotlinx.coroutines.flow.Flow<String?>,
) {

    fun start() {
        startNotificationObserver()
        startWidgetObserver()
        startWearObserver()
    }

    private fun startNotificationObserver() {
        val notifManager = ActiveShiftNotificationManager(app)
        scope.launch {
            observeUserId
                .filterNotNull()
                .flatMapLatest { userId ->
                    combine(
                        shiftsRepository.observeActiveShift(userId),
                        settingsRepository.observeSettings(userId),
                    ) { active, settings -> Triple(userId, active, settings) }
                }
                .map { (userId, active, settings) ->
                    NotificationPayload(userId, active, settings)
                }
                .distinctUntilChanged()
                .catch { /* never crash the app due to notification failures */ }
                .collect { payload ->
                    val shift = payload.activeShift
                    val settings = payload.settings
                    if (shift != null) {
                        notifManager.showActiveShiftNotification(shift)
                        OvertimeReminderScheduler.scheduleForActiveShift(app, shift, settings)
                        updateDynamicShortcuts(clockedIn = true)
                    } else {
                        notifManager.cancelActiveShiftNotification()
                        notifManager.cancelReminderNotification()
                        OvertimeReminderScheduler.cancelAll(app)
                        updateDynamicShortcuts(clockedIn = false)
                    }
                }
        }
    }

    private fun startWidgetObserver() {
        scope.launch {
            observeUserId
                .filterNotNull()
                .flatMapLatest { userId ->
                    combine(
                        shiftsRepository.observeActiveShift(userId),
                        settingsRepository.observeSettings(userId),
                    ) { active, settings -> Triple(userId, active, settings) }
                }
                .map { (userId, active, settings) ->
                    WidgetPayload(userId, active?.id, settings?.timezone)
                }
                .distinctUntilChanged()
                .debounce(WIDGET_DEBOUNCE_MS)
                .flatMapLatest { payload ->
                    val zone = WorkTimezone.zoneId(payload.timezone)
                    val today = LocalDate.now(zone)
                    val todayShiftsFlow = shiftsRepository.observeShiftsForDay(payload.userId, zone, today)
                    combine(
                        todayShiftsFlow,
                        shiftsRepository.observeRecentCompletedShifts(payload.userId, 1),
                        shiftsRepository.observeActiveShift(payload.userId),
                        settingsRepository.observeSettings(payload.userId),
                    ) { todayShifts, lastCompleted, active, settings ->
                        WidgetRefresh(active, lastCompleted.firstOrNull(), todayShifts, settings)
                    }
                }
                .catch { }
                .collect { refresh ->
                    ElmTrackrWidgetUpdater.update(
                        app,
                        refresh.activeShift,
                        refresh.lastCompletedShift,
                        refresh.todayShifts,
                        refresh.settings,
                    )
                }
        }
    }

    private fun startWearObserver() {
        scope.launch {
            observeUserId
                .filterNotNull()
                .flatMapLatest { userId ->
                    combine(
                        shiftsRepository.observeActiveShift(userId),
                        settingsRepository.observeSettings(userId),
                    ) { active, settings -> Triple(userId, active, settings) }
                }
                .map { (userId, active, settings) ->
                    WidgetPayload(userId, active?.id, settings?.timezone)
                }
                .distinctUntilChanged()
                .debounce(WEAR_DEBOUNCE_MS)
                .flatMapLatest { payload ->
                    val zone = WorkTimezone.zoneId(payload.timezone)
                    val today = LocalDate.now(zone)
                    val todayShiftsFlow = shiftsRepository.observeShiftsForDay(payload.userId, zone, today)
                    combine(
                        todayShiftsFlow,
                        shiftsRepository.observeRecentCompletedShifts(payload.userId, 1),
                        shiftsRepository.observeActiveShift(payload.userId),
                        settingsRepository.observeSettings(payload.userId),
                    ) { todayShifts, lastCompleted, active, settings ->
                        WidgetRefresh(active, lastCompleted.firstOrNull(), todayShifts, settings)
                    }
                }
                .catch { }
                .collect { refresh ->
                    WearSyncPublisher.publishFromWidgetContext(
                        app,
                        WidgetContext(
                            activeShift = refresh.activeShift,
                            lastCompletedShift = refresh.lastCompletedShift,
                            todayShifts = refresh.todayShifts,
                            settings = refresh.settings,
                        ),
                    )
                }
        }
    }

    fun publishSignedOutWearSnapshot() {
        scope.launch {
            WearSyncPublisher.publishSnapshot(app, WearShiftSnapshot.signedOut())
        }
    }

    fun refreshShortcuts(clockedIn: Boolean) {
        updateDynamicShortcuts(clockedIn)
    }

    private fun updateDynamicShortcuts(clockedIn: Boolean) {
        val shortcutManager = app.getSystemService(ShortcutManager::class.java) ?: return
        val shortcutId = "shortcut_clock_out"
        if (!clockedIn) {
            shortcutManager.removeDynamicShortcuts(listOf(shortcutId))
            return
        }

        val shortcut = ShortcutInfo.Builder(app, shortcutId)
            .setShortLabel(app.getString(R.string.shortcut_clock_out_short))
            .setLongLabel(app.getString(R.string.shortcut_clock_out_long))
            .setIcon(Icon.createWithResource(app, R.mipmap.ic_launcher))
            .setIntent(
                Intent(app, HeadlessTrampolineActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                },
            )
            .build()

        shortcutManager.dynamicShortcuts = listOf(shortcut)
    }

    private data class NotificationPayload(
        val userId: String,
        val activeShift: Shift?,
        val settings: UserSettings?,
    )

    private data class WidgetPayload(
        val userId: String,
        val activeShiftId: String?,
        val timezone: String?,
    )

    private data class WidgetRefresh(
        val activeShift: Shift?,
        val lastCompletedShift: Shift?,
        val todayShifts: List<Shift>,
        val settings: UserSettings?,
    )

    companion object {
        private const val WIDGET_DEBOUNCE_MS = 400L
        private const val WEAR_DEBOUNCE_MS = 400L
    }
}
