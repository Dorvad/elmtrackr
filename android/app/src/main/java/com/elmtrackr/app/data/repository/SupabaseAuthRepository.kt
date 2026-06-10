package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.auth.SupabaseClientProvider
import com.elmtrackr.app.data.local.dao.ProfileDao
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.domain.model.AuthResult
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

class SupabaseAuthRepository(
    private val profileDao: ProfileDao,
    private val appPrefs: AppPreferencesRepository,
) : AuthRepository {

    private val client: SupabaseClient? = SupabaseClientProvider.get()

    override fun isConfigured(): Boolean = client != null

    override fun observeCurrentProfile(): Flow<Profile?> {
        val c = client ?: return flowOf(null)
        return c.auth.sessionStatus
            // Don't emit while the SDK is restoring a session from storage —
            // keeps the ViewModel in its Loading initial state until settled.
            .filter { it !is SessionStatus.Initializing }
            .map { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user ?: return@map null
                        val profile = user.toProfile()
                        // Persist locally for offline profile display
                        runCatching {
                            profileDao.upsertProfile(profile.toEntity(userId = profile.id))
                            appPrefs.setLastActiveUserId(profile.id)
                        }
                        profile
                    }
                    else -> null
                }
            }
    }

    override suspend fun getCurrentProfile(): Profile? =
        client?.auth?.currentUserOrNull()?.toProfile()

    override suspend fun saveProfile(profile: Profile, userId: String) {
        profileDao.upsertProfile(profile.toEntity(userId = userId))
    }

    override suspend fun signIn(email: String, password: String): AuthResult {
        val c = client ?: return AuthResult.NotConfigured
        return try {
            c.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Sign in failed")
        }
    }

    override suspend fun signUp(email: String, password: String): AuthResult {
        val c = client ?: return AuthResult.NotConfigured
        return try {
            c.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Sign up failed")
        }
    }

    override suspend fun signOut() {
        try {
            client?.auth?.signOut()
        } catch (e: Exception) {
            // Best-effort — still clear local references below
        }
        appPrefs.setLastActiveUserId(null)
    }

    override suspend fun resetPassword(email: String): AuthResult {
        val c = client ?: return AuthResult.NotConfigured
        return try {
            c.auth.resetPasswordForEmail(email)
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Password reset failed")
        }
    }

    override suspend fun handleDeepLink(uriString: String) {
        // TODO: Parse fragment from deep-link URI and import the session.
        // Fragment format: access_token=...&refresh_token=...&type=...
        // This will be wired up fully when the email-confirmation / OAuth
        // flows are tested end-to-end.
    }

    private fun UserInfo.toProfile(): Profile = Profile(
        id = id,
        email = email ?: "",
        fullName = runCatching {
            userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
        }.getOrNull(),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )
}
