package com.elmtrackr.app.billing

import android.app.Activity
import com.elmtrackr.app.ui.settings.ClockFaceGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * How the clock face packs are sold.
 *
 * Split from [ClockFacePackEntitlements] on purpose. Entitlements answer one
 * question — may this pack be added — and every screen that installs a pack has
 * to ask it. The storefront is a much larger surface (prices, an Activity, a
 * purchase that can fail six ways) and only the gallery needs it, so binding the
 * two together would drag Play into every caller that just wanted a yes or no.
 */
interface ClockFacePackStore {

    /** What the gallery renders. Emits again whenever prices or ownership change. */
    val storefront: StateFlow<ClockFacePackStorefront>

    /**
     * Purchase outcomes, one per completed attempt.
     *
     * A separate stream rather than a return value from [launchPurchase] because
     * Play reports the result through its own listener, minutes later if the user
     * pays at a kiosk. A suspending call would have to either hang on that or lie
     * about it.
     */
    val events: Flow<PackPurchaseEvent>

    /**
     * Opens Play's purchase sheet for [pack].
     *
     * Returns once the sheet is up (or could not be shown). The purchase itself
     * lands on [events] and [storefront].
     */
    suspend fun launchPurchase(activity: Activity, pack: ClockFaceGroup)

    /**
     * Opens Play's purchase sheet for [ClockFacePackProducts.ALL_PACKS].
     *
     * Its own method rather than a nullable pack argument: "every pack, including
     * ones that do not exist yet" is a different product from any single pack, and
     * a caller that had to pass null to mean it would be one typo away from
     * charging for the wrong thing.
     */
    suspend fun launchAllPacksPurchase(activity: Activity)

    /**
     * Re-reads what the account owns from Play.
     *
     * This is the whole of "restore purchases": Play is the record, so a
     * reinstall, a new device or a refund is picked up by asking again. There is
     * deliberately no button for it — [refresh] runs on every foreground, which
     * is what makes a restore button unnecessary.
     */
    suspend fun refresh()
}

/**
 * Everything the gallery needs to draw the packs, resolved together.
 *
 * One object rather than separate flows for prices and ownership: they are read
 * in the same frame and a mismatch between them — a price on a pack that is
 * already owned — is exactly the flicker that separate emissions produce.
 */
data class ClockFacePackStorefront(
    /** Packs the user may add. See [ClockFacePackOwnership]. */
    val owned: Set<ClockFaceGroup> = emptySet(),
    /**
     * Play's own localized price per pack, e.g. `₪7.90`.
     *
     * Formatted by Play rather than by the app, and never cached across
     * locales or currencies: the price shown must be the price charged, and
     * Play is the only thing that knows what that is in this account's country.
     * A missing entry means Play has not answered yet, not that the pack is free.
     */
    val prices: Map<ClockFaceGroup, String> = emptyMap(),
    /** Localized price of [ClockFacePackProducts.ALL_PACKS], or null until Play answers. */
    val allPacksPrice: String? = null,
    val availability: BillingAvailability = BillingAvailability.LOADING,
) {
    /** True when every sellable pack is already the user's, so nothing is left to offer. */
    val everythingOwned: Boolean
        get() = owned.containsAll(ClockFacePackProducts.purchasablePacks)

    fun isOwned(pack: ClockFaceGroup): Boolean = pack.isBundled || pack in owned

    fun priceOf(pack: ClockFaceGroup): String? = prices[pack]
}

/** Whether Play can sell anything on this device right now. */
enum class BillingAvailability {
    /** Still connecting or still waiting on the first price query. */
    LOADING,

    /** Play answered and the products are sellable. */
    AVAILABLE,

    /**
     * No Play billing here — a sideload, an emulator without Play, a device
     * where the Store is disabled, or products not yet live in Play Console.
     *
     * The gallery says so rather than showing a Buy button that would fail at
     * tap time. Packs already owned or already added are untouched.
     */
    UNAVAILABLE,
}

/** The result of one purchase attempt, for a one-off message to the user. */
sealed interface PackPurchaseEvent {

    /** Paid and granted. [pack] is now in [ClockFacePackStorefront.owned]. */
    data class Purchased(val packs: Set<ClockFaceGroup>) : PackPurchaseEvent

    /**
     * Payment is still being taken — a cash or bank-transfer method Play settles
     * later. Nothing is granted yet, which is exactly why pending purchases have
     * to be enabled explicitly on the billing client rather than assumed away.
     */
    data object Pending : PackPurchaseEvent

    /** The user backed out. Not an error, and never worth an error message. */
    data object Cancelled : PackPurchaseEvent

    /** Play already had this purchase; ownership was re-applied from its record. */
    data object AlreadyOwned : PackPurchaseEvent

    /** Play could not complete the purchase. [debugMessage] is for logs, never for the UI. */
    data class Failed(val responseCode: Int, val debugMessage: String) : PackPurchaseEvent
}
