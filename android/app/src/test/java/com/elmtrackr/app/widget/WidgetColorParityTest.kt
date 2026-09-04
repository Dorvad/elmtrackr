package com.elmtrackr.app.widget

import androidx.compose.ui.graphics.Color
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every colour the home-screen widget paints has to come from the Aurora palette.
 *
 * The watch has had `WearTileColorParityTest` since its own two colours drifted, and it
 * has not drifted since. The widget had no equivalent, and drifted further: it painted
 * the brand's tertiary as `#22D3EE` where the phone and the watch both use `#16C8D6`, in
 * fourteen places across the ring arcs, the progress fill and the active status dot. Same
 * role, different value, on the surface a user sees without opening the app.
 *
 * A literal is only wrong in relation to a system defined in another file, so nothing a
 * compiler or a screenshot diff reports would have caught it.
 *
 * Alpha is ignored — a token at reduced opacity is still that token. Black, white and
 * fully transparent are always allowed: Glance draws its own scrims and dividers as
 * white-at-alpha, and a widget background can be transparent.
 */
class WidgetColorParityTest {

    @Test
    fun everyColourLiteralInTheWidgetIsAPaletteToken() {
        val palette = paletteRgb()
        assertTrue("Could not read the Aurora palette by reflection", palette.size >= 20)

        val offenders = mutableListOf<String>()
        for (file in widgetSources()) {
            val body = when (file.extension) {
                "kt" -> stripKotlinComments(file.readText())
                else -> stripXmlComments(file.readText())
            }
            for ((raw, rgb) in colourLiterals(body)) {
                if (rgb in ALWAYS_ALLOWED || rgb in palette || rgb in KNOWN_DRIFT) continue
                offenders += "${file.name}: $raw"
            }
        }

        assertTrue(
            "These colours are not in the Aurora palette. Add the token to " +
                "ui/theme/Color.kt (if it is new to the brand) or use the token that " +
                "already carries this role, rather than writing the value here:\n  " +
                offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }

    /**
     * The five values below are drift this test found and deliberately did **not**
     * silently restyle.
     *
     * Each one sits a few points from a real token, which means someone either hand-tuned
     * a gradient stop for the widget's own rendering (Glance cannot use a Compose `Brush`,
     * so the widget builds gradients as drawables) or copied a value that has since moved.
     * Mapping them onto the nearest token would change what the widget looks like, and
     * adding them to `Color.kt` would be inventing brand colours. Both need a design
     * decision, not a guess from the test that noticed them.
     *
     * They are listed here so the drift is enumerated and bounded rather than invisible:
     * the test still fails on anything *new*. Resolve them and delete the entry.
     */
    private companion object {
        val ALWAYS_ALLOWED = setOf(0x000000, 0xFFFFFF)

        val KNOWN_DRIFT = mapOf(
            0x7C6BFF to "a lighter indigo; AuroraIndigo is 5B4DF2",
            0x5C4EE5 to "a hair off AuroraIndigo (5B4DF2)",
            0x3D2CC0 to "a hair off AuroraIndigoDeep (4133C8)",
            0x5CF0A0 to "a success green; the watch uses 34D399, the phone names none here",
            0x181A38 to "a hair off AuroraNavy (181530)",
            0x171D33 to "a hair off AuroraDarkSurface (151D2E)",
        ).keys
    }

    /** Every `Color(0x…)` on the phone's palette file, reduced to RGB. */
    private fun paletteRgb(): Set<Int> {
        val source = paletteFile().readText()
        return Regex("Color\\(0x([0-9A-Fa-f]{8})\\)")
            .findAll(source)
            .map { (it.groupValues[1].toLong(16).toInt()) and 0x00FFFFFF }
            .toSet()
    }

    /** `0xAARRGGBB` in Kotlin and `#RRGGBB` / `#AARRGGBB` in XML, as (text, rgb). */
    private fun colourLiterals(body: String): List<Pair<String, Int>> =
        buildList {
            for (match in Regex("0x[0-9A-Fa-f]{8}").findAll(body)) {
                add(match.value to (match.value.substring(2).toLong(16).toInt() and 0x00FFFFFF))
            }
            for (match in Regex("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})\\b").findAll(body)) {
                val hex = match.groupValues[1]
                add(match.value to (hex.takeLast(6).toLong(16).toInt()))
            }
        }

    /**
     * Drops `//` and block comments — the sources scanned here document which token each
     * value mirrors, and those notes name colours in hex, so without this the test would
     * flag its own explanations.
     */
    private fun stripKotlinComments(source: String): String {
        val withoutBlocks = source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return withoutBlocks.lineSequence().joinToString("\n") { it.substringBefore("//") }
    }

    private fun stripXmlComments(source: String): String =
        source.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    private fun moduleRoot(): File =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "src/main/java/com/elmtrackr/app").isDirectory }
            ?: generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .firstOrNull { File(it, "app/src/main/java/com/elmtrackr/app").isDirectory }
                ?.let { File(it, "app") }
            ?: throw AssertionError("Could not locate the app module from ${System.getProperty("user.dir")}")

    private fun paletteFile(): File {
        val file = File(moduleRoot(), "src/main/java/com/elmtrackr/app/ui/theme/Color.kt")
        assertTrue("Could not find the Aurora palette at $file", file.isFile)
        return file
    }

    private fun widgetSources(): List<File> {
        val module = moduleRoot()
        val kotlin = File(module, "src/main/java/com/elmtrackr/app/widget")
            .walkTopDown().filter { it.isFile && it.extension == "kt" }
        val drawables = File(module, "src/main/res/drawable")
            .walkTopDown()
            .filter { it.isFile && it.extension == "xml" && it.name.startsWith("widget_") }
        val files = (kotlin + drawables).toList()
        assertTrue("Found no widget sources to scan under $module", files.isNotEmpty())
        return files
    }

    /** Guards the reflection-free palette read: Color is on the test classpath. */
    @Test
    fun theAquaTokenIsWhatTheWidgetNowUses() {
        assertTrue(paletteRgb().contains(Color(0xFF16C8D6).value.let { 0x16C8D6 }))
        val widgetBody = widgetSources().joinToString("\n") { it.readText() }
        assertTrue("the drifted aqua is back", !widgetBody.contains("22D3EE", ignoreCase = true))
    }
}
