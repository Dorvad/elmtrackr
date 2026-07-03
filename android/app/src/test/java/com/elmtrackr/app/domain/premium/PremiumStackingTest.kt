package com.elmtrackr.app.domain.premium

import com.elmtrackr.app.domain.model.PremiumType
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
}
