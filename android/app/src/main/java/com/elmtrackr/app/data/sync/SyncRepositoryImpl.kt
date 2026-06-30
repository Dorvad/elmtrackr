package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.dao.CompensationProfileDao
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.dao.TaskDao
import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import com.elmtrackr.app.data.remote.RemoteCompensationProfileDataSource
import com.elmtrackr.app.data.remote.RemoteRefundClaimDataSource
import com.elmtrackr.app.data.remote.RemoteShiftDataSource
import com.elmtrackr.app.data.remote.RemoteUserSettingsDataSource
import com.elmtrackr.app.data.remote.isoToEpoch
import com.elmtrackr.app.data.remote.toLocalEntity
import com.elmtrackr.app.data.remote.toRemoteInsert
import com.elmtrackr.app.data.remote.toRemoteUpdate
import com.elmtrackr.app.data.remote.toRemoteUpsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.time.Instant

class SyncRepositoryImpl(
    private val shiftDao: ShiftDao,
    private val refundClaimDao: RefundClaimDao,
    private val settingsDao: SettingsDao,
    private val compensationProfileDao: CompensationProfileDao,
    private val taskDao: TaskDao,
    private val remoteShifts: RemoteShiftDataSource?,
    private val remoteRefundClaims: RemoteRefundClaimDataSource?,
    private val remoteSettings: RemoteUserSettingsDataSource?,
    private val remoteCompensationProfiles: RemoteCompensationProfileDataSource?,
) : SyncRepository {

    private val idMapper = SyncIdMapper(shiftDao, compensationProfileDao, taskDao)
    private val lastSyncStatus = MutableStateFlow<String?>(null)

    override fun observePendingCount(userId: String): Flow<Int> =
        combine(
            shiftDao.observePendingSyncShifts(userId),
            refundClaimDao.observePendingSyncClaims(userId),
            settingsDao.observePendingSyncSettings(userId),
            compensationProfileDao.observePendingSyncProfiles(userId),
        ) { shifts, claims, settings, profiles ->
            shifts.size + claims.size + settings.size + profiles.size
        }

    override fun observeLastSyncStatus(): Flow<String?> = lastSyncStatus.asStateFlow()

    override suspend fun syncAll(userId: String): SyncResult {
        if (remoteShifts == null || remoteRefundClaims == null ||
            remoteSettings == null || remoteCompensationProfiles == null
        ) {
            lastSyncStatus.value = "Not configured"
            return SyncResult.NotConfigured
        }

        return runCatching {
            reconcileNeverSynced(userId)
            pushShifts(userId)
            pushRefundClaims(userId)
            pushCompensationProfiles(userId)
            pushUserSettings(userId)
            pullCompensationProfiles(userId)
            pullUserSettings(userId)
            pullShifts(userId)
            pullRefundClaims(userId)
            lastSyncStatus.value = "Synced ${Instant.now()}"
            SyncResult.Success
        }.getOrElse { error ->
            lastSyncStatus.value = "Failed: ${error.message ?: "unknown error"}"
            SyncResult.Error(error.message ?: "Sync failed")
        }
    }

    private suspend fun reconcileNeverSynced(userId: String) {
        shiftDao.getAllShiftsForUser(userId)
            .filter { it.remoteId == null && it.syncStatus == SyncStatus.SYNCED && it.deletedAt == null }
            .forEach { shiftDao.upsertShift(it.copy(syncStatus = SyncStatus.PENDING_CREATE)) }

        refundClaimDao.getAllClaimsForUser(userId)
            .filter { it.remoteId == null && it.syncStatus == SyncStatus.SYNCED }
            .forEach { refundClaimDao.upsertClaim(it.copy(syncStatus = SyncStatus.PENDING_CREATE)) }

        settingsDao.getAllSettingsForUser(userId)
            .filter { it.remoteId == null && it.syncStatus == SyncStatus.SYNCED && it.deletedAt == null }
            .forEach { settingsDao.upsertSettings(it.copy(syncStatus = SyncStatus.PENDING_CREATE)) }

        compensationProfileDao.getAllProfilesForUser(userId)
            .filter { it.remoteId == null && it.syncStatus == SyncStatus.SYNCED }
            .forEach { compensationProfileDao.upsert(it.copy(syncStatus = SyncStatus.PENDING_CREATE)) }
    }

    // ── Shifts ──────────────────────────────────────────────────────────────

    private suspend fun pushShifts(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (shift in shiftDao.getPendingSyncShifts(userId)) {
            runCatching {
                when (shift.syncStatus) {
                    SyncStatus.PENDING_DELETE -> pushShiftDelete(shift, now)
                    SyncStatus.PENDING_CREATE, SyncStatus.FAILED -> {
                        if (shift.remoteId == null) pushShiftCreate(shift, now)
                        else pushShiftUpdate(shift, now)
                    }
                    SyncStatus.PENDING_UPDATE -> pushShiftUpdate(shift, now)
                    SyncStatus.SYNCED -> Unit
                }
            }.onFailure { markShiftFailed(shift, it) }
        }
    }

    private suspend fun pushShiftCreate(shift: ShiftEntity, syncedAt: Long) {
        val remote = remoteShifts!!.insert(
            shift.toRemoteInsert(
                compensationProfileRemoteId = idMapper.profileLocalToRemote(shift.compensationProfileId),
                taskRemoteId = idMapper.taskLocalToRemote(shift.taskId),
            ),
        )
        shiftDao.updateSyncState(shift.localId, SyncStatus.SYNCED, remote.id, syncedAt, null)
    }

    private suspend fun pushShiftUpdate(shift: ShiftEntity, syncedAt: Long) {
        val remoteId = shift.remoteId ?: error("Missing remoteId for shift ${shift.localId}")
        remoteShifts!!.update(
            remoteId,
            shift.toRemoteUpdate(
                compensationProfileRemoteId = idMapper.profileLocalToRemote(shift.compensationProfileId),
                taskRemoteId = idMapper.taskLocalToRemote(shift.taskId),
            ),
        )
        shiftDao.updateSyncState(shift.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    private suspend fun pushShiftDelete(shift: ShiftEntity, syncedAt: Long) {
        shift.remoteId?.let { remoteShifts!!.delete(it) }
        shiftDao.updateSyncState(shift.localId, SyncStatus.SYNCED, shift.remoteId, syncedAt, null)
    }

    private suspend fun markShiftFailed(shift: ShiftEntity, error: Throwable) {
        shiftDao.updateSyncState(
            shift.localId, SyncStatus.FAILED, shift.remoteId, shift.lastSyncedAt, error.message,
        )
    }

    private suspend fun pullShifts(userId: String) {
        val remoteRows = remoteShifts!!.fetchAll()
        val remoteIds = remoteRows.map { it.id }.toSet()
        val localActiveExists = shiftDao.getActiveShifts(userId).isNotEmpty()

        for (remote in remoteRows) {
            val existing = shiftDao.getShiftByRemoteId(remote.id)
            if (existing != null) {
                if (existing.syncStatus != SyncStatus.SYNCED) continue
                if (isoToEpoch(remote.updatedAt) > existing.updatedAt) {
                    shiftDao.upsertShift(
                        remote.toLocalEntity(
                            existingLocalId = existing.localId,
                            compensationProfileLocalId = idMapper.profileRemoteToLocal(remote.compensationProfileId),
                            taskLocalId = remote.taskId,
                            syncStatus = SyncStatus.SYNCED,
                        ),
                    )
                }
                continue
            }
            if (remote.endTime == null && localActiveExists) continue
            shiftDao.insertShift(
                remote.toLocalEntity(
                    compensationProfileLocalId = idMapper.profileRemoteToLocal(remote.compensationProfileId),
                    taskLocalId = remote.taskId,
                ),
            )
        }

        val now = Instant.now().toEpochMilli()
        shiftDao.getAllShiftsForUser(userId)
            .filter { it.remoteId != null && it.syncStatus == SyncStatus.SYNCED }
            .filter { it.remoteId !in remoteIds }
            .forEach { shiftDao.softDeleteShift(it.localId, now, SyncStatus.SYNCED, now) }
    }

    // ── Refund claims ─────────────────────────────────────────────────────────

    private suspend fun pushRefundClaims(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (claim in refundClaimDao.getPendingSyncClaims(userId)) {
            runCatching {
                when (claim.syncStatus) {
                    SyncStatus.PENDING_DELETE -> pushRefundClaimDelete(claim, now)
                    SyncStatus.PENDING_CREATE, SyncStatus.FAILED -> {
                        val shiftRemoteId = idMapper.shiftLocalToRemote(claim.shiftLocalId)
                            ?: return@runCatching
                        if (claim.remoteId == null) pushRefundClaimCreate(claim, shiftRemoteId, now)
                        else pushRefundClaimUpdate(claim, now)
                    }
                    SyncStatus.PENDING_UPDATE -> pushRefundClaimUpdate(claim, now)
                    SyncStatus.SYNCED -> Unit
                }
            }.onFailure { markRefundClaimFailed(claim, it) }
        }
    }

    private suspend fun pushRefundClaimCreate(
        claim: RefundClaimEntity,
        shiftRemoteId: String,
        syncedAt: Long,
    ) {
        val remote = remoteRefundClaims!!.insert(claim.toRemoteInsert(shiftRemoteId))
        refundClaimDao.updateSyncState(claim.localId, SyncStatus.SYNCED, remote.id, syncedAt, null)
    }

    private suspend fun pushRefundClaimUpdate(claim: RefundClaimEntity, syncedAt: Long) {
        val remoteId = claim.remoteId ?: error("Missing remoteId for claim ${claim.localId}")
        remoteRefundClaims!!.update(remoteId, claim.toRemoteUpdate())
        refundClaimDao.updateSyncState(claim.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    private suspend fun pushRefundClaimDelete(claim: RefundClaimEntity, syncedAt: Long) {
        claim.remoteId?.let { remoteRefundClaims!!.delete(it) }
        refundClaimDao.updateSyncState(claim.localId, SyncStatus.SYNCED, claim.remoteId, syncedAt, null)
    }

    private suspend fun markRefundClaimFailed(claim: RefundClaimEntity, error: Throwable) {
        refundClaimDao.updateSyncState(
            claim.localId, SyncStatus.FAILED, claim.remoteId, claim.lastSyncedAt, error.message,
        )
    }

    private suspend fun pullRefundClaims(userId: String) {
        val remoteRows = remoteRefundClaims!!.fetchAll()
        val remoteIds = remoteRows.map { it.id }.toSet()

        for (remote in remoteRows) {
            val shiftLocalId = idMapper.shiftRemoteToLocal(remote.shiftId) ?: continue
            val existing = refundClaimDao.getClaimByRemoteId(remote.id)
            if (existing != null) {
                if (existing.syncStatus != SyncStatus.SYNCED) continue
                if (isoToEpoch(remote.updatedAt) > existing.updatedAt) {
                    refundClaimDao.upsertClaim(
                        remote.toLocalEntity(
                            shiftLocalId = shiftLocalId,
                            existingLocalId = existing.localId,
                        ),
                    )
                }
                continue
            }
            refundClaimDao.insertClaim(remote.toLocalEntity(shiftLocalId = shiftLocalId))
        }

        val now = Instant.now().toEpochMilli()
        refundClaimDao.getAllClaimsForUser(userId)
            .filter { it.remoteId != null && it.syncStatus == SyncStatus.SYNCED }
            .filter { it.remoteId !in remoteIds }
            .forEach { refundClaimDao.softDeleteClaim(it.localId, now, SyncStatus.SYNCED, now) }
    }

    // ── Compensation profiles ─────────────────────────────────────────────────

    private suspend fun pushCompensationProfiles(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (profile in compensationProfileDao.getPendingSyncProfiles(userId)) {
            runCatching {
                when (profile.syncStatus) {
                    SyncStatus.PENDING_DELETE -> pushCompensationProfileDelete(profile, now)
                    SyncStatus.PENDING_CREATE, SyncStatus.FAILED -> {
                        if (profile.remoteId == null) pushCompensationProfileCreate(profile, now)
                        else pushCompensationProfileUpdate(profile, now)
                    }
                    SyncStatus.PENDING_UPDATE -> pushCompensationProfileUpdate(profile, now)
                    SyncStatus.SYNCED -> Unit
                }
            }.onFailure { markCompensationProfileFailed(profile, it) }
        }
    }

    private suspend fun pushCompensationProfileCreate(profile: CompensationProfileEntity, syncedAt: Long) {
        val remote = remoteCompensationProfiles!!.insert(profile.toRemoteInsert())
        compensationProfileDao.updateSyncState(profile.localId, SyncStatus.SYNCED, remote.id, syncedAt, null)
    }

    private suspend fun pushCompensationProfileUpdate(profile: CompensationProfileEntity, syncedAt: Long) {
        val remoteId = profile.remoteId ?: error("Missing remoteId for profile ${profile.localId}")
        remoteCompensationProfiles!!.update(remoteId, profile.toRemoteUpdate())
        compensationProfileDao.updateSyncState(profile.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    private suspend fun pushCompensationProfileDelete(profile: CompensationProfileEntity, syncedAt: Long) {
        profile.remoteId?.let { remoteCompensationProfiles!!.delete(it) }
        compensationProfileDao.updateSyncState(
            profile.localId, SyncStatus.SYNCED, profile.remoteId, syncedAt, null,
        )
    }

    private suspend fun markCompensationProfileFailed(profile: CompensationProfileEntity, error: Throwable) {
        compensationProfileDao.updateSyncState(
            profile.localId, SyncStatus.FAILED, profile.remoteId, profile.lastSyncedAt, error.message,
        )
    }

    private suspend fun pullCompensationProfiles(userId: String) {
        val remoteRows = remoteCompensationProfiles!!.fetchAll()
        val remoteIds = remoteRows.map { it.id }.toSet()

        for (remote in remoteRows) {
            val existing = compensationProfileDao.getByRemoteId(remote.id)
            if (existing != null) {
                if (existing.syncStatus != SyncStatus.SYNCED) continue
                if (isoToEpoch(remote.updatedAt) > existing.updatedAt) {
                    compensationProfileDao.upsert(
                        remote.toLocalEntity(existingLocalId = existing.localId),
                    )
                }
                continue
            }
            compensationProfileDao.insert(remote.toLocalEntity())
        }

        val now = Instant.now().toEpochMilli()
        compensationProfileDao.getAllProfilesForUser(userId)
            .filter { it.remoteId != null && it.syncStatus == SyncStatus.SYNCED }
            .filter { it.remoteId !in remoteIds }
            .forEach {
                compensationProfileDao.upsert(
                    it.copy(isArchived = true, deletedAt = now, updatedAt = now, syncStatus = SyncStatus.SYNCED),
                )
            }
    }

    // ── User settings ─────────────────────────────────────────────────────────

    private suspend fun pushUserSettings(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (settings in settingsDao.getPendingSyncSettings(userId)) {
            runCatching {
                val profileRemoteId = idMapper.profileLocalToRemote(settings.defaultCompensationProfileId)
                when (settings.syncStatus) {
                    SyncStatus.PENDING_DELETE -> pushUserSettingsDelete(settings, now)
                    SyncStatus.PENDING_CREATE, SyncStatus.FAILED -> {
                        if (settings.remoteId == null) {
                            val remote = remoteSettings!!.upsert(settings.toRemoteUpsert(profileRemoteId))
                            settingsDao.updateSyncState(settings.localId, SyncStatus.SYNCED, remote.id, now, null)
                        } else {
                            pushUserSettingsUpdate(settings, profileRemoteId, now)
                        }
                    }
                    SyncStatus.PENDING_UPDATE -> pushUserSettingsUpdate(settings, profileRemoteId, now)
                    SyncStatus.SYNCED -> Unit
                }
            }.onFailure { markUserSettingsFailed(settings, it) }
        }
    }

    private suspend fun pushUserSettingsUpdate(
        settings: UserSettingsEntity,
        profileRemoteId: String?,
        syncedAt: Long,
    ) {
        val remoteId = settings.remoteId ?: error("Missing remoteId for settings ${settings.localId}")
        remoteSettings!!.update(remoteId, settings.toRemoteUpdate(profileRemoteId))
        settingsDao.updateSyncState(settings.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    private suspend fun pushUserSettingsDelete(settings: UserSettingsEntity, syncedAt: Long) {
        settingsDao.updateSyncState(settings.localId, SyncStatus.SYNCED, settings.remoteId, syncedAt, null)
    }

    private suspend fun markUserSettingsFailed(settings: UserSettingsEntity, error: Throwable) {
        settingsDao.updateSyncState(
            settings.localId, SyncStatus.FAILED, settings.remoteId, settings.lastSyncedAt, error.message,
        )
    }

    private suspend fun pullUserSettings(userId: String) {
        val remoteRows = remoteSettings!!.fetchAll()
        for (remote in remoteRows) {
            val profileLocalId = idMapper.profileRemoteToLocal(remote.defaultCompensationProfileId)
            val existing = settingsDao.getSettingsByRemoteId(remote.id)
                ?: settingsDao.getSettings(userId)
            if (existing != null) {
                if (existing.syncStatus != SyncStatus.SYNCED) continue
                if (isoToEpoch(remote.updatedAt) > existing.updatedAt) {
                    settingsDao.upsertSettings(
                        remote.toLocalEntity(
                            existingLocalId = existing.localId,
                            defaultCompensationProfileLocalId = profileLocalId,
                        ),
                    )
                }
                continue
            }
            settingsDao.insertSettings(
                remote.toLocalEntity(defaultCompensationProfileLocalId = profileLocalId),
            )
        }
    }
}
