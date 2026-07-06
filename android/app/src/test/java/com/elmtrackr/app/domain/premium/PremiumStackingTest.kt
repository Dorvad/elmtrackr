package com.elmtrackr.app.domain.premium

import com.elmtrackr.app.domain.model.PremiumType
import com.elmtrackr.app.domain.model.StackingPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class PremiumStackingTest {

    @Test
    fun `highest only picks max multiplier`() {
        assertEquals(1.5, PremiumStacking.combine(1.5, 1.25, PremiumType.HIGHEST_ONLY), 0.001)
    }

    @Test
    fun `additive sums premium deltas`() {
        assertEquals(1.75, PremiumStacking.combine(1.5, 1.25, PremiumType.ADDITIVE), 0.001)
    }

    @Test
    fun `multiplicative multiplies rates`() {
        assertEquals(1.875, PremiumStacking.combine(1.5, 1.25, PremiumType.MULTIPLICATIVE), 0.001)
    }

    @Test
    fun `policy combine matches premium combine for shared modes`() {
        assertEquals(
            PremiumStacking.combine(1.5, 1.25, PremiumType.HIGHEST_ONLY),
            PremiumStacking.combinePolicy(1.5, 1.25, StackingPolicy.HIGHEST_ONLY),
            0.0,
        )
        assertEquals(
            PremiumStacking.combine(1.5, 1.25, PremiumType.ADDITIVE),
            PremiumStacking.combinePolicy(1.5, 1.25, StackingPolicy.ADDITIVE),
            0.0,
        )
        assertEquals(
            PremiumStacking.combine(1.5, 1.25, PremiumType.MULTIPLICATIVE),
            PremiumStacking.combinePolicy(1.5, 1.25, StackingPolicy.MULTIPLICATIVE),
            0.0,
        )
        assertEquals(
            PremiumStacking.combine(1.5, 1.25, PremiumType.BASE_PLUS_PREMIUM),
            PremiumStacking.combinePolicy(1.5, 1.25, StackingPolicy.BASE_PLUS_PREMIUM),
            0.0,
        )
    }

    @Test
    fun `policy combine coerces sub-1 multipliers`() {
        assertEquals(1.25, PremiumStacking.combinePolicy(0.5, 1.25, StackingPolicy.ADDITIVE), 0.001)
    }
}
