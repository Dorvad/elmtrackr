package com.elmtrackr.app.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.elmtrackr.app.BuildConfig
import com.elmtrackr.app.domain.auth.GoogleIdTokenResult
import com.elmtrackr.app.domain.auth.GoogleSignInClient
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google sign-in through Credential Manager.
 *
 * [GetSignInWithGoogleOption] rather than `GetGoogleIdOption`: the latter is the
 * One Tap style prompt that filters to accounts already authorized for this app,
 * which is the wrong shape for a button labelled "continue with Google" — a first
 * time user has no authorized account and would get an empty sheet. This option
 * shows the full picker, including "use another account", so the same button
 * serves the first sign-up and every later sign-in.
 *
 * Nothing here talks to Supabase. It hands back a token and the raw nonce; the
 * repository trades them for a session.
 */
@Singleton
class CredentialManagerGoogleSignInClient @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : GoogleSignInClient {

    private val webClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID

    override val isConfigured: Boolean = webClientId.isNotBlank()

    override suspend fun requestIdToken(activityContext: Context): GoogleIdTokenResult {
        if (!isConfigured) return GoogleIdTokenResult.ProviderUnavailable

        val rawNonce = SignInNonce.random()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(webClientId)
                    .setNonce(SignInNonce.sha256Hex(rawNonce))
                    .build(),
            )
            .build()

        return try {
            val response = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
            readIdToken(response.credential, rawNonce)
        } catch (e: CancellationException) {
            // The coroutine was cancelled — the screen went away, the user did not
            // decline. Cancellation must propagate, never be reported.
            throw e
        } catch (e: Exception) {
            GoogleSignInFailures.classify(e)
        }
    }

    /** Best effort: failing to clear a hint is not worth failing a sign-out over. */
    override suspend fun forgetSelectedAccount() {
        if (!isConfigured) return
        try {
            CredentialManager.create(appContext).clearCredentialState(
                ClearCredentialStateRequest(ClearCredentialStateRequest.TYPE_CLEAR_CREDENTIAL_STATE),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best effort.
        }
    }

    private fun readIdToken(credential: Credential, rawNonce: String): GoogleIdTokenResult {
        val isGoogleToken = credential is CustomCredential &&
            (
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
                )
        if (!isGoogleToken) {
            return GoogleIdTokenResult.Failure(
                IllegalStateException("Unexpected credential type ${credential.type}"),
            )
        }
        return try {
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            if (token.isBlank()) {
                GoogleIdTokenResult.Failure(IllegalStateException("Google returned an empty ID token"))
            } else {
                GoogleIdTokenResult.Success(idToken = token, rawNonce = rawNonce)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GoogleIdTokenResult.Failure(e)
        }
    }
}
