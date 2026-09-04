package com.elmtrackr.app.billing

import com.elmtrackr.app.data.local.preferences.ClockFacePreferences
import com.elmtrackr.app.monitoring.CrashReporting
import com.elmtrackr.app.di.ApplicationScope
import com.elmtrackr.app.ui.settings.ClockFaceGroup
import com.elmtrackr.app.ui.settings.ClockFacePacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps what the app believes about purchases in step with what Play holds.
 *
 * One entry point, called when the app comes to the foreground. That single
 * moment covers everything a restore button would: a reinstall, a new device, a
 * refund, a switched Google account, and a purchase completed in the Play app
 * after the user left ElmTrackr. Asking Play is cheap and it is the only source
 * that can be right, so there is nothing to gain by asking less often — and a
 * "Restore purchases" button the user has to find is a support ticket waiting to
 * happen.
 *
 * Fire-and-forget on the application scope: nothing on screen waits for it. The
 * gallery renders from the cached ownership immediately and updates in place if
 * Play's answer differs.
 */
@Singleton
class ClockFacePackBillingCoordinator @Inject constructor(
    private val grandfathering: ClockFacePackGrandfathering,
    private val store: ClockFacePackStore,
    private val clockFacePreferences: ClockFacePreferences,
    private val appPreferences: com.elmtrackr.app.data.local.preferences.AppPreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {

    init {
        // Owning a pack and having it are two different things everywhere else in
        // this feature, and deliberately so — but not here. Someone who has just
        // paid for a pack has said what they want it for; making them find the Add
        // button afterwards would be asking the same question twice.
        //
        // Application-scoped rather than tied to the gallery, because Play can
        // confirm a purchase after the user has left the screen, or the app.
        scope.launch {
            store.events.collect { event ->
                if (event is PackPurchaseEvent.Purchased) install(event.packs)
            }
        }
    }

    fun onAppForegrounded() {
        scope.launch {
            // Ordered, not merely sequential: the free-era grant has to be on
            // disk before ownership is recomputed, or the first frame after an
            // update would show a user's own packs as locked.
            //
            // Independently guarded, though. These are two different systems —
            // local storage and Play — and a failure in the first used to skip
            // the second entirely, leaving ownership as whatever was last
            // cached with nothing retrying until the next foreground.
            // Before the seed, and that order is load-bearing. Entitlements moved
            // to their own DataStore so a corrupt app_preferences file cannot
            // revoke the free-era grant; on the first launch after that change an
            // upgrading user's packs live only in the old file. Seeding first would
            // read an empty new store, find the marker absent, re-derive the grant
            // from an equally empty installed set, and offer the user their own
            // packs for sale — the exact loss the split exists to prevent.
            runCatching { appPreferences.migrateEntitlementsIfNeeded() }
                .onFailure(CrashReporting::report)
            runCatching { grandfathering.seedIfNeeded() }
                .onFailure(CrashReporting::report)
            runCatching { store.refresh() }
                .onFailure(CrashReporting::report)
        }
    }

    private suspend fun install(packs: Set<ClockFaceGroup>) {
        if (packs.isEmpty()) return
        val stored = ClockFacePacks.resolve(
            clockFacePreferences.preferences.first().installedClockFacePacks,
        )
        if (stored.containsAll(packs)) return
        clockFacePreferences.setInstalledClockFacePacks(
            (stored + packs).mapTo(mutableSetOf()) { it.name },
        )
    }
}
