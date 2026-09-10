package com.elmtrackr.app.billing

import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.data.local.preferences.EntitlementsMigration
import com.elmtrackr.app.fake.FakeClockFacePackStore
import com.elmtrackr.app.fake.FakeClockFacePreferences
import com.elmtrackr.app.fake.FakePurchasePreferences
import com.elmtrackr.app.ui.settings.ClockFaceGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a restored purchase actually gets the user.
 *
 * The rest of this feature keeps *owning* a pack and *having* one deliberately
 * apart, and [ClockFacePackBillingCoordinator] is the one place they meet: it
 * adds a pack the moment the user pays for it, rather than leaving them to find
 * an Add button for something they have already bought.
 *
 * A restore is that same moment arriving late — a reinstall, a new phone, an
 * entitlements file that could not be read — and it used to stop at ownership.
 * Play's record came back, the pack did not: it stayed on the shop shelf behind
 * an Add button, which is the one place someone hunting for a pack they own does
 * not think to look, while `one-time-products.md` §6 promised the opposite.
 * These tests are that promise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = ScreenshotTestApplication::class)
class ClockFacePackRestoreTest {

    /**
     * The coordinator's own scope, unconfined so its collector subscribes the
     * moment it is built.
     *
     * That is what the application scope does in production — the singleton is
     * constructed at start-up, long before any purchase lands — and a queueing
     * dispatcher would instead let an event be emitted into a stream nobody was
     * listening to yet, which is a property of the test, not of the code.
     * Parented to [TestScope.backgroundScope] so it is cancelled with the test.
     */
    private fun TestScope.coordinatorScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    private fun coordinator(
        scope: CoroutineScope,
        store: ClockFacePackStore,
        facePreferences: FakeClockFacePreferences,
        purchasePreferences: FakePurchasePreferences = FakePurchasePreferences(),
        migration: EntitlementsMigration = NoOpEntitlementsMigration,
    ) = ClockFacePackBillingCoordinator(
        grandfathering = ClockFacePackGrandfathering(
            clockFacePreferences = facePreferences,
            purchasePreferences = purchasePreferences,
        ),
        store = store,
        clockFacePreferences = facePreferences,
        purchasePreferences = purchasePreferences,
        entitlementsMigration = migration,
        scope = scope,
    )

    /** The Play product id for [pack], for a fake that stores what Play reported. */
    private fun productOf(pack: ClockFaceGroup): String = ClockFacePackProducts.productId(pack)!!

    /**
     * A migration that has nothing to do, which is the state every device reaches
     * after its first paid launch.
     *
     * A stand-in rather than the real repository because the real one reads a
     * DataStore on its own dispatcher: `advanceUntilIdle` would return while it
     * was still suspended on real IO, and the assertions would race the work they
     * are about. What the migration itself does is the repository's business; what
     * this file is about is the order the coordinator runs things in and what it
     * does when one of them fails.
     */
    private object NoOpEntitlementsMigration : EntitlementsMigration {
        override suspend fun migrateEntitlementsIfNeeded() = Unit
    }

    /**
     * The reported bug, in one test: a user who bought Payday, reinstalled, and
     * could not find it. Ownership came back from Play; the pack has to come
     * back with it.
     */
    @Test
    fun `a restored purchase is added, not left on the shelf`() = runTest {
        val facePreferences = FakeClockFacePreferences()
        val store = FakeClockFacePackStore()
        coordinator(coordinatorScope(), store, facePreferences)
        advanceUntilIdle()

        store.emit(PackPurchaseEvent.Restored(setOf(ClockFaceGroup.PAYDAY)))
        advanceUntilIdle()

        assertEquals(setOf("PAYDAY"), facePreferences.installedClockFacePacks)
    }

    /** A restore adds to what the user has; it never replaces it. */
    @Test
    fun `a restore leaves the packs already installed alone`() = runTest {
        val facePreferences = FakeClockFacePreferences(initialPacks = setOf("NATURE"))
        val store = FakeClockFacePackStore()
        coordinator(coordinatorScope(), store, facePreferences)
        advanceUntilIdle()

        store.emit(PackPurchaseEvent.Restored(setOf(ClockFaceGroup.PAYDAY)))
        advanceUntilIdle()

        assertEquals(setOf("NATURE", "PAYDAY"), facePreferences.installedClockFacePacks)
    }

    /** The behaviour that already worked, kept working. */
    @Test
    fun `a completed purchase is still added`() = runTest {
        val facePreferences = FakeClockFacePreferences()
        val store = FakeClockFacePackStore()
        coordinator(coordinatorScope(), store, facePreferences)
        advanceUntilIdle()

        store.emit(PackPurchaseEvent.Purchased(setOf(ClockFaceGroup.JOURNEYS)))
        advanceUntilIdle()

        assertEquals(setOf("JOURNEYS"), facePreferences.installedClockFacePacks)
    }

    /**
     * Everything else on the stream is a message, not a grant. `AlreadyOwned`
     * in particular: it says Play holds a purchase, and the refresh it triggers
     * is what works out whether anything was actually missing. Installing from
     * the message itself would add a pack without knowing which.
     */
    @Test
    fun `an outcome that grants nothing installs nothing`() = runTest {
        val facePreferences = FakeClockFacePreferences()
        val store = FakeClockFacePackStore()
        coordinator(coordinatorScope(), store, facePreferences)
        advanceUntilIdle()

        store.emit(PackPurchaseEvent.AlreadyOwned)
        store.emit(PackPurchaseEvent.Pending)
        store.emit(PackPurchaseEvent.Cancelled)
        store.emit(PackPurchaseEvent.Failed(responseCode = 6, debugMessage = "test"))
        advanceUntilIdle()

        assertEquals(emptySet<String>(), facePreferences.installedClockFacePacks)
    }

