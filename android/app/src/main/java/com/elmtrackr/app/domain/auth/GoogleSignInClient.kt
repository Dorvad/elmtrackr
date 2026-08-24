package com.elmtrackr.app.domain.auth

import android.content.Context

/**
 * Obtains a Google ID token that [com.elmtrackr.app.domain.repository.AuthRepository]
 * can exchange for a Supabase session.
 *
 * Kept separate from the repository for one practical reason: getting the token
 * puts a system sheet on screen, so it needs an **Activity** context, which a
 * repository has no business holding. The repository does the half that is pure
 * network — token in, session out.
 *
 * There is deliberately no distinction here between signing up and signing in.
 * Google returns the same token either way and Supabase creates the account on
 * first use, so offering the user two buttons would be asking them to answer a
 * question the system already knows the answer to.
 */
interface GoogleSignInClient {

    /**
     * False when the build carries no Google Web client id.
     *
     * Checked before the button is drawn: a sign-in button that cannot work is
     * worse than no button, because the user has no way to tell that the failure
     * is ours and will keep trying.
     */
    val isConfigured: Boolean

    /**
     * @param activityContext must be an Activity — Credential Manager shows a
     *   sheet over it. It is used for the duration of the call and never held.
     */
    suspend fun requestIdToken(activityContext: Context): GoogleIdTokenResult

    /**
     * Forget which Google account was used, so the next sign-in shows the picker
     * again. Called on sign-out — without it the sheet silently reuses the last
     * account and "sign out, then switch users" becomes a loop with no exit.
     */
    suspend fun forgetSelectedAccount()
}

sealed interface GoogleIdTokenResult {

    /**
     * @param rawNonce the unhashed nonce. Google received its SHA-256 hash and
     *   embedded that in the token; Supabase hashes this value and compares. Send
     *   the hash to Supabase and every sign-in fails, so the two must not be
     *   confused — hence the name.
     */
    data class Success(val idToken: String, val rawNonce: String) : GoogleIdTokenResult

    /** The user dismissed the sheet. Not an error, and must not be shown as one. */
    data object Cancelled : GoogleIdTokenResult

    /** Nothing to offer: no Google account on the device, or none the picker would use. */
    data object NoAccountOnDevice : GoogleIdTokenResult

    /** Credential Manager cannot run here — no Play Services, or too old a device. */
    data object ProviderUnavailable : GoogleIdTokenResult

    data class Failure(val error: Throwable) : GoogleIdTokenResult
}
