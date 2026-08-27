package com.elmtrackr.app.billing

import com.elmtrackr.app.fake.FakeClockFacePreferences
import com.elmtrackr.app.fake.FakePurchasePreferences
import com.elmtrackr.app.ui.settings.ClockFaceGroup
import com.elmtrackr.app.ui.settings.ClockFacePacks
import com.elmtrackr.app.domain.model.ClockStyle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide who pays for what.
 *
 * All of it pure: the product catalog, the ownership union and the free-era
 * grant. Nothing here needs Play, which is the point — these are the decisions
 * that would be expensive to get wrong and impossible to check by hand once they
 * are behind a billing client.
 */
class ClockFacePackBillingTest {

    // ── Catalog ───────────────────────────────────────────────────────────────

    /**
     * The guard that matters most: a pack added to the catalog without a Play
     * product would be permanently unbuyable, and the only symptom in the app is
     * a Buy button that does nothing.
     */
    @Test
    fun `every sellable pack has a product id and every id maps back`() {
        ClockFaceGroup.packs.forEach { pack ->
            val id = ClockFacePackProducts.productId(pack)
            assertTrue("$pack has no product id", id != null)
            assertEquals(pack, ClockFacePackProducts.packOf(id!!))
        }
    }

    /** The bundled pack is not sold, and must not accidentally acquire a price. */
    @Test
    fun `the bundled pack has no product`() {
        assertNull(ClockFacePackProducts.productId(ClockFaceGroup.ESSENTIALS))
        assertFalse(ClockFaceGroup.ESSENTIALS in ClockFacePackProducts.purchasablePacks)
    }

    /**
     * Play only accepts lowercase letters, digits, underscores and periods, and
     * rejects an upload rather than the product — so a bad id is found at release
     * time, on a deadline, by someone who did not write it.
     */
    @Test
    fun `product ids are shaped the way Play requires`() {
        val allowed = Regex("^[a-z0-9][a-z0-9_.]*$")
        ClockFacePackProducts.all.forEach { id ->
            assertTrue("'$id' is not a valid Play product id", allowed.matches(id))
        }
    }

    @Test
    fun `the everything product grants every sellable pack`() {
        assertEquals(
            ClockFacePackProducts.purchasablePacks.toSet(),
            ClockFacePackProducts.packsGrantedBy(ClockFacePackProducts.ALL_PACKS),
        )
    }

    /**
     * A product retired in a later version, or one a newer build sold to the same
     * Google account, must not stop the ids beside it from being honoured.
     */
    @Test
    fun `an unknown product id grants nothing and blocks nothing`() {
        val granted = ClockFacePackProducts.packsGrantedBy(
            listOf("clock_faces_atlantis", ClockFacePackProducts.productId(ClockFaceGroup.NATURE)!!),
        )

        assertEquals(setOf(ClockFaceGroup.NATURE), granted)
    }

    // ── Ownership ─────────────────────────────────────────────────────────────

    @Test
    fun `a purchase grants its pack`() {
        val owned = ClockFacePackOwnership.owned(
            purchasedProductIds = listOf(ClockFacePackProducts.productId(ClockFaceGroup.PROGRESS)!!),
            grandfathered = emptySet(),
        )

        assertEquals(setOf(ClockFaceGroup.PROGRESS), owned)
    }

    @Test
    fun `the free-era grant is honoured with no purchase behind it`() {
        val owned = ClockFacePackOwnership.owned(
            purchasedProductIds = emptyList(),
            grandfathered = setOf(ClockFaceGroup.NATURE),
        )

        assertEquals(setOf(ClockFaceGroup.NATURE), owned)
    }

    /**
     * The bundled pack is free by being bundled, not by being owned. Letting it
     * into the owned set would give the question two answers.
     */
    @Test
    fun `the bundled pack is never reported as owned`() {
        val owned = ClockFacePackOwnership.owned(
            purchasedProductIds = emptyList(),
            grandfathered = setOf(ClockFaceGroup.ESSENTIALS, ClockFaceGroup.NATURE),
        )

        assertEquals(setOf(ClockFaceGroup.NATURE), owned)
    }

    @Test
    fun `the free-era seed takes exactly the packs the device had`() {
        val seed = ClockFacePackOwnership.grandfatherSeed(setOf("NATURE", "JOURNEYS"))

        assertEquals(setOf(ClockFaceGroup.NATURE, ClockFaceGroup.JOURNEYS), seed)
    }

    @Test
    fun `the free-era seed drops names that no longer exist`() {
        val seed = ClockFacePackOwnership.grandfatherSeed(setOf("NATURE", "FELLOWSHIP"))

        assertEquals(setOf(ClockFaceGroup.NATURE), seed)
    }

    // ── Grandfathering ────────────────────────────────────────────────────────

    @Test
    fun `grandfathering grants what was installed when packs became paid`() = runTest {
        val purchases = FakePurchasePreferences()
        val grandfathering = ClockFacePackGrandfathering(
            clockFacePreferences = FakeClockFacePreferences(initialPacks = setOf("NATURE")),
            purchasePreferences = purchases,
        )

        grandfathering.seedIfNeeded()

        assertEquals(setOf("NATURE"), purchases.grandfatheredClockFacePacks)
        assertTrue(purchases.grandfatheringDone)
    }

    /**
     * Running twice must not re-seed. A user who grandfathered nothing, then
     * bought a pack, then reopened the app would otherwise have the second run
     * overwrite their purchase-derived state with whatever they happened to have
     * installed.
     */
    @Test
    fun `grandfathering runs once`() = runTest {
        val purchases = FakePurchasePreferences(grandfathered = emptySet(), grandfatheringDone = true)
        val grandfathering = ClockFacePackGrandfathering(
            clockFacePreferences = FakeClockFacePreferences(initialPacks = setOf("NATURE")),
            purchasePreferences = purchases,
        )

        grandfathering.seedIfNeeded()

        assertEquals(emptySet<String>(), purchases.grandfatheredClockFacePacks)
    }

    /**
     * Nobody is charged for a pack they never took, and granting nothing is a
     * real outcome rather than a failed migration.
     */
    @Test
    fun `a device with no packs grandfathers nothing and is still marked done`() = runTest {
        val purchases = FakePurchasePreferences()
        val grandfathering = ClockFacePackGrandfathering(
            clockFacePreferences = FakeClockFacePreferences(),
            purchasePreferences = purchases,
        )

        grandfathering.seedIfNeeded()

        assertEquals(emptySet<String>(), purchases.grandfatheredClockFacePacks)
        assertTrue(purchases.grandfatheringDone)
    }

    // ── The invariant paid packs must not break ───────────────────────────────

    /**
     * Charging for packs must never take away the face someone is looking at.
     *
     * [ClockFacePacks.available] adds the group holding the selection regardless
     * of ownership, so a face that arrived by sync from another device stays
     * usable even though nothing on this device ever paid for it. This is the
     * test that fails if ownership is ever wired into availability.
     */
    @Test
    fun `a selected face stays available even when its pack is not owned`() {
        val available = ClockFacePacks.available(stored = emptySet(), selected = ClockStyle.VINYL)

        assertTrue(ClockFaceGroup.JOURNEYS in available)
    }

    /** The free storefront must keep behaving exactly as the free era did. */
    @Test
    fun `with billing off every pack reads as owned`() {
        val storefront = FreeClockFacePackStore().storefront.value

        assertTrue(storefront.everythingOwned)
        ClockFaceGroup.entries.forEach { assertTrue(storefront.isOwned(it)) }
    }
}
