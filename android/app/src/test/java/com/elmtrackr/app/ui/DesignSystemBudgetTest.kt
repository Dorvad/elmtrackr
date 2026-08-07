package com.elmtrackr.app.ui

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * A ratchet on design-system drift.
 *
 * The Aurora token and component layers are good; the problem was that nothing
 * stopped screens from bypassing them, so raw `dp`, raw hex and raw Material
 * components accumulated faster than they were cleaned up. Each budget below is
 * recorded at the current count and may only ever go **down**: adding a new
 * violation fails the build, and every commit that removes some lowers the
 * number in the same change.
 *
 * This is a plain JUnit test rather than a detekt rule on purpose — it runs
 * inside the `testDebugUnitTest` step CI already executes and needs no new
 * dependency.
 */
class DesignSystemBudgetTest {

    // ── Budgets ───────────────────────────────────────────────────────────────
    // Lower these in the same commit that removes violations. Never raise them.

    private val maxRawDp = 1034
    // Dropped from 115 when the illustration files came out of this count. What
    // remains is genuine theme drift on real screens, which is what the ratchet
    // is for; the brand and dial pigments it used to carry could never go down.
    private val maxRawColor = 55
    private val maxRawTextField = 37
    private val maxRawCard = 38
    private val maxUngatedAnimatorCheck = 0

    /**
     * Files that animate without consulting the reduce-motion setting at all.
     *
     * At zero, and it has to stay there. "Reduce motion" that stops most of the
     * animation is not a setting, it is a suggestion — someone who turns it on
     * because movement makes them ill is not helped by the two that were missed.
     */
    private val maxUngatedAnimation = 0

    /**
     * Files that are drawings rather than screens — clock-face canvases, the
     * ride-provider marks, the update wizard's artwork and the Wear settings
     * watch. Their numbers are illustration geometry and their hex values are
     * pigment: a dial is dark whatever the theme does, and a provider's mark is
     * its brand colour. Forcing either onto the token scale would be worse than
     * leaving them, so both the dp and the colour budgets skip these files.
     *
     * Keep this list to files that are *only* drawing. A screen that happens to
     * contain a canvas belongs in the budget — split the canvas out instead, or
     * real layout drift hides behind the exemption.
     */
    private val illustrationExempt = setOf(
        "ExpressiveClockFaces.kt",
        "RideProviderGraphics.kt",
        "PaidProjectsUpdateWizard.kt",
        "WearWatchGraphic.kt",
    )

    private val uiRoot: File by lazy { resolve("src/main/java/com/elmtrackr/app/ui") }
    private val appRoot: File by lazy { resolve("src/main/java/com/elmtrackr/app") }

    @Test
    fun `raw dp literals stay within budget`() {
        val count = uiRoot.kotlinFiles()
            .filterNot { it.isTokenLayer() || it.name in illustrationExempt }
            .sumOf { file -> RAW_DP.findAll(file.readText()).count { it.value != "0.dp" } }

        assertWithinBudget("raw dp literals", count, maxRawDp, "Spacing.sN / Layout.*")
    }

    @Test
    fun `raw colour literals stay within budget`() {
        val count = uiRoot.kotlinFiles()
            .filterNot { it.path.contains("/ui/theme/") || it.name in illustrationExempt }
            .sumOf { RAW_COLOR.findAll(it.readText()).count() }

        assertWithinBudget("raw Color(0x…) literals", count, maxRawColor, "a token in ui/theme")
    }

    @Test
    fun `raw Material text fields stay within budget`() {
        val count = uiRoot.kotlinFiles()
            .filterNot { it.isDesignLayer() }
            .sumOf { RAW_TEXT_FIELD.findAll(it.readText()).count() }

        assertWithinBudget("raw OutlinedTextField/TextField", count, maxRawTextField, "ElmTextField")
    }

    @Test
    fun `raw Material cards stay within budget`() {
        val count = uiRoot.kotlinFiles()
            .filterNot { it.isDesignLayer() }
            .sumOf { RAW_CARD.findAll(it.readText()).count() }

        assertWithinBudget("raw Card/ElevatedCard/OutlinedCard", count, maxRawCard, "ElmCard")
    }

