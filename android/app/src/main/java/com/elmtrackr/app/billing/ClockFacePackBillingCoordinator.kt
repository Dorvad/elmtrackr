package com.elmtrackr.app.billing

import com.elmtrackr.app.data.local.preferences.ClockFacePreferences
import com.elmtrackr.app.data.local.preferences.EntitlementsMigration
import com.elmtrackr.app.data.local.preferences.PurchasePreferences
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
 * moment covers everything a restore button covers: a reinstall, a new device, a
 * refund, a switched Google account, and a purchase completed in the Play app
 * after the user left ElmTrackr. Asking Play is cheap and it is the only source
 * that can be right, so there is nothing to gain by asking less often. The store
 * has a Restore button as well, and it runs the same refresh — it is there so a
 * user who has just reinstalled can see the app trying, not because the
 * foreground path needs the help.
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
    private val purchasePreferences: PurchasePreferences,
    private val entitlementsMigration: EntitlementsMigration,
    @ApplicationScope private val scope: CoroutineScope,
) {

    init {
        // Owning a pack and having it are two different things everywhere else in
        // this feature, and deliberately so — but not here. Someone who has just
        // paid for a pack has said what they want it for; making them find the Add
        // button afterwards would be asking the same question twice.
        //
        // A restore is the same moment arriving late. Play returning a purchase
        // this device did not know about — a reinstall, a new phone, an
        // entitlements file that could not be read — means the user already made
        // that decision and paid for it; leaving the pack owned but not added
        // puts it on the shop shelf, which is the one place someone looking for a
        // pack they own will not think to look. `one-time-products.md` §6 has
        // said so since the flag flip: *uninstall and reinstall — packs come back
        // with no restore button pressed.*
        //
        // Only what is new to the device, which is what [PackPurchaseEvent.Restored]
        // carries — the immediate half of the join. The steady state is
        // [reconcileAcquiredPacks], which catches every device this event cannot
        // reach; a removal survives both because it is recorded, not inferred.
        //
        // Application-scoped rather than tied to the gallery, because Play can
        // confirm a purchase after the user has left the screen, or the app.
        scope.launch {
            store.events.collect { event ->
                when (event) {
                    is PackPurchaseEvent.Purchased -> install(event.packs)
                    is PackPurchaseEvent.Restored -> install(event.packs)
                    else -> Unit
                }
            }
        }
    }

    fun onAppForegrounded() {
        scope.launch {
            // Ordered, not merely sequential: the free-era grant has to be on
            // disk before ownership is recomputed, or the first frame after an
            // update would show a user's own packs as locked.
            //
            // The migration comes first, and that order is load-bearing.
            // Entitlements moved to their own DataStore so a corrupt
            // app_preferences file cannot revoke the free-era grant; on the first
            // launch after that change an upgrading user's packs live only in the
            // old file. Seeding first would read an empty new store, find the
            // marker absent, re-derive the grant from an equally empty installed
            // set, and offer the user their own packs for sale — the exact loss
            // the split exists to prevent.
            //
            // Play is a separate system and stays independently guarded: a
            // failure in local storage must not skip the refresh, which is the
            // only thing that can recover a purchase.
            val migrated = runCatching { entitlementsMigration.migrateEntitlementsIfNeeded() }
                .onFailure(CrashReporting::report)
                .isSuccess
            // Guarded on the migration having actually run, not merely on the
            // order of the two calls. The seed is spent the first time it runs:
            // it writes its marker whether or not it granted anything, and it
            // derives the grant from the installed set in the *new* store. A
            // migration that threw leaves that store empty, so seeding after one
            // would grant nothing, mark the grant as worked out, and put the
            // user's free-era packs permanently on sale — the exact loss the
            // ordering above exists to prevent, arrived at through failure
            // instead of through sequence. Skipping costs one launch: the next
            // foreground retries both.
            if (migrated) {
                runCatching { grandfathering.seedIfNeeded() }
                    .onFailure(CrashReporting::report)
            }
            runCatching { store.refresh() }
                .onFailure(CrashReporting::report)
            // After the refresh, so Play's answer is in the cache being read.
            runCatching { reconcileAcquiredPacks() }
                .onFailure(CrashReporting::report)
        }
    }

    /**
     * Puts back a pack the user has acquired and does not have.
     *
     * The event-driven install above joins ownership to installation at the
     * moment ownership *changes*, which is right for the device that is watching
     * when it happens and useless for every other one. A device that reinstalled
     * before that join existed already has the product id in its cache, so Play
     * reports nothing new, [PackPurchaseEvent.Restored] never fires again, and
     * the pack sits behind an Add button on the shop shelf for good. The same
     * dead end follows a single failed write of the installed set: nothing
     * retries it, because the retry was keyed to an event that has been and gone.
     *
     * So the join is stated as an invariant instead of an event — a pack the user
     * acquired is installed unless they removed it — and checked on every
     * foreground. The record of removals is what makes that safe: without one,
     * "acquired and absent" would also describe a pack the
     * user deliberately took off their list, and reconciling would put it back
     * every time the app checked with Play.
     *
     * Read from [PurchasePreferences] rather than from the storefront, and that
     * is load-bearing: [FreeClockFacePackStore] reports every pack as owned so the
     * gallery renders as it did before packs were sold, and reconciling against
     * *that* would install the whole catalogue on every device in a free build.
     * Only a Play purchase or the free-era grant counts as acquired.
     *
     * One-time cost, worth naming: a user who removed a pack they own before this
     * record existed has no removal on file, so it comes back once. Getting a pack
     * back that you own is a smaller harm than being locked out of one you paid
     * for, and it only happens on the first foreground after the update.
     */
    private suspend fun reconcileAcquiredPacks() {
        val purchases = purchasePreferences.preferences.first()
        val acquired = ClockFacePackOwnership.owned(
            purchasedProductIds = purchases.ownedProductIds,
            grandfathered = ClockFacePacks.resolve(purchases.grandfatheredClockFacePacks),
        )
        if (acquired.isEmpty()) return
        val faces = clockFacePreferences.preferences.first()
        val installed = ClockFacePacks.resolve(faces.installedClockFacePacks)
        val missing = acquired - installed - ClockFacePacks.resolve(faces.removedClockFacePacks)
        if (missing.isEmpty()) return
        clockFacePreferences.setInstalledClockFacePacks(
            (installed + missing).mapTo(mutableSetOf()) { it.name },
        )
    }

    private suspend fun install(packs: Set<ClockFaceGroup>) {
        if (packs.isEmpty()) return
        val prefs = clockFacePreferences.preferences.first()
        // Buying or restoring a pack retracts an earlier removal of it, the same
        // way adding it by hand does. Left on file, the record would send the next
        // reconcile past a pack the user has just paid for.
        val removed = ClockFacePacks.resolve(prefs.removedClockFacePacks)
        if (removed.any { it in packs }) {
            clockFacePreferences.setRemovedClockFacePacks(
                (removed - packs).mapTo(mutableSetOf()) { it.name },
            )
        }
        val stored = ClockFacePacks.resolve(prefs.installedClockFacePacks)
        if (stored.containsAll(packs)) return
        clockFacePreferences.setInstalledClockFacePacks(
            (stored + packs).mapTo(mutableSetOf()) { it.name },
        )
    }
}
