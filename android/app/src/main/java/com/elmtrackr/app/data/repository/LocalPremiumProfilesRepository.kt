package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.dao.PremiumProfileDao
import com.elmtrackr.app.data.local.entity.PremiumProfileEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.mapper.mapToDomain
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toDomainOrNull
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.PremiumType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import java.time.Clock

@Singleton
class LocalPremiumProfilesRepository @Inject constructor(
    private val premiumProfileDao: PremiumProfileDao,
    private val syncTrigger: SyncTrigger,
    private val clock: Clock = Clock.systemUTC(),
) : PremiumProfilesRepository {

    private val ensureMutex = Mutex()

    override fun observeProfiles(userId: String): Flow<List<PremiumProfile>> =
        premiumProfileDao.observeProfiles(userId).map { entities -> entities.mapToDomain { it.toDomain() } }

    override suspend fun getProfiles(userId: String): List<PremiumProfile> =
        premiumProfileDao.getByUser(userId).mapToDomain { it.toDomain() }

    override suspend fun getProfileById(userId: String, profileId: String): PremiumProfile? =
        premiumProfileDao.getById(userId, profileId).toDomainOrNull { it.toDomain() }
            ?: premiumProfileDao.getByRemoteId(profileId)?.takeIf { it.userId == userId }?.toDomain()

    override suspend fun upsertProfile(profile: PremiumProfile): PremiumProfile {
        val now = clock.millis()
        val profileId = profile.id.ifBlank { UUID.randomUUID().toString() }
        val existing = premiumProfileDao.getById(profile.userId, profileId)
            ?: profile.remoteId?.let { premiumProfileDao.getByRemoteId(it) }
        if (profile.isDefault) {
            premiumProfileDao.clearDefaultForUser(profile.userId)
        }
        val entity = profile.copy(id = profileId).toEntity(
            syncStatus = syncStatusForMutation(existing),
            remoteId = existing?.remoteId ?: profile.remoteId,
        ).copy(
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        premiumProfileDao.insert(entity)
        syncTrigger.schedule()
        return entity.toDomain()
    }

    override suspend fun deleteProfile(userId: String, profileId: String) {
        val existing = premiumProfileDao.getById(userId, profileId)
            ?: premiumProfileDao.getByRemoteId(profileId)?.takeIf { it.userId == userId }
            ?: return
        if (existing.isDefault) return
        val now = clock.millis()
        premiumProfileDao.insert(
            existing.copy(
                isArchived = true,
                deletedAt = now,
                syncStatus = SyncStatus.PENDING_UPDATE,
                updatedAt = now,
            ),
        )
        syncTrigger.schedule()
    }

    override suspend fun ensureDefaults(userId: String): PremiumProfile = ensureMutex.withLock {
        val existing = getProfiles(userId)
        if (existing.isNotEmpty()) {
            return existing.firstOrNull { it.isDefault } ?: existing.first()
        }
        val now = Instant.now()
        val default = PremiumProfile(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = "Premium",
            multiplier = 1.5,
            premiumType = PremiumType.HIGHEST_ONLY,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )
        upsertProfile(default)
    }

    private fun syncStatusForMutation(existing: PremiumProfileEntity?): SyncStatus = when {
        existing == null -> SyncStatus.PENDING_CREATE
        existing.syncStatus == SyncStatus.PENDING_CREATE -> SyncStatus.PENDING_CREATE
        existing.syncStatus == SyncStatus.PENDING_DELETE -> SyncStatus.PENDING_DELETE
        else -> SyncStatus.PENDING_UPDATE
    }
}
