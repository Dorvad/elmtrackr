package com.elmtrackr.app.fake

import android.app.Activity
import com.elmtrackr.app.billing.BillingAvailability
import com.elmtrackr.app.billing.ClockFacePackProducts
import com.elmtrackr.app.billing.ClockFacePackStore
import com.elmtrackr.app.billing.ClockFacePackStorefront
import com.elmtrackr.app.billing.PackPurchaseEvent
import com.elmtrackr.app.billing.PackRestoreResult
import com.elmtrackr.app.ui.settings.ClockFaceGroup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A storefront the test drives.
 *
 * Defaults to every pack owned so the view-model tests that predate billing keep
 * describing what they were written to describe. A test about buying sets
 * [owned] to what it needs and emits through [emit].
 */
class FakeClockFacePackStore(
    owned: Set<ClockFaceGroup> = ClockFacePackProducts.purchasablePacks.toSet(),
    availability: BillingAvailability = BillingAvailability.AVAILABLE,
) : ClockFacePackStore {

    private val state = MutableStateFlow(
        ClockFacePackStorefront(owned = owned, availability = availability),
    )
    private val _events = MutableSharedFlow<PackPurchaseEvent>(extraBufferCapacity = 8)

    override val storefront: StateFlow<ClockFacePackStorefront> = state.asStateFlow()
    override val events: Flow<PackPurchaseEvent> = _events.asSharedFlow()

    var purchaseRequests: List<ClockFaceGroup> = emptyList()
        private set
    var allPacksPurchaseRequests: Int = 0
        private set
    var refreshCount: Int = 0
        private set

    /** What the next [refresh] answers. Set by a test about the Restore button. */
    var refreshResult: PackRestoreResult = PackRestoreResult.NothingRestored

    /**
     * When set, [refresh] waits on it before answering — for a test that needs
     * to see the store while a restore is still in flight.
     */
    var refreshGate: CompletableDeferred<Unit>? = null

    override suspend fun launchPurchase(activity: Activity, pack: ClockFaceGroup) {
        purchaseRequests = purchaseRequests + pack
    }

    override suspend fun launchAllPacksPurchase(activity: Activity) {
        allPacksPurchaseRequests++
    }

    override suspend fun refresh(): PackRestoreResult {
        refreshGate?.await()
        refreshCount++
        return refreshResult
    }

    suspend fun emit(event: PackPurchaseEvent) {
        _events.emit(event)
    }

    fun setOwned(owned: Set<ClockFaceGroup>) {
        state.value = state.value.copy(owned = owned)
    }
}
