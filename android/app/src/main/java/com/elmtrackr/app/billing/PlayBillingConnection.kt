package com.elmtrackr.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * The one place that talks to Google Play Billing.
 *
 * Everything above this file works in packs and prices; everything below is
 * Play's callback API turned into suspend functions. Keeping the boundary here
 * is what lets [PlayClockFacePackStore] be read as product logic, and what keeps
 * the Play import list from spreading past this package.
 *
 * Failure is reported as an ordinary value — null, false — rather than thrown.
 * Billing is unavailable on plenty of perfectly healthy devices (sideloads,
 * emulators without Play, a Store the user disabled, a country the product is
 * not sold in), so an unreachable service is a normal state to render, not an
 * error to crash on.
 */
@Singleton
class PlayBillingConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _purchaseUpdates = MutableSharedFlow<PurchaseUpdate>(
        // Buffered rather than rendezvous: Play delivers on its own thread and
        // must never be blocked waiting for a collector. Sized for far more
        // updates than a session can produce, so nothing is dropped in practice.
        extraBufferCapacity = 16,
    )

    /** Every result Play reports for a purchase started from this app. */
    val purchaseUpdates: SharedFlow<PurchaseUpdate> = _purchaseUpdates.asSharedFlow()

    private val connectMutex = Mutex()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        _purchaseUpdates.tryEmit(PurchaseUpdate(result, purchases ?: emptyList()))
    }

    /**
     * Built once and never closed.
     *
     * `endConnection` is for a client whose owner is going away; this one's owner
     * is the process. Reconnection is Play's job — see
     * [BillingClient.Builder.enableAutoServiceReconnection] — and tearing the
     * client down between screens would trade that for a fresh service bind on
     * every visit to the gallery.
     *
     * Lazy so that a build with paid packs switched off never constructs it, and
     * so nothing binds to the Play service during application start-up.
     */
    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            // Required before Play will sell anything, and not a formality: cash
            // and bank-transfer payments arrive as PENDING and must not be
            // granted until they settle. Declining to enable them would not make
            // them go away — it would make the client refuse to start.
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .enableAutoServiceReconnection()
            .build()
    }

    /**
     * Connects if not already connected, and reports whether Play is usable.
     *
     * Serialized on a mutex so concurrent callers — a foreground refresh and a
     * tap on Buy in the same frame — share one connection attempt instead of
     * racing two.
     */
    suspend fun ensureConnected(): Boolean = connectMutex.withLock {
        if (client.isReady) return@withLock true
        val result = runCatchingBilling("startConnection") { awaitSetup() }
            ?: return@withLock false
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "Billing unavailable: ${result.responseCode} ${result.debugMessage}")
        }
        result.responseCode == BillingClient.BillingResponseCode.OK
    }

    /**
     * Runs one Play call, turning a throw into the same null this file already
     * uses for "could not ask".
     *
     * The contract at the top of this class says failure arrives as a value
     * rather than an exception, and until this existed that was only true of the
     * response codes. `BillingClient` still throws on its own account — a closed
     * client, a flow launched off the main thread, a Store that dies mid-call —
     * and the tap that reaches here is a Buy button running on `viewModelScope`,
     * which has no exception handler and would take the process down with it.
     *
     * Cancellation is rethrown. It is an `Exception` in Kotlin, and swallowing it
     * would leave a cancelled coroutine believing it should carry on.
     */
    private suspend fun <T> runCatchingBilling(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.d(TAG, "Billing call '$what' threw: ${e.message}")
        null
    }

    /**
     * One connection attempt, as a suspend call.
     *
     * The guard is not defensive padding. With auto-reconnection on, Play calls
     * `onBillingSetupFinished` again on every later reconnect, and a disconnect
     * during the first setup calls the other method — both would resume a
     * continuation that is already done, which throws inside Play's own callback
     * thread.
     */
    private suspend fun awaitSetup(): BillingResult = suspendCancellableCoroutine { cont ->
        val resumed = AtomicBoolean(false)
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (resumed.compareAndSet(false, true)) cont.resume(billingResult)
            }

            override fun onBillingServiceDisconnected() {
                if (resumed.compareAndSet(false, true)) {
                    cont.resume(
                        BillingResult.newBuilder()
                            .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                            .setDebugMessage("Disconnected before setup finished")
                            .build(),
                    )
                }
            }
        })
    }

    /**
     * Play's details for [productIds], or null if the query failed.
     *
     * Null and empty mean different things and callers must not collapse them:
     * empty is Play saying "these products are not sold here", which happens for
     * a real reason (not published yet, not available in the user's country) and
     * should be shown as unavailable. Null is "we could not ask", which should
     * leave whatever was on screen alone.
     */
    suspend fun queryOneTimeProducts(productIds: List<String>): List<ProductDetails>? {
        if (productIds.isEmpty()) return emptyList()
        if (!ensureConnected()) return null
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()
        val result = runCatchingBilling("queryProductDetails") { client.queryProductDetails(params) }
            ?: return null
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "queryProductDetails failed: ${result.billingResult.debugMessage}")
            return null
        }
        return result.productDetailsList.orEmpty()
    }

    /**
     * Every one-time purchase Play holds for this account, or null if the query
     * failed.
     *
     * This is what makes a restore button unnecessary: Play answers with the
     * account's purchases on any device, so a reinstall recovers everything by
     * asking.
     *
     * Returned as Play gave them, pending rows included. Deciding that a
     * `PENDING` payment grants nothing is a product rule, not a transport
     * detail, so it belongs with the other grant rules in
     * [PlayClockFacePackStore] rather than being quietly applied here.
     */
    suspend fun queryOwnedPurchases(): List<Purchase>? {
        if (!ensureConnected()) return null
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = runCatchingBilling("queryPurchases") { client.queryPurchasesAsync(params) }
            ?: return null
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "queryPurchases failed: ${result.billingResult.debugMessage}")
            return null
        }
        return result.purchasesList
    }

    /**
     * Shows Play's purchase sheet for [product].
     *
     * Returns as soon as the sheet is up; the outcome arrives on
     * [purchaseUpdates]. Must be called on the main thread with a live Activity,
     * which is why the Activity is a parameter rather than something this class
     * holds — a retained Activity reference in a `@Singleton` is a leak.
     */
    suspend fun launchPurchase(activity: Activity, product: ProductDetails): BillingResult {
        if (!ensureConnected()) {
            return BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
                .setDebugMessage("Not connected to Play Billing")
                .build()
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .apply {
                // Set only when the product actually carries multiple offers.
                // Play returns a non-null list in that case alone, and passing a
                // token for a plain single-price product is not the documented
                // path for one.
                product.oneTimePurchaseOfferDetailsList
                    ?.firstOrNull()
                    ?.offerToken
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { setOfferToken(it) }
            }
            .build()
        return runCatchingBilling("launchBillingFlow") {
            client.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productParams))
                    .build(),
            )
        } ?: BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.ERROR)
            .setDebugMessage("launchBillingFlow threw")
            .build()
    }

    /**
     * Tells Play the purchase was honoured.
     *
     * Not optional bookkeeping: Play automatically refunds and revokes any
     * purchase left unacknowledged for three days. Acknowledging is therefore
     * part of granting, not a follow-up to it, and is retried on every refresh
     * because the acknowledgement itself can fail while the purchase stands.
     */
    suspend fun acknowledge(purchase: Purchase): Boolean {
        if (!ensureConnected()) return false
        val result = runCatchingBilling("acknowledgePurchase") {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build(),
            )
        } ?: return false
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "acknowledge failed: ${result.responseCode} ${result.debugMessage}")
        }
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    private companion object {
        const val TAG = "Billing"
    }
}

/** One report from Play about a purchase this app started. */
data class PurchaseUpdate(
    val result: BillingResult,
    val purchases: List<Purchase>,
)
