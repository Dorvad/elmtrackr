package com.elmtrackr.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncErrorMapperTest {

    @Test
    fun `row policy error does not expose request credentials`() {
        val rawError = IllegalStateException(
            "new row violates row-level security policy Code: 42501 " +
                "URL: https://example.supabase.co/rest/v1/profiles " +
                "Authorization=Bearer secret-token apikey=secret-key",
        )

        val message = SyncErrorMapper.messageFor(rawError)

        assertEquals("Remote access denied.", message)
        assertFalse(message.contains("https://"))
        assertFalse(message.contains("Bearer"))
        assertFalse(message.contains("secret"))
    }

    @Test
    fun `network and unknown errors use safe messages`() {
        assertEquals(
            "Network unavailable.",
            SyncErrorMapper.messageFor(IllegalStateException("java.net.UnknownHostException")),
        )
        assertEquals(
            "Remote sync failed.",
            SyncErrorMapper.messageFor(IllegalStateException("Headers: apikey=secret")),
        )
    }
}
