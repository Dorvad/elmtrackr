package com.elmtrackr.app.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.elmtrackr.app.data.local.preferences.PurchasePreferences
import com.elmtrackr.app.di.ApplicationScope
import com.elmtrackr.app.ui.settings.ClockFaceGroup
import com.elmtrackr.app.ui.settings.ClockFacePacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The clock face packs, sold through Google Play.
 *
 * Implements both halves of the seam — [ClockFacePackEntitlements] for the one
 * question the rest of the app asks, [ClockFacePackStore] for the gallery's
 * storefront — because they are two views of the same state and splitting the
 * state between two objects is how a Buy button ends up on a pack the user
 * already owns.
 *
 * **Play is the record; this class is a projection of it.** Nothing here decides
 * that a purchase happened. Every grant traces back either to a row Play
 * returned, or to the free-era grant in [ClockFacePackOwnership.grandfatherSeed],
 * and the cached copy in [PurchasePreferences] exists only so the answer survives
 * being offline. That is deliberate: an entitlement the app can talk itself into
 * is an entitlement anyone with a rooted phone can talk it into.
 */
@Singleton
class PlayClockFacePackStore @Inject constructor(
    private val connection: PlayBillingConnection,
    private val purchasePreferences: PurchasePreferences,
    @ApplicationScope private val scope: CoroutineScope,
) : ClockFacePackStore, ClockFacePackEntitlements {

    private val _storefront = MutableStateFlow(ClockFacePackStorefront())
    override val storefront: StateFlow<ClockFacePackStorefront> = _storefront.asStateFlow()

    private val _events = MutableSharedFlow<PackPurchaseEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<PackPurchaseEvent> = _events.asSharedFlow()

    /**
     * Play's product details, kept from the last query.
     *
     * Held so a tap on Buy can open the sheet immediately. `launchBillingFlow`
     * needs the [ProductDetails] object Play itself returned — a product id is not
     * enough — so without this every purchase would begin with a network round
     * trip the user waits through.
     */
    @Volatile
    private var products: Map<String, ProductDetails> = emptyMap()

    init {
        // Ownership is read from storage rather than computed at the point of
        // purchase, so the grandfathered grant and a Play purchase reach the
        // storefront by the same path and cannot disagree.
        scope.launch {
            purchasePreferences.preferences
                .map { prefs ->
                    ClockFacePackOwnership.owned(
                        purchasedProductIds = prefs.ownedProductIds,
                        grandfathered = ClockFacePacks.resolve(prefs.grandfatheredClockFacePacks),
                    )
                }
                .distinctUntilChanged()
                .collect { owned -> _storefront.update { it.copy(owned = owned) } }
        }
        scope.launch {
            connection.purchaseUpdates.collect(::onPurchaseUpdate)
        }
    }

    override suspend fun isEntitled(pack: ClockFaceGroup): Boolean =
        pack.isBundled || pack in currentOwned()

    override suspend fun anyPackRequiresPurchase(): Boolean =
        !currentOwned().containsAll(ClockFacePackProducts.purchasablePacks)

    override fun observeOwned(): Flow<Set<ClockFaceGroup>> =
        storefront.map { it.owned }.distinctUntilChanged()

    /**
     * Ownership as stored, not as last rendered.
     *
     * [isEntitled] can be asked before the first collection of the preferences
     * flow above has run — a background caller in the same millisecond as
     * start-up — and answering "no" then would refuse a pack the user owns. A
     * direct read costs one DataStore hit and cannot be early.
     */
    private suspend fun currentOwned(): Set<ClockFaceGroup> {
        val prefs = purchasePreferences.preferences.first()
        return ClockFacePackOwnership.owned(
            purchasedProductIds = prefs.ownedProductIds,
            grandfathered = ClockFacePacks.resolve(prefs.grandfatheredClockFacePacks),
        )
    }

    override suspend fun refresh() {
        val details = connection.queryOneTimeProducts(ClockFacePackProducts.all)
        val purchases = connection.queryOwnedPurchases()

        if (details != null) {
            products = details.associateBy { it.productId }
        }

        // Play answered with the account's complete purchase list, so this
        // replaces the cache rather than adding to it — that is what makes a
        // refund, a revoked purchase or a switched Google account take effect.
        // A failed query leaves the cache alone instead of clearing it.
        if (purchases != null) {
            val settled = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            purchasePreferences.setOwnedProductIds(
                settled.flatMapTo(mutableSetOf()) { it.products },
            )
            acknowledgeAll(settled)
        }

        _storefront.update { current ->
            current.copy(
                prices = pricesByPack(),
                priceMicros = priceMicrosByPack(),
                allPacksPrice = formattedPrice(ClockFacePackProducts.ALL_PACKS),
                allPacksPriceMicros = priceMicros(ClockFacePackProducts.ALL_PACKS),
                availability = availabilityAfter(details, current),
            )
        }
    }

    override suspend fun launchPurchase(activity: Activity, pack: ClockFaceGroup) {
        val productId = ClockFacePackProducts.productId(pack)
        if (productId == null) {
            // A bundled pack has nothing to sell. Reaching here means a caller
            // offered Buy on the free pack, which is a bug in that caller, not
            // something to charge the user for.
            return
        }
        launchProduct(activity, productId)
    }

    override suspend fun launchAllPacksPurchase(activity: Activity) {
        launchProduct(activity, ClockFacePackProducts.ALL_PACKS)
    }

    private suspend fun launchProduct(activity: Activity, productId: String) {
        // One retry through a refresh, because the details can be missing for an
        // ordinary reason — the gallery was opened before Play finished
        // connecting — and failing the tap outright would make the user press
        // Buy twice to buy once.
        val product = products[productId] ?: run {
            refresh()
            products[productId]
        }
        if (product == null) {
            _events.emit(
                PackPurchaseEvent.Failed(
                    responseCode = BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                    debugMessage = "No Play product details for $productId",
                ),
            )
            return
        }
        // launchBillingFlow is documented as main-thread only, and this is called
        // from a coroutine whose dispatcher the caller chose.
        val result = withContext(Dispatchers.Main) {
            connection.launchPurchase(activity, product)
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _events.emit(
                PackPurchaseEvent.Failed(result.responseCode, result.debugMessage),
            )
        }
    }

    /**
     * Applies one report from Play.
     *
     * Everything except OK is a message and nothing else: the storefront is
     * already correct, and re-querying on a cancelled purchase would spend a
     * round trip confirming what the user just told us.
     */
    private suspend fun onPurchaseUpdate(update: PurchaseUpdate) {
        when (update.result.responseCode) {
            BillingClient.BillingResponseCode.OK -> grant(update.purchases)

            BillingClient.BillingResponseCode.USER_CANCELED ->
                _events.emit(PackPurchaseEvent.Cancelled)

            // Play holds a purchase this device did not know about — a reinstall,
            // a second device, or an acknowledgement that failed last time. The
            // fix is to re-read Play, not to charge again.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                refresh()
                _events.emit(PackPurchaseEvent.AlreadyOwned)
            }

            else -> _events.emit(
                PackPurchaseEvent.Failed(
                    update.result.responseCode,
                    update.result.debugMessage,
                ),
            )
        }
    }

    private suspend fun grant(purchases: List<Purchase>) {
        val settled = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (settled.isEmpty()) {
            // Only pending payments came back. Nothing is granted until Play says
            // the money arrived; saying so is the whole reason pending purchases
            // are enabled explicitly on the client.
            if (purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
                _events.emit(PackPurchaseEvent.Pending)
            }
            return
        }

        val newIds = settled.flatMapTo(mutableSetOf()) { it.products }
        // Merged, not replaced: this update describes one purchase, while the
        // cache describes every purchase the account has.
        val existing = purchasePreferences.preferences.first().ownedProductIds
        purchasePreferences.setOwnedProductIds(existing + newIds)
        acknowledgeAll(settled)
        _events.emit(
            PackPurchaseEvent.Purchased(ClockFacePackProducts.packsGrantedBy(newIds)),
        )
    }

    /**
     * Acknowledges anything Play has not seen us honour.
     *
     * Run on every refresh as well as on purchase, because the acknowledgement
     * can fail on its own — no network at the moment of purchase — while the
     * purchase itself stands. An unacknowledged purchase is automatically
     * refunded and revoked after three days, so the retry is what stops a paying
     * user from silently losing their packs.
     */
    private suspend fun acknowledgeAll(purchases: List<Purchase>) {
        purchases.filterNot { it.isAcknowledged }.forEach { connection.acknowledge(it) }
    }

    private fun pricesByPack(): Map<ClockFaceGroup, String> =
        ClockFacePackProducts.purchasablePacks.mapNotNull { pack ->
            val price = ClockFacePackProducts.productId(pack)?.let(::formattedPrice)
            price?.let { pack to it }
        }.toMap()

    private fun priceMicrosByPack(): Map<ClockFaceGroup, Long> =
        ClockFacePackProducts.purchasablePacks.mapNotNull { pack ->
            val micros = ClockFacePackProducts.productId(pack)?.let(::priceMicros)
            micros?.let { pack to it }
        }.toMap()

    /**
     * The raw amount behind [formattedPrice], for the bundle's saving badge.
     *
     * Same two offer shapes as the formatted price, and read from the same offer,
     * so the number the badge is computed from is the number the user is shown.
     */
    private fun priceMicros(productId: String): Long? {
        val product = products[productId] ?: return null
        val offer = product.oneTimePurchaseOfferDetails
            ?: product.oneTimePurchaseOfferDetailsList?.firstOrNull()
            ?: return null
        return offer.priceAmountMicros
    }

    /**
     * Play's own price string for [productId], or null if it has not answered.
     *
     * Reads the single-offer getter first and falls back to the list. Play's
     * one-time products are configured as purchase options carrying offers, and
     * the list getter is documented as populated *only* for a product with more
     * than one offer — so a product that later gains a second offer, say an
     * introductory price, would report no price through the singular getter while
     * remaining perfectly buyable. That failure would surface as a Buy button with
     * a blank price, which reads as a broken app rather than a pricing change
     * made in the console. Checking both costs a line.
     */
    private fun formattedPrice(productId: String): String? {
        val product = products[productId] ?: return null
        return product.oneTimePurchaseOfferDetails?.formattedPrice
            ?: product.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
    }

    /**
     * What the storefront should say about Play after a query.
     *
     * A null answer means the question could not be asked. That is only worth
     * showing as unavailable the first time; once real prices are on screen,
     * blanking them on a dropped connection would be a worse lie than a
     * momentarily stale price.
     */
    private fun availabilityAfter(
        details: List<ProductDetails>?,
        current: ClockFacePackStorefront,
    ): BillingAvailability = when {
        details == null ->
            if (current.prices.isNotEmpty()) current.availability else BillingAvailability.UNAVAILABLE
        details.isEmpty() -> BillingAvailability.UNAVAILABLE
        else -> BillingAvailability.AVAILABLE
    }
}
