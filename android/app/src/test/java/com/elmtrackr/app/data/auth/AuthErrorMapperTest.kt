package com.elmtrackr.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AuthErrorMapperTest {

    @Test
    fun `invalid credentials message does not expose request details`() {
        val rawError = IllegalStateException(
            "invalid_credentials: Invalid login credentials " +
                "URL: https://example.supabase.co/auth/v1/token " +
                "Headers: Authorization=Bearer secret-token apikey=secret-key",
        )

        val message = AuthErrorMapper.messageFor(rawError, AuthOperation.SIGN_IN)

        assertEquals("Incorrect email or password.", message)
        assertFalse(message.contains("https://"))
        assertFalse(message.contains("Authorization"))
        assertFalse(message.contains("secret"))
    }

    @Test
    fun `known auth failures have actionable messages`() {
        assertEquals(
            "Confirm your email before signing in.",
            AuthErrorMapper.messageFor(
                IllegalStateException("email_not_confirmed"),
                AuthOperation.SIGN_IN,
            ),
        )
        assertEquals(
            "An account with this email already exists.",
            AuthErrorMapper.messageFor(
                IllegalStateException("user_already_exists"),
                AuthOperation.SIGN_UP,
            ),
        )
        assertEquals(
            "Too many attempts. Please wait and try again.",
            AuthErrorMapper.messageFor(
                IllegalStateException("over_email_send_rate_limit"),
                AuthOperation.PASSWORD_RESET,
            ),
        )
    }

    @Test
    fun `network failures and unknown failures use safe fallbacks`() {
        assertEquals(
            "Check your internet connection and try again.",
            AuthErrorMapper.messageFor(
                IllegalStateException("java.net.UnknownHostException"),
                AuthOperation.SIGN_IN,
            ),
        )
        assertEquals(
            "Unable to create your account. Please try again.",
            AuthErrorMapper.messageFor(
                IllegalStateException("URL: https://example.supabase.co Headers: apikey=secret"),
                AuthOperation.SIGN_UP,
            ),
        )
    }
}
