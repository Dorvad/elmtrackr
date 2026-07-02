package com.elmtrackr.app.ui.components.states

import org.junit.Assert.assertEquals
import org.junit.Test

class HumanizeErrorMessageTest {

    @Test
    fun `network failures become a connection message`() {
        val expected = "We couldn't reach the server. Check your connection and try again."
        assertEquals(expected, humanizeErrorMessage("java.net.UnknownHostException: api.supabase.co"))
        assertEquals(expected, humanizeErrorMessage("Unable to resolve host \"api.supabase.co\""))
        assertEquals(expected, humanizeErrorMessage("Read timeout after 30000ms"))
        assertEquals(expected, humanizeErrorMessage("Failed to connect to /10.0.0.2:443"))
    }

    @Test
    fun `technical noise becomes a generic message`() {
        val expected = "We couldn't load your data. Please try again."
        assertEquals(expected, humanizeErrorMessage(null))
        assertEquals(expected, humanizeErrorMessage(""))
        assertEquals(expected, humanizeErrorMessage("Unknown error"))
        assertEquals(expected, humanizeErrorMessage("NullPointerException"))
        assertEquals(expected, humanizeErrorMessage("kotlinx.serialization.MissingFieldException: field x"))
        assertEquals(expected, humanizeErrorMessage("com.elmtrackr.app.SomeClass$Inner failed"))
    }

    @Test
    fun `sentence-like messages pass through`() {
        assertEquals(
            "This ride is not eligible for a travel refund",
            humanizeErrorMessage("This ride is not eligible for a travel refund"),
        )
    }
}
