package com.elmtrackr.app.data.auth

import com.elmtrackr.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorMapperTest {

    @Test
    fun `invalid credentials map to the credentials resource, never raw details`() {
        val rawError = IllegalStateException(
            "invalid_credentials: Invalid login credentials " +
                "URL: https://example.supabase.co/auth/v1/token " +
                "Headers: Authorization=Bearer secret-token apikey=secret-key",
        )

        // Returning a string resource id (not raw text) guarantees the UI can
        // never leak URLs, headers, or tokens from the underlying exception.
        assertEquals(
            R.string.auth_error_incorrect_credentials,
            AuthErrorMapper.messageResFor(rawError, AuthOperation.SIGN_IN),
        )
    }

    @Test
    fun `known auth failures map to actionable resources`() {
        assertEquals(
            R.string.auth_error_confirm_email,
            AuthErrorMapper.messageResFor(
                IllegalStateException("email_not_confirmed"),
                AuthOperation.SIGN_IN,
            ),
        )
        assertEquals(
            R.string.auth_error_account_exists,
            AuthErrorMapper.messageResFor(
                IllegalStateException("user_already_exists"),
                AuthOperation.SIGN_UP,
            ),
        )
        assertEquals(
            R.string.auth_error_rate_limited,
            AuthErrorMapper.messageResFor(
                IllegalStateException("over_email_send_rate_limit"),
                AuthOperation.PASSWORD_RESET,
            ),
        )
    }

    @Test
    fun `network failures and unknown failures use safe fallbacks`() {
        assertEquals(
            R.string.auth_error_network,
            AuthErrorMapper.messageResFor(
                IllegalStateException("java.net.UnknownHostException"),
                AuthOperation.SIGN_IN,
            ),
        )
        assertEquals(
            R.string.auth_error_sign_up,
            AuthErrorMapper.messageResFor(
                IllegalStateException("URL: https://example.supabase.co Headers: apikey=secret"),
                AuthOperation.SIGN_UP,
            ),
        )
    }
}
