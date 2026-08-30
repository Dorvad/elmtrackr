package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.domain.model.ClockStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockFaceCatalogTest {

    /**
     * The guard that matters: a face added to [ClockStyle] but not to a group
     * would be unreachable from the gallery, which is now the only place all
     * faces are listed. Failing here is the reminder.
     */
    @Test
    fun `every clock face belongs to exactly one group`() {
        val grouped = ClockFaceGroup.entries.flatMap { it.faces }

        assertEquals(
            "faces missing from a group: ${ClockStyle.entries - grouped.toSet()}",
            ClockStyle.entries.toSet(),
            grouped.toSet(),
        )
        assertEquals(
            "a face appears in more than one group",
            grouped.size,
            grouped.distinct().size,
        )
    }

    /** Groups larger than four would break the gallery's 2×2 blocks. */
    @Test
    fun `no group holds more than four faces`() {
        ClockFaceGroup.entries.forEach { group ->
            assertTrue(
                "${group.name} holds ${group.faces.size}",
                group.faces.size <= ClockFaceGroup.GROUP_SIZE,
            )
            assertTrue("${group.name} is empty", group.faces.isNotEmpty())
        }
    }

    @Test
    fun `quick picks lead with the selected face`() {
        val picks = clockFaceQuickPicks(
            current = ClockStyle.VINYL,
            recents = listOf(ClockStyle.SPROUT, ClockStyle.METRO),
        )

        assertEquals(ClockStyle.VINYL, picks.first())
        assertEquals(
            listOf(ClockStyle.VINYL, ClockStyle.SPROUT, ClockStyle.METRO, ClockStyle.CLASSIC),
            picks,
        )
    }

    /**
     * The selected face is normally the newest entry in the history too, so
     * without deduplication the row would show it twice and offer three choices
     * instead of four.
     */
    @Test
    fun `quick picks do not repeat the selected face`() {
        val picks = clockFaceQuickPicks(
            current = ClockStyle.LUNA,
            recents = listOf(ClockStyle.LUNA, ClockStyle.TIDE, ClockStyle.SAND),
        )

        assertEquals(listOf(ClockStyle.LUNA, ClockStyle.TIDE, ClockStyle.SAND, ClockStyle.CLASSIC), picks)
        assertEquals(picks.size, picks.distinct().size)
    }

    /** A first run has no history; the defaults are better than declaration order. */
    @Test
    fun `quick picks fall back to the essentials with no history`() {
        assertEquals(
            ClockFaceGroup.ESSENTIALS.faces,
            clockFaceQuickPicks(current = ClockStyle.CLASSIC, recents = emptyList()),
        )
    }

    @Test
    fun `quick picks always return the full count`() {
        ClockStyle.entries.forEach { style ->
            assertEquals(
                "current=$style",
                CLOCK_FACE_QUICK_PICK_COUNT,
                clockFaceQuickPicks(current = style, recents = emptyList()).size,
            )
        }
    }

    @Test
    fun `recording a face moves it to the front and bounds the history`() {
        var recents = emptyList<ClockStyle>()
        listOf(ClockStyle.SAND, ClockStyle.TIDE, ClockStyle.LUNA, ClockStyle.METRO, ClockStyle.VINYL)
            .forEach { recents = updatedClockFaceRecents(recents, it) }

        assertEquals(
            listOf(ClockStyle.VINYL, ClockStyle.METRO, ClockStyle.LUNA, ClockStyle.TIDE),
            recents,
        )
    }

    @Test
    fun `re-recording a face already in the history does not duplicate it`() {
        val recents = updatedClockFaceRecents(
            listOf(ClockStyle.TIDE, ClockStyle.SAND, ClockStyle.LUNA),
            ClockStyle.SAND,
        )

        assertEquals(listOf(ClockStyle.SAND, ClockStyle.TIDE, ClockStyle.LUNA), recents)
    }

    // ── The "New" ribbon ─────────────────────────────────────────────────────

    /**
     * The ribbon's whole contract: new through the minor series a pack shipped
     * in and the one after, gone at the second, with no release checklist. A
     * wrong claim of newness is worse than a missing ribbon, so anything the
     * rule cannot read counts as not new.
     */
    @Test
    fun `a pack is new for two minor release series and then stops`() {
        assertTrue(ClockFaceGroup.PROGRESS.isNewIn("1.1.1"))
        assertTrue(ClockFaceGroup.PROGRESS.isNewIn("1.2.4"))
        assertFalse(ClockFaceGroup.PROGRESS.isNewIn("1.3.0"))
        assertFalse(ClockFaceGroup.PROGRESS.isNewIn("2.1.0"))
        // A downgrade below the shipping version makes no claim either.
        assertFalse(ClockFaceGroup.PROGRESS.isNewIn("1.0.9"))
    }

    /** The launch bundle predates the ribbon and never wears it. */
    @Test
    fun `the bundled pack is never new`() {
        assertFalse(ClockFaceGroup.ESSENTIALS.isNewIn("1.1.1"))
        assertFalse(ClockFaceGroup.ESSENTIALS.isNewIn("1.2.4"))
    }

    @Test
    fun `an unparseable version is not new`() {
        assertFalse(ClockFaceGroup.PROGRESS.isNewIn(""))
        assertFalse(ClockFaceGroup.PROGRESS.isNewIn("nightly"))
        assertFalse(ClockFaceGroup.PROGRESS.isNewIn("2"))
    }

    // ── Packs ────────────────────────────────────────────────────────────────

    /**
     * The invariant the whole design rests on: whatever is stored, the user can
     * always reach the face that is actually selected.
     *
     * This is why availability is derived rather than migrated. Packs default to
     * "not added", so an existing user who picked Vinyl before packs existed would
     * otherwise open Settings to find their own clock unavailable — and there is no
     * seeding step here that a future code path could forget to run.
     */
    @Test
    fun `the pack holding the selected face is always available`() {
        ClockStyle.entries.forEach { face ->
            val available = ClockFacePacks.available(stored = emptySet(), selected = face)
            assertTrue(
                "$face is not reachable with nothing stored",
                face in available.flatMap { it.faces },
            )
        }
    }

    @Test
    fun `only essentials is available on a fresh install`() {
        assertEquals(
            setOf(ClockFaceGroup.ESSENTIALS),
            ClockFacePacks.available(stored = emptySet(), selected = ClockStyle.CLASSIC),
        )
    }

    @Test
    fun `essentials is bundled and nothing else is`() {
        assertEquals(listOf(ClockFaceGroup.ESSENTIALS), ClockFaceGroup.bundled)
        assertEquals(5, ClockFaceGroup.packs.size)
        assertTrue(ClockFaceGroup.packs.none { it.isBundled })
    }

    /**
     * The Payday contract: these four faces, in this order — Meter is
     * `faces.first()`, which makes it the store card's hero — shipped in
     * 1.3.0, so the New ribbon runs through the 1.3.x and 1.4.x series and
     * then retires itself.
     */
    @Test
    fun `payday holds the four money faces and is new for two series`() {
        assertEquals(
            listOf(ClockStyle.METER, ClockStyle.STACKS, ClockStyle.JAR, ClockStyle.TICKER),
            ClockFaceGroup.PAYDAY.faces,
        )
        assertTrue(ClockFaceGroup.PAYDAY.isNewIn("1.3.0"))
        assertTrue(ClockFaceGroup.PAYDAY.isNewIn("1.4.2"))
        assertFalse(ClockFaceGroup.PAYDAY.isNewIn("1.5.0"))
        assertFalse(ClockFaceGroup.PAYDAY.isNewIn("2.3.0"))
    }

    @Test
    fun `available faces follow gallery order`() {
        val faces = ClockFacePacks.availableFaces(
            stored = setOf(ClockFaceGroup.NATURE),
            selected = ClockStyle.CLASSIC,
        )

        assertEquals(ClockFaceGroup.ESSENTIALS.faces + ClockFaceGroup.NATURE.faces, faces)
    }

    @Test
    fun `the bundled pack can never be removed`() {
        assertFalse(ClockFacePacks.canRemove(ClockFaceGroup.ESSENTIALS, ClockStyle.VINYL))
    }

    /** Removing it would leave the dashboard drawing a face the user no longer has. */
    @Test
    fun `the pack holding the selected face cannot be removed`() {
        assertFalse(ClockFacePacks.canRemove(ClockFaceGroup.JOURNEYS, ClockStyle.VINYL))
        assertTrue(ClockFacePacks.canRemove(ClockFaceGroup.JOURNEYS, ClockStyle.CLASSIC))
    }

    @Test
    fun `the fallback after removing the selected pack is a bundled face`() {
        val fallback = ClockFacePacks.fallbackAfterRemoving(ClockFaceGroup.NATURE, ClockStyle.TIDE)

        assertEquals(ClockStyle.CLASSIC, fallback)
        assertTrue(fallback in ClockFaceGroup.ESSENTIALS.faces)
    }

    @Test
    fun `removing an unrelated pack needs no fallback`() {
        assertEquals(
            null,
            ClockFacePacks.fallbackAfterRemoving(ClockFaceGroup.NATURE, ClockStyle.CLASSIC),
        )
    }

    /** Same reasoning as the face history: a removed pack must vanish, not resolve. */
    @Test
    fun `unknown stored pack names are dropped`() {
        assertEquals(
            setOf(ClockFaceGroup.NATURE),
            ClockFacePacks.resolve(listOf("NATURE", "SEASONAL_2027", "")),
        )
    }

    @Test
    fun `quick picks never offer a face from a pack the user does not have`() {
        val available = ClockFacePacks.availableFaces(
            stored = emptySet(),
            selected = ClockStyle.CLASSIC,
        )

        val picks = clockFaceQuickPicks(
            current = ClockStyle.CLASSIC,
            // A history from before a pack was removed.
            recents = listOf(ClockStyle.VINYL, ClockStyle.TIDE),
            available = available,
        )

        assertEquals(ClockFaceGroup.ESSENTIALS.faces.toSet(), picks.toSet())
    }

    @Test
    fun `quick picks still lead with the selected face from an added pack`() {
        val available = ClockFacePacks.availableFaces(
            stored = setOf(ClockFaceGroup.JOURNEYS),
            selected = ClockStyle.VINYL,
        )

        val picks = clockFaceQuickPicks(
            current = ClockStyle.VINYL,
            recents = emptyList(),
            available = available,
        )

        assertEquals(ClockStyle.VINYL, picks.first())
        assertEquals(CLOCK_FACE_QUICK_PICK_COUNT, picks.size)
    }
}
