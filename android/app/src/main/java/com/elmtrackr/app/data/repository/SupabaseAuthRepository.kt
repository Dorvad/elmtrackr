package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.auth.SupabaseClientProvider
import com.elmtrackr.app.data.auth.AuthCallbackParser
import com.elmtrackr.app.data.auth.AuthCallbackPayload
import com.elmtrackr.app.data.auth.AuthErrorMapper
import com.elmtrackr.app.data.auth.AuthOperation
import com.elmtrackr.app.data.auth.AuthSessionCoordinator
import com.elmtrackr.app.data.local.dao.ProfileDao
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.domain.model.AuthResult
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.data.local.LocalUserDataCleaner
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val appPrefs: AppPreferencesRepository,
    private val localUserDataCleaner: LocalUserDataCleaner,
    authSessionCoordinator: AuthSessionCoordinator,
) : AuthRepository {

    private val onAuthenticated: suspend (String) -> Unit = authSessionCoordinator::onUserAuthenticated

    private companion object {
        const val AUTH_CALLBACK = "elmtrackr://auth/callback"
        const val AUTH_RESET_CALLBACK = "elmtrackr://auth/reset-password"
    }

    private val client: SupabaseClient? = SupabaseClientProvider.get()
    private val _passwordRecoveryRequired = MutableStateFlow(false)
    private val _deepLinkErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    override val deepLinkErrors: SharedFlow<String> = _deepLinkErrors.asSharedFlow()

    override fun observePasswordRecoveryRequired(): Flow<Boolean> =
        _passwordRecoveryRequired.asStateFlow()

    override fun isConfigured(): Boolean = client != null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCurrentProfile(): Flow<Profile?> {
        val c = client ?: return flowOf(null)
        return c.auth.sessionStatus
            .filter { it !is SessionStatus.Initializing }
            .flatMapLatest { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user ?: return@flatMapLatest flowOf(null)
                        val authProfile = user.toProfile()
                        flow {
                            runCatching {
                                onAuthenticated(authProfile.id)
                                if (profileDao.getProfile(authProfile.id) == null) {
                                    profileDao.upsertProfile(
                                        authProfile.toEntity(
                                            userId = authProfile.id,
                                            syncStatus = SyncStatus.SYNCED,
                                            remoteId = authProfile.id,
                                            lastSyncedAt = Instant.now().toEpochMilli(),
                                        ),
                                    )
                                }
                                appPrefs.setLastActiveUserId(authProfile.id)
                            }
                            emitAll(
                                profileDao.observeProfile(authProfile.id).map { localProfile ->
                                    localProfile?.toDomain() ?: authProfile
                                },
                            )
                        }
                    }
                    else -> flowOf(null)
                }
            }
    }

    override suspend fun getCurrentProfile(): Profile? {
        val authProfile = client?.auth?.currentUserOrNull()?.toProfile() ?: return null
        return profileDao.getProfile(authProfile.id)?.toDomain() ?: authProfile
    }

    override suspend fun saveProfile(profile: Profile, userId: String) {
        profileDao.upsertProfile(
            profile.toEntity(
                userId = userId,
                syncStatus = SyncStatus.SYNCED,
                remoteId = userId,
            ),
        )
    }

    override suspend fun signIn(email: String, password: String): AuthResult {
        val c = client ?: return AuthResult.NotConfigured
        return try {
            c.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            _passwordRecoveryRequired.value = false
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(AuthErrorMapper.messageFor(e, AuthOperation.SIGN_IN))
        }
    }

    override suspend fun signUp(email: String, password: String): AuthResult {
        val c = client ?: return AuthResult.NotConfigured
        return try {
            c.auth.signUpWith(Email, redirectUrl = AUTH_CALLBACK) {
                this.email = email
                this.password = password
            }
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(AuthErrorMapper.messageFor(e, AuthOperation.SIGN_UP))
        }
    }

    override suspend fun signOut() {
        try {
            client?.auth?.signOut()
        } catch (e: Exception) {
            // Best-effort — still clear local references below
        }
        _passwordRecoveryRequired.value = false
        appPrefs.setLastActiveUserId(null)
    }

    override suspend fun deleteAccount(): AuthResult {
        val userId = getCurrentProfile()?.id
            ?: return AuthResult.Error("No account to delete")
        return try {
            client?.postgrest?.rpc("delete_own_account")
            localUserDataCleaner.clearUserData(userId)
            signOut()
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(AuthErrorMapper.messageFor(e, AuthOperation.DELETE_ACCOUNT))
        }
    }

    override suspend fun resetPassword(email: String): AuthResult {
        val c = client ?: return AuthResult.NotConfigured
        return try {
            c.auth.resetPasswordForEmail(email, redirectUrl = AUTH_RESET_CALLBACK)
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(AuthErrorMapper.messageFor(e, AuthOperation.PASSWORD_RESET))
        }
    }

    override suspend fun updatePassword(newPassword: String): AuthResult {
        val c = client ?: return AuthResult.NotConfigured
        return try {
            c.auth.updateUser {
                password = newPassword
            }
            _passwordRecoveryRequired.value = false
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(AuthErrorMapper.messageFor(e, AuthOperation.UPDATE_PASSWORD))
        }
    }

    override suspend fun clearPasswordRecoveryRequired() {
        _passwordRecoveryRequired.value = false
    }

    override suspend fun handleDeepLink(uriString: String) {
        val c = client ?: return
        try {
            when (val payload = AuthCallbackParser.parse(uriString)) {
                is AuthCallbackPayload.Code -> {
                    c.auth.exchangeCodeForSession(payload.value)
                    _passwordRecoveryRequired.value = payload.isRecovery
                }
                is AuthCallbackPayload.Tokens -> {
                    c.auth.importAuthToken(payload.accessToken, payload.refreshToken)
                    _passwordRecoveryRequired.value = payload.isRecovery
                }
            }
        } catch (e: Exception) {
            _passwordRecoveryRequired.value = false
            _deepLinkErrors.emit(
                AuthErrorMapper.messageFor(e, AuthOperation.PASSWORD_RECOVERY),
            )
        }
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
