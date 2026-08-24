package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.SickPayTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SickPayOptionsTest {

    // ── Recognising what a stored ladder is ───────────────────────────────────

    @Test
    fun `the Israeli preset ladder is the region standard`() {
        assertEquals(
            SickPayOption.REGION_STANDARD,
            SickPayOptions.of(LeavePresets.israeliSickTiers, RegionCode.IL),
        )
    }

    @Test
    fun `a single full rung is recognised as paid from day one`() {
        assertEquals(
            SickPayOption.FROM_DAY_ONE,
            SickPayOptions.of(LeavePresets.fullPayFromDayOneTiers, RegionCode.IL),
        )
    }

    /**
     * Outside Israel the preset *is* full pay from day one, and naming what it
     * pays is more use to the reader than naming where it came from.
     */
    @Test
    fun `a region whose standard is full pay reports the named arrangement`() {
        assertEquals(
            SickPayOption.FROM_DAY_ONE,
            SickPayOptions.of(SickPayOptions.standardTiers(RegionCode.US), RegionCode.US),
        )
        assertTrue(SickPayOptions.standardIsFromDayOne(RegionCode.US))
        assertTrue(!SickPayOptions.standardIsFromDayOne(RegionCode.IL))
    }

    /**
     * The case this enum exists for: a ladder built on another device must be
     * recognised, not silently replaced the moment the settings screen opens.
     */
    @Test
    fun `an arrangement neither option can express is custom`() {
        val employerLadder = listOf(
            SickPayTier(fromDay = 1, toDay = 1, multiplier = 0.5),
            SickPayTier(fromDay = 2, toDay = null, multiplier = 1.0),
        )
        assertEquals(SickPayOption.CUSTOM, SickPayOptions.of(employerLadder, RegionCode.IL))
        assertNull(SickPayOptions.tiersFor(SickPayOption.CUSTOM, RegionCode.IL))
    }

    /** Order is not part of the arrangement; the calculator resolves by day. */
    @Test
    fun `the same rungs in a different order are the same arrangement`() {
        assertEquals(
            SickPayOption.REGION_STANDARD,
            SickPayOptions.of(LeavePresets.israeliSickTiers.reversed(), RegionCode.IL),
        )
    }

    /** No ladder carries no arrangement to preserve, so it is not "custom". */
    @Test
    fun `an empty ladder is the region standard`() {
        assertEquals(SickPayOption.REGION_STANDARD, SickPayOptions.of(emptyList(), RegionCode.IL))
    }

    // ── Round trip ────────────────────────────────────────────────────────────

    @Test
    fun `every selectable option round-trips through its own ladder`() {
        for (region in RegionCode.entries) {
            for (option in SickPayOptions.selectable) {
                val tiers = SickPayOptions.tiersFor(option, region)!!
                val resolved = SickPayOptions.of(tiers, region)
                // Where a region's standard is full pay from day one the two
                // options are the same ladder, so REGION_STANDARD resolves to
                // FROM_DAY_ONE. Anything else would mean the ladder changed.
                val expected = if (
                    option == SickPayOption.REGION_STANDARD && SickPayOptions.standardIsFromDayOne(region)
                ) {
                    SickPayOption.FROM_DAY_ONE
                } else {
                    option
                }
                assertEquals("$region/$option", expected, resolved)
            }
        }
    }

    /** Switching to "from day one" is what an employer with a better deal needs. */
    @Test
    fun `switching an Israeli policy to day one pays the first day in full`() {
        val tiers = SickPayOptions.tiersFor(SickPayOption.FROM_DAY_ONE, RegionCode.IL)!!
        assertEquals(1.0, SickPayCalculator.resolveTier(tiers, 1)?.multiplier)
        assertEquals(1.0, SickPayCalculator.resolveTier(tiers, 30)?.multiplier)
    }

    // ── Describing a ladder for the screen ────────────────────────────────────

    @Test
    fun `the Israeli ladder describes as three rungs, the last open-ended`() {
        val rungs = SickPayOptions.describe(LeavePresets.israeliSickTiers)

        assertEquals(3, rungs.size)
        assertEquals(SickPayRung(fromDay = 1, toDay = 1, multiplier = 0.0), rungs[0])
        assertEquals(SickPayRung(fromDay = 2, toDay = 3, multiplier = 0.5), rungs[1])
        assertEquals(SickPayRung(fromDay = 4, toDay = null, multiplier = 1.0), rungs[2])
    }

    @Test
    fun `full pay from day one describes as one open-ended rung`() {
        val rungs = SickPayOptions.describe(LeavePresets.fullPayFromDayOneTiers)

        assertEquals(listOf(SickPayRung(fromDay = 1, toDay = null, multiplier = 1.0)), rungs)
    }

    /**
     * Described by what the ladder *pays*, not by how it is stored: these rungs
     * overlap, and the calculator lets the bounded one win. Reading the list
     * would report a 100% first day.
     */
    @Test
    fun `overlapping rungs are described the way they are paid`() {
        val overlapping = listOf(
            SickPayTier(fromDay = 1, toDay = null, multiplier = 1.0),
            SickPayTier(fromDay = 2, toDay = 3, multiplier = 0.5),
        )

        val rungs = SickPayOptions.describe(overlapping)

        assertEquals(SickPayRung(fromDay = 1, toDay = 1, multiplier = 1.0), rungs[0])
        assertEquals(SickPayRung(fromDay = 2, toDay = 3, multiplier = 0.5), rungs[1])
        assertEquals(SickPayRung(fromDay = 4, toDay = null, multiplier = 1.0), rungs[2])
    }

    /**
     * A hole is reported as a hole. Zero is a real, intentional rate here — the
     * Israeli first day — so "the policy does not say" cannot render as 0%.
     */
    @Test
    fun `a gap in the ladder is described as unset, not as zero`() {
        val holed = listOf(SickPayTier(fromDay = 3, toDay = null, multiplier = 1.0))

        val rungs = SickPayOptions.describe(holed)

        assertEquals(SickPayRung(fromDay = 1, toDay = 2, multiplier = null), rungs[0])
        assertEquals(SickPayRung(fromDay = 3, toDay = null, multiplier = 1.0), rungs[1])
    }

    @Test
    fun `an empty ladder describes as nothing rather than as an unpaid one`() {
        assertEquals(emptyList<SickPayRung>(), SickPayOptions.describe(emptyList()))
    }

    /** A ladder that keeps changing past the horizon still ends somewhere. */
    @Test
    fun `a bounded final rung keeps its end day`() {
        val bounded = listOf(SickPayTier(fromDay = 1, toDay = 5, multiplier = 1.0))

        val rungs = SickPayOptions.describe(bounded)

        assertEquals(SickPayRung(fromDay = 1, toDay = 5, multiplier = 1.0), rungs[0])
        // Days past the last rung have no rate, and say so.
        assertEquals(null, rungs[1].multiplier)
    }
}
