package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.domain.model.AuthResult
import com.elmtrackr.app.domain.model.Profile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    /** True when SUPABASE_URL and SUPABASE_ANON_KEY are both present in the build config. */
    fun isConfigured(): Boolean

    /** Emits the current signed-in profile, or null when not authenticated. */
    fun observeCurrentProfile(): Flow<Profile?>

    suspend fun getCurrentProfile(): Profile?

    suspend fun saveProfile(profile: Profile, userId: String)

    /** Sign in with email and password. */
    suspend fun signIn(email: String, password: String): AuthResult

    /** Create a new account with email and password. */
    suspend fun signUp(email: String, password: String): AuthResult

    /** Sign out and clear the local session reference. */
    suspend fun signOut()

    /** Send a password-reset email. */
    suspend fun resetPassword(email: String): AuthResult

    /**
     * Handle a deep-link URI received by the activity (email confirmation,
     * password reset, magic link).  URI scheme: elmtrackr://auth/callback
     */
    suspend fun handleDeepLink(uriString: String)
}
