package com.elmtrackr.app.fake

import com.elmtrackr.app.domain.model.AuthResult
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

class FakeAuthRepository : AuthRepository {

    private val _profile = MutableStateFlow<Profile?>(null)

    var configured: Boolean = true
    var signInResult: AuthResult = AuthResult.Success
    var signUpResult: AuthResult = AuthResult.Success
    var resetPasswordResult: AuthResult = AuthResult.Success

    fun setProfile(profile: Profile?) { _profile.value = profile }

    override fun isConfigured(): Boolean = configured

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
    }

    override suspend fun resetPassword(email: String): AuthResult = resetPasswordResult

    override suspend fun handleDeepLink(uriString: String) {}
}
