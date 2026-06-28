package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.local.mapper.toDomainOrNull
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.sync.NoOpSyncTrigger
import com.elmtrackr.app.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

class LocalSettingsRepository(
    private val settingsDao: SettingsDao,
    private val syncTrigger: SyncTrigger = NoOpSyncTrigger,
) : SettingsRepository {

    override fun observeSettings(userId: String): Flow<UserSettings?> =
        settingsDao.observeSettings(userId).map { it.toDomainOrNull { entity -> entity.toDomain() } }

    override suspend fun getSettings(userId: String): UserSettings? =
        settingsDao.getSettings(userId).toDomainOrNull { it.toDomain() }

    override suspend fun saveSettings(settings: UserSettings) {
        val existing = settingsDao.getSettings(settings.userId)
        val newStatus = if (existing?.syncStatus == SyncStatus.SYNCED)
            SyncStatus.PENDING_UPDATE else existing?.syncStatus ?: SyncStatus.PENDING_UPDATE
        settingsDao.upsertSettings(
            settings.toEntity(
                syncStatus = newStatus,
                remoteId = existing?.remoteId,
                lastSyncedAt = existing?.lastSyncedAt,
            )
        )
        syncTrigger.schedule()
    }

    override suspend fun createDefaultSettings(userId: String): UserSettings {
        val now = Instant.now()
        val settings = UserSettings(
            id = UUID.randomUUID().toString(),
            userId = userId,
            createdAt = now,
            updatedAt = now,
        )
        settingsDao.insertSettings(settings.toEntity(syncStatus = SyncStatus.PENDING_CREATE))
        syncTrigger.schedule()
        return settings
    }
}
