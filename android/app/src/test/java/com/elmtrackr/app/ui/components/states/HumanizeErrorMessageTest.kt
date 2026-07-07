package com.elmtrackr.app.ui.components.states

import org.junit.Assert.assertEquals
import org.junit.Test

class HumanizeErrorMessageTest {

    @Test
    fun `network failures become a connection message`() {
        val expected = ErrorMessageKind.NETWORK
        assertEquals(expected, classifyErrorMessage("java.net.UnknownHostException: api.supabase.co"))
        assertEquals(expected, classifyErrorMessage("Unable to resolve host \"api.supabase.co\""))
        assertEquals(expected, classifyErrorMessage("Read timeout after 30000ms"))
        assertEquals(expected, classifyErrorMessage("Failed to connect to /10.0.0.2:443"))
    }

    @Test
    fun `technical noise becomes a generic message`() {
        val expected = ErrorMessageKind.GENERIC
        assertEquals(expected, classifyErrorMessage(null))
        assertEquals(expected, classifyErrorMessage(""))
        assertEquals(expected, classifyErrorMessage("Unknown error"))
        assertEquals(expected, classifyErrorMessage("NullPointerException"))
        assertEquals(expected, classifyErrorMessage("kotlinx.serialization.MissingFieldException: field x"))
        assertEquals(expected, classifyErrorMessage("com.elmtrackr.app.SomeClass\$Inner failed"))
    }

    @Test
    fun `sentence-like messages pass through`() {
        assertEquals(
            ErrorMessageKind.PASSTHROUGH,
            classifyErrorMessage("This ride is not eligible for a travel refund"),
        )
    }
}
