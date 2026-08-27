package com.elmtrackr.app.data.local.preferences

import kotlinx.coroutines.flow.Flow

/**
 * What this device remembers about what the user has bought.
 *
 * Device-local because it is a cache of Play's record, not a second record.
 * Purchases belong to the Google account and are re-read from Play on every
 * foreground; nothing here is ever the reason a pack is granted for the first
 * time. It exists so the answer survives being offline and so no screen has to
 * render a "not owned" frame while the billing service connects.
 *
 * Deliberately not in the Supabase contract. A purchase synced through the
 * ElmTrackr account would be an entitlement the app itself hands out, which is a
 * far more attractive thing to forge than a DataStore file on one phone, and it
 * would disagree with Play the moment a refund is issued.
 */
interface PurchasePreferences {
    val preferences: Flow<AppPreferenceValues>

    /** Replaces the cached purchase list with what Play just reported. */
    suspend fun setOwnedProductIds(productIds: Set<String>)

    /**
     * Records the packs granted for free at the paid-packs switch, and marks the
     * grant as worked out. Writing both together is the point; see the
     * implementation.
     */
    suspend fun setGrandfatheredClockFacePacks(packNames: Set<String>)
}
