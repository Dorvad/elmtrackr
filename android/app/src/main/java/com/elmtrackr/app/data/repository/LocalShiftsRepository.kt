package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.local.mapper.mapToDomain
import com.elmtrackr.app.data.local.mapper.toDomainOrNull
import com.elmtrackr.app.data.sync.SyncTrigger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.elmtrackr.app.domain.compensation.CompensationRulesCodec
import com.elmtrackr.app.domain.model.CompensationSnapshot
import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.repository.RefundsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.domain.time.WorkTimezone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalShiftsRepository @Inject constructor(
    private val shiftDao: ShiftDao,
    private val refundsRepository: RefundsRepository,
    private val syncTrigger: SyncTrigger,
) : ShiftsRepository {

    // clockIn is check-then-insert; without this a double tap could create
    // two concurrent active shifts.
    private val clockInMutex = Mutex()

    override fun observeShifts(userId: String): Flow<List<Shift>> =
        shiftDao.observeShifts(userId).map { entities -> entities.mapToDomain { it.toDomain() } }

    override fun observeActiveShift(userId: String): Flow<Shift?> =
        shiftDao.observeActiveShift(userId).map { it.toDomainOrNull { entity -> entity.toDomain() } }

    override suspend fun getShiftById(localId: String): Shift? =
        // Soft-deleted rows stay invisible to the domain layer: a stale
        // notification or edit action must not act on a shift that was
        // deleted on another device (and would otherwise resurrect it).
        shiftDao.getShiftById(localId)?.takeIf { it.deletedAt == null }
            .toDomainOrNull { it.toDomain() }

    override suspend fun clockIn(
        userId: String,
        compensationProfileId: String?,
        taskId: String?,
        taskNameSnapshot: String?,
        taskIconSnapshot: String?,
        taskHourlyRateSnapshot: Double?,
        compensationSource: CompensationSource,
        projectId: String?,
        projectNameSnapshot: String?,
    ): Shift = clockInMutex.withLock {
        shiftDao.getActiveShifts(userId).maxByOrNull { it.startTime }?.let { activeShift ->
            return activeShift.toDomain()
        }

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
            // Project time only when the caller asked for it. A project link
            // without PROJECT here would still be paid as wages.
            projectId = projectId?.takeIf { compensationSource.isProjectTime },
            projectNameSnapshot = projectNameSnapshot?.takeIf { compensationSource.isProjectTime },
            compensationSource = compensationSource.name,
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
        val existing = shiftDao.getShiftById(localId)?.takeIf { it.deletedAt == null }
            ?: error("Shift $localId not found")
        val now = Instant.now().toEpochMilli()
        val updated = existing.copy(
            endTime = now,
            breakMinutes = breakMinutes,
            notes = notes,
            compensationSnapshotJson = compensationSnapshot?.let { CompensationRulesCodec.encodeSnapshot(it) },
            updatedAt = now,
            syncStatus = syncStatusForMutation(existing),
        )
        shiftDao.updateShift(updated)
        syncTrigger.schedule()
        return updated.toDomain()
    }

    // Same mutex as clockIn: this is a check-then-insert against the
    // (userId, startTime) natural key, and two concurrent calls (a double tap on
    // Save, or a manual add racing a sync-triggered write) could both pass the
    // check and insert duplicates that the sync engine then mapped to one remote row.
    override suspend fun createManualShift(shift: Shift): Shift = clockInMutex.withLock {
        shiftDao.getShiftByStartTime(shift.userId, shift.startTime.toEpochMilli())?.let { existing ->
            return@withLock existing.toDomain()
        }
        val entity = shift.toEntity(syncStatus = SyncStatus.PENDING_CREATE)
        shiftDao.insertShift(entity)
        syncTrigger.schedule()
        entity.toDomain()
    }

    override suspend fun updateShift(shift: Shift): Shift {
        val existing = shiftDao.getShiftById(shift.id)
        val entity = shift.toEntity(
            syncStatus = existing?.let(::syncStatusForMutation) ?: SyncStatus.PENDING_CREATE,
            remoteId = existing?.remoteId,
            lastSyncedAt = existing?.lastSyncedAt,
        )
        shiftDao.upsertShift(entity)
        syncTrigger.schedule()
        return entity.toDomain()
    }

    override suspend fun deleteShift(localId: String) {
        val now = Instant.now().toEpochMilli()
        refundsRepository.deleteClaimsForShift(localId)
        shiftDao.softDeleteShift(
            localId = localId,
            deletedAt = now,
            syncStatus = SyncStatus.PENDING_DELETE,
            updatedAt = now,
        )
        syncTrigger.schedule()
    }

    override fun observeShiftsByMonth(userId: String, year: Int, month: Int): Flow<List<Shift>> =
        observeShiftsByMonthInZone(userId, year, month, ZoneId.of("UTC"))

    override fun observeShiftsByMonthInZone(
        userId: String,
        year: Int,
        month: Int,
        zone: ZoneId,
    ): Flow<List<Shift>> {
        val (from, to) = WorkTimezone.monthRangeEpochMillis(year, month, zone)
        return shiftDao.observeShiftsByDateRange(userId, from, to).map { entities ->
            entities.mapToDomain { it.toDomain() }
        }
    }

    override fun observeShiftsForDay(
        userId: String,
        zone: ZoneId,
        date: java.time.LocalDate,
    ): Flow<List<Shift>> {
        val (from, to) = WorkTimezone.dayRangeEpochMillis(date, zone)
        return shiftDao.observeShiftsForDay(userId, from, to).map { entities ->
            entities.mapToDomain { it.toDomain() }
        }
    }

    override fun observeRecentCompletedShifts(userId: String, limit: Int): Flow<List<Shift>> =
        shiftDao.observeRecentCompletedShifts(userId, limit).map { entities -> entities.mapToDomain { it.toDomain() } }

    override fun observeShiftsForProject(userId: String, projectId: String): Flow<List<Shift>> =
        shiftDao.observeShiftsForProject(userId, projectId)
            .map { entities -> entities.mapToDomain { it.toDomain() } }

    override suspend fun hasAnyShifts(userId: String): Boolean =
        shiftDao.hasAnyShifts(userId)

    private fun syncStatusForMutation(existing: ShiftEntity): SyncStatus = when {
        existing.syncStatus == SyncStatus.PENDING_CREATE -> SyncStatus.PENDING_CREATE
        existing.syncStatus == SyncStatus.PENDING_DELETE -> SyncStatus.PENDING_DELETE
        else -> SyncStatus.PENDING_UPDATE
    }
}