    /**
     * The device the September fix cannot reach, and the reason this reconcile
     * exists.
     *
     * `Restored` carries what Play reported *minus what the device had cached*.
     * A device that reinstalled before the fix shipped cached the purchase
     * without installing the pack, so the difference is empty on every foreground
     * from then on and the event never fires again. Ownership is real, the pack
     * is absent, and nothing joins the two: Payday stays behind an Add button for
     * good. Only a check of the steady state gets it back.
     */
    @Test
    fun `an acquired pack the device never installed is put back`() = runTest {
        val facePreferences = FakeClockFacePreferences()
        val purchases = FakePurchasePreferences(
            ownedProductIds = setOf(productOf(ClockFaceGroup.PAYDAY)),
        )
        val store = FakeClockFacePackStore()
        val coordinator = coordinator(coordinatorScope(), store, facePreferences, purchases)
        advanceUntilIdle()

        coordinator.onAppForegrounded()
        advanceUntilIdle()

        assertEquals(setOf("PAYDAY"), facePreferences.installedClockFacePacks)
    }

    /** The free-era grant is acquired too, and comes back the same way. */
    @Test
    fun `a grandfathered pack the device lost is put back`() = runTest {
        val facePreferences = FakeClockFacePreferences()
        val purchases = FakePurchasePreferences(
            grandfathered = setOf("PAYDAY"),
            grandfatheringDone = true,
        )
        val store = FakeClockFacePackStore()
        val coordinator = coordinator(coordinatorScope(), store, facePreferences, purchases)
        advanceUntilIdle()

        coordinator.onAppForegrounded()
        advanceUntilIdle()

        assertEquals(setOf("PAYDAY"), facePreferences.installedClockFacePacks)
    }

    /**
     * The other half of the rule, and the reason a removal has to be recorded
     * rather than inferred from absence: reconciling without it would put a pack
     * the user took off their list back on it every time the app checked.
     */
    @Test
    fun `a pack the user removed is not put back`() = runTest {
        val facePreferences = FakeClockFacePreferences(initialRemovedPacks = setOf("PAYDAY"))
        val purchases = FakePurchasePreferences(
            ownedProductIds = setOf(productOf(ClockFaceGroup.PAYDAY)),
        )
        val store = FakeClockFacePackStore()
        val coordinator = coordinator(coordinatorScope(), store, facePreferences, purchases)
        advanceUntilIdle()

        coordinator.onAppForegrounded()
        advanceUntilIdle()

        assertEquals(emptySet<String>(), facePreferences.installedClockFacePacks)
    }

    /**
     * A free build must not install the catalogue.
     *
     * `FreeClockFacePackStore` reports every pack as owned so the gallery renders
     * as it did before packs were sold. Reconciling against the storefront would
     * therefore add all of them on every device; reconciling against what was
     * actually acquired adds none.
     */
    @Test
    fun `nothing is installed when nothing was acquired`() = runTest {
        val facePreferences = FakeClockFacePreferences(initialPacks = setOf("NATURE"))
        val store = FakeClockFacePackStore(owned = ClockFacePackProducts.purchasablePacks.toSet())
        val coordinator = coordinator(coordinatorScope(), store, facePreferences)
        advanceUntilIdle()

        coordinator.onAppForegrounded()
        advanceUntilIdle()

        assertEquals(setOf("NATURE"), facePreferences.installedClockFacePacks)
    }

    /** Buying a pack retracts an earlier removal of it; so does restoring one. */
    @Test
    fun `acquiring a pack again clears the removal that was on file`() = runTest {
        val facePreferences = FakeClockFacePreferences(initialRemovedPacks = setOf("PAYDAY"))
        val store = FakeClockFacePackStore()
        coordinator(coordinatorScope(), store, facePreferences)
        advanceUntilIdle()

        store.emit(PackPurchaseEvent.Purchased(setOf(ClockFaceGroup.PAYDAY)))
        advanceUntilIdle()

        assertEquals(setOf("PAYDAY"), facePreferences.installedClockFacePacks)
        assertEquals(emptySet<String>(), facePreferences.removedClockFacePacks)
    }

    /**
     * The seed is spent the first time it runs — it writes its marker whether or
     * not it granted anything — and it derives the grant from the store the
     * migration fills. Seeding after a migration that threw would grant nothing,
     * mark the grant as worked out, and put the user's free-era packs on sale
     * permanently. Skipping costs one launch.
     */
    @Test
    fun `the free-era grant is not seeded when the migration failed`() = runTest {
        val facePreferences = FakeClockFacePreferences(initialPacks = setOf("PAYDAY"))
        val purchases = FakePurchasePreferences()
        val store = FakeClockFacePackStore()
        val coordinator = coordinator(
            coordinatorScope(),
            store,
            facePreferences,
            purchases,
            migration = object : EntitlementsMigration {
                override suspend fun migrateEntitlementsIfNeeded() =
                    error("entitlements file unreadable")
            },
        )
        advanceUntilIdle()

        coordinator.onAppForegrounded()
        advanceUntilIdle()

        assertFalse("the seed must stay unspent", purchases.grandfatheringDone)
        assertEquals(emptySet<String>(), purchases.grandfatheredClockFacePacks)
        // Play is a separate system: a local failure must not skip the refresh.
        assertEquals(1, store.refreshCount)
    }
}
