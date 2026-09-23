package com.elmtrackr.wear

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import com.elmtrackr.wear.complication.ElmTrackrComplicationService
import com.elmtrackr.wear.monitoring.WearCrashReporting
import com.elmtrackr.wear.ongoing.WearOngoingShift
import com.elmtrackr.wear.sync.WearShiftSnapshot
import com.elmtrackr.wear.sync.WearDataListenerService
import com.elmtrackr.wear.tile.WearPunchTrampolineActivity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
     * The same launch, at the largest font the wearer can ask for and at the module's
     * minimum API.
     *
     * `wear-play-resubmission-2026-08.md` §3 lists "Settings → Display → Font size at
     * its largest" among the cases to cover on hardware, and it was never covered
     * anywhere — the run above, like every other test in this module, uses the default
     * scale. The watch's display styles cap their own growth (see
     * `TextStyle.withCappedFontScale`), which is arithmetic on the launch path that
     * only executes above 1.3x, so a default-scale test never reaches it.
     *
     * API 30 is the module's `minSdk` and a real Wear OS level, so it is worth one
     * pass of its own: the 33 above is where everything else runs.
     */
    @Test
    @Config(sdk = [33], fontScale = 2.0f)
    fun `the launcher activity survives the largest accessibility font size`() {
        val controller = Robolectric.buildActivity(WearMainActivity::class.java)

        controller.create()
        controller.start()
        controller.resume()
        controller.destroy()
    }

    @Test
    @Config(sdk = [30], fontScale = 1.5f)
    fun `the launcher activity survives minSdk at a large font size`() {
        val controller = Robolectric.buildActivity(WearMainActivity::class.java)

        controller.create()
        controller.start()
        controller.resume()
        controller.destroy()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w227dp-h227dp-small-notlong-round")
    fun `the launcher activity reaches resume on a round canvas`() {
        val controller = Robolectric.buildActivity(WearMainActivity::class.java)

        controller.create()
        controller.start()
        controller.resume()
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
     * WorkManager is on-demand via [ElmTrackrWearApp] as Configuration.Provider.
     * If the default androidx.startup initializer is left in, this still passes
     * on Robolectric and still crashes on a real watch — so this test is the
     * on-demand half; [WearManifestContractTest.workManagerDoesNotAutoInitBeforeApplicationOnCreate]
     * is the ContentProvider half.
     */
    @Test
    fun `WorkManager is reachable after Application onCreate`() {
        assertNotNull(androidx.work.WorkManager.getInstance(app()))
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
     * The complication picker asks for preview data on the main thread, with no
     * framework guard around the call, so a throw there is a crash while a reviewer
     * is adding the complication to a watch face. Every supported type has to
     * produce something — real data, or the empty slot the provider falls back to.
     */
    @Test
    fun `the complication preview builds for every supported type`() {
        val service = Robolectric.setupService(ElmTrackrComplicationService::class.java)

        for (type in listOf(ComplicationType.SHORT_TEXT, ComplicationType.LONG_TEXT, ComplicationType.RANGED_VALUE)) {
            val data = service.getPreviewData(type)
            assertNotNull("no preview for $type", data)
            assertFalse("preview for $type fell back to the empty slot", data is NoDataComplicationData)
        }
        service.onDestroy()
    }

    /**
     * The trampoline is the one component a tile tap reaches directly, and it is
     * exported — so it receives whatever anyone sends it. It must finish rather
     * than throw on an intent carrying no action, an unknown action, or nothing
     * at all.
     */
    /**
     * Crash reporting must never be the crash.
     *
     * It is started first in `Application.onCreate`, in a module Play keeps rejecting
     * for dying on the launch path, so every entry point swallows its own failures.
     * This build has no DSN — `local.properties` carries none on CI — so the calls
     * below all take the unavailable branch, which is the one that must also be inert:
     * an unconfigured reporter has to do nothing quietly rather than throw.
     */
    @Test
    fun `crash reporting is inert and silent without a DSN`() {
        assertFalse("a test build must not be compiled with a DSN", WearCrashReporting.isAvailable())

        // None of these may throw.
        WearCrashReporting.startIfConsented(app())
        WearCrashReporting.report(IllegalStateException("handled, not fatal"))
        WearCrashReporting.applyPhoneConsent(app(), enabled = false)
        WearCrashReporting.applyPhoneConsent(app(), enabled = true)
    }

    /**
     * The phone owns the consent and the watch remembers the answer.
     *
     * The watch ships no settings screen, so its only source of truth is the snapshot
     * the phone pushes; the answer is cached so it survives a launch that happens
     * before any snapshot arrives. It defaults to on, matching the phone's own
     * opt-out default — and a watch that has never been paired still reports, which
     * is the entire point on a store reviewer's device.
     */
    @Test
    fun `the phone's consent is remembered and defaults to on`() {
        val context = app()
        assertTrue("an unpaired watch should still report", WearCrashReporting.isEnabled(context))

        // Application.onCreate collects crashReportingEnabled from the cached
        // snapshot (default on) on Dispatchers.IO. Wait for that write to land
        // so this opt-out is not overwritten by the default.
        Thread.sleep(150)
        WearCrashReporting.applyPhoneConsent(context, enabled = false)
        assertFalse("an opt-out from the phone must stick", WearCrashReporting.isEnabled(context))

        WearCrashReporting.applyPhoneConsent(context, enabled = true)
        assertTrue(WearCrashReporting.isEnabled(context))
    }

    @Test
    fun `the exported punch trampoline requires the tile token`() {
        val token = WearPunchTrampolineActivity.tileLaunchToken(app())

        assertFalse(
            WearPunchTrampolineActivity.isAuthorizedTileIntent(
                app(),
                Intent().putExtra(WearPunchTrampolineActivity.EXTRA_ACTION, WearPunchTrampolineActivity.ACTION_IN),
            ),
        )
        assertFalse(
            WearPunchTrampolineActivity.isAuthorizedTileIntent(
                app(),
                Intent()
                    .putExtra(WearPunchTrampolineActivity.EXTRA_ACTION, WearPunchTrampolineActivity.ACTION_IN)
                    .putExtra(WearPunchTrampolineActivity.EXTRA_TOKEN, "not-$token"),
            ),
        )
        assertTrue(
            WearPunchTrampolineActivity.isAuthorizedTileIntent(
                app(),
                Intent()
                    .putExtra(WearPunchTrampolineActivity.EXTRA_ACTION, WearPunchTrampolineActivity.ACTION_IN)
                    .putExtra(WearPunchTrampolineActivity.EXTRA_TOKEN, token),
            ),
        )
    }

    /**
     * The Wear quality guidelines require an ongoing activity while a shift runs
     * — an indicator on the watch face and a chip in recents — and Play rejected
     * 10056 for not having one. It rides on an ongoing notification, so the
     * observable contract is: an active snapshot posts it, an inactive one clears
     * it, and the notification carries the ongoing flag the system keys on.
     */
    @Test
    fun `an active snapshot posts the ongoing shift and an idle one clears it`() = runTest {
        val context = app()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        val repository = context.wearStateRepository

        repository.applySnapshot(
            WearShiftSnapshot(
                signedIn = true,
                isActive = true,
                shiftStartEpochMillis = System.currentTimeMillis() - 600_000L,
                startTimeLabel = "09:00",
            ),
            persist = false,
        )

        val posted = notifications.getNotification(WearOngoingShift.NOTIFICATION_ID)
        assertNotNull("no ongoing shift notification was posted", posted)
        assertTrue(
            "the shift notification must be ongoing for Wear OS to treat it as an ongoing activity",
            posted.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0,
        )
        assertTrue(
            "the ongoing-activity extras are missing, so no indicator would show on the watch face",
            posted.extras.keySet().any { it.contains("ongoing", ignoreCase = true) },
        )

        repository.applySnapshot(WearShiftSnapshot.signedOut(), persist = false)

        assertNull(
            "clocking out must clear the ongoing shift",
            notifications.getNotification(WearOngoingShift.NOTIFICATION_ID),
        )
    }

    /** Without the permission the indicator is a degraded feature, never a crash. */
    @Test
    fun `the ongoing shift is silent when notifications are not permitted`() {
        WearOngoingShift.sync(
            app(),
            WearShiftSnapshot(signedIn = true, isActive = true, shiftStartEpochMillis = 1_000L),
        )
        WearOngoingShift.sync(app(), WearShiftSnapshot.signedOut())
    }

    @Test
    fun `consent-only data updates the cached snapshot`() = runTest {
        val repository = app().wearStateRepository

        repository.applyCrashReportingConsent(enabled = false)

        assertFalse(repository.snapshot.value.crashReportingEnabled)

        repository.applyCrashReportingConsent(enabled = true)

        assertTrue(repository.snapshot.value.crashReportingEnabled)
    }

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
            controller.start()
            controller.resume()
            controller.pause()
            controller.stop()
            // Reaching here means onCreate did not throw. The activity is a
            // translucent trampoline, so finishing is the correct outcome for an
            // action it does not handle — including driving resume, which is the
            // Theme.NoDisplay crash the previous theme hit on real watches.
            controller.destroy()
        }
    }
}
