package com.elmtrackr.app.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Bidi

/**
 * What isolation does and does not buy, measured against [java.text.Bidi] —
 * the JDK's implementation of the same Unicode algorithm Android runs at layout
 * time — rather than against the isolate characters alone. Asserting that
 * [BidiText.isolate] inserted two characters would only prove the function did
 * what its name says.
 *
 * The scope is narrower than the folklore. Most tokens survive a Hebrew
 * sentence untouched: a reference like "2026-014" holds together because rule
 * W4 treats a hyphen between two European numbers as part of the number, and
 * emails, currency codes, alphanumeric references and Latin client names are
 * all strong left-to-right runs that the algorithm keeps intact on its own.
 *
 * What genuinely breaks is a token whose first or last character is
 * directionally neutral, because a neutral character resolves from its
 * surroundings and so gets absorbed into the Hebrew around it. A phone number
 * written "+972-50-1234567" is the clear case: the leading plus ends up drawn
 * at the wrong end of the number. Isolation is therefore a correctness fix for
 * that shape and, for the rest, defence against user-entered text whose shape
 * cannot be predicted here — a reference the user types in brackets, a note
 * that ends in punctuation, a client name with a trailing dot.
 */
class BidiTextTest {

    private val hebrew = "פרויקט"

    /** The bidi runs of [text] as resolved in a right-to-left paragraph. */
    private fun runsOf(text: String): List<Pair<String, Int>> {
        val bidi = Bidi(text, Bidi.DIRECTION_RIGHT_TO_LEFT)
        return (0 until bidi.runCount).map { run ->
            text.substring(bidi.getRunStart(run), bidi.getRunLimit(run)) to bidi.getRunLevel(run)
        }
    }

    /** The run that contains [needle], or null if the token was split apart. */
    private fun runContaining(text: String, needle: String): String? =
        runsOf(text).map { it.first }.firstOrNull { it.contains(needle) }

    // ── The shape that genuinely breaks ──────────────────────────────────────

    @Test
    fun `a leading plus is absorbed by Hebrew when the number is not isolated`() {
        val text = "$hebrew +972-50-1234567"

        // The plus lands in the right-to-left run with the Hebrew, so it is
        // drawn at the far end of the number instead of in front of it.
        val plusRun = runsOf(text).first { it.first.contains("+") }
        assertEquals("the plus should be swept into the RTL run", 1, plusRun.second)
        assertTrue(plusRun.first.contains(hebrew))
    }

    @Test
    fun `isolating the number keeps the plus attached to it`() {
        val text = "$hebrew ${BidiText.isolate("+972-50-1234567")}"

        val numberRun = runContaining(text, "972-50-1234567")
        assertTrue(
            "expected the plus inside the number's own run, got $numberRun",
            numberRun!!.contains("+972-50-1234567"),
        )
    }

    @Test
    fun `an isolated token is one contiguous run`() {
        listOf("+972-50-1234567", "(2026-014)", "\"REF-9\"", "2026-014.")
            .forEach { token ->
                val text = "$hebrew ${BidiText.isolate(token)} $hebrew"
                val stripped = BidiText.strip(runContaining(text, BidiText.strip(token).trim('.', '"'))!!)

                assertFalse(
                    "$token was mixed into the Hebrew around it: $stripped",
                    stripped.contains(hebrew),
                )
            }
    }

    // ── The shapes that were already safe ────────────────────────────────────

    @Test
    fun `a digit-and-hyphen reference is already one run without help`() {
        // Recorded so the claim behind the rest of this file stays honest: this
        // token never needed isolating, and a test asserting it "gets fixed"
        // would be testing nothing.
        assertEquals("2026-014", runContaining("$hebrew 2026-014", "2026-014"))
    }

    @Test
    fun `emails, codes and Latin names are already single runs`() {
        listOf("dor@example.com", "USD", "Acme Ltd", "INV-2026-014", "12.5%").forEach {
            assertEquals("$it should already survive", it, runContaining("$hebrew $it", it))
        }
    }

    @Test
    fun `isolating a token that was already safe does not disturb it`() {
        listOf("dor@example.com", "USD", "2026-014", "Acme Ltd").forEach { token ->
            val text = "$hebrew ${BidiText.isolate(token)}"

            assertEquals(token, BidiText.strip(runContaining(text, token)!!))
        }
    }

    // ── Direction is taken from the token, not forced ────────────────────────

    @Test
    fun `a Hebrew token inside an isolate stays right-to-left`() {
        // FIRST_STRONG rather than a left-to-right isolate: a Hebrew client name
        // in an English sentence must not be flipped into LTR.
        val text = "Project ${BidiText.isolate("פרויקט חדש")} was added"

        val run = runsOf(text).first { it.first.contains("פרויקט") }
        assertTrue("a Hebrew token should resolve to an odd level", run.second % 2 == 1)
    }

    @Test
    fun `an English token inside an isolate stays left-to-right`() {
        val text = "$hebrew ${BidiText.isolate("Acme Ltd")}"

        val run = runsOf(text).first { it.first.contains("Acme") }
        assertTrue("an English token should resolve to an even level", run.second % 2 == 0)
    }

    // ── Behaviour of the helper itself ───────────────────────────────────────

    @Test
    fun `isolating wraps the token in one isolate pair`() {
        val wrapped = BidiText.isolate("USD")

        assertEquals(BidiText.FIRST_STRONG_ISOLATE, wrapped.first())
        assertEquals(BidiText.POP_DIRECTIONAL_ISOLATE, wrapped.last())
        assertEquals(5, wrapped.length)
    }

    @Test
    fun `isolating twice does not nest`() {
        val once = BidiText.isolate("2026-014")

        assertEquals(once, BidiText.isolate(once))
    }

    @Test
    fun `null and blank tokens are left alone`() {
        // Adding invisible characters to an empty field would make a later
        // isBlank check answer no.
        assertEquals("", BidiText.isolate(null))
        assertEquals("", BidiText.isolate(""))
        assertEquals("   ", BidiText.isolate("   "))
        assertFalse(BidiText.containsIsolates(BidiText.isolate("")))
    }

    @Test
    fun `stripping is the inverse of isolating for every token shape`() {
        listOf("USD", "2026-014", "dor@example.com", "+972-50-1234567", "אקמה", "Acme Ltd", "(x)")
            .forEach { assertEquals(it, BidiText.strip(BidiText.isolate(it))) }
    }

    @Test
    fun `stripping leaves ordinary text as the same instance`() {
        // Exports run this over every cell; text with no isolates should not pay
        // for a copy.
        val plain = "2026-014"

        assertSame(plain, BidiText.strip(plain))
    }

    @Test
    fun `stripping removes the other isolate characters too`() {
        // LRI and RLI are not produced here, but text arriving from elsewhere
        // may carry them and an export must not.
        val text = "⁦USD⁩ ⁧ILS⁩"

        assertEquals("USD ILS", BidiText.strip(text))
        assertFalse(BidiText.containsIsolates(BidiText.strip(text)))
    }

    @Test
    fun `stripping handles null`() {
        assertEquals("", BidiText.strip(null))
    }

    @Test
    fun `isolateAll wraps each argument`() {
        val parts = BidiText.isolateAll("USD", "ILS")

        assertEquals(2, parts.size)
        parts.forEach { assertTrue(BidiText.containsIsolates(it)) }
    }
}
