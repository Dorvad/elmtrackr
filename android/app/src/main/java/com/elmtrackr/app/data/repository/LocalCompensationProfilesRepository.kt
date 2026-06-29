package com.elmtrackr.app.data.repository

import android.util.Log
import com.elmtrackr.app.data.local.dao.CompensationProfileDao
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.local.mapper.mapToDomain
import com.elmtrackr.app.data.local.mapper.toDomainOrNull
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

class LocalCompensationProfilesRepository(
    private val profileDao: CompensationProfileDao,
    private val settingsRepository: SettingsRepository,
) : CompensationProfilesRepository {

    override fun observeProfiles(userId: String): Flow<List<CompensationProfile>> =
        profileDao.observeProfiles(userId).map { entities -> entities.mapToDomain { it.toDomain() } }

    override suspend fun getProfiles(userId: String): List<CompensationProfile> =
        profileDao.getByUser(userId).mapToDomain { it.toDomain() }

    override suspend fun getProfileById(userId: String, profileId: String): CompensationProfile? =
        profileDao.getById(userId, profileId).toDomainOrNull { it.toDomain() }

    override suspend fun upsertProfile(profile: CompensationProfile): CompensationProfile {
        val now = System.currentTimeMillis()
        val profileId = profile.id.ifBlank { UUID.randomUUID().toString() }
        val existing = profileDao.getById(profile.userId, profileId)
            ?: profile.remoteId?.let { profileDao.getByRemoteId(it) }
        if (profile.isDefault) {
            profileDao.clearDefaultForUser(profile.userId)
        }
        val entity = profile.copy(id = profileId).toEntity(
            syncStatus = SyncStatus.SYNCED,
            remoteId = existing?.remoteId ?: profile.remoteId,
        ).copy(
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        profileDao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun deleteProfile(userId: String, profileId: String) {
        val existing = profileDao.getById(userId, profileId) ?: return
        profileDao.insert(
            existing.copy(
                syncStatus = SyncStatus.SYNCED,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun ensureMigrated(userId: String): CompensationProfile? {
        return try {
            val settings = settingsRepository.getSettings(userId) ?: return null
            if (!settings.onboardingCompleted) return null
            val existing = getProfiles(userId).filter { !it.isArchived }
            if (existing.isNotEmpty()) {
                return existing.firstOrNull { it.isDefault } ?: existing.first()
            }

            val now = Instant.now()
            val migration = CompensationResolver.buildMigrationProfile(userId, settings).copy(
                id = UUID.randomUUID().toString(),
                createdAt = now,
                updatedAt = now,
            )
            val saved = upsertProfile(migration)
            val updates = CompensationResolver.profileToLegacySettingsUpdates(saved)
            val updated = settings.apply(updates).copy(updatedAt = now)
            settingsRepository.saveSettings(updated)
            saved
        } catch (e: Exception) {
            Log.w(TAG, "Compensation profile migration failed for user $userId", e)
            null
        }
    }

    private companion object {
        const val TAG = "CompensationProfiles"
    }
}
