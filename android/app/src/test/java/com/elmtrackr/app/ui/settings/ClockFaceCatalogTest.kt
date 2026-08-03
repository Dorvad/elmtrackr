package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.domain.model.ClockStyle
import org.junit.Assert.assertEquals
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
}
