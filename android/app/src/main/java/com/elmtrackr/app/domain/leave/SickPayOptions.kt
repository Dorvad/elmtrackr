package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.SickPayTier

/**
 * The sick-pay arrangements a workplace can be set to from the settings screen.
 *
 * The ladder itself is data ([SickPayTier]), and deliberately so — nothing in the
 * calculator asks "is this Israel". What was missing was a way for a user to
 * *say* which arrangement their employer actually has: the region preset was
 * applied once when the policy was created and there was no screen that could
 * change it, so an Israeli user whose employer pays from day one was stuck with
 * the statutory 0% first day.
 *
 * [CUSTOM] exists so that a ladder this screen cannot express — one built on
 * another device, or synced from a future version — is recognised rather than
 * silently replaced the moment the screen loads.
 */
enum class SickPayOption {
    /** One open-ended rung: every sick day pays in full. */
    FROM_DAY_ONE,

    /** Whatever the region preset describes. In Israel that is 0% / 50% / 100%. */
    REGION_STANDARD,

    /** Something the two named options cannot express; left exactly as stored. */
    CUSTOM,
}

object SickPayOptions {

    /** The two arrangements a user can pick, in the order the selector shows them. */
    val selectable: List<SickPayOption> = listOf(SickPayOption.FROM_DAY_ONE, SickPayOption.REGION_STANDARD)

    /**
     * Which arrangement [tiers] represents for a workplace in [regionCode].
     *
     * [SickPayOption.FROM_DAY_ONE] is tested first, so a region whose own preset
     * pays in full from day one reports the named arrangement rather than
     * "standard" — the two are the same ladder, and naming what it pays is more
     * use to the reader than naming where it came from.
     *
     * An empty ladder is [SickPayOption.REGION_STANDARD]: it carries no
     * arrangement to preserve, and the preset is what
     * [com.elmtrackr.app.data.repository.LocalWorkplacesRepository] would create
     * for it anyway.
     */
    fun of(tiers: List<SickPayTier>, regionCode: RegionCode): SickPayOption = when {
        tiers.isEmpty() -> SickPayOption.REGION_STANDARD
        sameLadder(tiers, LeavePresets.fullPayFromDayOneTiers) -> SickPayOption.FROM_DAY_ONE
        sameLadder(tiers, standardTiers(regionCode)) -> SickPayOption.REGION_STANDARD
        else -> SickPayOption.CUSTOM
    }

    /**
     * The ladder for [option], or null for [SickPayOption.CUSTOM] — whose ladder
     * is whatever was already stored, and which therefore cannot be generated.
     */
    fun tiersFor(option: SickPayOption, regionCode: RegionCode): List<SickPayTier>? = when (option) {
        SickPayOption.FROM_DAY_ONE -> LeavePresets.fullPayFromDayOneTiers
        SickPayOption.REGION_STANDARD -> standardTiers(regionCode)
        SickPayOption.CUSTOM -> null
    }

    /** The region preset's ladder, falling back to full pay when it has none. */
    fun standardTiers(regionCode: RegionCode): List<SickPayTier> =
        LeavePresets.forRegion(regionCode).sick.payTiers
            .ifEmpty { LeavePresets.fullPayFromDayOneTiers }

    /**
     * True when the region's own standard is already full pay from day one, so
     * the two selectable options produce the same ladder.
     *
     * The selector still offers both — hiding one would make the same screen
     * look different per region for no gain — but the screen can use this to
     * say so instead of leaving the reader to wonder why nothing changed.
     */
    fun standardIsFromDayOne(regionCode: RegionCode): Boolean =
        sameLadder(standardTiers(regionCode), LeavePresets.fullPayFromDayOneTiers)

    /**
     * The ladder as ordered, non-overlapping rungs for display, resolved through
     * [SickPayCalculator] rather than by reading the list directly.
     *
     * Reading the list would describe the policy as stored; resolving describes
     * what it *pays*, which is what the reader is checking against a payslip. The
     * two differ whenever rungs overlap — which they are allowed to do, since
     * "days 2–3 at 50%" is meant to win over an open-ended "from day 1 at 100%".
     *
     * The final rung is open-ended ([SickPayRung.toDay] null) when the ladder
     * keeps paying the same rate indefinitely.
     */
    fun describe(tiers: List<SickPayTier>, throughDay: Int = DESCRIBE_THROUGH_DAY): List<SickPayRung> {
        if (tiers.isEmpty()) return emptyList()
        val rungs = mutableListOf<SickPayRung>()
        for (day in 1..throughDay) {
            val multiplier = SickPayCalculator.resolveTier(tiers, day)?.multiplier
            val last = rungs.lastOrNull()
            if (last != null && last.multiplier == multiplier) {
                rungs[rungs.lastIndex] = last.copy(toDay = day)
            } else {
                rungs += SickPayRung(fromDay = day, toDay = day, multiplier = multiplier)
            }
        }
        // Past the horizon the ladder is whatever it pays on the last day walked;
        // if that is still the same rung, it has no end to state.
        val beyond = SickPayCalculator.resolveTier(tiers, throughDay + 1)?.multiplier
        rungs.lastOrNull()?.let { last ->
            if (last.multiplier == beyond) rungs[rungs.lastIndex] = last.copy(toDay = null)
        }
        return rungs
    }

    /**
     * How far [describe] walks the ladder. Four weeks: long enough to cover the
     * rungs any of the presets define and any plausible employer arrangement,
     * short enough that the description stays readable.
     */
    private const val DESCRIBE_THROUGH_DAY = 28

    private fun sameLadder(a: List<SickPayTier>, b: List<SickPayTier>): Boolean =
        a.sortedBy { it.fromDay } == b.sortedBy { it.fromDay }
}

/**
 * One run of consecutive sick days that pay the same rate.
 *
 * [multiplier] is null where the ladder says nothing about those days — the same
 * hole [SickPayCalculator.resolveTier] reports, surfaced so the screen can show
 * the gap rather than implying those days pay zero.
 */
data class SickPayRung(
    val fromDay: Int,
    val toDay: Int?,
    val multiplier: Double?,
)
