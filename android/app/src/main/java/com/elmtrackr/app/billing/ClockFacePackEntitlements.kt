package com.elmtrackr.app.billing

import com.elmtrackr.app.ui.settings.ClockFaceGroup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether the user may add a clock face pack.
 *
 * The one seam a purchase flow needs. Every path that installs a pack asks here
 * first, so making packs paid means replacing the Hilt binding with a Play Billing
 * implementation — no call site changes, and no screen learns what a purchase is.
 *
 * Deliberately narrow: one question, one answer. Prices, SKUs, restore flows and
 * purchase acknowledgement are not modelled, because guessing their shape before
 * there is a billing integration to shape them against produces scaffolding that
 * has to be thrown away. What is fixed here is only that the decision has exactly
 * one home.
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
}

/**
 * Every pack free, which is the current product state.
 *
 * Not a test double — this is what ships. It exists so the entitlement check is
 * already threaded through every call site before there is any billing, which is
 * the whole point of putting the seam in early.
 */
@Singleton
class FreeClockFacePackEntitlements @Inject constructor() : ClockFacePackEntitlements {
    override suspend fun isEntitled(pack: ClockFaceGroup): Boolean = true
    override suspend fun anyPackRequiresPurchase(): Boolean = false
}
