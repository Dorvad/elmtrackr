package com.elmtrackr.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IanaTimezonesTest {

    @Test
    fun `normalize returns device default for blank`() {
        val normalized = IanaTimezones.normalize("  ")
        assertTrue(IanaTimezones.isValid(normalized))
    }

    @Test
    fun `normalize keeps valid IANA id`() {
        assertEquals("Europe/London", IanaTimezones.normalize("Europe/London"))
    }

    @Test
    fun `normalize falls back for invalid id`() {
        val normalized = IanaTimezones.normalize("Not/A_Real_Zone")
        assertTrue(IanaTimezones.isValid(normalized))
        assertFalse(normalized == "Not/A_Real_Zone")
    }

    @Test
    fun `common zones include Jerusalem and UTC`() {
        assertTrue(IanaTimezones.common.contains("Asia/Jerusalem"))
        assertTrue(IanaTimezones.common.contains("UTC"))
    }

    @Test
    fun `display options include selected custom zone`() {
        val options = IanaTimezones.displayOptions("Australia/Perth")
        assertTrue(options.contains("Australia/Perth"))
    }
}
