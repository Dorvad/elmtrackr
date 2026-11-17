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

    /**
     * The implicit flow's callback: a finished session in the fragment. The app is
     * PKCE-only and never asks for one, so the only sender is somebody else — and
     * importing it would sign the user into an account of that sender's choosing.
     */
    @Test
    fun `refuses a session handed over in the fragment`() {
        assertThrows(IllegalStateException::class.java) {
            AuthCallbackParser.parse(
                "elmtrackr://auth/callback#access_token=access&refresh_token=refresh",
            )
        }
    }

    @Test
    fun `refuses a recovery session handed over in the fragment`() {
        assertThrows(IllegalStateException::class.java) {
            AuthCallbackParser.parse(
                "elmtrackr://auth/reset-password#access_token=access&refresh_token=refresh&type=recovery",
            )
        }
    }

    /** A code still wins when a fragment is present: the code is what gets exchanged. */
    @Test
    fun `a code is read even when the callback also carries a fragment`() {
        assertEquals(
            AuthCallbackPayload.Code("abc123"),
            AuthCallbackParser.parse("elmtrackr://auth/callback?code=abc123#access_token=x&refresh_token=y"),
        )
    }

    @Test
    fun `refuses a callback with neither a code nor an error`() {
        assertThrows(IllegalStateException::class.java) {
            AuthCallbackParser.parse("elmtrackr://auth/callback")
        }
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
