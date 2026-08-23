package com.elmtrackr.wear

import com.elmtrackr.wear.sync.WearAuroraColors
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every colour the watch paints has to come from the shared palette.
 *
 * This is the check that would have caught the two colours that drifted. The
 * bolt mark was filled with `#4664F4`, which appears in neither app's palette,
 * so the brand's primary mark was the one element the phone and the watch did
 * not share. The running-shift dot was `0xFF34D399` written inline — the right
 * value, taken from the phone's `AuroraDarkSuccess`, but with nothing tying it
 * back there, so the next change to the phone's success colour would have
 * silently left the watch behind.
 *
 * Neither was a bug a compiler, a lint rule or a screenshot diff would report.
 * A literal is only wrong in relation to a system that lives in another module,
 * which is why it needs a test that reads both.
 *
 * Black, white and fully transparent are always allowed: the face background is
 * required to be black by the Wear colour guidance, and white is the ink on the
 * brand gradient. Alpha is ignored — a token at reduced opacity is still that
 * token.
 */
class WearTileColorParityTest {

    @Test
    fun everyColourLiteralInTheWatchUiIsAPaletteToken() {
        val palette = paletteRgb()
        assertTrue("Could not read WearAuroraColors by reflection", palette.size >= 6)

        val offenders = mutableListOf<String>()
        for (file in watchSources()) {
            val body = when (file.extension) {
                "kt" -> stripKotlinComments(file.readText())
                else -> stripXmlComments(file.readText())
            }
            for ((raw, rgb) in colourLiterals(body)) {
                if (rgb in ALWAYS_ALLOWED || rgb in palette) continue
                offenders += "${file.name}: $raw"
            }
        }

        assertTrue(
            "These colours are not in WearAuroraColors. Add the token to the shared " +
                "palette (and to the phone's Color.kt if it is new to the brand) rather " +
                "than writing the value here:\n  " + offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }

    /** Every `const val Int` on the shared palette object, reduced to RGB. */
    private fun paletteRgb(): Set<Int> =
        WearAuroraColors::class.java.declaredFields
            .filter { it.type == Int::class.javaPrimitiveType }
            .mapNotNull { field ->
                field.isAccessible = true
                (field.get(WearAuroraColors) as? Int)?.and(0x00FFFFFF)
            }
            .toSet()

    /** `0xAARRGGBB` in Kotlin and `#RRGGBB` / `#AARRGGBB` in XML, as (text, rgb). */
    private fun colourLiterals(body: String): List<Pair<String, Int>> =
        buildList {
            for (match in Regex("0x[0-9A-Fa-f]{8}").findAll(body)) {
                add(match.value to (match.value.substring(2).toLong(16).toInt() and 0x00FFFFFF))
            }
            for (match in Regex("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})\\b").findAll(body)) {
                val hex = match.groupValues[1]
                val rgb = hex.takeLast(6).toLong(16).toInt()
                add(match.value to rgb)
            }
        }

    /**
     * Drops `//` and block comments.
     *
     * The sources scanned here document which phone token each value mirrors,
     * and those notes name colours in hex. Without this the test would flag its
     * own explanations. Scoped to `ui/` and `tile/`, neither of which contains a
     * string with `//` in it — the `wear://` data-layer URIs live in `sync/`.
     */
    private fun stripKotlinComments(source: String): String {
        val withoutBlocks = source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return withoutBlocks.lineSequence().joinToString("\n") { it.substringBefore("//") }
    }

    /**
     * Drops `<!-- -->`.
     *
     * Same reason as [stripKotlinComments]: the drawables carry a note saying
     * which colour they replaced, and that note names it in hex.
     */
    private fun stripXmlComments(source: String): String =
        source.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    private fun watchSources(): List<File> {
        val module = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "src/main/java/com/elmtrackr/wear").isDirectory }
            ?: generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .firstOrNull { File(it, "wear/src/main/java/com/elmtrackr/wear").isDirectory }
                ?.let { File(it, "wear") }
            ?: throw AssertionError("Could not locate the wear module from ${System.getProperty("user.dir")}")

        val roots = listOf(
            File(module, "src/main/java/com/elmtrackr/wear/ui"),
            File(module, "src/main/java/com/elmtrackr/wear/tile"),
            File(module, "src/main/res/drawable"),
        )
        val files = roots.filter { it.isDirectory }
            .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension in setOf("kt", "xml") } }
        assertTrue("Found no watch UI sources to scan under $module", files.isNotEmpty())
        return files
    }

    private companion object {
        val ALWAYS_ALLOWED = setOf(0x000000, 0xFFFFFF)
    }
}
