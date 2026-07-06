package com.elmtrackr.app.domain.premium

import com.elmtrackr.app.domain.model.PremiumType
import com.elmtrackr.app.domain.model.StackingPolicy
import kotlin.math.max

/**
 * Combines a shift premium multiplier with another rate multiplier (overtime, night, weekend).
 */
object PremiumStacking {

    fun combine(premiumMultiplier: Double, otherMultiplier: Double, type: PremiumType): Double {
        val premium = premiumMultiplier.coerceAtLeast(1.0)
        val other = otherMultiplier.coerceAtLeast(1.0)
        return when (type) {
            PremiumType.HIGHEST_ONLY -> maxOf(premium, other)
            PremiumType.ADDITIVE -> 1.0 + (premium - 1.0) + (other - 1.0)
            PremiumType.MULTIPLICATIVE -> premium * other
            PremiumType.BASE_PLUS_PREMIUM -> other + (premium - 1.0)
            PremiumType.PREMIUM_IN_REGULAR_RATE -> maxOf(premium, other)
            PremiumType.EXCLUDED_FROM_REGULAR_RATE -> maxOf(premium, other)
        }
    }

    /**
     * Combines two rate multipliers under a rule-level [StackingPolicy], with the
     * same math as [combine] so compensation rules and premium profiles stack
     * identically for matching options.
     */
    fun combinePolicy(premiumMultiplier: Double, otherMultiplier: Double, policy: StackingPolicy): Double {
        val premium = premiumMultiplier.coerceAtLeast(1.0)
        val other = otherMultiplier.coerceAtLeast(1.0)
        return when (policy) {
            StackingPolicy.HIGHEST_ONLY -> maxOf(premium, other)
            StackingPolicy.ADDITIVE -> 1.0 + (premium - 1.0) + (other - 1.0)
            StackingPolicy.MULTIPLICATIVE -> premium * other
            StackingPolicy.BASE_PLUS_PREMIUM -> other + (premium - 1.0)
            StackingPolicy.PREMIUM_IN_REGULAR_RATE -> maxOf(premium, other)
            StackingPolicy.EXCLUDED_FROM_REGULAR_RATE -> maxOf(premium, other)
        }
    }

    fun combineWithPolicy(
        premiumMultiplier: Double,
        otherMultiplier: Double,
        premiumType: PremiumType,
        fallbackPolicy: StackingPolicy,
    ): Double = when (premiumType) {
        PremiumType.HIGHEST_ONLY,
        PremiumType.ADDITIVE,
        PremiumType.MULTIPLICATIVE,
        PremiumType.BASE_PLUS_PREMIUM,
        PremiumType.PREMIUM_IN_REGULAR_RATE,
        PremiumType.EXCLUDED_FROM_REGULAR_RATE,
        -> combine(premiumMultiplier, otherMultiplier, premiumType)
    }

    fun regularRateForOvertime(baseMultiplier: Double, premiumMultiplier: Double, type: PremiumType): Double =
        when (type) {
            PremiumType.PREMIUM_IN_REGULAR_RATE -> combine(1.0, premiumMultiplier, type)
            else -> baseMultiplier
        }

    fun usesSeparatePremiumLine(type: PremiumType): Boolean = type == PremiumType.BASE_PLUS_PREMIUM

    fun excludesFromOvertimeBase(type: PremiumType): Boolean = type == PremiumType.EXCLUDED_FROM_REGULAR_RATE
}
