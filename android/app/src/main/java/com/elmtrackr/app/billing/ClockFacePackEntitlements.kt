package com.elmtrackr.app.billing

import com.elmtrackr.app.ui.settings.ClockFaceGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether the user may add a clock face pack.
 *
 * The one seam a purchase flow needs. Every path that installs a pack asks here
 * first, so which packs are paid is decided in one place — and swapping the Hilt
 * binding for [PlayClockFacePackStore] made packs paid without a single call site
 * changing.
 *
 * Deliberately narrow: one question, one answer, no Activity, no prices. What it
 * costs and how it is bought live in [ClockFacePackStore], which only the gallery
 * needs. Keeping them apart is what lets a background caller check entitlement
 * without dragging Play in behind it.
 */
interface ClockFacePackEntitlements {

    /** True when [pack] may be installed. */
    suspend fun isEntitled(pack: ClockFaceGroup): Boolean

    /**
     * True when any pack requires something of the user before it can be added.
     * Lets a screen decide whether to say "add" or something about buying, without
     * knowing which it is.
     */
    suspend fun anyPackRequiresPurchase(): Boolean

    /**
     * The packs the user owns, re-emitted as purchases land.
     *
     * Observable rather than a suspend read because ownership changes while a
     * screen is open: the gallery is what the user is looking at when Play
     * confirms their purchase, and a one-shot read would leave it showing a Buy
     * button for a pack they had just bought.
     */
    fun observeOwned(): Flow<Set<ClockFaceGroup>>
}

/**
 * Every pack free.
 *
 * Not a test double — this is what ships until the products in
 * [ClockFacePackProducts] are live in Play Console and
 * `PAID_CLOCK_FACE_PACKS` is switched on. Until then it keeps behaviour exactly
 * as it was through 1.2.4, so the billing code below can be merged, reviewed and
 * released without changing what any user sees.
 */
@Singleton
class FreeClockFacePackEntitlements @Inject constructor() : ClockFacePackEntitlements {
    override suspend fun isEntitled(pack: ClockFaceGroup): Boolean = true
    override suspend fun anyPackRequiresPurchase(): Boolean = false
    override fun observeOwned(): Flow<Set<ClockFaceGroup>> =
        flowOf(ClockFacePackProducts.purchasablePacks.toSet())
}
