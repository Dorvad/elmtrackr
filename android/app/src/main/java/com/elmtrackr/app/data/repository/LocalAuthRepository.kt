package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.dao.ProfileDao
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class LocalAuthRepository(
    private val profileDao: ProfileDao,
    private val appPrefs: AppPreferencesRepository,
) : AuthRepository {

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

    override suspend fun signInLocally(userId: String): Profile? {
        appPrefs.setLastActiveUserId(userId)
        return profileDao.getProfile(userId)?.toDomain()
    }

    override suspend fun signOut() {
        appPrefs.setLastActiveUserId(null)
    }
}
