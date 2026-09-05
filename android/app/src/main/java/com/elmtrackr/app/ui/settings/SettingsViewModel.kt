package com.elmtrackr.app.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.R
import com.elmtrackr.app.data.local.preferences.AppLockPreferencesStore
import com.elmtrackr.app.billing.ClockFacePackEntitlements
import com.elmtrackr.app.billing.ClockFacePackStore
import com.elmtrackr.app.billing.ClockFacePackStorefront
import com.elmtrackr.app.billing.PackPurchaseEvent
import com.elmtrackr.app.billing.PackRestoreResult
import com.elmtrackr.app.data.local.preferences.ClockFacePreferences
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.model.AuthResult
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.data.sync.SyncHealth
import com.elmtrackr.app.data.sync.SyncRepository
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.security.AppLockController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant
import kotlin.math.roundToInt
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class SettingsFeatureFlags(
    val travelRefunds: Boolean,
    val paidProjects: Boolean,
    val insights: Boolean,
    val clockStyles: Boolean,
    val overtimeReminders: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val compensationProfilesRepository: CompensationProfilesRepository,
    private val themeStore: ThemePreferenceStore,
    private val syncRepository: SyncRepository,
    private val syncTrigger: SyncTrigger,
    private val appPreferences: AppLockPreferencesStore,
    private val clockFacePreferences: ClockFacePreferences,
    private val clockFacePackEntitlements: ClockFacePackEntitlements,
    private val clockFacePackStore: ClockFacePackStore,
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _isSyncing = MutableStateFlow(false)
    private val _validationErrors = MutableStateFlow<Map<String, UiText>>(emptyMap())
    private val _passwordResetFeedback = MutableStateFlow<UiText?>(null)
    private val _saveFeedback = MutableStateFlow<SettingsSaveFeedback?>(null)
    private val _isDeletingAccount = MutableStateFlow(false)
    private val _accountActionFeedback = MutableStateFlow<UiText?>(null)
    private val _packPurchaseFeedback = MutableStateFlow<UiText?>(null)

    /**
     * Prices and ownership for the face store, straight from Play's storefront.
     *
     * Passed through rather than folded into [SettingsUiState]. The storefront
     * changes on its own schedule — Play answers a price query, a purchase lands
     * while the screen is open — and merging it into the settings state would
     * rebuild every settings form on each of those, for one screen's benefit.
     */
    val packStorefront: StateFlow<ClockFacePackStorefront> = clockFacePackStore.storefront

    /** One-off purchase outcome for the snackbar. Null once shown. */
    val packPurchaseFeedback: StateFlow<UiText?> = _packPurchaseFeedback.asStateFlow()

    /**
     * Whether a restore is in flight, so the button can say so.
     *
     * Play's connection is allowed twenty seconds before it is given up on, and
     * a tap that shows nothing for that long reads as a dead control — the user
     * taps again, and again. Its own flow rather than part of [SettingsUiState]
     * for the same reason [packStorefront] is: one screen needs it, and every
     * settings form would otherwise rebuild twice per tap.
     */
    private val _isRestoringPacks = MutableStateFlow(false)
    val isRestoringPacks: StateFlow<Boolean> = _isRestoringPacks.asStateFlow()

    /**
     * The packs a purchase just landed, for the store's inline success strip.
     *
     * Carried separately from [packPurchaseFeedback] because the strip needs to
     * *name* what arrived — "Progress added" — and the snackbar text cannot.
     * The store clears it after showing; the pack itself is already installed
     * by the billing coordinator, so losing this state loses a confirmation,
     * never a purchase.
     */
    private val _justUnlockedPacks = MutableStateFlow<Set<ClockFaceGroup>>(emptySet())
    val justUnlockedPacks: StateFlow<Set<ClockFaceGroup>> = _justUnlockedPacks.asStateFlow()

    init {
        viewModelScope.launch {
            clockFacePackStore.events.collect { event ->
                // A restore names its packs on the same strip a purchase does.
                // Someone who has just reinstalled needs to be told *which* packs
                // came back, and the snackbar cannot say it.
                when (event) {
                    is PackPurchaseEvent.Purchased -> _justUnlockedPacks.value = event.packs
                    is PackPurchaseEvent.Restored -> _justUnlockedPacks.value = event.packs
                    else -> Unit
                }
                _packPurchaseFeedback.value = when (event) {
                    // Nothing to say. The user pressed back on Play's sheet and
                    // already knows what happened; a snackbar would be the app
                    // narrating their own tap back at them.
                    PackPurchaseEvent.Cancelled -> null
                    is PackPurchaseEvent.Purchased -> UiText.Res(R.string.settings_pack_purchased)
                    PackPurchaseEvent.Pending -> UiText.Res(R.string.settings_pack_purchase_pending)
                    PackPurchaseEvent.AlreadyOwned -> UiText.Res(R.string.settings_pack_purchase_restored)
                    is PackPurchaseEvent.Restored -> UiText.Res(R.string.settings_pack_purchase_restored)
                    // The response code is for the log, not the user: Play has
                    // already shown its own error, and repeating a numeric code
                    // helps nobody holding a phone.
                    is PackPurchaseEvent.Failed -> UiText.Res(R.string.settings_pack_purchase_failed)
                }
            }
        }
    }

    private data class CoreData(
        val settings: UserSettings?,
        val compensationProfiles: List<com.elmtrackr.app.domain.model.CompensationProfile> = emptyList(),
    )

    private data class Extras(
        val profile: Profile?,
        val theme: String,
        val isSaving: Boolean,
        val isSyncing: Boolean,
        val syncHealth: SyncHealth?,
        val lastSyncStatus: String?,
        val validationErrors: Map<String, UiText>,
        val passwordResetFeedback: UiText?,
        val saveFeedback: SettingsSaveFeedback?,
        val isDeletingAccount: Boolean,
        val accountActionFeedback: UiText?,
        val appLockEnabled: Boolean,
        val reduceMotionEnabled: Boolean,
        val recentClockFaces: List<ClockStyle>,
        val storedClockFacePacks: Set<ClockFaceGroup>,
    )

    private val syncHealthFlow = authRepository.observeCurrentProfile().flatMapLatest { profile ->
        if (profile == null) flowOf(null)
        else syncRepository.observeSyncHealth(profile.id)
    }

    private val lastSyncFlow = syncRepository.observeLastSyncStatus()

    private val coreData = authRepository.observeCurrentProfile().flatMapLatest { profile ->
        if (profile == null) {
            flowOf(CoreData(null))
        } else {
            combine(
                settingsRepository.observeSettings(profile.id),
                compensationProfilesRepository.observeProfiles(profile.id),
            ) { settings, compensationProfiles ->
                CoreData(settings, compensationProfiles)
            }
        }
    }

    private val extras = combine(
        combine(
            combine(
                authRepository.observeCurrentProfile(),
                themeStore.observeTheme(),
                // Each value read from the interface that owns it. Both flows are
                // the same DataStore in production, so the copy is a no-op there;
                // it matters because reading the face history off the app-lock
                // store made the two halves of the feature — the write through
                // ClockFacePreferences and the read — silently independent.
                combine(
                    appPreferences.preferences,
                    clockFacePreferences.preferences,
                ) { prefs, facePrefs ->
                    prefs.copy(
                        recentClockFaces = facePrefs.recentClockFaces,
                        installedClockFacePacks = facePrefs.installedClockFacePacks,
                    )
                },
            ) { profile, theme, prefs ->
                Triple(profile, theme, prefs)
            },
            combine(
                _isSaving,
                _isSyncing,
                syncHealthFlow,
            ) { saving, syncing, health ->
                Triple(saving, syncing, health)
            },
        ) { profileMeta, syncMeta ->
            SyncExtras(
                profile = profileMeta.first,
                theme = profileMeta.second,
                appLockEnabled = profileMeta.third.appLockEnabled,
                reduceMotionEnabled = profileMeta.third.reduceMotionEnabled,
                recentClockFaces = resolveClockFaceRecents(profileMeta.third.recentClockFaces),
                storedClockFacePacks = ClockFacePacks.resolve(profileMeta.third.installedClockFacePacks),
                isSaving = syncMeta.first,
                isSyncing = syncMeta.second,
                syncHealth = syncMeta.third,
            )
        },
        combine(
            lastSyncFlow,
            combine(
                combine(
                    _validationErrors,
                    _passwordResetFeedback,
                ) { errors, resetFeedback ->
                    Pair(errors, resetFeedback)
                },
                combine(
                    _saveFeedback,
                    _isDeletingAccount,
                    _accountActionFeedback,
                ) { saveFeedback, deleting, accountFeedback ->
                    Triple(saveFeedback, deleting, accountFeedback)
                },
            ) { (errors, resetFeedback), (saveFeedback, deleting, accountFeedback) ->
                AccountExtras(errors, resetFeedback, saveFeedback, deleting, accountFeedback)
            },
        ) { lastSync, account -> lastSync to account },
    ) { syncExtras, lastAccount ->
        val (lastSync, account) = lastAccount
        Extras(
            syncExtras.profile,
            syncExtras.theme,
            syncExtras.isSaving,
            syncExtras.isSyncing,
            syncExtras.syncHealth,
            lastSync,
            account.errors,
            account.resetFeedback,
            account.saveFeedback,
            account.deleting,
            account.accountFeedback,
            syncExtras.appLockEnabled,
            syncExtras.reduceMotionEnabled,
            syncExtras.recentClockFaces,
            syncExtras.storedClockFacePacks,
        )
    }

    private data class SyncExtras(
        val profile: Profile?,
        val theme: String,
        val appLockEnabled: Boolean,
        val reduceMotionEnabled: Boolean,
        val recentClockFaces: List<ClockStyle>,
        val storedClockFacePacks: Set<ClockFaceGroup>,
        val isSaving: Boolean,
        val isSyncing: Boolean,
        val syncHealth: SyncHealth?,
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        coreData,
        extras,
    ) { core, extras ->
        if (core.settings == null) SettingsUiState.Loading
        else SettingsUiState.Ready(
            settings = core.settings,
            profile = extras.profile,
            selectedTheme = extras.theme,
            isSaving = extras.isSaving,
            syncPendingCount = extras.syncHealth?.pendingCount ?: 0,
            syncFailedCount = extras.syncHealth?.failedCount ?: 0,
            lastSyncStatus = extras.lastSyncStatus,
            isSyncing = extras.isSyncing,
            validationErrors = extras.validationErrors,
            passwordResetFeedback = extras.passwordResetFeedback,
            saveFeedback = extras.saveFeedback,
            isDeletingAccount = extras.isDeletingAccount,
            accountActionFeedback = extras.accountActionFeedback,
            appLockEnabled = extras.appLockEnabled,
            reduceMotionEnabled = extras.reduceMotionEnabled,
            recentClockFaces = extras.recentClockFaces,
            storedClockFacePacks = extras.storedClockFacePacks,
            compensationProfileCount = core.compensationProfiles.size,
            defaultCompensationProfileName = core.compensationProfiles
                .let { list -> list.firstOrNull { it.isDefault } ?: list.firstOrNull() }
                ?.name,
        )
    }.catch { e ->
        emit(SettingsUiState.Error(e.message ?: "Unknown error"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState.Loading,
    )

    fun saveSettings(
        displayName: String,
        dailyOtHours: Double,
        weeklyOtHours: Double,
        hourlyRate: Double?,
        timezone: String,
        clockStyle: ClockStyle,
        currency: CurrencyCode = CurrencyCode.ILS,
        weekendDays: List<Int>,
        featureFlags: SettingsFeatureFlags? = null,
    ) {
        val errors = validate(dailyOtHours, weeklyOtHours, hourlyRate)
        if (errors.isNotEmpty()) {
            _validationErrors.value = errors
            _saveFeedback.value = SettingsSaveFeedback(
                message = UiText.Res(R.string.settings_feedback_fix_fields),
                isError = true,
            )
            return
        }
        _validationErrors.value = emptyMap()
        viewModelScope.launch {
            _isSaving.value = true
            val currentProfile = authRepository.getCurrentProfile()
                ?: run {
                    _isSaving.value = false
                    _saveFeedback.value = SettingsSaveFeedback(UiText.Res(R.string.settings_feedback_save_failed), isError = true)
                    return@launch
                }
            val existing = settingsRepository.getSettings(currentProfile.id)
                ?: run {
                    _isSaving.value = false
                    _saveFeedback.value = SettingsSaveFeedback(UiText.Res(R.string.settings_feedback_save_failed), isError = true)
                    return@launch
                }
            val flags = featureFlags ?: SettingsFeatureFlags(
                travelRefunds = existing.featuresTravelRefunds,
                paidProjects = existing.featuresPaidProjects,
                insights = existing.featuresInsights,
                clockStyles = existing.featuresClockStyles,
                overtimeReminders = existing.featuresOvertimeReminders,
            )
            val normalizedTimezone = IanaTimezones.normalize(timezone.trim())
            val savedSettings = existing.copy(
                dailyOvertimeThresholdMinutes = (dailyOtHours * 60).roundToInt(),
                weeklyOvertimeThresholdMinutes = (weeklyOtHours * 60).roundToInt(),
                hourlyRate = hourlyRate,
                timezone = normalizedTimezone,
                clockStyle = clockStyle,
                currency = currency,
                weekendDays = weekendDays,
                featuresTravelRefunds = flags.travelRefunds,
                featuresPaidProjects = flags.paidProjects,
                featuresInsights = flags.insights,
                featuresClockStyles = flags.clockStyles,
                featuresOvertimeReminders = flags.overtimeReminders,
                updatedAt = Instant.now(),
            )
            settingsRepository.saveSettings(savedSettings)
            compensationProfilesRepository.ensureMigrated(currentProfile.id)
            val profiles = compensationProfilesRepository.getProfiles(currentProfile.id)
            val defaultProfile = profiles.firstOrNull { it.isDefault } ?: profiles.firstOrNull()
            if (defaultProfile != null) {
                val newDailyStandard = (dailyOtHours * 60).roundToInt()
                val newWeeklyStandard = (weeklyOtHours * 60).roundToInt()
                val updatedProfile = defaultProfile.copy(
                    baseHourlyRate = hourlyRate,
                    currencyCode = currency.name,
                    timezone = normalizedTimezone,
                    rules = defaultProfile.rules.copy(
                        dailyStandardMinutes = newDailyStandard,
                        weeklyStandardMinutes = newWeeklyStandard,
                        weekendDays = weekendDays,
                        // Keep the overtime ladders aligned with the new standards —
                        // otherwise tiers could start before the standard is reached.
                        dailyOvertimeTiers = CompensationResolver.remapOvertimeTiers(
                            defaultProfile.rules.dailyOvertimeTiers,
                            defaultProfile.rules.dailyStandardMinutes,
                            newDailyStandard,
                        ),
                        weeklyOvertimeTiers = CompensationResolver.remapOvertimeTiers(
                            defaultProfile.rules.weeklyOvertimeTiers,
                            defaultProfile.rules.weeklyStandardMinutes,
                            newWeeklyStandard,
                        ),
                    ),
                )
                val savedProfile = compensationProfilesRepository.upsertProfile(updatedProfile)
                settingsRepository.saveSettings(
                    savedSettings.apply(CompensationResolver.profileToLegacySettingsUpdates(savedProfile)),
                )
            }
            val existingProfile = currentProfile
            if (existingProfile.fullName != displayName.trim().ifBlank { null }) {
                val newName = displayName.trim().ifBlank { null }
                authRepository.saveProfile(
                    existingProfile.copy(fullName = newName, updatedAt = Instant.now()),
                    existingProfile.id,
                )
            }
            recordClockFaceUse(clockStyle)
            _isSaving.value = false
            _saveFeedback.value = SettingsSaveFeedback(UiText.Res(R.string.settings_feedback_saved))
        }
    }

    /**
     * Records the face in the recently-used list, on save rather than on tap.
     *
     * Tapping a tile in the appearance screen only previews it — the value is not
     * committed until Save, and the screen counts it as an unsaved change.
     * Recording on tap would fill the history with faces the user looked at and
     * rejected, which is the opposite of useful, and would reorder the row under
     * the user's finger while they were still choosing.
     */
    private suspend fun recordClockFaceUse(style: ClockStyle) {
        val current = resolveClockFaceRecents(
            clockFacePreferences.preferences.first().recentClockFaces,
        )
        val updated = updatedClockFaceRecents(current, style)
        if (updated != current) {
            clockFacePreferences.setRecentClockFaces(updated.map { it.name })
        }
    }

    /**
     * Adds a face pack, if the user is entitled to it.
     *
     * The entitlement check is here rather than in the screen so a future paid pack
     * cannot be added by a caller that forgot to ask. Today
     * [com.elmtrackr.app.billing.FreeClockFacePackEntitlements] says yes to
     * everything, so this is a no-op gate — which is the point of adding it before
     * there is any billing rather than after.
     */
    fun installClockFacePack(pack: ClockFaceGroup) {
        viewModelScope.launch {
            if (!clockFacePackEntitlements.isEntitled(pack)) return@launch
            val stored = ClockFacePacks.resolve(
                clockFacePreferences.preferences.first().installedClockFacePacks,
            )
            clockFacePreferences.setInstalledClockFacePacks((stored + pack).map { it.name }.toSet())
        }
    }

    /**
     * Removes a face pack.
     *
     * [onSelectionReset] fires when the removed pack held the selected face, so the
     * screen can move its unsaved selection to the fallback. Without it the pack
     * would disappear while the appearance screen still showed a face from it, and
     * the next save would store a face the user no longer has.
     */
    fun removeClockFacePack(
        pack: ClockFaceGroup,
        selected: ClockStyle,
        onSelectionReset: (ClockStyle) -> Unit = {},
    ) {
        if (pack.isBundled) return
        viewModelScope.launch {
            val stored = ClockFacePacks.resolve(
                clockFacePreferences.preferences.first().installedClockFacePacks,
            )
            clockFacePreferences.setInstalledClockFacePacks((stored - pack).map { it.name }.toSet())
            ClockFacePacks.fallbackAfterRemoving(pack, selected)?.let(onSelectionReset)
        }
    }

    /**
     * Opens Play's purchase sheet for [pack].
     *
     * Takes the Activity as a parameter and keeps no reference to it: Play needs a
     * live Activity to draw over, and a view model that held one would outlive it
     * across a rotation.
     */
    fun purchaseClockFacePack(activity: Activity, pack: ClockFaceGroup) {
        viewModelScope.launch { clockFacePackStore.launchPurchase(activity, pack) }
    }

    /** Opens Play's purchase sheet for every pack at once. */
    fun purchaseAllClockFacePacks(activity: Activity) {
        viewModelScope.launch { clockFacePackStore.launchAllPacksPurchase(activity) }
    }

    fun clearPackPurchaseFeedback() {
        _packPurchaseFeedback.value = null
    }

    fun clearJustUnlockedPacks() {
        _justUnlockedPacks.value = emptySet()
    }

    /**
     * Re-reads what the account owns from Play, for the store's Restore action.
     *
     * The same refresh already runs on every foreground, so the usual answer is
     * that nothing was missing — and saying so is the point. A button that does
     * its job silently is indistinguishable from a button that is broken, which
     * is what a user who has just reinstalled and cannot find their pack will
     * conclude. Every outcome gets a sentence.
     *
     * [PackRestoreResult.Restored] is deliberately not one of them: the store
     * already reports that through [packPurchaseFeedback] and the unlock strip
     * when the restore event lands, and two messages for one tap would talk over
     * each other.
     */
    fun restoreClockFacePacks() {
        // Set before the launch, not inside it: two taps in the same frame both
        // reach this function before either coroutine body runs on a dispatcher
        // that queues, and the second would start a query of its own.
        if (_isRestoringPacks.value) return
        _isRestoringPacks.value = true
        viewModelScope.launch {
            try {
                when (clockFacePackStore.refresh()) {
                    is PackRestoreResult.Restored -> Unit
                    PackRestoreResult.NothingRestored ->
                        _packPurchaseFeedback.value =
                            UiText.Res(R.string.settings_pack_restore_nothing)
                    PackRestoreResult.Unavailable ->
                        _packPurchaseFeedback.value =
                            UiText.Res(R.string.settings_pack_restore_unavailable)
                }
            } finally {
                _isRestoringPacks.value = false
            }
        }
    }

    fun clearSaveFeedback() {
        _saveFeedback.value = null
    }

    fun clearAccountActionFeedback() {
        _accountActionFeedback.value = null
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _isDeletingAccount.value = true
            when (val result = authRepository.deleteAccount()) {
                is AuthResult.Success ->
                    _accountActionFeedback.value = UiText.Res(R.string.settings_feedback_account_deleted)
                is AuthResult.NotConfigured ->
                    _accountActionFeedback.value = UiText.Res(R.string.settings_feedback_local_cleared)
                is AuthResult.Error ->
                    _accountActionFeedback.value = result.message
            }
            _isDeletingAccount.value = false
        }
    }

    private data class AccountExtras(
        val errors: Map<String, UiText>,
        val resetFeedback: UiText?,
        val saveFeedback: SettingsSaveFeedback?,
        val deleting: Boolean,
        val accountFeedback: UiText?,
    )

    fun saveTheme(theme: String) {
        viewModelScope.launch { themeStore.saveTheme(theme) }
    }

    fun resetPassword() {
        viewModelScope.launch {
            val profile = authRepository.getCurrentProfile() ?: return@launch
            when (val result = authRepository.resetPassword(profile.email)) {
                is AuthResult.Success ->
                    _passwordResetFeedback.value = UiText.Res(R.string.settings_feedback_reset_sent, profile.email)
                is AuthResult.NotConfigured ->
                    _passwordResetFeedback.value = UiText.Res(R.string.auth_error_not_configured)
                is AuthResult.Error ->
                    _passwordResetFeedback.value = result.message
            }
        }
    }

    fun clearPasswordResetFeedback() {
        _passwordResetFeedback.value = null
    }

    fun syncNow() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            _isSyncing.value = true
            runCatching { syncRepository.syncAll(userId) }
            _isSyncing.value = false
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setAppLockEnabled(enabled)
            AppLockController.configure(enabled, initiallyUnlocked = true)
            if (enabled) {
                AppLockController.unlock()
            }
        }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setReduceMotion(enabled)
        }
    }

    fun ensureSettingsExist() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentProfile()?.id ?: return@launch
            if (settingsRepository.getSettings(userId) == null) {
                settingsRepository.createDefaultSettings(userId)
            }
            compensationProfilesRepository.ensureMigrated(userId)
        }
    }

    internal fun validate(
        dailyOtHours: Double,
        weeklyOtHours: Double,
        hourlyRate: Double?,
    ): Map<String, UiText> {
        val errors = mutableMapOf<String, UiText>()
        if (dailyOtHours <= 0.0) errors["dailyOt"] = UiText.Res(R.string.settings_error_positive)
        else if (dailyOtHours > 24.0) errors["dailyOt"] = UiText.Res(R.string.settings_error_max_24)
        if (weeklyOtHours <= 0.0) errors["weeklyOt"] = UiText.Res(R.string.settings_error_positive)
        else if (weeklyOtHours > 168.0) errors["weeklyOt"] = UiText.Res(R.string.settings_error_max_168)
        else if (dailyOtHours > 0 && weeklyOtHours < dailyOtHours) {
            errors["weeklyOt"] = UiText.Res(R.string.settings_error_weekly_gte_daily)
        }
        if (hourlyRate != null && hourlyRate < 0.0) errors["hourlyRate"] = UiText.Res(R.string.settings_error_rate_nonnegative)
        return errors
    }
}
