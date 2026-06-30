package com.elmtrackr.app.domain.compensation

import com.elmtrackr.app.domain.model.RegionCode
import org.junit.Assert.assertEquals
import org.junit.Test

class RegionPresetsTest {

    @Test
    fun `onboarding region list puts Israel second to last`() {
        val codes = RegionPresets.all.map { it.regionCode }
        assertEquals(RegionCode.US, codes.first())
        assertEquals(RegionCode.IL, codes[codes.size - 2])
        assertEquals(RegionCode.CUSTOM, codes.last())
    }
}
