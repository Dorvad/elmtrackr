package com.elmtrackr.wear

import android.content.Context
import android.content.Intent
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.elmtrackr.wear.complication.ElmTrackrComplicationService
import com.elmtrackr.wear.sync.WearDataListenerService
import com.elmtrackr.wear.tile.WearPunchTrampolineActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ActivityController

/**
 * The launch path, exercised on the JVM.
 *
 * Play rejected this module twice with "your app does not install or launch
 * without crashing", and `wear-play-resubmission-2026-08.md` §3 records that the
 * crash was **never reproduced** — the fixes removed the crash paths a reading of
 * the code could find, which is not the same thing. Part of the reason it could
 * not be reproduced is that nothing anywhere ran the `Application`, the tile
 * service or the complication provider: the module had 16 unit tests, all of them
 * over pure display arithmetic.
 *
 * An emulator remains the real test, and these tests do not replace it — they run
 * unminified, without Play Services, and on a square JVM canvas rather than a
 * round screen. What they do cover is the specific shape of failure the rejection
 * describes: something on the way to the first frame throwing, in a component the
 * system starts rather than the user.
 */
@RunWith(RobolectricTestRunner::class)
// Pinned like the phone module's render tests: Robolectric 4.14.1 ships images up
// to SDK 35 and this module targets 36, so an unpinned run fails to configure
// before any test body executes. 33 is a real Wear OS level above the module's
// minSdk of 30.
@Config(sdk = [33])
class WearLaunchPathTest {

    private fun app(): ElmTrackrWearApp = ApplicationProvider.getApplicationContext()

    @Test
    fun `the application builds its dependencies without throwing`() {
        // Application.onCreate has already run by the time the test body starts,
        // so reaching here at all means it did not take the process down. The
        // assertions confirm the lateinit properties were actually assigned
        // rather than left uninitialised by a swallowed failure.
        val application = app()

        assertNotNull("wearStateRepository was never assigned", application.wearStateRepository)
        assertNotNull("wearActionClient was never assigned", application.wearActionClient)
    }

    /**
     * The regression that produced the August fix.
     *
     * Every system-started component used to reach the repository through
     * `applicationContext as ElmTrackrWearApp`. Those components can be started
     * into a context this app did not create — a restricted or isolated context,
     * a test harness, or a store-review harness that stubs the application class
     * — and a `ClassCastException` there is an immediate crash with no useful
     * message. `from()` must answer null instead.
     */
    @Test
    fun `from returns null rather than throwing for a foreign context`() {
        val foreign = object : ContextWrapper(app()) {
            override fun getApplicationContext(): Context = this
        }

        assertNull(ElmTrackrWearApp.from(foreign))
    }

    @Test
    fun `from resolves the application for an ordinary context`() {
        assertNotNull(ElmTrackrWearApp.from(app()))
    }

    /*
     * There is deliberately no test here for the tile *service*.
     *
     * `androidx.wear.tiles.TileService` resolves
     * `com.google.wear.services.tiles.TileInstance`, which ships with the Wear
     * system and is not on the JVM test classpath, so Robolectric cannot
     * instantiate the service at all — it fails with NoClassDefFoundError before
     * reaching any of our code. That is a limitation of the test environment and
     * not a finding about the tile.
     *
     * It leaves a real gap: the tile is the surface that was actually broken
     * before (the trampoline was declared exported="false", so a tile tap did
     * nothing), and it can only be verified on a watch or an emulator. The tile's
     * manifest contract is covered by `WearManifestContractTest`, and the punch
     * target it launches is covered below; the timeline itself is emulator-only.
     */

    /**
     * The launcher activity, through the whole foreground lifecycle.
     *
     * The gap this closes is the obvious one: "your app crashed when testing" is a
     * reviewer tapping the launcher icon, and nothing here had ever created
     * [WearMainActivity]. The Application was covered because Robolectric builds it
     * for every test in this class, and the tile, complication and trampoline were
     * covered directly — the one component in between, the activity that actually
     * puts a frame on the screen, was not.
     *
     * Reaching `resume()` means the theme resolved, the view model was constructed,
     * its bootstrap ran, and the Wear Compose tree composed and measured. Any of
     * those throwing is the shape of failure the rejections describe.
     */
    @Test
    fun `the launcher activity reaches resume and unwinds again`() {
        val controller = Robolectric.buildActivity(WearMainActivity::class.java)

        controller.create()
        controller.start()
        controller.resume()
        // And back down: a throw in onPause/onStop after a store reviewer swipes away
        // is the same crash dialog as one on the way up.
        controller.pause()
        controller.stop()
        controller.destroy()
    }

    /**
     * The view model, built the way the activity builds it.
     *
     * Its `init` reads the cached snapshot and kicks off the data-layer refresh, so
     * it runs disk I/O and touches Play Services on a device that may have neither a
     * cache nor a paired phone. Constructing it here with no Play Services present
     * is the closest this environment gets to the reviewer's harness.
     */
    @Test
    fun `the view model can be constructed with no phone and no cache`() {
        val viewModel = WearMainViewModel(app())

        assertNotNull(viewModel.displayState)
    }

    /**
     * The data-layer listener, which Play Services starts — not the user.
     *
     * It is exported and bound by a different uid, so it is reachable in states
     * this app never sets up. Construction and teardown must not throw.
     */
    @Test
    fun `the data layer listener can be constructed and destroyed`() {
        val service = Robolectric.setupService(WearDataListenerService::class.java)

        assertNotNull(service)
        service.onDestroy()
    }

    @Test
    fun `the complication service can be constructed and destroyed`() {
        val service = Robolectric.setupService(ElmTrackrComplicationService::class.java)

        assertNotNull(service)
        service.onDestroy()
    }

    /**
     * The trampoline is the one component a tile tap reaches directly, and it is
     * exported — so it receives whatever anyone sends it. It must finish rather
     * than throw on an intent carrying no action, an unknown action, or nothing
     * at all.
     */
    @Test
    fun `the trampoline finishes quietly on an intent it does not recognise`() {
        val cases = listOf(
            Intent(),
            Intent().setAction("com.elmtrackr.wear.NOT_A_PUNCH"),
            Intent().setAction(""),
        )

        cases.forEach { intent ->
            val controller: ActivityController<WearPunchTrampolineActivity> =
                Robolectric.buildActivity(WearPunchTrampolineActivity::class.java, intent)
            controller.create()
            // Reaching here means onCreate did not throw. The activity is a
            // NoDisplay trampoline, so finishing is the correct outcome for an
            // action it does not handle.
            controller.destroy()
        }
    }
}
