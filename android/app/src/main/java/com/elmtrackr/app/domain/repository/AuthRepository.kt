package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.model.AuthResult
import com.elmtrackr.app.domain.model.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

interface AuthRepository {

    /** True when SUPABASE_URL and SUPABASE_ANON_KEY are both present in the build config. */
    fun isConfigured(): Boolean

    /** Emits the current signed-in profile, or null when not authenticated. */
    fun observeCurrentProfile(): Flow<Profile?>

    /** True while the user must choose a new password after a recovery deep link. */
    fun observePasswordRecoveryRequired(): Flow<Boolean>

    /** Errors from deep-link handling that should be shown on the auth screen. */
    val deepLinkErrors: SharedFlow<UiText>

    suspend fun getCurrentProfile(): Profile?

    suspend fun saveProfile(profile: Profile, userId: String)

    /** Sign in with email and password. */
    suspend fun signIn(email: String, password: String): AuthResult

    /** Create a new account with email and password. */
    suspend fun signUp(email: String, password: String): AuthResult

    /**
     * Trade a Google ID token for a Supabase session.
     *
     * Signing up and signing in are the same call: Supabase creates the account
     * on first use, so there is nothing here for the user to choose between.
     *
     * @param rawNonce the *unhashed* nonce from the same request. Google was given
     *   its SHA-256 hash and put that in the token; Supabase hashes this value and
     *   compares. Passing the hash here fails every time.
     */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String): AuthResult

    /**
     * Start Google sign-in in a browser, for devices where Credential Manager has
     * nothing to offer. The session arrives later through [handleDeepLink], so
     * success here means "the browser opened", not "signed in".
     */
    suspend fun startGoogleBrowserSignIn(): AuthResult

    /** Sign out and clear the local session reference. */
    suspend fun signOut()

    /**
     * Permanently delete the signed-in account and all associated cloud and local data.
     * Requires network when Supabase is configured.
     */
    suspend fun deleteAccount(): AuthResult

    /** Send a password-reset email. */
    suspend fun resetPassword(email: String): AuthResult

    /** Save a new password after opening a recovery link. */
    suspend fun updatePassword(newPassword: String): AuthResult

    /** Clear the recovery gate after the user dismisses or completes reset. */
    suspend fun clearPasswordRecoveryRequired()

    /**
     * Handle a deep-link URI received by the activity (email confirmation,
     * password reset, magic link).  URI scheme: elmtrackr://auth/callback
     */
    suspend fun handleDeepLink(uriString: String)
}
