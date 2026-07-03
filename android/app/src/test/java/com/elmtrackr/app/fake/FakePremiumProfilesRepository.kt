package com.elmtrackr.app.fake

import com.elmtrackr.app.data.repository.PremiumProfilesRepository
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.PremiumType
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePremiumProfilesRepository : PremiumProfilesRepository {
    private val profiles = MutableStateFlow<List<PremiumProfile>>(emptyList())

    fun setProfiles(vararg items: PremiumProfile) {
        profiles.value = items.toList()
    }

    override fun observeProfiles(userId: String): Flow<List<PremiumProfile>> =
        profiles.map { list -> list.filter { it.userId == userId } }

    override suspend fun getProfiles(userId: String): List<PremiumProfile> =
        profiles.value.filter { it.userId == userId }

    override suspend fun getProfileById(userId: String, profileId: String): PremiumProfile? =
        profiles.value.firstOrNull { it.userId == userId && it.id == profileId }

    override suspend fun upsertProfile(profile: PremiumProfile): PremiumProfile {
        profiles.value = profiles.value.filter { it.id != profile.id } + profile
        return profile
    }

    override suspend fun deleteProfile(userId: String, profileId: String) {
        profiles.value = profiles.value.filterNot { it.userId == userId && it.id == profileId }
    }

    override suspend fun ensureDefaults(userId: String): PremiumProfile {
        profiles.value.firstOrNull { it.userId == userId && it.isDefault }?.let { return it }
        val created = PremiumProfile(
            id = "default-premium",
            userId = userId,
            name = "Premium",
            multiplier = 1.5,
            premiumType = PremiumType.HIGHEST_ONLY,
            isDefault = true,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        profiles.value = profiles.value + created
        return created
    }
}
