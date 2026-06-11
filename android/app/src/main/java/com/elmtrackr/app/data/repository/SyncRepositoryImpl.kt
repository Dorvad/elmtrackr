package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.dao.ProfileDao
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.remote.RemoteProfileDataSource
import com.elmtrackr.app.data.remote.RemoteRefundsDataSource
import com.elmtrackr.app.data.remote.RemoteSettingsDataSource
import com.elmtrackr.app.data.remote.RemoteShiftsDataSource
import com.elmtrackr.app.data.remote.toProfileEntity
import com.elmtrackr.app.data.remote.toRefundClaimEntity
import com.elmtrackr.app.data.remote.toRemoteJson
import com.elmtrackr.app.data.remote.toShiftEntity
import com.elmtrackr.app.data.remote.toUserSettingsEntity
import com.elmtrackr.app.domain.model.SyncItemError
import com.elmtrackr.app.domain.model.SyncResult
import com.elmtrackr.app.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.UUID

class SyncRepositoryImpl(
    private val shiftDao: ShiftDao,
    private val refundClaimDao: RefundClaimDao,
    private val settingsDao: SettingsDao,
    private val profileDao: ProfileDao,
    private val remoteShifts: RemoteShiftsDataSource?,
    private val remoteRefunds: RemoteRefundsDataSource?,
    private val remoteSettings: RemoteSettingsDataSource?,
    private val remoteProfile: RemoteProfileDataSource?,
) : SyncRepository {

    private val _lastSyncStatus = MutableStateFlow<String?>(null)

    override fun observePendingCount(): Flow<Int> =
        combine(
            shiftDao.observePendingSyncShifts(),
            refundClaimDao.observePendingSyncClaims(),
        ) { shifts, claims -> shifts.size + claims.size }

    override fun observeLastSyncStatus(): Flow<String?> = _lastSyncStatus

    override suspend fun syncAll(userId: String): SyncResult {
        if (remoteShifts == null) return SyncResult.NotConfigured

        val errors = mutableListOf<SyncItemError>()

        // Push in dependency order: shifts before claims (claims reference shift remoteId)
        errors += pushShifts(userId)
        errors += pushRefundClaims(userId)
        errors += pushSettings(userId)
        errors += pushProfiles(userId)

        // Pull phase
        errors += pullShifts(userId)
        errors += pullRefundClaims(userId)
        errors += pullSettings(userId)
        errors += pullProfiles(userId)

        _lastSyncStatus.value = if (errors.isEmpty()) "success" else "partial:${errors.size}"

        return if (errors.isEmpty()) SyncResult.Success else SyncResult.PartialSuccess(errors)
    }

    // ---- Push ----

    private suspend fun pushShifts(userId: String): List<SyncItemError> {
        val errors = mutableListOf<SyncItemError>()
        val pending = shiftDao.getPendingSyncShifts()
        val now = Instant.now().toEpochMilli()

        for (entity in pending) {
            runCatching {
                when (entity.syncStatus) {
                    SyncStatus.PENDING_DELETE -> {
                        entity.remoteId?.let { remoteShifts!!.delete(it) }
                        shiftDao.updateSyncState(entity.localId, SyncStatus.SYNCED, entity.remoteId, now, null)
                    }
                    else -> {
                        val remoteId = entity.remoteId ?: UUID.randomUUID().toString()
                        remoteShifts!!.upsert(entity.toRemoteJson(overrideId = remoteId))
                        shiftDao.updateSyncState(entity.localId, SyncStatus.SYNCED, remoteId, now, null)
                    }
                }
            }.onFailure { e ->
                val msg = e.message ?: "unknown error"
                errors += SyncItemError("shift", entity.localId, msg)
                shiftDao.updateSyncState(entity.localId, SyncStatus.FAILED, entity.remoteId, entity.lastSyncedAt, msg)
            }
        }
        return errors
    }

    private suspend fun pushRefundClaims(userId: String): List<SyncItemError> {
        val errors = mutableListOf<SyncItemError>()
        val pending = refundClaimDao.getPendingSyncClaims()
        val now = Instant.now().toEpochMilli()

        for (entity in pending) {
            runCatching {
                when (entity.syncStatus) {
                    SyncStatus.PENDING_DELETE -> {
                        entity.remoteId?.let { remoteRefunds!!.delete(it) }
                        refundClaimDao.updateSyncState(entity.localId, SyncStatus.SYNCED, entity.remoteId, now, null)
                    }
                    else -> {
                        val shiftRemoteId = shiftDao.getShiftById(entity.shiftLocalId)?.remoteId
                            ?: return@runCatching // shift not synced yet — defer this claim
                        val remoteId = entity.remoteId ?: UUID.randomUUID().toString()
                        remoteRefunds!!.upsert(entity.toRemoteJson(overrideId = remoteId, shiftRemoteId = shiftRemoteId))
                        refundClaimDao.updateSyncState(entity.localId, SyncStatus.SYNCED, remoteId, now, null)
                    }
                }
            }.onFailure { e ->
                val msg = e.message ?: "unknown error"
                errors += SyncItemError("refund_claim", entity.localId, msg)
                refundClaimDao.updateSyncState(entity.localId, SyncStatus.FAILED, entity.remoteId, entity.lastSyncedAt, msg)
            }
        }
        return errors
    }

    private suspend fun pushSettings(userId: String): List<SyncItemError> {
        val errors = mutableListOf<SyncItemError>()
        val pending = settingsDao.getPendingSyncSettings()
        val now = Instant.now().toEpochMilli()

        for (entity in pending) {
            runCatching {
                val remoteId = entity.remoteId ?: UUID.randomUUID().toString()
                remoteSettings!!.upsert(entity.toRemoteJson(overrideId = remoteId))
                settingsDao.updateSyncState(entity.localId, SyncStatus.SYNCED, remoteId, now, null)
            }.onFailure { e ->
                val msg = e.message ?: "unknown error"
                errors += SyncItemError("settings", entity.localId, msg)
                settingsDao.updateSyncState(entity.localId, SyncStatus.FAILED, entity.remoteId, entity.lastSyncedAt, msg)
            }
        }
        return errors
    }

    private suspend fun pushProfiles(userId: String): List<SyncItemError> {
        val errors = mutableListOf<SyncItemError>()
        val pending = profileDao.getPendingSyncProfiles()
        val now = Instant.now().toEpochMilli()

        for (entity in pending) {
            runCatching {
                remoteProfile!!.upsert(entity.toRemoteJson())
                profileDao.updateSyncState(entity.localId, SyncStatus.SYNCED, entity.remoteId ?: entity.localId, now, null)
            }.onFailure { e ->
                val msg = e.message ?: "unknown error"
                errors += SyncItemError("profile", entity.localId, msg)
                profileDao.updateSyncState(entity.localId, SyncStatus.FAILED, entity.remoteId, entity.lastSyncedAt, msg)
            }
        }
        return errors
    }

    // ---- Pull ----

    private suspend fun pullShifts(userId: String): List<SyncItemError> {
        val errors = mutableListOf<SyncItemError>()
        val remoteItems: List<JsonObject> = runCatching { remoteShifts!!.fetchAll(userId) }
            .getOrElse { e -> return listOf(SyncItemError("shift_pull", userId, e.message ?: "fetch failed")) }

        val activeLocalShifts = shiftDao.getActiveShifts(userId)

        for (remote in remoteItems) {
            runCatching {
                val remoteId = remote["id"]?.jsonPrimitive?.content ?: return@runCatching
                val remoteUpdatedAt = remote["updated_at"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val remoteDeletedAt = remote["deleted_at"]?.jsonPrimitive?.content?.toLongOrNull()
                val local = shiftDao.getShiftByRemoteId(remoteId)

                when {
                    local == null -> {
                        // New record from remote — guard against duplicate active shift
                        val remoteEndTime = remote["end_time"]?.jsonPrimitive?.content?.toLongOrNull()
                        if (remoteEndTime == null && activeLocalShifts.isNotEmpty()) {
                            return@runCatching // already have a local active shift
                        }
                        shiftDao.upsertShift(remote.toShiftEntity())
                    }
                    local.syncStatus == SyncStatus.SYNCED -> {
                        // No local pending changes — apply remote if it's newer or deleted
                        if (remoteDeletedAt != null) {
                            shiftDao.softDeleteShift(local.localId, remoteDeletedAt, SyncStatus.SYNCED, remoteDeletedAt)
                        } else if (remoteUpdatedAt > local.updatedAt) {
                            shiftDao.upsertShift(remote.toShiftEntity(existingLocalId = local.localId))
                        }
                    }
                    // else: local has pending changes — keep local, remote will be overwritten on next push
                }
            }.onFailure { e ->
                errors += SyncItemError("shift_pull", remote["id"]?.jsonPrimitive?.content ?: "?", e.message ?: "map error")
            }
        }
        return errors
    }

    private suspend fun pullRefundClaims(userId: String): List<SyncItemError> {
        val errors = mutableListOf<SyncItemError>()
        val remoteItems: List<JsonObject> = runCatching { remoteRefunds!!.fetchAll(userId) }
            .getOrElse { e -> return listOf(SyncItemError("claim_pull", userId, e.message ?: "fetch failed")) }

        for (remote in remoteItems) {
            runCatching {
                val remoteId = remote["id"]?.jsonPrimitive?.content ?: return@runCatching
                val remoteUpdatedAt = remote["updated_at"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val remoteDeletedAt = remote["deleted_at"]?.jsonPrimitive?.content?.toLongOrNull()
                val local = refundClaimDao.getClaimByRemoteId(remoteId)

                val shiftRemoteId = remote["shift_id"]?.jsonPrimitive?.content ?: return@runCatching
                val localShift = shiftDao.getShiftByRemoteId(shiftRemoteId)
                val shiftLocalId = localShift?.localId ?: shiftRemoteId

                when {
                    local == null -> {
                        refundClaimDao.upsertClaim(remote.toRefundClaimEntity(shiftLocalId = shiftLocalId))
                    }
                    local.syncStatus == SyncStatus.SYNCED -> {
                        if (remoteDeletedAt != null) {
                            refundClaimDao.softDeleteClaim(local.localId, remoteDeletedAt, SyncStatus.SYNCED, remoteDeletedAt)
                        } else if (remoteUpdatedAt > local.updatedAt) {
                            refundClaimDao.upsertClaim(remote.toRefundClaimEntity(shiftLocalId = shiftLocalId, existingLocalId = local.localId))
                        }
                    }
                }
            }.onFailure { e ->
                errors += SyncItemError("claim_pull", remote["id"]?.jsonPrimitive?.content ?: "?", e.message ?: "map error")
            }
        }
        return errors
    }

    private suspend fun pullSettings(userId: String): List<SyncItemError> {
        val errors = mutableListOf<SyncItemError>()
        val remoteItems: List<JsonObject> = runCatching { remoteSettings!!.fetchAll(userId) }
            .getOrElse { e -> return listOf(SyncItemError("settings_pull", userId, e.message ?: "fetch failed")) }

        for (remote in remoteItems) {
            runCatching {
                val remoteId = remote["id"]?.jsonPrimitive?.content ?: return@runCatching
                val remoteUpdatedAt = remote["updated_at"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val local = settingsDao.getSettingsByRemoteId(remoteId)

                when {
                    local == null -> settingsDao.upsertSettings(remote.toUserSettingsEntity())
                    local.syncStatus == SyncStatus.SYNCED && remoteUpdatedAt > local.updatedAt ->
                        settingsDao.upsertSettings(remote.toUserSettingsEntity(existingLocalId = local.localId))
                }
            }.onFailure { e ->
                errors += SyncItemError("settings_pull", remote["id"]?.jsonPrimitive?.content ?: "?", e.message ?: "map error")
            }
        }
        return errors
    }

    private suspend fun pullProfiles(userId: String): List<SyncItemError> {
        val errors = mutableListOf<SyncItemError>()
        val remoteItems: List<JsonObject> = runCatching { remoteProfile!!.fetchAll(userId) }
            .getOrElse { e -> return listOf(SyncItemError("profile_pull", userId, e.message ?: "fetch failed")) }

        for (remote in remoteItems) {
            runCatching {
                val remoteId = remote["id"]?.jsonPrimitive?.content ?: return@runCatching
                val remoteUpdatedAt = remote["updated_at"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val local = profileDao.getProfileByRemoteId(remoteId)

                when {
                    local == null -> profileDao.upsertProfile(remote.toProfileEntity(userId = userId))
                    local.syncStatus == SyncStatus.SYNCED && remoteUpdatedAt > local.updatedAt ->
                        profileDao.upsertProfile(remote.toProfileEntity(userId = userId, existingLocalId = local.localId))
                }
            }.onFailure { e ->
                errors += SyncItemError("profile_pull", remote["id"]?.jsonPrimitive?.content ?: "?", e.message ?: "map error")
            }
        }
        return errors
    }
}
