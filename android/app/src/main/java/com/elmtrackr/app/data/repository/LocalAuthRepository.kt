package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.LocalUserDataCleaner
import com.elmtrackr.app.data.local.dao.ProfileDao
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.domain.model.AuthResult
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Offline-only auth used when Supabase is not configured.
 * Operations that require a remote service return [AuthResult.NotConfigured].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalAuthRepository(
    private val profileDao: ProfileDao,
    private val appPrefs: AppPreferencesRepository,
    private val localUserDataCleaner: LocalUserDataCleaner,
) : AuthRepository {

    private val _passwordRecoveryRequired = MutableStateFlow(false)
    private val _deepLinkErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    override val deepLinkErrors: SharedFlow<String> = _deepLinkErrors.asSharedFlow()

    override fun isConfigured(): Boolean = false

    override fun observePasswordRecoveryRequired(): Flow<Boolean> = _passwordRecoveryRequired

    override fun observeCurrentProfile(): Flow<Profile?> =
        appPrefs.preferences
            .flatMapLatest { prefs ->
                val userId = prefs.lastActiveUserId ?: return@flatMapLatest flowOf(null)
                profileDao.observeProfile(userId).map { it?.toDomain() }
            }

    override suspend fun getCurrentProfile(): Profile? {
        val userId = appPrefs.preferences.first().lastActiveUserId ?: return null
        return profileDao.getProfile(userId)?.toDomain()
    }

    override suspend fun saveProfile(profile: Profile, userId: String) {
        profileDao.upsertProfile(profile.toEntity(userId = userId))
    }

    override suspend fun signIn(email: String, password: String): AuthResult =
        AuthResult.NotConfigured

    override suspend fun signUp(email: String, password: String): AuthResult =
        AuthResult.NotConfigured

    override suspend fun signOut() {
        appPrefs.setLastActiveUserId(null)
    }

    override suspend fun deleteAccount(): AuthResult {
        val userId = getCurrentProfile()?.id
            ?: return AuthResult.Error("No account to delete")
        localUserDataCleaner.clearUserData(userId)
        signOut()
        return AuthResult.Success
    }

    override suspend fun resetPassword(email: String): AuthResult =
        AuthResult.NotConfigured

    override suspend fun updatePassword(newPassword: String): AuthResult =
        AuthResult.NotConfigured

    override suspend fun clearPasswordRecoveryRequired() {
        _passwordRecoveryRequired.value = false
    }

    override suspend fun handleDeepLink(uriString: String) {
        // No-op for offline repository
    }
}
