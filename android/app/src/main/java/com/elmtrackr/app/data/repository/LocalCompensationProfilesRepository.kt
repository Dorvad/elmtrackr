package com.elmtrackr.app.data.repository

import android.util.Log
import com.elmtrackr.app.data.local.dao.CompensationProfileDao
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.local.mapper.mapToDomain
import com.elmtrackr.app.data.local.mapper.toDomainOrNull
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCompensationProfilesRepository @Inject constructor(
    private val profileDao: CompensationProfileDao,
    private val settingsRepository: SettingsRepository,
    private val syncTrigger: SyncTrigger,
) : CompensationProfilesRepository {

    private val migrationMutex = Mutex()

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
            syncStatus = syncStatusForMutation(existing),
            remoteId = existing?.remoteId ?: profile.remoteId,
        ).copy(
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        profileDao.insert(entity)
        syncTrigger.schedule()
        return entity.toDomain()
    }

    override suspend fun deleteProfile(userId: String, profileId: String) {
        val existing = profileDao.getById(userId, profileId)
            ?: profileDao.getByRemoteId(profileId)?.takeIf { it.userId == userId }
            ?: return
        val now = System.currentTimeMillis()
        profileDao.insert(
            existing.copy(
                isArchived = true,
                deletedAt = now,
                syncStatus = SyncStatus.PENDING_UPDATE,
                updatedAt = now,
            ),
        )
        syncTrigger.schedule()
    }

    override suspend fun ensureMigrated(userId: String): CompensationProfile? {
        return migrationMutex.withLock {
            try {
                val settings = settingsRepository.getSettings(userId) ?: return@withLock null
                if (!settings.onboardingCompleted) return@withLock null
                val existing = getProfiles(userId).filter { !it.isArchived }
                if (existing.isNotEmpty()) {
                    return@withLock existing.firstOrNull { it.isDefault } ?: existing.first()
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
    }

    private fun syncStatusForMutation(existing: CompensationProfileEntity?): SyncStatus = when {
        existing == null -> SyncStatus.PENDING_CREATE
        existing.syncStatus == SyncStatus.PENDING_CREATE -> SyncStatus.PENDING_CREATE
        existing.syncStatus == SyncStatus.PENDING_DELETE -> SyncStatus.PENDING_DELETE
        else -> SyncStatus.PENDING_UPDATE
    }

    private companion object {
        const val TAG = "CompensationProfiles"
    }
}
