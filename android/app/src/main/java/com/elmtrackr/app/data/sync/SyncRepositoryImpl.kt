package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.remote.RemoteShiftDataSource
import com.elmtrackr.app.data.remote.isoToEpoch
import com.elmtrackr.app.data.remote.toLocalEntity
import com.elmtrackr.app.data.remote.toRemoteInsert
import com.elmtrackr.app.data.remote.toRemoteUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

class SyncRepositoryImpl(
    private val shiftDao: ShiftDao,
    private val remoteShifts: RemoteShiftDataSource?,
) : SyncRepository {

    private val lastSyncStatus = MutableStateFlow<String?>(null)

    override fun observePendingCount(userId: String): Flow<Int> =
        shiftDao.observePendingSyncShifts(userId).map { it.size }

    override fun observeLastSyncStatus(): Flow<String?> = lastSyncStatus.asStateFlow()

    override suspend fun syncAll(userId: String): SyncResult {
        if (remoteShifts == null) {
            lastSyncStatus.value = "Not configured"
            return SyncResult.NotConfigured
        }

        return runCatching {
            reconcileNeverSynced(userId)
            pushShifts(userId)
            pullShifts(userId)
            lastSyncStatus.value = "Synced ${Instant.now()}"
            SyncResult.Success
        }.getOrElse { error ->
            lastSyncStatus.value = "Failed: ${error.message ?: "unknown error"}"
            SyncResult.Error(error.message ?: "Sync failed")
        }
    }

    private suspend fun reconcileNeverSynced(userId: String) {
        val stale = shiftDao.getAllShiftsForUser(userId)
            .filter { it.remoteId == null && it.syncStatus == SyncStatus.SYNCED }
        for (shift in stale) {
            val status = if (shift.deletedAt != null) {
                SyncStatus.SYNCED
            } else {
                SyncStatus.PENDING_CREATE
            }
            if (status != SyncStatus.SYNCED) {
                shiftDao.upsertShift(shift.copy(syncStatus = status))
            }
        }
    }

    private suspend fun pushShifts(userId: String) {
        val pending = shiftDao.getPendingSyncShifts(userId)
        val now = Instant.now().toEpochMilli()
        for (shift in pending) {
            runCatching {
                when (shift.syncStatus) {
                    SyncStatus.PENDING_DELETE -> pushDelete(shift, now)
                    SyncStatus.PENDING_CREATE, SyncStatus.FAILED -> {
                        if (shift.remoteId == null) {
                            pushCreate(shift, now)
                        } else {
                            pushUpdate(shift, now)
                        }
                    }
                    SyncStatus.PENDING_UPDATE -> pushUpdate(shift, now)
                    SyncStatus.SYNCED -> Unit
                }
            }.onFailure { error ->
                shiftDao.updateSyncState(
                    localId = shift.localId,
                    syncStatus = SyncStatus.FAILED,
                    remoteId = shift.remoteId,
                    lastSyncedAt = shift.lastSyncedAt,
                    lastSyncError = error.message,
                )
            }
        }
    }

    private suspend fun pushCreate(shift: ShiftEntity, syncedAt: Long) {
        val remote = remoteShifts!!.insert(shift.toRemoteInsert())
        shiftDao.updateSyncState(
            localId = shift.localId,
            syncStatus = SyncStatus.SYNCED,
            remoteId = remote.id,
            lastSyncedAt = syncedAt,
            lastSyncError = null,
        )
        val remoteUpdatedAt = isoToEpoch(remote.updatedAt)
        if (remoteUpdatedAt != shift.updatedAt) {
            shiftDao.upsertShift(
                shift.copy(
                    remoteId = remote.id,
                    updatedAt = remoteUpdatedAt,
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncedAt = syncedAt,
                    lastSyncError = null,
                ),
            )
        }
    }

    private suspend fun pushUpdate(shift: ShiftEntity, syncedAt: Long) {
        val remoteId = shift.remoteId
            ?: error("Cannot update shift ${shift.localId} without remoteId")
        remoteShifts!!.update(remoteId, shift.toRemoteUpdate())
        shiftDao.updateSyncState(
            localId = shift.localId,
            syncStatus = SyncStatus.SYNCED,
            remoteId = remoteId,
            lastSyncedAt = syncedAt,
            lastSyncError = null,
        )
    }

    private suspend fun pushDelete(shift: ShiftEntity, syncedAt: Long) {
        val remoteId = shift.remoteId
        if (remoteId != null) {
            remoteShifts!!.delete(remoteId)
        }
        shiftDao.updateSyncState(
            localId = shift.localId,
            syncStatus = SyncStatus.SYNCED,
            remoteId = remoteId,
            lastSyncedAt = syncedAt,
            lastSyncError = null,
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
                val remoteUpdatedAt = isoToEpoch(remote.updatedAt)
                if (remoteUpdatedAt > existing.updatedAt) {
                    shiftDao.upsertShift(
                        remote.toLocalEntity(
                            existingLocalId = existing.localId,
                            syncStatus = SyncStatus.SYNCED,
                        ),
                    )
                }
                continue
            }

            if (remote.endTime == null && localActiveExists) continue

            shiftDao.insertShift(remote.toLocalEntity())
        }

        val localSynced = shiftDao.getAllShiftsForUser(userId)
            .filter { it.remoteId != null && it.syncStatus == SyncStatus.SYNCED }
        val now = Instant.now().toEpochMilli()
        for (local in localSynced) {
            if (local.remoteId !in remoteIds) {
                shiftDao.softDeleteShift(
                    localId = local.localId,
                    deletedAt = now,
                    syncStatus = SyncStatus.SYNCED,
                    updatedAt = now,
                )
            }
        }
    }
}
