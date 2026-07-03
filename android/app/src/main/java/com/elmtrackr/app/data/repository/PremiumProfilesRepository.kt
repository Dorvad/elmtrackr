package com.elmtrackr.app.data.repository

import com.elmtrackr.app.domain.model.PremiumProfile
import kotlinx.coroutines.flow.Flow

interface PremiumProfilesRepository {
    fun observeProfiles(userId: String): Flow<List<PremiumProfile>>
    suspend fun getProfiles(userId: String): List<PremiumProfile>
    suspend fun getProfileById(userId: String, profileId: String): PremiumProfile?
    suspend fun upsertProfile(profile: PremiumProfile): PremiumProfile
    suspend fun deleteProfile(userId: String, profileId: String)
    suspend fun ensureDefaults(userId: String): PremiumProfile
}
