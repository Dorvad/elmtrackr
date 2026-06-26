package com.elmtrackr.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AuthCallbackParserTest {
    @Test
    fun `parses PKCE code callback`() {
        assertEquals(
            AuthCallbackPayload.Code("abc123"),
            AuthCallbackParser.parse("elmtrackr://auth/callback?code=abc123"),
        )
    }

    @Test
    fun `parses recovery PKCE code on reset-password path`() {
        assertEquals(
            AuthCallbackPayload.Code("abc123", isRecovery = true),
            AuthCallbackParser.parse("elmtrackr://auth/reset-password?code=abc123"),
        )
    }

    @Test
    fun `parses token fragment callback`() {
        assertEquals(
            AuthCallbackPayload.Tokens("access", "refresh", isRecovery = true),
            AuthCallbackParser.parse("elmtrackr://auth/callback#access_token=access&refresh_token=refresh&type=recovery"),
        )
    }

    @Test
    fun `surfaces provider error`() {
        assertThrows(IllegalStateException::class.java) {
            AuthCallbackParser.parse("elmtrackr://auth/callback?error=access_denied&error_description=Expired%20link")
        }
    }

    @Test
    fun `rejects unrelated URI`() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthCallbackParser.parse("https://example.com/callback?code=abc")
        }
    }
}
