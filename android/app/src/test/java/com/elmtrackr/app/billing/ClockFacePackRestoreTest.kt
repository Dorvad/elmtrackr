package com.elmtrackr.app.billing

import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
    ) = ClockFacePackBillingCoordinator(
        grandfathering = ClockFacePackGrandfathering(
            clockFacePreferences = facePreferences,
            purchasePreferences = FakePurchasePreferences(),
        ),
        store = store,
        clockFacePreferences = facePreferences,
        appPreferences = AppPreferencesRepository(RuntimeEnvironment.getApplication()),
        scope = scope,
    )

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
}
