package com.elmtrackr.app.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class AppWindowSizeTest {

    @Test
    fun deviceFormFactor_phoneBelow600dp() {
        assertEquals(DeviceFormFactor.Phone, deviceFormFactor(599))
        assertEquals(DeviceFormFactor.Phone, deviceFormFactor(360))
    }

    @Test
    fun deviceFormFactor_tabletAt600dpAndAbove() {
        assertEquals(DeviceFormFactor.Tablet, deviceFormFactor(600))
        assertEquals(DeviceFormFactor.Tablet, deviceFormFactor(840))
    }
}
