package com.elmtrackr.app.ui.dashboard

import androidx.compose.ui.Alignment
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.settings.ClockFaceGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Evolution pack: the art's own rules, and the wiring around it.
 *
 * The creatures are hand-authored pixel grids, which makes them the one part of the
 * catalogue where a typo is a silent visual defect rather than a compile error — a stray
 * character paints nothing, a row a pixel short shears a form down one side, and an eye
 * authored as a single pixel simply stops blinking. So the conventions `EvolutionCreatures`
 * documents are asserted here rather than trusted, over every form of every species: a
 * hand-edit that breaks one fails a test instead of shipping.
 */
class EvolutionCreaturesTest {

    private val species = mapOf(
        ClockStyle.FERN to FernCreature,
        ClockStyle.EMBER to EmberCreature,
        ClockStyle.DROPLET to DropletCreature,
        ClockStyle.SPARK to SparkCreature,
    )

    private fun forEachForm(check: (String, Int, EvolutionSpecies, List<String>) -> Unit) {
        species.forEach { (face, creature) ->
            creature.forms.forEachIndexed { hour, form ->
                check("$face hour $hour", hour, creature, form)
            }
        }
    }

    @Test
    fun `every creature has a form for the shell and for each of the eight hours`() {
        // Nine, not eight: hour zero is the shell the day starts in, and the renderer
        // indexes straight into this list with the floor of the hours worked.
        species.forEach { (face, creature) ->
            assertEquals("$face", EVOLUTION_FINAL_STAGE + 1, creature.forms.size)
        }
    }

    @Test
    fun `every form is a rectangle`() {
        // A short row would shear the form down one side, and the renderer reads the
        // sprite's width off the first row alone.
        forEachForm { where, _, _, form ->
            val width = form.first().length
            assertTrue("$where: empty form", width > 0)
            form.forEachIndexed { row, line ->
                assertEquals("$where: row $row is a different width", width, line.length)
            }
        }
    }

    @Test
    fun `every form is cropped to its own art`() {
        // The forms are drawn bottom-aligned on a shared ground line, so a blank row left
        // at the bottom would lift that creature off the ground by a pixel, and one at the
        // top would silently shrink it.
        forEachForm { where, _, _, form ->
            assertTrue("$where: blank first row", form.first().any { it != '.' })
            assertTrue("$where: blank last row", form.last().any { it != '.' })
        }
    }

    @Test
    fun `every pixel in every form is a colour`() {
        // The one check that catches a typo in the art. An unknown character paints
        // nothing, which is a hole in the creature and no error anywhere.
        forEachForm { where, _, creature, form ->
            form.forEach { line ->
                line.forEach { cell ->
                    if (cell != '.') {
                        assertNotNull(
                            "$where: '$cell' is not in the legend",
                            creature.ramp.inkOf(cell),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `every eye is a two pixel pair, so it can blink and glance`() {
        // The renderer finds eyes by shape -- an `ek` or `ke` run -- and re-emits them
        // shut or with the pupil moved. A single stray 'k' would be a pupil that never
        // blinks and never looks anywhere.
        forEachForm { where, _, _, form ->
            form.forEachIndexed { row, line ->
                var col = 0
                while (col < line.length) {
                    val cell = line[col]
                    if (cell != 'e' && cell != 'k') {
                        col++
                        continue
                    }
                    val next = line.getOrNull(col + 1)
                    assertTrue(
                        "$where: row $row has an unpaired eye pixel at $col",
                        (cell == 'e' && next == 'k') || (cell == 'k' && next == 'e'),
                    )
                    col += 2
                }
            }
        }
    }

    @Test
    fun `every hatched form has eyes`() {
        // The shell has none, by design. Everything after it is a character, and a
        // character with no eyes cannot do any of the things that make it feel alive.
        forEachForm { where, hour, _, form ->
            val hasEyes = form.any { line -> line.any { it == 'e' } }
            assertEquals("$where: eyes", hour > 0, hasEyes)
        }
    }

    @Test
    fun `each hour is at least as big as the one before it`() {
        // Growth has to be visible, and a form that came out shorter than its predecessor
        // would read as the creature having shrunk -- the opposite of what the hour means.
        // Measured from hour one: the shell is a shell and is taller than a hatchling.
        species.forEach { (face, creature) ->
            (2..EVOLUTION_FINAL_STAGE).forEach { hour ->
                val previous = creature.forms[hour - 1].size
                val current = creature.forms[hour].size
                assertTrue(
                    "$face hour $hour is $current rows, down from $previous",
                    current >= previous,
                )
            }
            assertTrue(
                "$face should end taller than it hatched",
                creature.forms[EVOLUTION_FINAL_STAGE].size > creature.forms[1].size,
            )
        }
    }

    @Test
    fun `the four species are distinct drawings`() {
        // Four palettes over one set of sprites would be a recolour sold as a pack.
        val silhouettes = species.values.map { creature ->
            creature.forms.map { form -> form.map { line -> line.map { if (it == '.') '.' else '#' } } }
        }
        silhouettes.indices.forEach { a ->
            (a + 1 until silhouettes.size).forEach { b ->
                assertFalse("species $a and $b draw the same shapes", silhouettes[a] == silhouettes[b])
            }
        }
    }

    @Test
    fun `only the Evolution faces have a creature`() {
        val pack = ClockFaceGroup.EVOLUTION.faces.map { it.toSupportedOrDefault() }.toSet()
        SupportedClockStyle.entries.forEach { style ->
            val creature = evolutionSpeciesOf(style)
            if (style in pack) {
                assertNotNull("$style is in the pack and needs a creature", creature)
            } else {
                assertNull("$style is not in the pack and must have none", creature)
            }
        }
    }

    @Test
    fun `only the Evolution faces move their reading off centre`() {
        // The creature stands where a centred reading would print the elapsed time across
        // its face. Stated over the whole enum so a face added to this pack later without
        // the alignment fails here rather than on someone's dashboard.
        val pack = ClockFaceGroup.EVOLUTION.faces.map { it.toSupportedOrDefault() }.toSet()
        SupportedClockStyle.entries.forEach { style ->
            val centred = style.readingAlignment() == Alignment.Center
            assertEquals("$style: readingAlignment", style !in pack, centred)
        }
    }

    @Test
    fun `the Evolution faces let the dashboard compose the reading`() {
        // They draw a creature, not a readout. Were they to claim otherwise the dashboard
        // would suppress the elapsed time and the face would show no time at all.
        ClockFaceGroup.EVOLUTION.faces.forEach { face ->
            assertFalse("$face", face.toSupportedOrDefault().drawsOwnReading())
        }
    }

    @Test
    fun `the pack is four faces, sold, and carries a version`() {
        assertEquals(ClockFaceGroup.GROUP_SIZE, ClockFaceGroup.EVOLUTION.faces.size)
        assertEquals(
            listOf(ClockStyle.FERN, ClockStyle.EMBER, ClockStyle.DROPLET, ClockStyle.SPARK),
            ClockFaceGroup.EVOLUTION.faces,
        )
        assertFalse(ClockFaceGroup.EVOLUTION.isBundled)
        assertTrue(ClockFaceGroup.EVOLUTION in ClockFaceGroup.packs)
        assertEquals("1.4.0", ClockFaceGroup.EVOLUTION.since)
        assertTrue(ClockFaceGroup.EVOLUTION.isNewIn("1.4.0"))
        assertFalse("the ribbon must expire without a checklist", ClockFaceGroup.EVOLUTION.isNewIn("1.6.0"))
    }
}
