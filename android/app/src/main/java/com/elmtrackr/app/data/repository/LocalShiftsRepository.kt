package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.local.mapper.mapToDomain
import com.elmtrackr.app.data.local.mapper.toDomainOrNull
import com.elmtrackr.app.domain.compensation.CompensationRulesCodec
import com.elmtrackr.app.domain.model.CompensationSnapshot
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.sync.NoOpSyncTrigger
import com.elmtrackr.app.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

class LocalShiftsRepository(
    private val shiftDao: ShiftDao,
    private val syncTrigger: SyncTrigger = NoOpSyncTrigger,
) : ShiftsRepository {

    override fun observeShifts(userId: String): Flow<List<Shift>> =
        shiftDao.observeShifts(userId).map { entities -> entities.mapToDomain { it.toDomain() } }

    override fun observeActiveShift(userId: String): Flow<Shift?> =
        shiftDao.observeActiveShift(userId).map { it.toDomainOrNull { entity -> entity.toDomain() } }

    override suspend fun getShiftById(localId: String): Shift? =
        shiftDao.getShiftById(localId).toDomainOrNull { it.toDomain() }

    override suspend fun clockIn(
        userId: String,
        compensationProfileId: String?,
        taskId: String?,
        taskNameSnapshot: String?,
        taskIconSnapshot: String?,
        taskHourlyRateSnapshot: Double?,
    ): Shift {
        val now = Instant.now().toEpochMilli()
        val entity = ShiftEntity(
            localId = UUID.randomUUID().toString(),
            remoteId = null,
            userId = userId,
            startTime = now,
            endTime = null,
            breakMinutes = 0,
            notes = null,
            isSpecialDay = false,
            refundAction = null,
            compensationProfileId = compensationProfileId,
            compensationSnapshotJson = null,
            taskId = taskId,
            taskNameSnapshot = taskNameSnapshot,
            taskIconSnapshot = taskIconSnapshot,
            taskHourlyRateSnapshot = taskHourlyRateSnapshot,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            syncStatus = SyncStatus.PENDING_CREATE,
            lastSyncError = null,
            lastSyncedAt = null,
        )
        shiftDao.insertShift(entity)
        syncTrigger.schedule()
        return entity.toDomain()
    }

    override suspend fun clockOut(
        localId: String,
        breakMinutes: Int,
        notes: String?,
        compensationSnapshot: CompensationSnapshot?,
    ): Shift {
        val existing = shiftDao.getShiftById(localId)
            ?: error("Shift $localId not found")
        val now = Instant.now().toEpochMilli()
        val newStatus = if (existing.syncStatus == SyncStatus.SYNCED)
            SyncStatus.PENDING_UPDATE else existing.syncStatus
        val updated = existing.copy(
            endTime = now,
            breakMinutes = breakMinutes,
            notes = notes,
            compensationSnapshotJson = compensationSnapshot?.let { CompensationRulesCodec.encodeSnapshot(it) },
            updatedAt = now,
            syncStatus = newStatus,
        )
        shiftDao.updateShift(updated)
        syncTrigger.schedule()
        return updated.toDomain()
    }

    override suspend fun createManualShift(shift: Shift): Shift {
        val entity = shift.toEntity(syncStatus = SyncStatus.PENDING_CREATE)
        shiftDao.insertShift(entity)
        syncTrigger.schedule()
        return entity.toDomain()
    }

    override suspend fun updateShift(shift: Shift): Shift {
        val existing = shiftDao.getShiftById(shift.id)
        val newStatus = if (existing?.syncStatus == SyncStatus.SYNCED)
            SyncStatus.PENDING_UPDATE else existing?.syncStatus ?: SyncStatus.PENDING_UPDATE
        val entity = shift.toEntity(
            syncStatus = newStatus,
            remoteId = existing?.remoteId,
            lastSyncedAt = existing?.lastSyncedAt,
        )
        shiftDao.upsertShift(entity)
        syncTrigger.schedule()
        return entity.toDomain()
    }

    override suspend fun deleteShift(localId: String) {
        val now = Instant.now().toEpochMilli()
        shiftDao.softDeleteShift(
            localId = localId,
            deletedAt = now,
            syncStatus = SyncStatus.PENDING_DELETE,
            updatedAt = now,
        )
        syncTrigger.schedule()
    }

    override fun observeShiftsByMonth(userId: String, year: Int, month: Int): Flow<List<Shift>> {
        val ym = YearMonth.of(year, month)
        val from = ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val to = ym.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return shiftDao.observeShiftsByDateRange(userId, from, to).map { entities ->
            entities.mapToDomain { it.toDomain() }
        }
    }

    override fun observeRecentCompletedShifts(userId: String, limit: Int): Flow<List<Shift>> =
        shiftDao.observeRecentCompletedShifts(userId, limit).map { entities -> entities.mapToDomain { it.toDomain() } }

    override fun observePendingSyncShifts(userId: String): Flow<List<Shift>> =
        shiftDao.observePendingSyncShifts(userId).map { entities -> entities.mapToDomain { it.toDomain() } }

    override suspend fun hasAnyShifts(userId: String): Boolean =
        shiftDao.getAllShiftsForUser(userId).isNotEmpty()
}
