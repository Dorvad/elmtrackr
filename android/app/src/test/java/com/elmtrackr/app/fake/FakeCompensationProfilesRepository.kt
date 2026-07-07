package com.elmtrackr.app.fake

import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.domain.model.CompensationProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeCompensationProfilesRepository : CompensationProfilesRepository {
    private val profiles = MutableStateFlow<List<CompensationProfile>>(emptyList())

    fun setProfiles(vararg items: CompensationProfile) {
        profiles.value = items.toList()
    }

    override fun observeProfiles(userId: String): Flow<List<CompensationProfile>> =
        profiles.map { list -> list.filter { it.userId == userId } }

    override suspend fun getProfiles(userId: String): List<CompensationProfile> =
        profiles.value.filter { it.userId == userId }

    override suspend fun getProfileById(userId: String, profileId: String): CompensationProfile? =
        profiles.value.firstOrNull { it.userId == userId && it.id == profileId }

    override suspend fun upsertProfile(profile: CompensationProfile): CompensationProfile {
        val profileId = profile.id.ifBlank { java.util.UUID.randomUUID().toString() }
        val saved = profile.copy(id = profileId)
        profiles.value = profiles.value.filter { it.id != profileId } + saved
        return saved
    }

    override suspend fun deleteProfile(userId: String, profileId: String) {
        profiles.value = profiles.value.filterNot { it.userId == userId && it.id == profileId }
    }

    override suspend fun ensureMigrated(userId: String): CompensationProfile? =
        profiles.value.firstOrNull { it.userId == userId && it.isDefault }
            ?: profiles.value.firstOrNull { it.userId == userId }
}
