package com.elmtrackr.app.data.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.elmtrackr.app.domain.auth.GoogleIdTokenResult

/**
 * Sorts a Credential Manager failure into the four outcomes the UI treats
 * differently. Pure, so the mapping is testable without a device.
 *
 * The split that matters is "we can fall back to the browser" versus "something
 * is actually wrong". Only the first two cases below are recoverable that way; a
 * misconfigured client id is not, and is deliberately left as a [Failure] so it
 * reaches crash reporting instead of being papered over by a fallback that
 * happens to work.
 */
internal object GoogleSignInFailures {

    fun classify(error: Throwable): GoogleIdTokenResult = when {
        error is GetCredentialCancellationException -> GoogleIdTokenResult.Cancelled

        error is NoCredentialException -> GoogleIdTokenResult.NoAccountOnDevice

        // Play Services absent or too old, or the OS predates the backing API.
        error is GetCredentialProviderConfigurationException ||
            error is GetCredentialUnsupportedException -> GoogleIdTokenResult.ProviderUnavailable

        // The provider reports "nothing to offer" as a generic failure often
        // enough to be worth reading. Matched on the provider's own wording and
        // nothing looser: guessing wrong here sends a user to the browser when
        // the real problem was elsewhere.
        error.mentions(NO_MATCHING_CREDENTIAL) -> GoogleIdTokenResult.NoAccountOnDevice

        else -> GoogleIdTokenResult.Failure(error)
    }

    private fun Throwable.mentions(text: String): Boolean =
        generateSequence(this) { it.cause }
            .take(CAUSE_DEPTH)
            .any { it.message.orEmpty().contains(text, ignoreCase = true) }

    private const val NO_MATCHING_CREDENTIAL = "cannot find a matching credential"
    private const val CAUSE_DEPTH = 5
}