    /**
     * `ValueAnimator.areAnimatorsEnabled()` on its own ignores the app's own
     * "reduce motion" setting — it only sees the system animator scale. Every
     * call site outside the gate is a place the user preference is silently
     * dropped. Budget target: zero.
     */
    @Test
    fun `reduce-motion gate is not bypassed`() {
        val offenders = appRoot.kotlinFiles()
            .filterNot { it.name == "AuroraReduceMotion.kt" }
            .filter { file -> file.readText().lines().any { it.isCode() && it.contains("areAnimatorsEnabled") } }
            .map { it.name }
            .sorted()

        assertWithinBudget(
            "direct areAnimatorsEnabled checks (${offenders.joinToString()})",
            offenders.size,
            maxUngatedAnimatorCheck,
            "auroraMotionEnabled()",
        )
    }

    /**
     * Every file that animates must decide what to do when motion is reduced.
     *
     * Gating at the call site is what let the side navigation's selection pill
     * and the empty state's entrance keep animating with the setting on: each is
     * one `animateFloatAsState` in a file nobody revisited, and nothing pointed
     * that out. Either read [auroraMotionEnabled] or pass a spec that already
     * accounts for it — `auroraAnimationSpec` collapses to a snap.
     */
    @Test
    fun `animations consult the reduce-motion setting`() {
        val offenders = uiRoot.kotlinFiles()
            .filter { file ->
                val text = file.readText()
                val animates = text.lines().any { it.isCode() && ANIMATES.containsMatchIn(it) }
                val gated = text.contains("auroraMotionEnabled") ||
                    text.contains("LocalReduceMotion") ||
                    text.contains("auroraAnimationSpec")
                animates && !gated
            }
            .map { it.name }
            .sorted()

        assertWithinBudget(
            "files animating without a reduce-motion check (${offenders.joinToString()})",
            offenders.size,
            maxUngatedAnimation,
            "auroraMotionEnabled() or auroraAnimationSpec()",
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun assertWithinBudget(what: String, count: Int, budget: Int, useInstead: String) {
        assertTrue(
            "$what: found $count, budget is $budget. Use $useInstead. " +
                "If you removed some, lower the budget in this file to $count.",
            count <= budget,
        )
    }

    private fun File.kotlinFiles(): List<File> =
        walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun File.isTokenLayer(): Boolean =
        path.contains("/ui/theme/") || path.contains("/ui/design/")

    private fun File.isDesignLayer(): Boolean = path.contains("/ui/design/")

    /** Comment lines mentioning an API are documentation, not a call site. */
    private fun String.isCode(): Boolean {
        val t = trim()
        return !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*")
    }

    /**
     * Unit tests run with the module directory as the working directory, but
     * that is a Gradle default rather than a guarantee, so walk up until the
     * source tree is found instead of assuming.
     */
    private fun resolve(relative: String): File {
        var dir: File? = File(".").absoluteFile
        repeat(4) {
            val candidate = dir?.resolve(relative)
            if (candidate?.isDirectory == true) return candidate
            val nested = dir?.resolve("app")?.resolve(relative)
            if (nested?.isDirectory == true) return nested
            dir = dir?.parentFile
        }
        fail("Could not locate $relative from ${File(".").absolutePath}")
        error("unreachable")
    }

    private companion object {
        val RAW_DP = Regex("""\b\d+(\.\d+)?\.dp\b""")
        val RAW_COLOR = Regex("""\bColor\(0x""")
        val RAW_TEXT_FIELD = Regex("""(^|[^A-Za-z])(OutlinedTextField|TextField)\(""", RegexOption.MULTILINE)
        val ANIMATES = Regex(
            """\b(animate(Float|Dp|Color|Int|Offset|Size|Rect)AsState|rememberInfiniteTransition)\b""",
        )
        val RAW_CARD = Regex("""(^|[^A-Za-z])(Card|ElevatedCard|OutlinedCard)\(""", RegexOption.MULTILINE)
    }
}
