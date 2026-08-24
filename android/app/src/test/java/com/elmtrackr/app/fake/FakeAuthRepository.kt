package com.elmtrackr.app.fake

import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.AuthResult
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant

class FakeAuthRepository : AuthRepository {

    private val _profile = MutableStateFlow<Profile?>(null)
    private val _passwordRecoveryRequired = MutableStateFlow(false)
    private val _deepLinkErrors = MutableSharedFlow<UiText>(extraBufferCapacity = 1)

    var configured: Boolean = true
    var signInResult: AuthResult = AuthResult.Success
    var signInSetsProfile: Boolean = true
    var signUpResult: AuthResult = AuthResult.Success
    var resetPasswordResult: AuthResult = AuthResult.Success
    var googleSignInResult: AuthResult = AuthResult.Success
    var googleBrowserResult: AuthResult = AuthResult.Success
    var updatePasswordResult: AuthResult = AuthResult.Success

    override val deepLinkErrors: SharedFlow<UiText> = _deepLinkErrors.asSharedFlow()

    fun setProfile(profile: Profile?) { _profile.value = profile }

    fun setPasswordRecoveryRequired(required: Boolean) {
        _passwordRecoveryRequired.value = required
    }

    override fun isConfigured(): Boolean = configured

    override fun observePasswordRecoveryRequired(): Flow<Boolean> = _passwordRecoveryRequired

    override fun observeCurrentProfile(): Flow<Profile?> = _profile

    override suspend fun getCurrentProfile(): Profile? = _profile.value

    override suspend fun saveProfile(profile: Profile, userId: String) {
        _profile.value = profile
    }

    override suspend fun signIn(email: String, password: String): AuthResult {
        if (signInResult is AuthResult.Success && signInSetsProfile) {
            _profile.value = Profile(
                id = "user-1",
                email = email,
                fullName = null,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
        }
        return signInResult
    }

    override suspend fun signUp(email: String, password: String): AuthResult {
        if (signUpResult is AuthResult.Success) {
            _profile.value = Profile(
                id = "user-1",
                email = email,
                fullName = null,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
        }
        return signUpResult
    }

    /** The token and nonce of the last [signInWithGoogle], for assertions. */
    var lastGoogleIdToken: String? = null
        private set
    var lastGoogleRawNonce: String? = null
        private set
    var browserSignInStarted: Int = 0
        private set

    override suspend fun signInWithGoogle(idToken: String, rawNonce: String): AuthResult {
        lastGoogleIdToken = idToken
        lastGoogleRawNonce = rawNonce
        if (googleSignInResult is AuthResult.Success) {
            _profile.value = Profile(
                id = "user-1",
                email = "google@example.com",
                fullName = null,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
        }
        return googleSignInResult
    }

    override suspend fun startGoogleBrowserSignIn(): AuthResult {
        browserSignInStarted++
        return googleBrowserResult
    }

    override suspend fun signOut() {
        _profile.value = null
        _passwordRecoveryRequired.value = false
    }

    override suspend fun resetPassword(email: String): AuthResult = resetPasswordResult

    override suspend fun updatePassword(newPassword: String): AuthResult {
        if (updatePasswordResult is AuthResult.Success) {
            _passwordRecoveryRequired.value = false
        }
        return updatePasswordResult
    }

    override suspend fun clearPasswordRecoveryRequired() {
        _passwordRecoveryRequired.value = false
    }

    override suspend fun handleDeepLink(uriString: String) {}

    override suspend fun deleteAccount(): AuthResult {
        val userId = _profile.value?.id ?: return AuthResult.Error(UiText.Res(R.string.auth_error_no_account_to_delete))
        _profile.value = null
        return AuthResult.Success
    }
}
