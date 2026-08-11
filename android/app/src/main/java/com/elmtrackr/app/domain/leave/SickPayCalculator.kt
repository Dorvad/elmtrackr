package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.model.SickPayTier

/**
 * Resolves the sick pay ladder. Entirely policy-driven: there is no
 * `if (day == 1)` anywhere, because the ladder is a workplace's arrangement and
 * a workplace that pays in full from day one must get full pay from the same
 * code path that pays the Israeli default 0% / 50% / 100%.
 */
object SickPayCalculator {

    /**
     * The rung covering [ordinal], or null when the ladder has a hole at that
     * day.
     *
     * A hole returns null rather than 0.0 on purpose. Zero is a real,
     * intentional multiplier — the Israeli first day is zero — so it cannot also
     * mean "this policy does not say". The caller turns null into a prompt to fix
     * the policy instead of showing an unexplained nothing.
     *
     * When rungs overlap, the most specific wins: the one starting latest, and
     * among those the one that is bounded. That makes "days 2-3 at 50%" override
     * an open-ended "from day 1 at 100%" without the user having to order the
     * list correctly.
     */
    fun resolveTier(tiers: List<SickPayTier>, ordinal: Int): SickPayTier? {
        if (ordinal < 1) return null
        return tiers
            .filter { tier ->
                ordinal >= tier.fromDay && (tier.toDay == null || ordinal <= tier.toDay)
            }
            .maxWithOrNull(
                compareBy({ it.fromDay }, { if (it.toDay == null) 0 else 1 }),
            )
    }

    /**
     * [expectedPay] is what the user would have been paid for that day had they
     * worked it; the return value is that amount after the ladder is applied.
     */
    fun calculate(ordinal: Int, expectedPay: Double, tiers: List<SickPayTier>): SickPayResult? {
        val tier = resolveTier(tiers, ordinal) ?: return null
        return SickPayResult(
            ordinal = ordinal,
            multiplier = tier.multiplier,
            estimatedGrossPay = expectedPay * tier.multiplier,
            tier = tier,
        )
    }
}

data class SickPayResult(
    val ordinal: Int,
    val multiplier: Double,
    val estimatedGrossPay: Double,
    val tier: SickPayTier,
)
