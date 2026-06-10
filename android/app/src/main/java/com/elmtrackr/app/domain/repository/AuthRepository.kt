package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.domain.model.Profile
import kotlinx.coroutines.flow.Flow

/**
 * Manages authentication state and profile persistence.
 * Stub implementation used until Supabase auth is connected.
 */
interface AuthRepository {

    /** Emits the current signed-in profile, or null if not authenticated. */
    fun observeCurrentProfile(): Flow<Profile?>

    suspend fun getCurrentProfile(): Profile?

    suspend fun saveProfile(profile: Profile, userId: String)

    /** Local sign-in using a stored profile (offline/stub use). */
    suspend fun signInLocally(userId: String): Profile?

    suspend fun signOut()
}
