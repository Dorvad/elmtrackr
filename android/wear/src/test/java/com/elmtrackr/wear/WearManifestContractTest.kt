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
    }
}
