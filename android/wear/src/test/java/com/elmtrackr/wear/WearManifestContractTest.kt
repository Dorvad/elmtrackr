package com.elmtrackr.wear

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Manifest facts the Wear surfaces depend on but nothing else checks.
 *
 * These are the kind of regression a debug build never surfaces: the app still
 * launches, the tile still renders, and only the tap does nothing — which is
 * how the tile's punch button reached a store reviewer broken. A plain XML
 * assertion is cheap enough to run on every commit.
 */
class WearManifestContractTest {

    private val manifest: Element by lazy {
        val file = resolveManifest()
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
            .documentElement
    }

    @Test
    fun tileTrampolineIsExported() {
        val trampoline = activity(".tile.WearPunchTrampolineActivity")
        assertNotNull("The tile's trampoline activity is missing from the manifest", trampoline)
        assertEquals(
            "The tile's LaunchAction is dispatched by the Wear OS tile host, a separate uid. " +
                "With exported=false the activity manager refuses the start and tapping the " +
                "tile silently does nothing.",
            "true",
            trampoline!!.getAttributeNS(ANDROID_NS, "exported"),
        )
    }

    @Test
    fun watchAppIsStandalone() {
        val application = manifest.getElementsByTagName("application").item(0) as Element
        val nodes = application.getElementsByTagName("meta-data")
        val standalone = (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .firstOrNull { it.getAttributeNS(ANDROID_NS, "name") == "com.google.android.wearable.standalone" }
        assertNotNull("standalone meta-data missing", standalone)
        assertEquals(
            "A non-standalone watch cannot punch without a signed-in phone, which is " +
                "the review path that keeps being rejected as functionality not working.",
            "true",
            standalone!!.getAttributeNS(ANDROID_NS, "value"),
        )
    }

    @Test
    fun workManagerDoesNotAutoInitBeforeApplicationOnCreate() {
        val providers = manifest.getElementsByTagName("provider")
        val startup = (0 until providers.length)
            .map { providers.item(it) as Element }
            .firstOrNull {
                it.getAttributeNS(ANDROID_NS, "name") == "androidx.startup.InitializationProvider"
            }
        assertNotNull("androidx.startup provider missing — merge/remove of WorkManagerInitializer needs it", startup)
        val metas = startup!!.getElementsByTagName("meta-data")
        val workManager = (0 until metas.length)
            .map { metas.item(it) as Element }
            .firstOrNull {
                it.getAttributeNS(ANDROID_NS, "name") == "androidx.work.WorkManagerInitializer"
            }
        assertNotNull(workManager)
        assertEquals(
            "WorkManager's default initializer is a ContentProvider and runs before " +
                "Application.onCreate. Leave it in and a JobScheduler failure on a review " +
                "watch is a launch crash Sentry never sees. Found tools:node=" +
                workManager!!.getAttributeNS(TOOLS_NS, "node"),
            "remove",
            workManager.getAttributeNS(TOOLS_NS, "node"),
        )
    }

    @Test
    fun launcherUsesTheBlackWearTheme() {
        val application = manifest.getElementsByTagName("application").item(0) as Element
        val main = activity(".WearMainActivity")
        assertEquals("@style/Theme.ElmTrackrWear", application.getAttributeNS(ANDROID_NS, "theme"))
        assertEquals("@style/Theme.ElmTrackrWear", main!!.getAttributeNS(ANDROID_NS, "theme"))
    }

    @Test
    fun tileTrampolineDoesNotUseThemeNoDisplay() {
        val trampoline = activity(".tile.WearPunchTrampolineActivity")
        assertNotNull(trampoline)
        val theme = trampoline!!.getAttributeNS(ANDROID_NS, "theme")
        assertTrue(
            "Theme.NoDisplay crashes on Wear when onResume is delivered after finish(). " +
                "Use a translucent theme instead. Found: $theme",
            theme.contains("Translucent"),
        )
    }

    @Test
    fun postNotificationsIsDeclaredForTheOngoingActivity() {
        val nodes = manifest.getElementsByTagName("uses-permission")
        val names = (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS(ANDROID_NS, "name") }
        assertTrue(
            "The running shift is a Wear OS Ongoing Activity, which rides on a notification; " +
                "from API 33 that needs POST_NOTIFICATIONS or the watch-face indicator never appears.",
            names.contains("android.permission.POST_NOTIFICATIONS"),
        )
    }

    @Test
    fun launcherActivityIsExported() {
        val main = activity(".WearMainActivity")
        assertNotNull("The watch launcher activity is missing from the manifest", main)
        assertEquals("true", main!!.getAttributeNS(ANDROID_NS, "exported"))
    }

    @Test
    fun launcherIconUsesTheDensityBucketedMipmap() {
        val application = manifest.getElementsByTagName("application").item(0) as Element
        // A nodpi bitmap is decoded at its authored size wherever it is drawn,
        // so a 1024x1024 master as the launcher icon costs the watch launcher a
        // 4 MB allocation for a 48dp slot.
        assertEquals("@mipmap/ic_launcher", application.getAttributeNS(ANDROID_NS, "icon"))
        assertEquals("@mipmap/ic_launcher_round", application.getAttributeNS(ANDROID_NS, "roundIcon"))
    }

    @Test
    fun everyWearSurfaceIsStillDeclared() {
        val nodes = manifest.getElementsByTagName("service")
        val services = (0 until nodes.length)
            .map { (nodes.item(it) as Element).getAttributeNS(ANDROID_NS, "name") }
        // The store listing describes a tile and a complication; both have to
        // exist for that description to be accurate.
        assertTrue("Tile service missing", services.contains(".tile.ElmTrackrTileService"))
        assertTrue(
            "Complication service missing",
            services.contains(".complication.ElmTrackrComplicationService"),
        )
        assertTrue("Data layer listener missing", services.contains(".sync.WearDataListenerService"))
    }

    @Test
    fun dataListenerReceivesCapabilityChanges() {
        val nodes = manifest.getElementsByTagName("service")
        val listener = (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .firstOrNull { it.getAttributeNS(ANDROID_NS, "name") == ".sync.WearDataListenerService" }
        assertNotNull(listener)
        val actions = listener!!.getElementsByTagName("action")
        val names = (0 until actions.length).map { (actions.item(it) as Element).getAttributeNS(ANDROID_NS, "name") }
        assertTrue(
            "Without CAPABILITY_CHANGED the watch never learns the phone came back " +
                "unless the launcher opens, so tile-only punches stay queued.",
            names.contains("com.google.android.gms.wearable.CAPABILITY_CHANGED"),
        )
    }

    private fun activity(name: String): Element? {
        val nodes = manifest.getElementsByTagName("activity")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .firstOrNull { it.getAttributeNS(ANDROID_NS, "name") == name }
    }

    private fun resolveManifest(): File {
        // The unit-test working directory differs between Gradle and IDE runs,
        // so walk up from wherever the test starts until the module is found.
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            for (candidate in listOf("src/main/AndroidManifest.xml", "wear/src/main/AndroidManifest.xml")) {
                val file = File(dir, candidate)
                if (file.isFile) return file
            }
            dir = dir.parentFile
        }
        throw AssertionError("Could not locate the wear AndroidManifest.xml from ${System.getProperty("user.dir")}")
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val TOOLS_NS = "http://schemas.android.com/tools"
    }
}
