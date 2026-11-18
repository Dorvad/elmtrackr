package com.elmtrackr.app

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The app must not require hardware it can do without.
 *
 * Google Play filters an app out of every device that lacks a feature the app
 * requires — and "requires" includes features nobody declared. Android infers
 * them, and Play applies the inference:
 *
 *  - an activity locked to portrait implies `android.hardware.screen.portrait`;
 *  - the `CAMERA` permission implies `android.hardware.camera`, the rear camera.
 *
 * In November 2026 Play refused to send a release for review over the first of
 * these. Nothing in this app's own manifest sets an orientation. The lock came
 * from a dependency: the ML Kit document scanner ships an invisible delegate
 * activity with `screenOrientation="portrait"`, and the manifest merger brought
 * it in. The second was already there, unnoticed — `camera.any` was marked
 * optional, which does not cancel what the permission implies about the rear one.
 *
 * The remedy is to say so explicitly: `<uses-feature … required="false">`
 * overrides the inference. This test reads the *merged* manifest, the one that
 * includes every library's contribution, and checks that each inferred feature
 * has been answered. A dependency that adds another orientation lock fails here,
 * in the unit-test run, instead of at upload.
 *
 * What this cannot see is `aapt`'s own final list. The release checklist has the
 * `aapt2 dump badging` step for that.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = ScreenshotTestApplication::class)
class ManifestHardwareFeaturesTest {

    private val packageInfo: PackageInfo by lazy {
        val context = RuntimeEnvironment.getApplication()
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or
                PackageManager.GET_CONFIGURATIONS or
                PackageManager.GET_PERMISSIONS,
        )
    }

    private val features: List<FeatureInfo> by lazy {
        packageInfo.reqFeatures.orEmpty().filter { it.name != null }
    }

    private fun declaredOptional(feature: String): Boolean =
        features.any { it.name == feature && it.flags and FeatureInfo.FLAG_REQUIRED == 0 }

    /** The merged manifest really is what is being read: a library's activity is in it. */
    @Test
    fun `the manifest under test includes what libraries contribute`() {
        val activities = packageInfo.activities.orEmpty().map { it.name }
        assertTrue(
            "no library activity found — this test would be reading the app's own manifest only",
            activities.any { !it.startsWith("com.elmtrackr.") },
        )
    }

    @Test
    fun `every orientation lock is answered with an optional screen feature`() {
        val unanswered = packageInfo.activities.orEmpty().mapNotNull { activity ->
            val implied = impliedScreenFeature(activity.screenOrientation) ?: return@mapNotNull null
            if (declaredOptional(implied)) null else "${activity.name} implies $implied"
        }

        assertTrue(
            "An activity is locked to one orientation, which makes Google Play treat that " +
                "screen orientation as required hardware and hide the app from devices " +
                "without it. Add <uses-feature android:name=\"…\" android:required=\"false\" /> " +
                "to AndroidManifest.xml for: $unanswered",
            unanswered.isEmpty(),
        )
    }

    @Test
    fun `the camera permission does not make a camera required`() {
        val requestsCamera = Manifest.permission.CAMERA in packageInfo.requestedPermissions.orEmpty()
        if (!requestsCamera) return

        // Receipt scanning is the only use of the camera, and a claim can be saved
        // with a picked file or with no receipt at all.
        val missing = listOf(PackageManager.FEATURE_CAMERA, PackageManager.FEATURE_CAMERA_ANY)
            .filterNot(::declaredOptional)

        assertTrue(
            "The CAMERA permission implies these as required unless each is declared " +
                "with android:required=\"false\": $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `no hardware feature is declared as required`() {
        val required = features
            .filter { it.flags and FeatureInfo.FLAG_REQUIRED != 0 }
            .map { it.name }

        assertTrue(
            "The app runs without special hardware. A required feature removes it from " +
                "every device lacking that feature: $required",
            required.isEmpty(),
        )
    }

    /** Mirrors the inference in the platform's package parser and in aapt. */
    private fun impliedScreenFeature(screenOrientation: Int): String? = when (screenOrientation) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
        -> PackageManager.FEATURE_SCREEN_PORTRAIT

        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
        -> PackageManager.FEATURE_SCREEN_LANDSCAPE

        else -> null
    }
}
