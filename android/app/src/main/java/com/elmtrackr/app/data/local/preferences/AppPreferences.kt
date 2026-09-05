package com.elmtrackr.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.elmtrackr.app.monitoring.CrashReporting
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * These preferences are read during startup, so a corrupt file used to be an
 * unrecoverable crash loop: the read threw, the throw reached the application
 * scope's root `launch`, and the process died again on every relaunch. The
 * corruption handler trades the stored values — theme, onboarding flag,
 * dismissal state, all of them re-derivable or re-settable — for the app being
 * able to start. None of this is user data; the shifts live in Room.
 */
val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * What the user owns, kept apart from everything else — and deliberately with no
 * corruption handler.
 *
 * The handler on `app_preferences` above is right for what that file holds: values
 * that are re-derived or re-settable, where a corrupt file must not become a
 * launch crash loop. It became wrong for entitlements the moment they moved in
 * beside them. `grandfathered_clock_face_packs` records packs granted for free at
 * the paid-packs switch, and it exists **nowhere else** — not in Play, which never
 * sold them, and not in Supabase, deliberately. Wiping it on corruption revoked
 * them permanently, and `seedIfNeeded` then re-derived the grant from
 * `installed_clock_face_packs`, which the same wipe had just emptied — so the app
 * would go on to offer the user their own packs for sale.
 *
 * Without a handler a corrupt file surfaces as an IOException on read instead. The
 * callers below catch it and answer with empty values for this launch, so a
 * corrupt entitlements file is a bad session rather than a permanent loss: Play
 * purchases return on the next foreground refresh, and the grandfathered set is
 * still on disk to be recovered rather than overwritten with nothing.
 */
val Context.entitlementsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "entitlements",
)

object AppPreferenceKeys {
    val SELECTED_THEME = stringPreferencesKey("selected_theme")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val FIRST_CLOCK_IN_CELEBRATED = booleanPreferencesKey("first_clock_in_celebrated")
    val FIRST_CLOCK_IN_CELEBRATION_PENDING = booleanPreferencesKey("first_clock_in_celebration_pending")
    val LAST_ACTIVE_USER_ID = stringPreferencesKey("last_active_user_id")
    val DEVICE_ID = stringPreferencesKey("device_id")
    val LEGACY_DATA_ADOPTED = booleanPreferencesKey("legacy_data_adopted")
    val NOTIFICATION_PERMISSION_PROMPTED = booleanPreferencesKey("notification_permission_prompted")
    val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    val SETUP_CHECKLIST_DISMISSED = booleanPreferencesKey("setup_checklist_dismissed")
    val SETUP_CHECKLIST_VISITED_STEPS = stringSetPreferencesKey("setup_checklist_visited_steps")
    val SETUP_CHECKLIST_CELEBRATED = booleanPreferencesKey("setup_checklist_celebrated")
    val PAID_PROJECTS_DISCOVERY_DISMISSED =
        booleanPreferencesKey("paid_projects_discovery_dismissed")

    /**
     * Whether the Projects tab's how-to guide has been shown once.
     *
     * Its own key rather than a reuse of the announcement above: the two answer
     * different questions ("do you want this module?" against "here is how it
     * works"), and a user who took the module from the announcement would
     * otherwise never be shown how to use it.
     */
    val PROJECTS_GUIDE_SEEN = booleanPreferencesKey("projects_guide_seen")

    val WEAR_SYNC_ENABLED = booleanPreferencesKey("wear_sync_enabled")

    /**
     * The month whose refund reminder the user dismissed, as `YYYY-MM`.
     *
     * A month rather than a boolean because the reminder is recurring: unresolved
     * refund claims are settled per month, so a flag would silence the nudge for
     * every future month as well. Storing which month was dismissed lets the
     * reminder re-arm on its own when the next one comes round.
     *
     * A string rather than an int pair because that is what [java.time.YearMonth]
     * parses and prints, so no format lives in two places.
     */
    val REFUND_REMINDER_DISMISSED_MONTH =
        stringPreferencesKey("refund_reminder_dismissed_month")

    /**
     * Newest-first clock face names, newline-separated.
     *
     * A string rather than a string set because the order *is* the data — a set
     * would return the four faces in arbitrary order and the row would reshuffle
     * itself on every read. Newline as the separator because enum names cannot
     * contain one, so the parse needs no escaping.
     */
    val RECENT_CLOCK_FACES = stringPreferencesKey("recent_clock_faces")

