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
     * Re-reads what the account owns from Play, and reports what that recovered.
     *
     * This is the whole of "restore purchases": Play is the record, so a
     * reinstall, a new device or a refund is picked up by asking again. It runs
     * on every foreground as well as behind the store's Restore button, so the
     * common case is that nothing has changed and the answer is
     * [PackRestoreResult.NothingRestored].
     *
     * Returning a result rather than Unit is what lets the button say something.
     * A restore that silently succeeds and a restore that silently failed look
     * identical to the person who pressed it, and "nothing happened" is the one
     * outcome a restore affordance must never produce.
     */
    suspend fun refresh(): PackRestoreResult
}

/**
 * What one call to [ClockFacePackStore.refresh] recovered.
 *
 * Three outcomes rather than a boolean, because "nothing came back" and "we
 * could not ask" are different things the user needs told differently: the
 * first means their purchases are already in place, the second means try again
 * with a connection.
 */
sealed interface PackRestoreResult {

    /**
     * Play reported packs this device did not know it owned, and they have been
     * granted. A reinstall's first refresh is the ordinary case.
     */
    data class Restored(val packs: Set<ClockFaceGroup>) : PackRestoreResult

    /** Play answered and had nothing this device was missing. */
    data object NothingRestored : PackRestoreResult

    /** Play could not be asked, so nothing is known and nothing was changed. */
    data object Unavailable : PackRestoreResult
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
    /**
     * The same prices as raw amounts, for arithmetic the app may do but must
     * never print.
     *
     * Micros because that is what Play reports and it is exact — a price in
     * whole currency units would round, and a rounded saving is a wrong one.
     * Formatting these back into a currency string is deliberately not done
     * anywhere: the app would have to guess a symbol, a separator and a position
     * that Play already knows, and the two would disagree in some locale nobody
     * tested.
     */
    val priceMicros: Map<ClockFaceGroup, Long> = emptyMap(),
    /** Localized price of [ClockFacePackProducts.ALL_PACKS], or null until Play answers. */
    val allPacksPrice: String? = null,
    /** The bundle price as a raw amount. See [priceMicros]. */
    val allPacksPriceMicros: Long? = null,
    val availability: BillingAvailability = BillingAvailability.LOADING,
) {
    /** True when every sellable pack is already the user's, so nothing is left to offer. */
    val everythingOwned: Boolean
        get() = owned.containsAll(ClockFacePackProducts.purchasablePacks)

    /** The packs still for sale to this user, in gallery order. */
    val unownedPacks: List<ClockFaceGroup>
        get() = ClockFacePackProducts.purchasablePacks.filterNot { it in owned }

    /**
     * Whether the everything-at-once offer is worth putting in front of the user.
     *
     * Not simply "anything left unowned". Someone one pack short is better served
     * by buying that pack, and a bundle offered beside it is either worse value or
     * asks them to pay twice for what they have. Below two packs the honest move
     * is to say nothing.
     */
    val offerAllPacks: Boolean
        get() = unownedPacks.size >= MIN_PACKS_FOR_BUNDLE

    /**
     * How much less the bundle costs than the packs it would actually add, as a
     * whole percentage — or null when that cannot be said truthfully.
     *
     * Measured against what *this* user still needs, not against the full set.
     * Someone who already owns two packs is not saving anything on those, and a
     * badge computed from the catalogue price would quietly overstate the deal by
     * exactly the amount they had already paid.
     *
     * Null whenever any input is missing or the bundle is not genuinely cheaper.
     * That is the important half: mispricing the bundle in Play Console makes the
     * badge disappear rather than making the app claim a saving that is not there.
     */
    val allPacksSavingPercent: Int?
        get() {
            val bundle = allPacksPriceMicros ?: return null
            val packs = unownedPacks
            if (packs.size < MIN_PACKS_FOR_BUNDLE) return null
            val each = packs.map { priceMicros[it] }
            if (each.any { it == null }) return null
            val separately = each.filterNotNull().sum()
            if (separately <= 0L || bundle >= separately) return null
            return ((separately - bundle) * 100 / separately).toInt()
        }

    fun isOwned(pack: ClockFaceGroup): Boolean = pack.isBundled || pack in owned

    fun priceOf(pack: ClockFaceGroup): String? = prices[pack]

    private companion object {
        /** Below this many packs left, the bundle is not the right advice. */
        const val MIN_PACKS_FOR_BUNDLE = 2
    }
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

    /**
     * Play reported packs this device did not know it owned — a reinstall, a new
     * device, or an entitlements file that could not be read last launch.
     *
     * Carried on the same stream as a purchase because it grants the same thing
     * and has to be honoured the same way: whoever installs a pack after a
     * purchase has to install one after a restore, or the user is left owning a
     * pack that is nowhere they would look for it.
     *
     * Only ever the packs that are *new to this device*. A pack the user owns and
     * deliberately removed is already in the cache, so it is not reported again
     * and does not reappear behind their back on the next foreground.
     *
     * A purchase completing at the same moment as a refresh can produce this and
     * [Purchased] for the same pack — the interleaving the ownership mutex in
     * [PlayClockFacePackStore] is written around. Both grant the same thing and
     * installing is idempotent, so the cost is one extra snackbar in a race, not
     * a wrong result.
     */
    data class Restored(val packs: Set<ClockFaceGroup>) : PackPurchaseEvent

    /** Play could not complete the purchase. [debugMessage] is for logs, never for the UI. */
    data class Failed(val responseCode: Int, val debugMessage: String) : PackPurchaseEvent
}
