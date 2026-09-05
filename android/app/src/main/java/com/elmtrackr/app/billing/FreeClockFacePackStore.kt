package com.elmtrackr.app.billing

import android.app.Activity
import com.elmtrackr.app.ui.settings.ClockFaceGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The storefront when nothing is for sale.
 *
 * Bound whenever `PAID_CLOCK_FACE_PACKS` is off, which is every build until the
 * products in [ClockFacePackProducts] are live in Play Console. Reporting every
 * pack as already owned is what makes the gallery render exactly as it did
 * through 1.2.4 — no prices, no Buy, no Play connection — while the paid path
 * beside it stays compiled, reviewed and one flag away.
 *
 * [BillingAvailability.AVAILABLE] is inert here rather than a claim about Play:
 * with nothing unowned there is nothing for the gallery to offer, so it never
 * reads the field.
 */
@Singleton
class FreeClockFacePackStore @Inject constructor() : ClockFacePackStore {

    private val state = MutableStateFlow(
        ClockFacePackStorefront(
            owned = ClockFacePackProducts.purchasablePacks.toSet(),
            availability = BillingAvailability.AVAILABLE,
        ),
    )

    override val storefront: StateFlow<ClockFacePackStorefront> = state.asStateFlow()

    override val events: Flow<PackPurchaseEvent> = emptyFlow()

    override suspend fun launchPurchase(activity: Activity, pack: ClockFaceGroup) = Unit

    override suspend fun launchAllPacksPurchase(activity: Activity) = Unit

    /**
     * Nothing to restore: every pack is already owned and no Play account is
     * consulted, so a restore here has nothing it could recover.
     */
    override suspend fun refresh(): PackRestoreResult = PackRestoreResult.NothingRestored
}
