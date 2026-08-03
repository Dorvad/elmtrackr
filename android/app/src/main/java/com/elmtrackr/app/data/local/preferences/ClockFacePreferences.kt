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
}
