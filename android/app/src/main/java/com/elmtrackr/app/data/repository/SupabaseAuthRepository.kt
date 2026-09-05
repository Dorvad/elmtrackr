package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.auth.SupabaseClientProvider
import com.elmtrackr.app.data.auth.AuthCallbackParser
import com.elmtrackr.app.data.auth.AuthCallbackPayload
import com.elmtrackr.app.data.auth.AuthErrorMapper
import com.elmtrackr.app.data.auth.AuthOperation
import com.elmtrackr.app.data.auth.AuthSessionCoordinator
import com.elmtrackr.app.data.auth.SessionPresence
import com.elmtrackr.app.data.auth.presence
import com.elmtrackr.app.data.local.dao.ProfileDao
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.auth.GoogleSignInClient
import com.elmtrackr.app.domain.model.AuthResult
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.data.local.LocalUserDataCleaner
import com.elmtrackr.app.di.ApplicationScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
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
    private val authSessionCoordinator: AuthSessionCoordinator,
    private val syncTrigger: SyncTrigger,
    private val googleSignInClient: GoogleSignInClient,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : AuthRepository {

    private val onAuthenticated: suspend (String) -> Unit = authSessionCoordinator::onUserAuthenticated

    private companion object {
        const val AUTH_CALLBACK = "elmtrackr://auth/callback"
        const val AUTH_RESET_CALLBACK = "elmtrackr://auth/reset-password"
    }

    private val client: SupabaseClient? = SupabaseClientProvider.get()
    private val _passwordRecoveryRequired = MutableStateFlow(false)
    private val _deepLinkErrors = MutableSharedFlow<UiText>(extraBufferCapacity = 1)

    override val deepLinkErrors: SharedFlow<UiText> = _deepLinkErrors.asSharedFlow()

    override fun observePasswordRecoveryRequired(): Flow<Boolean> =
        _passwordRecoveryRequired.asStateFlow()

    override fun isConfigured(): Boolean = client != null

    // Shared so the many ViewModels observing the profile drive a single
    // session pipeline (one bootstrap, one Room observer) instead of one each.
    private val currentProfile: Flow<Profile?> = buildCurrentProfileFlow()

    override fun observeCurrentProfile(): Flow<Profile?> = currentProfile

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun buildCurrentProfileFlow(): Flow<Profile?> {
        val c = client ?: return flowOf(null)
        return c.auth.sessionStatus
            .filter { it !is SessionStatus.Initializing }
            .flatMapLatest<SessionStatus, Profile?> { status ->
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
                                            syncStatus = if (isConfigured()) {
                                                SyncStatus.PENDING_UPDATE
                                            } else {
                                                SyncStatus.SYNCED
                                            },
                                            remoteId = authProfile.id,
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
                    /**
                     * Not a sign-out. supabase-kt reports this when it could
                     * not *reach* the auth server to refresh — a network error
                     * or a 5xx — and it says so in its own logs: "Couldn't
                     * reach Supabase … Retrying in …". The session is still on
                     * the device and the library is already retrying. A refresh
                     * the server *rejects* is a different branch entirely, and
                     * that one clears the session and arrives here as
                     * [SessionStatus.NotAuthenticated].
                     *
                     * Emitting null here was the bug behind "it logged me out
                     * when I ended a shift". Clocking out schedules a sync, the
                     * sync is the request that discovers the access token has
                     * expired, and the end of a shift is exactly when someone
                     * walks out of the building and off the wifi — so the
                     * refresh fails on the network, the profile goes null, and
                     * `AppShellViewModel` sends a signed-in user to the login
                     * screen with their session intact.
                     *
                     * Serving the retained user instead costs nothing: this is
                     * a local-first app, every other reader already resolves
                     * the user through [CurrentUserProvider]'s
                     * `lastActiveUserId`, and the data was on the device
                     * already. When the retry succeeds the Authenticated branch
                     * takes over again; if the token really is dead, the
                     * NotAuthenticated branch below still signs them out.
                     */
                    else -> when (status.presence()) {
                        SessionPresence.RETAINED -> retainedProfile()
                        SessionPresence.SIGNED_OUT -> flowOf(null)
                        // Handled by the branch above; the session carries the
                        // user there and this arm has nothing to read it from.
                        SessionPresence.SIGNED_IN -> flowOf(null)
                    }
                }
            }
            .shareIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(
                    stopTimeoutMillis = 5_000,
                    replayExpirationMillis = 0,
                ),
                replay = 1,
            )
    }

    /**
     * The user the last authenticated session named, from local storage.
     *
     * Used while the session is in [SessionStatus.RefreshFailure] — see the
     * branch above. `lastActiveUserId` is written in the Authenticated branch and
     * cleared only by [signOut], so it names a session the user has not ended.
     * Null when there is nothing to fall back to, which correctly reads as signed
     * out.
     */
    private fun retainedProfile(): Flow<Profile?> = flow {
        val retainedId = appPrefs.preferences.first().lastActiveUserId
        if (retainedId == null) {
            emit(null)
            return@flow
        }
        emitAll(profileDao.observeProfile(retainedId).map { it?.toDomain() })
    }

    /**
     * The signed-in profile, tolerating a session that is mid-retry.
     *
     * `currentUserOrNull()` reads the session status, so it answers null during a
     * [SessionStatus.RefreshFailure] just as the flow above did. That matters
     * more here than it looks: `DashboardViewModel.clockOut` opens with
     * `getCurrentProfile()?.id ?: return`, so a failed token refresh did not only
     * show the login screen — it made the Clock out button do nothing at all,
     * silently, while the shift kept running. The widget and Wear paths were
     * unaffected because they resolve the user through [CurrentUserProvider],
     * which has always read `lastActiveUserId`.
     */
    override suspend fun getCurrentProfile(): Profile? {
        val c = client ?: return null
        c.auth.currentUserOrNull()?.toProfile()?.let { authProfile ->
            return profileDao.getProfile(authProfile.id)?.toDomain() ?: authProfile
        }
        if (c.auth.sessionStatus.value.presence() != SessionPresence.RETAINED) return null
        val retainedId = appPrefs.preferences.first().lastActiveUserId ?: return null
        return profileDao.getProfile(retainedId)?.toDomain()
    }

    override suspend fun saveProfile(profile: Profile, userId: String) {
        val existing = profileDao.getProfile(userId)
        profileDao.upsertProfile(
            profile.toEntity(
                userId = userId,
                syncStatus = SyncStatus.PENDING_UPDATE,
                remoteId = userId,
                lastSyncedAt = existing?.lastSyncedAt,
            ),
        )
        syncTrigger.schedule()
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
            AuthResult.Error(UiText.Res(AuthErrorMapper.messageResFor(e, AuthOperation.SIGN_IN)))
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
            AuthResult.Error(UiText.Res(AuthErrorMapper.messageResFor(e, AuthOperation.SIGN_UP)))
        }
    }

    override suspend fun signInWithGoogle(idToken: String, rawNonce: String): AuthResult {
        val c = client ?: return AuthResult.NotConfigured
        return try {
            c.auth.signInWith(IDToken) {
                this.idToken = idToken
                this.provider = Google
                // Raw, not hashed. Supabase hashes this and compares it against
                // the token's nonce claim, which holds the hash Google was given.
                this.nonce = rawNonce
            }
            _passwordRecoveryRequired.value = false
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(UiText.Res(AuthErrorMapper.messageResFor(e, AuthOperation.GOOGLE_SIGN_IN)))
        }
    }

    override suspend fun startGoogleBrowserSignIn(): AuthResult {
        val c = client ?: return AuthResult.NotConfigured
        return try {
            // Opens a browser and returns through elmtrackr://auth/callback, which
            // handleDeepLink already exchanges. Success means the browser opened;
            // the session arrives later, on the deep link.
            c.auth.signInWith(Google, redirectUrl = AUTH_CALLBACK)
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(UiText.Res(AuthErrorMapper.messageResFor(e, AuthOperation.GOOGLE_SIGN_IN)))
        }
    }

    override suspend fun signOut() {
        try {
            client?.auth?.signOut()
        } catch (e: Exception) {
            // Best-effort — still clear local references below
        }
        // Drop the remembered Google account too, so the next sign-in offers the
        // picker instead of silently returning the account just signed out of.
        googleSignInClient.forgetSelectedAccount()
        _passwordRecoveryRequired.value = false
        authSessionCoordinator.resetSession()
        appPrefs.setLastActiveUserId(null)
        // The next account on this device starts its first-run journey fresh;
        // without this the new user inherits the previous user's checklist,
        // celebrations, and the device-level onboarding flag.
        appPrefs.resetFirstRunNudges()
    }

    override suspend fun deleteAccount(): AuthResult {
        val userId = getCurrentProfile()?.id
            ?: return AuthResult.Error(UiText.Res(R.string.auth_error_no_account_to_delete))
        return try {
            client?.postgrest?.rpc("delete_own_account")
            localUserDataCleaner.clearUserData(userId)
            signOut()
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(UiText.Res(AuthErrorMapper.messageResFor(e, AuthOperation.DELETE_ACCOUNT)))
        }
    }

    override suspend fun resetPassword(email: String): AuthResult {
        val c = client ?: return AuthResult.NotConfigured
        return try {
            c.auth.resetPasswordForEmail(email, redirectUrl = AUTH_RESET_CALLBACK)
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(UiText.Res(AuthErrorMapper.messageResFor(e, AuthOperation.PASSWORD_RESET)))
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
            AuthResult.Error(UiText.Res(AuthErrorMapper.messageResFor(e, AuthOperation.UPDATE_PASSWORD)))
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
                UiText.Res(AuthErrorMapper.messageResFor(e, AuthOperation.PASSWORD_RECOVERY)),
            )
        }
    }

    private fun UserInfo.toProfile(): Profile = Profile(
        id = id,
        email = email ?: "",
        // "name" as a fallback for "full_name": which key a provider writes is
        // the provider's choice, and a user who signed up with Google should not
        // have to type a name the account already knows. Missing is still fine —
        // the greeting falls back and the checklist asks.
        fullName = runCatching {
            userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
                ?: userMetadata?.get("name")?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() },
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )
}
