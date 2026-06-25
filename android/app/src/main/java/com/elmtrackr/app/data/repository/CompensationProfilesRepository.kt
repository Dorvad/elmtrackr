package com.elmtrackr.app.data.repository

import com.elmtrackr.app.domain.model.CompensationProfile
import kotlinx.coroutines.flow.Flow

interface CompensationProfilesRepository {
    fun observeProfiles(userId: String): Flow<List<CompensationProfile>>
    suspend fun getProfiles(userId: String): List<CompensationProfile>
    suspend fun getProfileById(userId: String, profileId: String): CompensationProfile?
    suspend fun upsertProfile(profile: CompensationProfile): CompensationProfile
    suspend fun deleteProfile(userId: String, profileId: String)
    suspend fun ensureMigrated(userId: String): CompensationProfile?
}