    /**
     * Names of the face packs the user has added. A set, not an ordered string:
     * unlike the history, nothing about presence depends on order.
     *
     * Absent is not the same as empty and both are fine — the bundled pack and the
     * pack holding the selected face are added at read time, so a missing or lost
     * value degrades to "just the defaults, plus whatever you have selected".
     */
    val INSTALLED_CLOCK_FACE_PACKS = stringSetPreferencesKey("installed_clock_face_packs")

    /**
     * Play product ids this account has bought, as Play last reported them.
     *
     * A cache, never the record. Play owns the truth and is re-asked on every
     * foreground; this exists so a user who paid can still add their packs on a
     * plane, and so the gallery does not blink through a "not owned" frame while
     * the billing service connects. Stale in exactly one direction that matters —
     * a refund shows here until the next successful query — which is the right
     * trade against locking a paying user out of what they bought.
     */
    val OWNED_PRODUCT_IDS = stringSetPreferencesKey("owned_product_ids")

    /**
     * Pack names granted for free because the user already had them when packs
     * became paid. Permanent, and never cleared on sign-out.
     *
     * Separate from [OWNED_PRODUCT_IDS] rather than written into it as a fake
     * purchase: Play would drop an id it does not recognise on the next refresh,
     * and a grant with no receipt behind it should not be able to masquerade as
     * one.
     */
    val GRANDFATHERED_CLOCK_FACE_PACKS =
        stringSetPreferencesKey("grandfathered_clock_face_packs")

    /**
     * Whether the free-era grant above has been worked out for this device.
     *
     * A marker rather than "is the set empty?", because empty is a legitimate
     * outcome: a user who never added a pack grandfathers nothing, and re-running
     * the seed later would then hand them whatever they had bought in between.
     */
    val CLOCK_FACE_PACKS_GRANDFATHERED =
        booleanPreferencesKey("clock_face_packs_grandfathered")
}

data class AppPreferenceValues(
    val selectedTheme: String = "system",
    val onboardingCompleted: Boolean = false,
    val firstClockInCelebrated: Boolean = false,
    val firstClockInCelebrationPending: Boolean = false,
    val lastActiveUserId: String? = null,
    val deviceId: String? = null,
    val legacyDataAdopted: Boolean = false,
    val appLockEnabled: Boolean = false,
    val reduceMotionEnabled: Boolean = false,
    val setupChecklistDismissed: Boolean = false,
    val setupChecklistVisitedSteps: Set<String> = emptySet(),
    val setupChecklistCelebrated: Boolean = false,
    val paidProjectsDiscoveryDismissed: Boolean = false,
    /** Whether the Projects tab's how-to guide has been shown. */
    val projectsGuideSeen: Boolean = false,
    /** Raw `YYYY-MM`, or null if the reminder was never dismissed. Parsed by the domain layer. */
    val refundReminderDismissedMonth: String? = null,
    /** Raw face names, newest first. Resolved to the enum by the UI layer. */
    val recentClockFaces: List<String> = emptyList(),
    /** Raw pack names. Resolved, and widened to include the defaults, by the UI layer. */
    val installedClockFacePacks: Set<String> = emptySet(),
    /** Raw Play product ids, as last reported by Play. Resolved by the billing layer. */
    val ownedProductIds: Set<String> = emptySet(),
    /** Raw pack names granted for free at the paid-packs switch. */
    val grandfatheredClockFacePacks: Set<String> = emptySet(),
    /** Whether [grandfatheredClockFacePacks] has been worked out on this device. */
    val clockFacePacksGrandfathered: Boolean = false,
    /** On by default: pairing a watch should work without a settings visit. */
    val wearSyncEnabled: Boolean = true,
)

