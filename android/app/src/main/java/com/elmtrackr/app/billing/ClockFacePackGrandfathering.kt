package com.elmtrackr.app.billing

import com.elmtrackr.app.data.local.preferences.ClockFacePreferences
import com.elmtrackr.app.data.local.preferences.PurchasePreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands the free era's packs to the people who were in it.
 *
 * Runs once per device, the first time a build that charges for packs starts.
 * Whatever the user had installed at that moment becomes permanently theirs; the
 * charge applies only to packs they had not taken. See
 * [ClockFacePackOwnership.grandfatherSeed] for why that is the right trade.
 *
 * **Timing is the whole design.** The seed has to be read on the first run of the
 * paid build and not a moment earlier, so a user who adds three more packs while
 * they are still free keeps all three. That is why this is gated on the build
 * flag rather than run unconditionally: a seed taken during the free era would
 * freeze ownership at whatever the user happened to have that week.
 */
@Singleton
class ClockFacePackGrandfathering @Inject constructor(
    private val clockFacePreferences: ClockFacePreferences,
    private val purchasePreferences: PurchasePreferences,
) {

    /**
     * Seeds the grant if it has not been seeded.
     *
     * Idempotent by marker rather than by emptiness, because granting nothing is
     * a legitimate result — a user who never opened the gallery grandfathers no
     * packs, and re-running the seed later would then hand them whatever they had
     * bought in the meantime.
     */
    suspend fun seedIfNeeded() {
        if (purchasePreferences.preferences.first().clockFacePacksGrandfathered) return
        val installed = clockFacePreferences.preferences.first().installedClockFacePacks
        purchasePreferences.setGrandfatheredClockFacePacks(
            ClockFacePackOwnership.grandfatherSeed(installed).mapTo(mutableSetOf()) { it.name },
        )
    }
}
