package com.elmtrackr.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * The status pill is one colour used twice: as a 14% tint for its container and as
 * the text on top of it. That measures the accent against a wash of itself, which
 * is a far narrower gap than the accent against the surface — and it was failing.
 * On white the Active pill's aqua reached 1.83:1, Paused 2.59:1, Completed 3.56:1,
 * so four of the five work statuses were below AA on the word that carries the
 * meaning.
 *
 * `auroraStatusPillInk` is a composable, so the mapping is duplicated here as data
 * rather than invoked. That is a deliberate trade: the alternative is a Robolectric
 * render test per pair, and what matters is the arithmetic, which is testable on
 * the JVM. The duplication is the cost of that, and the tests below name every
 * mapped pair so a token changed on one side and not the other fails here.
 */
class StatusPillContrastTest {

    /** The alpha `StatusPill` applies to the accent for its container. */
    private val pillTintAlpha = 0.14f

    private val lightSurface = LightColorScheme.surface
    private val darkSurface = DarkColorScheme.surface

    /**
     * Every accent a pill is drawn from, with the ink `auroraStatusPillInk` resolves
     * for it, per theme. Kept in the same order as the `when` in that function.
     */
    private val lightPairs: List<Triple<String, Color, Color>> = listOf(
        Triple("Active / aqua", AuroraAqua, AuroraInfoInk),
        Triple("Paused / peach", AuroraPeachDeep, AuroraWarningInk),
        Triple("Completed / plum", AuroraPlum, AuroraPlumDeep),
        Triple("Draft / indigo", AuroraIndigo, AuroraIndigoDeep),
        Triple("Paid / success", AuroraSuccessInk, AuroraSuccessInkStrong),
        Triple("Overdue / error", LightColorScheme.error, LightColorScheme.error),
        Triple("Not billed / muted", LightColorScheme.onSurfaceVariant, LightColorScheme.onSurfaceVariant),
    )

    private val darkPairs: List<Triple<String, Color, Color>> = listOf(
        Triple("Active / aqua", AuroraAqua, AuroraDarkInfoInk),
        Triple("Paused / peach", AuroraPeachDeep, AuroraDarkOvertimeInk),
        Triple("Completed / plum", AuroraPlum, AuroraDarkPlumInk),
        Triple("Draft / indigo", AuroraIndigo, AuroraDarkIndigoInk),
        Triple("Paid / success", AuroraDarkSuccessInk, AuroraDarkSuccessInk),
        Triple("Overdue / error", DarkColorScheme.error, DarkColorScheme.error),
        Triple("Not billed / muted", DarkColorScheme.onSurfaceVariant, DarkColorScheme.onSurfaceVariant),
        Triple("Health flag / tertiary", DarkColorScheme.tertiary, DarkColorScheme.tertiary),
    )

    @Test
    fun `every light status pill is readable on its own tint`() {
        lightPairs.forEach { (name, accent, ink) ->
            assertPillContrast(name, ink, accent, lightSurface)
        }
    }

    @Test
    fun `every dark status pill is readable on its own tint`() {
        darkPairs.forEach { (name, accent, ink) ->
            assertPillContrast(name, ink, accent, darkSurface)
        }
    }

    /**
     * The reason the ink split exists. If an accent ever passes as its own pill text,
     * that pairing can be simplified — but until then, this failing is the signal
     * that someone has reverted the split.
     */
    @Test
    fun `the raw accents still fail as their own pill text`() {
        listOf(
            "aqua" to AuroraAqua,
            "peach" to AuroraPeachDeep,
            "plum" to AuroraPlum,
        ).forEach { (name, accent) ->
            val ratio = contrastRatio(accent, pillContainer(accent, lightSurface))
            assertTrue(
                "$name measured ${"%.2f".format(ratio)}:1 as its own pill text — " +
                    "if this now passes, revisit auroraStatusPillInk",
                ratio < 4.5,
            )
        }
    }

    /**
     * In the light scheme `tertiary` *is* `AuroraAqua`, so the resolver's AuroraAqua
     * branch has to be reached before its tertiary branch. This pins that: the value
     * a health-flag pill resolves to must be the aqua ink, not the accent.
     */
    @Test
    fun `the light tertiary accent resolves to the aqua ink, not itself`() {
        assertTrue(
            "LightColorScheme.tertiary is expected to be AuroraAqua; if that changed, " +
                "auroraStatusPillInk's branch order no longer matters and this test can go",
            LightColorScheme.tertiary == AuroraAqua,
        )
        assertPillContrast("Health flag / tertiary", AuroraInfoInk, LightColorScheme.tertiary, lightSurface)
    }

    /** The unmapped-accent fallback has to be readable, since it is what a new status gets. */
    @Test
    fun `the fallback ink is readable on any pill tint`() {
        listOf(
            "light" to Triple(LightColorScheme.onSurface, lightSurface, AuroraRose),
            "dark" to Triple(DarkColorScheme.onSurface, darkSurface, AuroraRose),
        ).forEach { (theme, triple) ->
            val (ink, surface, unmappedAccent) = triple
            assertPillContrast("$theme fallback", ink, unmappedAccent, surface)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** What `StatusPill` actually draws behind its label. */
    private fun pillContainer(accent: Color, surface: Color): Color =
        accent.copy(alpha = pillTintAlpha).compositeOver(surface)

    private fun assertPillContrast(name: String, ink: Color, accent: Color, surface: Color) {
        val container = pillContainer(accent, surface)
        val ratio = contrastRatio(ink, container)
        assertTrue(
            "$name pill text measured ${"%.2f".format(ratio)}:1 on its own 14% tint, needs 4.5",
            ratio >= 4.5,
        )
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (max(la, lb) / min(la, lb)).toDouble()
    }
}
