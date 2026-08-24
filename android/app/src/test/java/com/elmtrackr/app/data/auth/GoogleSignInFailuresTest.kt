package com.elmtrackr.app.data.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.elmtrackr.app.domain.auth.GoogleIdTokenResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The classification decides whether a user gets sent to the browser, gets an
 * error, or gets left alone — three visibly different outcomes from the same
 * tap, so each mapping is pinned.
 */
class GoogleSignInFailuresTest {

    @Test
    fun `dismissing the sheet is a cancellation, not a failure`() {
        assertEquals(
            GoogleIdTokenResult.Cancelled,
            GoogleSignInFailures.classify(GetCredentialCancellationException()),
        )
    }

    @Test
    fun `no credential means no account to offer`() {
        assertEquals(
            GoogleIdTokenResult.NoAccountOnDevice,
            GoogleSignInFailures.classify(NoCredentialException()),
        )
    }

    @Test
    fun `a missing provider is recoverable through the browser`() {
        assertEquals(
            GoogleIdTokenResult.ProviderUnavailable,
            GoogleSignInFailures.classify(GetCredentialProviderConfigurationException()),
        )
        assertEquals(
            GoogleIdTokenResult.ProviderUnavailable,
            GoogleSignInFailures.classify(GetCredentialUnsupportedException()),
        )
    }

    @Test
    fun `the provider's own wording for an empty picker is read as no account`() {
        assertEquals(
            GoogleIdTokenResult.NoAccountOnDevice,
            GoogleSignInFailures.classify(
                GetCredentialUnknownException("16: [28433] Cannot find a matching credential."),
            ),
        )
    }

    @Test
    fun `a cause several levels down is still read`() {
        val wrapped = IllegalStateException(
            "wrapper",
            IllegalStateException("Cannot find a matching credential"),
        )
        assertEquals(GoogleIdTokenResult.NoAccountOnDevice, GoogleSignInFailures.classify(wrapped))
    }

    @Test
    fun `a misconfigured client stays a failure so it reaches crash reporting`() {
        // Deliberately not routed to the browser: a fallback that happens to work
        // would hide a broken client id for as long as it kept working.
        val result = GoogleSignInFailures.classify(
            GetCredentialUnknownException("[28444] Developer console is not set up correctly."),
        )
        assertTrue(result.toString(), result is GoogleIdTokenResult.Failure)
    }

    @Test
    fun `anything unrecognised is a failure rather than a guess`() {
        val boom = RuntimeException("boom")
        assertEquals(GoogleIdTokenResult.Failure(boom), GoogleSignInFailures.classify(boom))
    }
}
