package com.elmtrackr.app.data.local.preferences

import kotlinx.coroutines.flow.Flow

/**
 * The recently-used clock faces, newest first.
 *
 * Device-local rather than synced, and deliberately so: which faces you tried
 * lately is a property of how you use this phone, not of your account, and it
 * has no business occupying a column in the Supabase contract. Losing it costs
 * the user nothing — the appearance screen falls back to the defaults.
 */
interface ClockFacePreferences {
    val preferences: Flow<AppPreferenceValues>

    /** Records [styleName] as the most recent face, bounded by the caller. */
    suspend fun setRecentClockFaces(styleNames: List<String>)

    /**
     * The face packs the user has added.
     *
     * Device-local like the history above, and for a stronger reason: which packs
     * are present is what this device shows, and syncing it would mean a user
     * removing a pack on a tablet silently removing it from their phone. The
     * selected face itself does sync — it is in the Supabase contract — which is
     * why the available set is derived from the stored set *plus* the selection
     * rather than read straight from storage.
     */
    suspend fun setInstalledClockFacePacks(packNames: Set<String>)

    /**
     * The packs the user has taken off this device on purpose.
     *
     * Recorded rather than inferred from absence, because absence has two causes
     * that call for opposite handling: a pack the user removed should stay
     * removed, and a pack they own but never had installed should be put back.
     * `ClockFacePackBillingCoordinator` is what tells them apart, and this is
     * what it tells them apart with.
     */
    suspend fun setRemovedClockFacePacks(packNames: Set<String>)
}

/**
 * Moving an existing install's entitlements into their own store, once.
 *
 * Its own interface, and a one-method one, because the order it runs in relative
 * to the free-era seed is load-bearing and the failure case is what
 * `ClockFacePackBillingCoordinator` has to get right: a seed that runs after a
 * migration that threw grants nothing and marks the grant as worked out. That is
 * a rule worth a test, and a test needs something that can fail on request.
 */
interface EntitlementsMigration {
    suspend fun migrateEntitlementsIfNeeded()
}
