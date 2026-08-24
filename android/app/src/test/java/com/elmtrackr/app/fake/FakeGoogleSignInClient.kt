package com.elmtrackr.app.fake

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import com.elmtrackr.app.domain.auth.GoogleIdTokenResult
import com.elmtrackr.app.domain.auth.GoogleSignInClient

/**
 * Stands in for Credential Manager, which cannot run off a device.
 *
 * The point of the seam: everything the ViewModel does with a Google result —
 * exchange it, stay silent on cancel, fall back to the browser, surface a real
 * failure — is decided here and is testable without Play Services.
 */
class FakeGoogleSignInClient(
    override var isConfigured: Boolean = true,
) : GoogleSignInClient {

    var nextResult: GoogleIdTokenResult =
        GoogleIdTokenResult.Success(idToken = "id-token", rawNonce = "raw-nonce")

    /**
     * Set to hold [requestIdToken] open, so a test can look at the screen while
     * the Google sheet would be up. Complete it to let the call finish.
     */
    var holdUntil: CompletableDeferred<Unit>? = null

    var requestCount: Int = 0
        private set
    var forgetCount: Int = 0
        private set

    override suspend fun requestIdToken(activityContext: Context): GoogleIdTokenResult {
        requestCount++
        holdUntil?.await()
        return nextResult
    }

    override suspend fun forgetSelectedAccount() {
        forgetCount++
    }
}
