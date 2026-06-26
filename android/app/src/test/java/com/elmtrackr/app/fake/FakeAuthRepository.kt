package com.elmtrackr.app.fake

import com.elmtrackr.app.domain.model.AuthResult
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
    private val _deepLinkErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    var configured: Boolean = true
    var signInResult: AuthResult = AuthResult.Success
    var signUpResult: AuthResult = AuthResult.Success
    var resetPasswordResult: AuthResult = AuthResult.Success
    var updatePasswordResult: AuthResult = AuthResult.Success

    override val deepLinkErrors: SharedFlow<String> = _deepLinkErrors.asSharedFlow()

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
        if (signInResult is AuthResult.Success) {
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
}
