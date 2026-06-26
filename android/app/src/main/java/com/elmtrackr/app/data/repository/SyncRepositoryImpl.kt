package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.dao.CompensationProfileDao
import com.elmtrackr.app.data.local.dao.ProfileDao
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.remote.RemoteCompensationProfilesDataSource
import com.elmtrackr.app.data.remote.RemoteProfileDataSource
import com.elmtrackr.app.data.remote.RemoteRefundsDataSource
import com.elmtrackr.app.data.remote.RemoteSettingsDataSource
import com.elmtrackr.app.data.remote.RemoteShiftsDataSource
import com.elmtrackr.app.data.remote.toCompensationProfileEntity
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.UUID

class SyncRepositoryImpl(
    private val shiftDao: ShiftDao,
    private val refundClaimDao: RefundClaimDao,
    private val settingsDao: SettingsDao,
    private val profileDao: ProfileDao,
    private val compensationProfileDao: CompensationProfileDao,
    private val remoteShifts: RemoteShiftsDataSource?,
    private val remoteRefunds: RemoteRefundsDataSource?,
    private val remoteSettings: RemoteSettingsDataSource?,
    private val remoteProfile: RemoteProfileDataSource?,
    private val remoteCompensationProfiles: RemoteCompensationProfilesDataSource?,
) : SyncRepository {

    private fun JsonObject.timestampMillis(key: String): Long =
        this[key]?.jsonPrimitive?.content?.let { value ->
            value.toLongOrNull() ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
        } ?: 0L

    private val _lastSyncStatus = MutableStateFlow<String?>(null)

    override fun observePendingCount(userId: String): Flow<Int> =
        combine(
            shiftDao.observePendingSyncShifts(userId),
            refundClaimDao.observePendingSyncClaims(userId),
            compensationProfileDao.observeProfiles(userId),
        ) { shifts, claims, profiles ->
            shifts.size + claims.size + profiles.count { it.syncStatus != SyncStatus.SYNCED }
        }

    override fun observeLastSyncStatus(): Flow<String?> = _lastSyncStatus

    override suspend fun syncAll(userId: String): SyncResult {
        if (remoteShifts == null) return SyncResult.NotConfigured

        val errors = mutableListOf<SyncItemError>()

        // Reconcile remote IDs before pushing. This is required when Android first
        // connects to an account that already has web-created settings/profile data.
        errors += pullShifts(userId)
        errors += pullRefundClaims(userId)
        errors += pullSettings(userId)
        errors += pullProfiles(userId)
        errors += pullCompensationProfiles(userId)

        // Push in dependency order: compensation profiles before shifts/settings that reference them.
        errors += pushCompensationProfiles(userId)
        errors += pushShifts(userId)
        errors += pushRefundClaims(userId)
        errors += pushSettings(userId)
        errors += pushProfiles(userId)

        _lastSyncStatus.value = if (errors.isEmpty()) "success" else "partial:${errors.size}"

        return if (errors.isEmpty()) SyncResult.Success else SyncResult.PartialSuccess(errors)
    }

    // ---- Push ----

    private suspend fun resolveProfileRemoteId(localId: String?): String? {
        if (localId == null) return null
        return compensationProfileDao.getByLocalId(localId)?.remoteId ?: localId
    }

    private suspend fun resolveProfileLocalId(remoteId: String?): String? {
        if (remoteId == null) return null
        return compensationProfileDao.getByRemoteId(remoteId)?.localId ?: remoteId
    }

    private suspend fun pushCompensationProfiles(userId: String): List<SyncItemError> {
        val remote = remoteCompensationProfiles ?: return emptyList()
        val errors = mutableListOf<SyncItemError>()
        val pending = compensationProfileDao.getPendingSyncProfiles(userId)
        val now = Instant.now().toEpochMilli()

        for (entity in pending) {
            runCatching {
                when (entity.syncStatus) {
                    SyncStatus.PENDING_DELETE -> {
                        entity.remoteId?.let { remote.deleteProfile(it) }
                        compensationProfileDao.updateSyncState(
                            entity.localId, SyncStatus.SYNCED, entity.remoteId, now, null,
                        )
                    }
                    else -> {
                        val remoteId = entity.remoteId ?: UUID.randomUUID().toString()
                        remote.upsertProfile(entity.copy(remoteId = remoteId))
                        compensationProfileDao.updateSyncState(
                            entity.localId, SyncStatus.SYNCED, remoteId, now, null,
                        )
                    }
                }
            }.onFailure { e ->
                val msg = SyncErrorMapper.messageFor(e)
                errors += SyncItemError("compensation_profile", entity.localId, msg)
                compensationProfileDao.updateSyncState(
                    entity.localId, SyncStatus.FAILED, entity.remoteId, entity.lastSyncedAt, msg,
                )
            }
        }
        return errors
    }

    private suspend fun pushShifts(userId: String): List<SyncItemError> {
        val errors = mutableListOf<SyncItemError>()
        val pending = shiftDao.getPendingSyncShifts(userId)
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
                        val profileRemoteId = resolveProfileRemoteId(entity.compensationProfileId)
                        remoteShifts!!.upsert(
                            entity.toRemoteJson(
                                overrideId = remoteId,
                                compensationProfileRemoteId = profileRemoteId,
                            ),
                        )
                        shiftDao.updateSyncState(entity.localId, SyncStatus.SYNCED, remoteId, now, null)
                    }
                }
            }.onFailure { e ->
                val msg = SyncErrorMapper.messageFor(e)
                errors += SyncItemError("shift", entity.localId, msg)
                shiftDao.updateSyncState(entity.localId, SyncStatus.FAILED, entity.remoteId, entity.lastSyncedAt, msg)
            }
        }
        return errors
    }

    private suspend fun pushRefundClaims(userId: String): List<SyncItemError> {
        val errors = mutableListOf<SyncItemError>()
        val pending = refundClaimDao.getPendingSyncClaims(userId)
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
                val msg = SyncErrorMapper.messageFor(e)
                errors += SyncItemError("refund_claim", entity.localId, msg)
                refundClaimDao.updateSyncState(entity.localId, SyncStatus.FAILED, entity.remoteId, entity.lastSyncedAt, msg)
            }
        }
        return errors
    }

    private suspend fun pushSettings(userId: String): List<SyncItemError> {
        val errors = mutableListOf<SyncItemError>()
        val pending = settingsDao.getPendingSyncSettings(userId)
        val now = Instant.now().toEpochMilli()

        for (entity in pending) {
            runCatching {
                val remoteId = entity.remoteId ?: UUID.randomUUID().toString()
                val defaultProfileRemoteId = resolveProfileRemoteId(entity.defaultCompensationProfileId)
                runCatching {
                    remoteSettings!!.upsert(
                        entity.toRemoteJson(
                            overrideId = remoteId,
                            defaultCompensationProfileRemoteId = defaultProfileRemoteId,
                        ),
                    )
                }.recoverCatching { error ->
                    if (!error.isMissingCurrencyColumn()) throw error
                    remoteSettings!!.upsert(
                        entity.toRemoteJson(
                            overrideId = remoteId,
                            includeCurrency = false,
                            defaultCompensationProfileRemoteId = defaultProfileRemoteId,
                        ),
                    )
                }.getOrThrow()
                settingsDao.updateSyncState(entity.localId, SyncStatus.SYNCED, remoteId, now, null)
            }.onFailure { e ->
                val msg = SyncErrorMapper.messageFor(e)
                errors += SyncItemError("settings", entity.localId, msg)
                settingsDao.updateSyncState(entity.localId, SyncStatus.FAILED, entity.remoteId, entity.lastSyncedAt, msg)
            }
        }
        return errors
    }

    private suspend fun pushProfiles(userId: String): List<SyncItemError> {
        val errors = mutableListOf<SyncItemError>()
        val pending = profileDao.getPendingSyncProfiles(userId)
        val now = Instant.now().toEpochMilli()

        for (entity in pending) {
            runCatching {
                remoteProfile!!.upsert(entity.toRemoteJson())
                profileDao.updateSyncState(entity.localId, SyncStatus.SYNCED, entity.remoteId ?: entity.localId, now, null)
            }.onFailure { e ->
                val msg = SyncErrorMapper.messageFor(e)
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
            .getOrElse { e -> return listOf(SyncItemError("shift_pull", userId, SyncErrorMapper.messageFor(e))) }

        val activeLocalShifts = shiftDao.getActiveShifts(userId)

        for (remote in remoteItems) {
            runCatching {
                val remoteId = remote["id"]?.jsonPrimitive?.content ?: return@runCatching
                val remoteUpdatedAt = remote.timestampMillis("updated_at")
                val local = shiftDao.getShiftByRemoteId(remoteId)

                when {
                    local == null -> {
                        // New record from remote — guard against duplicate active shift
                        val remoteEndTime = remote["end_time"]?.jsonPrimitive?.content?.toLongOrNull()
                        if (remoteEndTime == null && activeLocalShifts.isNotEmpty()) {
                            return@runCatching // already have a local active shift
                        }
                        shiftDao.upsertShift(
                            remote.toShiftEntity().copy(
                                compensationProfileId = resolveProfileLocalId(
                                    remote.remoteStr("compensation_profile_id"),
                                ),
                            ),
                        )
                    }
                    local.syncStatus == SyncStatus.SYNCED -> {
                        // No local pending changes — apply remote if it's newer or deleted
                        if (remoteUpdatedAt > local.updatedAt) {
                            shiftDao.upsertShift(
                                remote.toShiftEntity(existingLocalId = local.localId).copy(
                                    compensationProfileId = resolveProfileLocalId(
                                        remote.remoteStr("compensation_profile_id"),
                                    ) ?: local.compensationProfileId,
                                ),
                            )
                        }
                    }
                    // else: local has pending changes — keep local, remote will be overwritten on next push
                }
            }.onFailure { e ->
                errors += SyncItemError("shift_pull", remote["id"]?.jsonPrimitive?.content ?: "?", SyncErrorMapper.messageFor(e))
            }
        }
        return errors
    }

    private suspend fun pullRefundClaims(userId: String): List<SyncItemError> {
        val errors = mutableListOf<SyncItemError>()
        val remoteItems: List<JsonObject> = runCatching { remoteRefunds!!.fetchAll(userId) }
            .getOrElse { e -> return listOf(SyncItemError("claim_pull", userId, SyncErrorMapper.messageFor(e))) }

        for (remote in remoteItems) {
            runCatching {
                val remoteId = remote["id"]?.jsonPrimitive?.content ?: return@runCatching
                val remoteUpdatedAt = remote.timestampMillis("updated_at")
                val local = refundClaimDao.getClaimByRemoteId(remoteId)

                val shiftRemoteId = remote["shift_id"]?.jsonPrimitive?.content ?: return@runCatching
                val localShift = shiftDao.getShiftByRemoteId(shiftRemoteId)
                val shiftLocalId = localShift?.localId ?: shiftRemoteId

                when {
                    local == null -> {
                        refundClaimDao.upsertClaim(remote.toRefundClaimEntity(shiftLocalId = shiftLocalId))
                    }
                    local.syncStatus == SyncStatus.SYNCED -> {
                        if (remoteUpdatedAt > local.updatedAt) {
                            refundClaimDao.upsertClaim(remote.toRefundClaimEntity(shiftLocalId = shiftLocalId, existingLocalId = local.localId))
                        }
                    }
                }
            }.onFailure { e ->
                errors += SyncItemError("claim_pull", remote["id"]?.jsonPrimitive?.content ?: "?", SyncErrorMapper.messageFor(e))
            }
        }
        return errors
    }

    private suspend fun pullSettings(userId: String): List<SyncItemError> {
        val errors = mutableListOf<SyncItemError>()
        val remoteItems: List<JsonObject> = runCatching { remoteSettings!!.fetchAll(userId) }
            .getOrElse { e -> return listOf(SyncItemError("settings_pull", userId, SyncErrorMapper.messageFor(e))) }

        for (remote in remoteItems) {
            runCatching {
                val remoteId = remote["id"]?.jsonPrimitive?.content ?: return@runCatching
                val remoteUpdatedAt = remote.timestampMillis("updated_at")
                val local = settingsDao.getSettingsByRemoteId(remoteId)
                    ?: settingsDao.getSettings(userId)

                when {
                    local == null -> settingsDao.upsertSettings(
                        remote.toUserSettingsEntity().copy(
                            defaultCompensationProfileId = resolveProfileLocalId(
                                remote.remoteStr("default_compensation_profile_id"),
                            ),
                        ),
                    )
                    local.syncStatus == SyncStatus.SYNCED && remoteUpdatedAt > local.updatedAt ->
                        settingsDao.upsertSettings(
                            remote.toUserSettingsEntity(
                                existingLocalId = local.localId,
                                fallbackCurrency = local.currency,
                            ).copy(
                                defaultCompensationProfileId = resolveProfileLocalId(
                                    remote.remoteStr("default_compensation_profile_id"),
                                ) ?: local.defaultCompensationProfileId,
                            ),
                        )
                    local.remoteId == null -> settingsDao.updateSyncState(
                        local.localId,
                        local.syncStatus,
                        remoteId,
                        local.lastSyncedAt,
                        local.lastSyncError,
                    )
                }
            }.onFailure { e ->
                errors += SyncItemError("settings_pull", remote["id"]?.jsonPrimitive?.content ?: "?", SyncErrorMapper.messageFor(e))
            }
        }
        return errors
    }

    private fun JsonObject.remoteStr(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private suspend fun pullCompensationProfiles(userId: String): List<SyncItemError> {
        val remote = remoteCompensationProfiles ?: return emptyList()
        val errors = mutableListOf<SyncItemError>()
        val remoteItems: List<JsonObject> = runCatching { remote.fetchProfiles(userId) }
            .getOrElse { e -> return listOf(SyncItemError("comp_profile_pull", userId, SyncErrorMapper.messageFor(e))) }

        for (remote in remoteItems) {
            runCatching {
                val remoteId = remote["id"]?.jsonPrimitive?.content ?: return@runCatching
                val remoteUpdatedAt = remote.timestampMillis("updated_at")
                val local = compensationProfileDao.getByRemoteId(remoteId)

                when {
                    local == null -> compensationProfileDao.insert(remote.toCompensationProfileEntity())
                    local.syncStatus == SyncStatus.SYNCED && remoteUpdatedAt > local.updatedAt ->
                        compensationProfileDao.insert(
                            remote.toCompensationProfileEntity(existingLocalId = local.localId),
                        )
                }
            }.onFailure { e ->
                errors += SyncItemError(
                    "comp_profile_pull",
                    remote["id"]?.jsonPrimitive?.content ?: "?",
                    SyncErrorMapper.messageFor(e),
                )
            }
        }
        return errors
    }

    private suspend fun pullProfiles(userId: String): List<SyncItemError> {
        val errors = mutableListOf<SyncItemError>()
        val remoteItems: List<JsonObject> = runCatching { remoteProfile!!.fetchAll(userId) }
            .getOrElse { e -> return listOf(SyncItemError("profile_pull", userId, SyncErrorMapper.messageFor(e))) }

        for (remote in remoteItems) {
            runCatching {
                val remoteId = remote["id"]?.jsonPrimitive?.content ?: return@runCatching
                val remoteUpdatedAt = remote.timestampMillis("updated_at")
                val local = profileDao.getProfileByRemoteId(remoteId)

                when {
                    local == null -> profileDao.upsertProfile(remote.toProfileEntity(userId = userId))
                    local.syncStatus == SyncStatus.SYNCED && remoteUpdatedAt > local.updatedAt ->
                        profileDao.upsertProfile(remote.toProfileEntity(userId = userId, existingLocalId = local.localId))
                }
            }.onFailure { e ->
                errors += SyncItemError("profile_pull", remote["id"]?.jsonPrimitive?.content ?: "?", SyncErrorMapper.messageFor(e))
            }
        }
        return errors
    }
}

private fun Throwable.isMissingCurrencyColumn(): Boolean = generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .any { message ->
        message.contains("currency", ignoreCase = true) &&
            (message.contains("column", ignoreCase = true) || message.contains("schema cache", ignoreCase = true))
    }
