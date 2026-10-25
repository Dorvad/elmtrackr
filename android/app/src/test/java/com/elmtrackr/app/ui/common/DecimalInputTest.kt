package com.elmtrackr.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecimalInputTest {

    /** The onboarding bug: the comma vanished and 62.5 became 625. */
    @Test
    fun `a comma is a decimal point, not something to drop`() {
        assertEquals("62.5", DecimalInput.sanitize("62,5"))
        assertEquals(62.5, DecimalInput.parse("62,5")!!, 0.0)
    }

    @Test
    fun `the Arabic separator and Arabic-Indic digits are read as typed`() {
        assertEquals("62.5", DecimalInput.sanitize("٦٢٫٥"))
    }

    @Test
    fun `only the first separator counts and junk is dropped`() {
        assertEquals("1.23", DecimalInput.sanitize("1.2.3"))
        assertEquals("45", DecimalInput.sanitize("₪ 45"))
        assertEquals("0.5", DecimalInput.sanitize(",5"))
    }

    @Test
    fun `blank stays blank so a field can mean no rate`() {
        assertEquals("", DecimalInput.sanitize(""))
        assertNull(DecimalInput.parse(""))
    }

    /** Hours were shown with the device separator ("8,60") and could not be edited back. */
    @Test
    fun `values are formatted to parse back in every language`() {
        assertEquals("8.6", DecimalInput.format(516 / 60.0))
        assertEquals("8", DecimalInput.format(8.0))
        assertEquals("1.25", DecimalInput.format(1.25))
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ru"))
            assertEquals("8.6", DecimalInput.format(516 / 60.0))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