class AppPreferencesRepository(private val context: Context) :
    AppPreferencesStore,
    AppLockPreferencesStore,
    OnboardingPreferences,
    SetupChecklistPreferences,
    FeatureDiscoveryPreferences,
    ClockFacePreferences,
    PurchasePreferences,
    WearSyncPreferences {

    /**
     * Every stored preference, re-read on each write.
     *
     * Retried rather than caught. `MainActivity` collects this to decide the
     * theme and — through `AppLockGate` — whether the app is locked, and it
     * deliberately treats "no value yet" as *unknown* rather than "lock off".
     * That is the right call for security and it makes this flow load-bearing
     * for the whole UI: if the collection ends, nothing downstream ever gets a
     * value and the app sits on its loading screen for good.
     *
     * A corrupt file is already handled where the store is declared, but a
     * transient read failure — IO pressure, a device mid-restore — used to end
     * the flow permanently. Retrying keeps the app trying to boot instead of
     * dying quietly, and deliberately does *not* substitute defaults: emitting
     * `appLockEnabled = false` because a read failed would unlock a locked app,
     * which is not a trade this file gets to make.
     */
    /**
     * The entitlement values, or empty ones if the file cannot be read.
     *
     * No `retryWhen` and no crash-report here, unlike the main store: a corrupt
     * entitlements file must not stall the flow the UI collects, and answering
     * empty for this launch is recoverable — Play restores purchases on the next
     * foreground refresh, and the grandfathered set stays on disk rather than
     * being overwritten with nothing.
     */
    private val entitlements: Flow<EntitlementValues> =
        context.entitlementsDataStore.data
            .catch { cause ->
                if (cause !is IOException) throw cause
                CrashReporting.report(cause)
                emit(emptyPreferences())
            }
            .map { prefs ->
                EntitlementValues(
                    installedClockFacePacks =
                        prefs[AppPreferenceKeys.INSTALLED_CLOCK_FACE_PACKS] ?: emptySet(),
                    ownedProductIds = prefs[AppPreferenceKeys.OWNED_PRODUCT_IDS] ?: emptySet(),
                    grandfatheredClockFacePacks =
                        prefs[AppPreferenceKeys.GRANDFATHERED_CLOCK_FACE_PACKS] ?: emptySet(),
                    clockFacePacksGrandfathered =
                        prefs[AppPreferenceKeys.CLOCK_FACE_PACKS_GRANDFATHERED] ?: false,
                )
            }

    private data class EntitlementValues(
        val installedClockFacePacks: Set<String>,
        val ownedProductIds: Set<String>,
        val grandfatheredClockFacePacks: Set<String>,
        val clockFacePacksGrandfathered: Boolean,
    )

    override val preferences: Flow<AppPreferenceValues> =
        context.appPreferencesDataStore.data
            .retryWhen { cause, attempt ->
                if (cause !is IOException) return@retryWhen false
                CrashReporting.report(cause)
                delay(PREFERENCES_RETRY_DELAY_MILLIS * (attempt + 1).coerceAtMost(PREFERENCES_RETRY_BACKOFF_CAP))
                true
            }
            .map { prefs ->
            AppPreferenceValues(
                selectedTheme = prefs[AppPreferenceKeys.SELECTED_THEME] ?: "system",
                onboardingCompleted = prefs[AppPreferenceKeys.ONBOARDING_COMPLETED] ?: false,
                firstClockInCelebrated = prefs[AppPreferenceKeys.FIRST_CLOCK_IN_CELEBRATED] ?: false,
                firstClockInCelebrationPending = prefs[AppPreferenceKeys.FIRST_CLOCK_IN_CELEBRATION_PENDING] ?: false,
                lastActiveUserId = prefs[AppPreferenceKeys.LAST_ACTIVE_USER_ID],
                deviceId = prefs[AppPreferenceKeys.DEVICE_ID],
                legacyDataAdopted = prefs[AppPreferenceKeys.LEGACY_DATA_ADOPTED] ?: false,
                appLockEnabled = prefs[AppPreferenceKeys.APP_LOCK_ENABLED] ?: false,
                reduceMotionEnabled = prefs[AppPreferenceKeys.REDUCE_MOTION] ?: false,
                setupChecklistDismissed = prefs[AppPreferenceKeys.SETUP_CHECKLIST_DISMISSED] ?: false,
                setupChecklistVisitedSteps = prefs[AppPreferenceKeys.SETUP_CHECKLIST_VISITED_STEPS] ?: emptySet(),
                setupChecklistCelebrated = prefs[AppPreferenceKeys.SETUP_CHECKLIST_CELEBRATED] ?: false,
                paidProjectsDiscoveryDismissed =
                    prefs[AppPreferenceKeys.PAID_PROJECTS_DISCOVERY_DISMISSED] ?: false,
                projectsGuideSeen = prefs[AppPreferenceKeys.PROJECTS_GUIDE_SEEN] ?: false,
                wearSyncEnabled = prefs[AppPreferenceKeys.WEAR_SYNC_ENABLED] ?: true,
                refundReminderDismissedMonth =
                    prefs[AppPreferenceKeys.REFUND_REMINDER_DISMISSED_MONTH],
                recentClockFaces =
                    prefs[AppPreferenceKeys.RECENT_CLOCK_FACES].orEmpty()
                        .lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toList(),
                // Filled from the entitlements store by the combine below; the
                // values here are placeholders so the shape stays one data class.
                installedClockFacePacks = emptySet(),
                ownedProductIds = emptySet(),
                grandfatheredClockFacePacks = emptySet(),
                clockFacePacksGrandfathered = false,
            )
        }
            // Entitlements come from their own store, which has no
            // wipe-on-corruption handler: a corrupt preferences file must not be
            // able to revoke packs the user was granted for free, because that
            // grant exists nowhere else — not in Play, which never sold them, and
            // not in Supabase, deliberately.
            .combine(entitlements) { values, owned ->
                values.copy(
                    installedClockFacePacks = owned.installedClockFacePacks,
                    ownedProductIds = owned.ownedProductIds,
                    grandfatheredClockFacePacks = owned.grandfatheredClockFacePacks,
                    clockFacePacksGrandfathered = owned.clockFacePacksGrandfathered,
                )
            }

    private companion object {
        /** First retry delay after a failed preferences read; grows per attempt. */
        const val PREFERENCES_RETRY_DELAY_MILLIS = 200L

        /** Where the linear backoff stops growing, so retries stay responsive. */
        const val PREFERENCES_RETRY_BACKOFF_CAP = 10L
    }

    suspend fun setSelectedTheme(theme: String) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.SELECTED_THEME] = theme }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setFirstClockInCelebrated(celebrated: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.FIRST_CLOCK_IN_CELEBRATED] = celebrated }
    }

    override suspend fun setFirstClockInCelebrationPending(pending: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.FIRST_CLOCK_IN_CELEBRATION_PENDING] = pending }
    }

    suspend fun setLastActiveUserId(userId: String?) {
        context.appPreferencesDataStore.edit {
            if (userId != null) it[AppPreferenceKeys.LAST_ACTIVE_USER_ID] = userId
            else it.remove(AppPreferenceKeys.LAST_ACTIVE_USER_ID)
        }
    }

    suspend fun setDeviceId(deviceId: String) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.DEVICE_ID] = deviceId }
    }

    override suspend fun currentPreferences(): AppPreferenceValues = preferences.first()

    suspend fun setLegacyDataAdopted(adopted: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.LEGACY_DATA_ADOPTED] = adopted }
    }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.APP_LOCK_ENABLED] = enabled }
    }

    override suspend fun setReduceMotion(enabled: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.REDUCE_MOTION] = enabled }
    }

    override suspend fun setSetupChecklistDismissed(dismissed: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.SETUP_CHECKLIST_DISMISSED] = dismissed }
    }

    override suspend fun markSetupStepVisited(stepKey: String) {
        context.appPreferencesDataStore.edit {
            it[AppPreferenceKeys.SETUP_CHECKLIST_VISITED_STEPS] =
                (it[AppPreferenceKeys.SETUP_CHECKLIST_VISITED_STEPS] ?: emptySet()) + stepKey
        }
    }

    override suspend fun setSetupChecklistCelebrated(celebrated: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.SETUP_CHECKLIST_CELEBRATED] = celebrated }
    }

    override suspend fun setPaidProjectsDiscoveryDismissed(dismissed: Boolean) {
        context.appPreferencesDataStore.edit {
            it[AppPreferenceKeys.PAID_PROJECTS_DISCOVERY_DISMISSED] = dismissed
        }
    }

    override suspend fun setProjectsGuideSeen(seen: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.PROJECTS_GUIDE_SEEN] = seen }
    }

    override suspend fun setWearSyncEnabled(enabled: Boolean) {
        context.appPreferencesDataStore.edit { it[AppPreferenceKeys.WEAR_SYNC_ENABLED] = enabled }
    }

    override suspend fun setRefundReminderDismissedMonth(month: String) {
        context.appPreferencesDataStore.edit {
            it[AppPreferenceKeys.REFUND_REMINDER_DISMISSED_MONTH] = month
        }
    }

    override suspend fun setRecentClockFaces(styleNames: List<String>) {
        context.appPreferencesDataStore.edit {
            it[AppPreferenceKeys.RECENT_CLOCK_FACES] = styleNames.joinToString("\n")
        }
    }

    override suspend fun setInstalledClockFacePacks(packNames: Set<String>) {
        context.entitlementsDataStore.edit {
            it[AppPreferenceKeys.INSTALLED_CLOCK_FACE_PACKS] = packNames
        }
    }

    override suspend fun setOwnedProductIds(productIds: Set<String>) {
        context.entitlementsDataStore.edit {
            it[AppPreferenceKeys.OWNED_PRODUCT_IDS] = productIds
        }
    }

    /**
     * Writes the free-era grant and its marker in one edit.
     *
     * One transaction rather than two calls, because a process death between them
     * would leave the marker set with nothing granted — the user's packs gone, and
     * the seed that would have restored them already spent.
     */
    override suspend fun setGrandfatheredClockFacePacks(packNames: Set<String>) {
        context.entitlementsDataStore.edit {
            it[AppPreferenceKeys.GRANDFATHERED_CLOCK_FACE_PACKS] = packNames
            it[AppPreferenceKeys.CLOCK_FACE_PACKS_GRANDFATHERED] = true
        }
    }

    /**
     * Moves an existing install's entitlements into their own store, once.
     *
     * Runs before anything reads them. Without it the split would look exactly
     * like the corruption it exists to prevent: an upgrading user's packs would
     * read as empty on first launch, `seedIfNeeded` would find its marker gone and
     * re-derive the grant from an equally empty installed set, and the app would
     * offer to sell them packs they already had.
     *
     * Keyed on the marker being absent from the new store *and* present in the
     * old, so it neither re-runs nor fires on a fresh install. The old keys are
     * deliberately left in place: this is cheap, and a rollback to a build that
     * still reads them finds them intact.
     */
    suspend fun migrateEntitlementsIfNeeded() {
        val already = context.entitlementsDataStore.data.first()
        if (already[AppPreferenceKeys.CLOCK_FACE_PACKS_GRANDFATHERED] != null) return
        val old = context.appPreferencesDataStore.data.first()
        if (old[AppPreferenceKeys.CLOCK_FACE_PACKS_GRANDFATHERED] == null &&
            old[AppPreferenceKeys.OWNED_PRODUCT_IDS] == null &&
            old[AppPreferenceKeys.INSTALLED_CLOCK_FACE_PACKS] == null
        ) {
            return
        }
        context.entitlementsDataStore.edit { new ->
            old[AppPreferenceKeys.INSTALLED_CLOCK_FACE_PACKS]?.let {
                new[AppPreferenceKeys.INSTALLED_CLOCK_FACE_PACKS] = it
            }
            old[AppPreferenceKeys.OWNED_PRODUCT_IDS]?.let {
                new[AppPreferenceKeys.OWNED_PRODUCT_IDS] = it
            }
            old[AppPreferenceKeys.GRANDFATHERED_CLOCK_FACE_PACKS]?.let {
                new[AppPreferenceKeys.GRANDFATHERED_CLOCK_FACE_PACKS] = it
            }
            old[AppPreferenceKeys.CLOCK_FACE_PACKS_GRANDFATHERED]?.let {
                new[AppPreferenceKeys.CLOCK_FACE_PACKS_GRANDFATHERED] = it
            }
        }
    }

    /**
     * Clears the first-run nudge bookkeeping on sign-out so the next account on
     * this device gets its own onboarding, checklist, celebrations and feature
     * discovery instead of inheriting the previous user's. Deliberately keeps
     * genuinely device-scoped state: theme, reduce motion, app lock, device id,
     * legacy-adoption marker, and the once-per-install notification education.
     *
     * Purchases are kept too, and for a stronger reason than "device-scoped":
     * Play holds them against the Google account, not the ElmTrackr one, so
     * clearing them here would strand a paying user behind a sign-out they had no
     * reason to connect to it. The next refresh re-reads them from Play anyway.
     */
    suspend fun resetFirstRunNudges() {
        context.appPreferencesDataStore.edit {
            it.remove(AppPreferenceKeys.ONBOARDING_COMPLETED)
            it.remove(AppPreferenceKeys.FIRST_CLOCK_IN_CELEBRATED)
            it.remove(AppPreferenceKeys.FIRST_CLOCK_IN_CELEBRATION_PENDING)
            it.remove(AppPreferenceKeys.SETUP_CHECKLIST_DISMISSED)
            it.remove(AppPreferenceKeys.SETUP_CHECKLIST_VISITED_STEPS)
            it.remove(AppPreferenceKeys.SETUP_CHECKLIST_CELEBRATED)
            it.remove(AppPreferenceKeys.PAID_PROJECTS_DISCOVERY_DISMISSED)
            // The guide is per-person, not per-device: the next account on this
            // phone has not seen how projects work just because the last one did.
            it.remove(AppPreferenceKeys.PROJECTS_GUIDE_SEEN)
            // Added when the two changes met: the refund reminder is a per-user
            // nudge dismissal like the others above, so leaving it would silence a
            // new account's reminder for whatever month the previous user dismissed.
            it.remove(AppPreferenceKeys.REFUND_REMINDER_DISMISSED_MONTH)
        }
    }
}
