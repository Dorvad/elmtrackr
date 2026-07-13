package com.elmtrackr.app.sideeffects

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.di.ApplicationScope
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.domain.time.WorkTimezone
import com.elmtrackr.app.language.withAppLocale
import com.elmtrackr.app.notification.ActiveShiftNotificationManager
import com.elmtrackr.app.notification.OvertimeReminderScheduler
import com.elmtrackr.app.shortcuts.HeadlessTrampolineActivity
import com.elmtrackr.app.widget.ElmTrackrWidgetUpdater
import com.elmtrackr.app.widget.WidgetContext
import com.elmtrackr.app.wear.WearSyncPublisher
import com.elmtrackr.wear.sync.WearShiftSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Splits active-shift side effects into focused observers so notification work
 * is not blocked by widget/Wear refreshes (and vice versa).
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Singleton
class ActiveShiftSideEffectsCoordinator @Inject constructor(
    private val app: Application,
    @ApplicationScope private val scope: CoroutineScope,
    private val shiftsRepository: ShiftsRepository,
    private val settingsRepository: SettingsRepository,
    currentUserProvider: CurrentUserProvider,
) {

    private val observeUserId = currentUserProvider.userId

    fun start() {
        startNotificationObserver()
        startWidgetObserver()
        startWearObserver()
    }

    private fun startNotificationObserver() {
        val notifManager = ActiveShiftNotificationManager(app)
        scope.launch {
            observeUserId
                .flatMapLatest { userId ->
                    if (userId == null) {
                        // Signed out: fall through to the cleanup branch below so
                        // the previous user's notification and shortcut disappear.
                        flowOf(NotificationPayload(null, null, null))
                    } else {
                        combine(
                            shiftsRepository.observeActiveShift(userId),
                            settingsRepository.observeSettings(userId),
                        ) { active, settings -> NotificationPayload(userId, active, settings) }
                    }
                }
                .distinctUntilChanged()
                .catch { /* never crash the app due to notification failures */ }
                .collect { payload ->
                    val shift = payload.activeShift
                    val settings = payload.settings
                    if (shift != null) {
                        val zone = settings?.let { WorkTimezone.zoneFor(it) } ?: java.time.ZoneId.systemDefault()
                        notifManager.showActiveShiftNotification(shift, zone)
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
                .flatMapLatest { userId ->
                    if (userId == null) {
                        // Signed out: push an empty state so the widget stops
                        // showing the previous user's shift.
                        flowOf(WidgetRefresh(null, null, emptyList(), null, isSignedIn = false))
                    } else {
                        combine(
                            shiftsRepository.observeActiveShift(userId),
                            settingsRepository.observeSettings(userId),
                        ) { active, settings -> Triple(userId, active, settings) }
                            .distinctUntilChanged { old, new ->
                                old.second?.id == new.second?.id && old.third?.timezone == new.third?.timezone
                            }
                            .debounce(WIDGET_DEBOUNCE_MS)
                            .flatMapLatest { (uid, active, settings) ->
                                val zone = WorkTimezone.zoneId(settings?.timezone)
                                val today = LocalDate.now(zone)
                                val todayShiftsFlow = shiftsRepository.observeShiftsForDay(uid, zone, today)
                                combine(
                                    todayShiftsFlow,
                                    shiftsRepository.observeRecentCompletedShifts(uid, 1),
                                ) { todayShifts, lastCompleted ->
                                    WidgetRefresh(active, lastCompleted.firstOrNull(), todayShifts, settings)
                                }
                            }
                    }
                }
                .catch { }
                .collect { refresh ->
                    ElmTrackrWidgetUpdater.update(
                        app,
                        WidgetContext(
                            activeShift = refresh.activeShift,
                            lastCompletedShift = refresh.lastCompletedShift,
                            todayShifts = refresh.todayShifts,
                            settings = refresh.settings,
                            isSignedIn = refresh.isSignedIn,
                        ),
                    )
                }
        }
    }

    private fun startWearObserver() {
        scope.launch {
            observeUserId
                .flatMapLatest { userId ->
                    if (userId == null) {
                        flowOf(null)
                    } else {
                        combine(
                            shiftsRepository.observeActiveShift(userId),
                            settingsRepository.observeSettings(userId),
                        ) { active, settings -> Triple(userId, active, settings) }
                            .distinctUntilChanged { old, new ->
                                old.second?.id == new.second?.id && old.third?.timezone == new.third?.timezone
                            }
                            .debounce(WEAR_DEBOUNCE_MS)
                            .flatMapLatest { (uid, active, settings) ->
                                val zone = WorkTimezone.zoneId(settings?.timezone)
                                val today = LocalDate.now(zone)
                                val todayShiftsFlow = shiftsRepository.observeShiftsForDay(uid, zone, today)
                                combine(
                                    todayShiftsFlow,
                                    shiftsRepository.observeRecentCompletedShifts(uid, 1),
                                ) { todayShifts, lastCompleted ->
                                    WidgetRefresh(active, lastCompleted.firstOrNull(), todayShifts, settings)
                                }
                            }
                    }
                }
                .catch { }
                .collect { refresh ->
                    if (refresh == null) {
                        WearSyncPublisher.publishSnapshot(app, WearShiftSnapshot.signedOut())
                    } else {
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

        val localized = app.withAppLocale()
        val shortcut = ShortcutInfo.Builder(app, shortcutId)
            .setShortLabel(localized.getString(R.string.shortcut_clock_out_short))
            .setLongLabel(localized.getString(R.string.shortcut_clock_out_long))
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
        val userId: String?,
        val activeShift: Shift?,
        val settings: UserSettings?,
    )

    private data class WidgetRefresh(
        val activeShift: Shift?,
        val lastCompletedShift: Shift?,
        val todayShifts: List<Shift>,
        val settings: UserSettings?,
        val isSignedIn: Boolean = true,
    )

    companion object {
        private const val WIDGET_DEBOUNCE_MS = 400L
        private const val WEAR_DEBOUNCE_MS = 400L
    }
}
