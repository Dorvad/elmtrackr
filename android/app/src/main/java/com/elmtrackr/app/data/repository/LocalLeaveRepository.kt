package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.TransactionRunner
import com.elmtrackr.app.data.local.dao.AbsenceAllocationDao
import com.elmtrackr.app.data.local.dao.AbsenceEventDao
import com.elmtrackr.app.data.local.dao.LeaveBalanceSnapshotDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.dao.WorkplaceDao
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.mapper.mapToDomain
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.domain.leave.LeaveBalanceEstimate
import com.elmtrackr.app.domain.leave.LeaveBalanceEstimator
import com.elmtrackr.app.domain.leave.LeaveCalculator
import com.elmtrackr.app.domain.leave.LeaveEarningsBase
import com.elmtrackr.app.domain.leave.LeaveEarningsHistory
import com.elmtrackr.app.domain.leave.LeaveEstimate
import com.elmtrackr.app.domain.leave.LeaveEstimateRequest
import com.elmtrackr.app.domain.model.AbsenceAllocation
import com.elmtrackr.app.domain.model.AbsenceEvent
import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.LeaveBalanceSnapshot
import com.elmtrackr.app.domain.model.LeavePolicy
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.model.Workplace
import com.elmtrackr.app.domain.repository.AbsenceDayInput
import com.elmtrackr.app.domain.repository.AbsenceDraft
import com.elmtrackr.app.domain.repository.LeaveRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.WorkplacesRepository
import com.elmtrackr.app.domain.time.WorkTimezone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLeaveRepository @Inject constructor(
    private val absenceEventDao: AbsenceEventDao,
    private val absenceAllocationDao: AbsenceAllocationDao,
    private val balanceSnapshotDao: LeaveBalanceSnapshotDao,
    private val workplaceDao: WorkplaceDao,
    private val shiftDao: ShiftDao,
    private val workplacesRepository: WorkplacesRepository,
    private val settingsRepository: SettingsRepository,
    private val compensationProfilesRepository: CompensationProfilesRepository,
    private val transactionRunner: TransactionRunner,
    private val syncTrigger: SyncTrigger,
) : LeaveRepository {

    override fun observeEvents(userId: String): Flow<List<AbsenceEvent>> =
        absenceEventDao.observeEvents(userId).map { rows -> rows.mapToDomain { it.toDomain() } }

    override fun observeAllocations(userId: String): Flow<List<AbsenceAllocation>> =
        absenceAllocationDao.observeAllocations(userId).map { rows -> rows.mapToDomain { it.toDomain() } }

    override fun observeAllocationsInMonth(
        userId: String,
        year: Int,
        month: Int,
    ): Flow<List<AbsenceAllocation>> {
        val first = YearMonth.of(year, month).atDay(1)
        val last = YearMonth.of(year, month).atEndOfMonth()
        return absenceAllocationDao
            .observeInDateRange(userId, first.toEpochDay(), last.toEpochDay())
            .map { rows -> rows.mapToDomain { it.toDomain() } }
    }

    override suspend fun getEvent(userId: String, eventId: String): AbsenceEvent? =
        absenceEventDao.getById(userId, eventId)?.let { row -> runCatching { row.toDomain() }.getOrNull() }

    override suspend fun getAllocationsForEvent(eventId: String): List<AbsenceAllocation> =
        absenceAllocationDao.getForEvent(eventId).mapToDomain { it.toDomain() }

    // ── Saving ────────────────────────────────────────────────────────────────

    override suspend fun saveAbsence(userId: String, draft: AbsenceDraft): AbsenceEvent {
        val now = Instant.now()
        val event = AbsenceEvent(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = draft.type,
            startDate = draft.startDate,
            endDate = draft.endDate,
            notes = draft.notes,
            createdAt = now,
            updatedAt = now,
        )
        val allocations = priceAllocations(userId, event, draft, now)
        // One transaction: an absence that reached the balance without appearing in
        // the history, or the reverse, is worse than one that failed to save.
        transactionRunner.inTransaction {
            absenceEventDao.upsert(event.toEntity(syncStatus = SyncStatus.PENDING_CREATE))
            absenceAllocationDao.upsertAll(
                allocations.map { it.toEntity(syncStatus = SyncStatus.PENDING_CREATE) },
            )
        }
        syncTrigger.schedule()
        return event
    }

    override suspend fun updateAbsence(
        userId: String,
        eventId: String,
        draft: AbsenceDraft,
    ): AbsenceEvent? {
        val existing = absenceEventDao.getById(userId, eventId) ?: return null
        val now = Instant.now()
        val event = AbsenceEvent(
            id = existing.localId,
            userId = userId,
            type = draft.type,
            startDate = draft.startDate,
            endDate = draft.endDate,
            notes = draft.notes,
            createdAt = Instant.ofEpochMilli(existing.createdAt),
            updatedAt = now,
            remoteId = existing.remoteId,
        )
        // Rebuilt rather than patched. Changing the range moves every sick-day
        // ordinal, and an allocation that only had its date edited would keep a
        // multiplier that was computed for a different day of the illness.
        val allocations = priceAllocations(userId, event, draft, now)
        transactionRunner.inTransaction {
            absenceEventDao.upsert(
                event.toEntity(
                    syncStatus = syncStatusForMutation(existing.syncStatus),
                    remoteId = existing.remoteId,
                ),
            )
            absenceAllocationDao.softDeleteForEvent(
                eventLocalId = event.id,
                deletedAt = now.toEpochMilli(),
                syncStatus = SyncStatus.PENDING_UPDATE,
                updatedAt = now.toEpochMilli(),
            )
            absenceAllocationDao.upsertAll(
                allocations.map { it.toEntity(syncStatus = SyncStatus.PENDING_CREATE) },
            )
        }
        syncTrigger.schedule()
        return event
    }

    override suspend fun deleteAbsence(userId: String, eventId: String) {
        val existing = absenceEventDao.getById(userId, eventId) ?: return
        val now = Instant.now().toEpochMilli()
        transactionRunner.inTransaction {
            absenceAllocationDao.softDeleteForEvent(
                eventLocalId = existing.localId,
                deletedAt = now,
                syncStatus = SyncStatus.PENDING_UPDATE,
                updatedAt = now,
            )
            absenceEventDao.softDelete(
                localId = existing.localId,
                deletedAt = now,
                syncStatus = SyncStatus.PENDING_UPDATE,
                updatedAt = now,
            )
        }
        syncTrigger.schedule()
    }

    override suspend fun mergeSickPeriods(
        userId: String,
        targetEventId: String,
        sourceEventId: String,
    ): AbsenceEvent? {
        val target = absenceEventDao.getById(userId, targetEventId) ?: return null
        val source = absenceEventDao.getById(userId, sourceEventId) ?: return null
        if (target.type != source.type) return null

        val merged = AbsenceEvent(
            id = target.localId,
            userId = userId,
            type = AbsenceType.fromPersisted(target.type) ?: return null,
            startDate = LocalDate.ofEpochDay(minOf(target.startDate, source.startDate)),
            endDate = LocalDate.ofEpochDay(maxOf(target.endDate, source.endDate)),
            notes = listOfNotNull(target.notes, source.notes).joinToString("\n").ifBlank { null },
            createdAt = Instant.ofEpochMilli(target.createdAt),
            updatedAt = Instant.now(),
            remoteId = target.remoteId,
        )
        val now = Instant.now()
        // The days the two periods covered are kept, but re-priced: once they are
        // one illness the ordinals run through, which is the entire reason a user
        // would accept the merge.
        val existingDays = (
            absenceAllocationDao.getForEvent(target.localId) +
                absenceAllocationDao.getForEvent(source.localId)
            )
            .mapToDomain { it.toDomain() }
            .distinctBy { it.workplaceId to it.affectedDate }
        val draft = AbsenceDraft(
            type = merged.type,
            startDate = merged.startDate,
            endDate = merged.endDate,
            notes = merged.notes,
            days = existingDays.map { allocation ->
                AbsenceDayInput(
                    workplaceId = allocation.workplaceId,
                    date = allocation.affectedDate,
                    entitlementUnits = allocation.entitlementUnits,
                    unit = allocation.unit,
                    expectedWorkMinutes = allocation.expectedWorkMinutes,
                )
            },
        )
        val allocations = priceAllocations(userId, merged, draft, now)

        transactionRunner.inTransaction {
            absenceEventDao.upsert(
                merged.toEntity(
                    syncStatus = syncStatusForMutation(target.syncStatus),
                    remoteId = target.remoteId,
                ),
            )
            absenceAllocationDao.softDeleteForEvent(
                target.localId, now.toEpochMilli(), SyncStatus.PENDING_UPDATE, now.toEpochMilli(),
            )
            absenceAllocationDao.softDeleteForEvent(
                source.localId, now.toEpochMilli(), SyncStatus.PENDING_UPDATE, now.toEpochMilli(),
            )
            absenceEventDao.softDelete(
                source.localId, now.toEpochMilli(), SyncStatus.PENDING_UPDATE, now.toEpochMilli(),
            )
            absenceAllocationDao.upsertAll(
                allocations.map { it.toEntity(syncStatus = SyncStatus.PENDING_CREATE) },
            )
        }
        syncTrigger.schedule()
        return merged
    }

    /**
     * Prices every day of a draft, one workplace at a time.
     *
     * A day the engine cannot value is still saved. It consumed entitlement
     * whether or not the app can say what it was worth, so dropping it would
     * quietly overstate the remaining balance; it is stored with a zero estimate
     * and no calculation snapshot, which is how the UI knows to ask for a value
     * rather than presenting zero as an answer.
     */
    private suspend fun priceAllocations(
        userId: String,
        event: AbsenceEvent,
        draft: AbsenceDraft,
        now: Instant,
    ): List<AbsenceAllocation> {
        val settings = settingsRepository.getSettings(userId)
        val profiles = compensationProfilesRepository.getProfiles(userId)
        val defaultWorkplaceId = workplaceDao.getDefaultWorkplace(userId)?.localId
        val historyCache = mutableMapOf<String, LeaveEarningsHistory>()
        val policyCache = mutableMapOf<String, LeavePolicy?>()

        return draft.days.map { day ->
            val policy = policyCache.getOrPut(day.workplaceId) {
                workplacesRepository.resolvePolicy(day.workplaceId, now)
            }
            val workplace = workplaceDao.getByLocalId(day.workplaceId)?.toDomain()
            val history = if (settings == null) {
                LeaveEarningsHistory.EMPTY
            } else {
                historyCache.getOrPut(day.workplaceId) {
                    buildHistory(
                        userId = userId,
                        settings = settings,
                        profiles = profiles,
                        workplaceId = day.workplaceId,
                        isDefaultWorkplace = day.workplaceId == defaultWorkplaceId,
                        reference = YearMonth.from(day.date),
                    )
                }
            }
            val snapshot = policy?.let {
                val estimate = LeaveCalculator.estimate(
                    LeaveEstimateRequest(
                        event = event,
                        workplaceId = day.workplaceId,
                        policy = it,
                        affectedDate = day.date,
                        entitlementUnits = day.entitlementUnits,
                        unit = day.unit,
                        expectedWorkMinutes = day.expectedWorkMinutes,
                        hourlyRate = hourlyRateFor(day.workplaceId, profiles, settings),
                        currencyCode = workplace?.currencyCode
                            ?: settings?.currencyCode
                            ?: settings?.currency?.name
                            ?: "ILS",
                        history = history,
                        manualDailyAmount = day.manualDailyAmount,
                        manualReason = day.manualReason,
                        calculatedAt = now,
                    ),
                )
                (estimate as? LeaveEstimate.Ready)?.snapshot
            }

            AbsenceAllocation(
                id = UUID.randomUUID().toString(),
                userId = userId,
                absenceEventId = event.id,
                workplaceId = day.workplaceId,
                affectedDate = day.date,
                entitlementUnits = day.entitlementUnits,
                unit = day.unit,
                expectedWorkMinutes = day.expectedWorkMinutes,
                policySnapshot = policy?.let { LeaveCalculator.buildPolicySnapshot(it, now) },
                calculationSnapshot = snapshot,
                estimatedGrossPay = snapshot?.estimatedGrossPay ?: 0.0,
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    private suspend fun buildHistory(
        userId: String,
        settings: UserSettings,
        profiles: List<CompensationProfile>,
        workplaceId: String,
        isDefaultWorkplace: Boolean,
        reference: YearMonth,
    ): LeaveEarningsHistory {
        val zone = WorkTimezone.zoneFor(settings)
        val from = reference.minusMonths(EARNINGS_LOOKBACK_MONTHS).atDay(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val to = reference.atEndOfMonth().plusDays(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val shifts: List<Shift> = shiftDao
            .getCompletedShiftsInRange(userId, from, to)
            .mapToDomain { it.toDomain() }
        return LeaveEarningsBase.build(
            shifts = shifts,
            settings = settings,
            profiles = profiles,
            workplaceId = workplaceId,
            // Shifts recorded before workplaces existed carry none. Counting them
            // for the default workplace is what stops a long-standing user from
            // having no pay history at all the first time they report leave.
            treatUnassignedAsThisWorkplace = isDefaultWorkplace,
            months = EARNINGS_LOOKBACK_MONTHS.toInt(),
            reference = reference,
        )
    }

    private fun hourlyRateFor(
        workplaceId: String,
        profiles: List<CompensationProfile>,
        settings: UserSettings?,
    ): Double? = profiles.firstOrNull { it.workplaceId == workplaceId }?.baseHourlyRate
        ?: profiles.firstOrNull { it.isDefault }?.baseHourlyRate
        ?: settings?.hourlyRate

    // ── Payslip balances ──────────────────────────────────────────────────────

    override fun observeBalanceSnapshots(userId: String): Flow<List<LeaveBalanceSnapshot>> =
        balanceSnapshotDao.observeSnapshots(userId).map { rows -> rows.mapToDomain { it.toDomain() } }

    override suspend fun getBalanceHistory(
        workplaceId: String,
        type: AbsenceType,
    ): List<LeaveBalanceSnapshot> =
        balanceSnapshotDao.getHistory(workplaceId, type.persistedValue).mapToDomain { it.toDomain() }

    override suspend fun addBalanceSnapshot(snapshot: LeaveBalanceSnapshot): LeaveBalanceSnapshot {
        val now = Instant.now()
        // Always a new row. Overwriting the previous balance would destroy the
        // reference point the estimate is measured from.
        val stored = snapshot.copy(
            id = snapshot.id.ifBlank { UUID.randomUUID().toString() },
            createdAt = if (snapshot.createdAt == Instant.EPOCH) now else snapshot.createdAt,
            updatedAt = now,
        )
        balanceSnapshotDao.upsert(stored.toEntity(syncStatus = SyncStatus.PENDING_CREATE))
        syncTrigger.schedule()
        return stored
    }

    override suspend fun deleteBalanceSnapshot(userId: String, snapshotId: String) {
        val existing = balanceSnapshotDao.getByLocalId(snapshotId)?.takeIf { it.userId == userId } ?: return
        val now = Instant.now().toEpochMilli()
        balanceSnapshotDao.softDelete(
            localId = existing.localId,
            deletedAt = now,
            syncStatus = syncStatusForMutation(existing.syncStatus),
            updatedAt = now,
        )
        syncTrigger.schedule()
    }

    override suspend fun estimateBalance(
        userId: String,
        workplaceId: String,
        type: AbsenceType,
    ): LeaveBalanceEstimate {
        val latest = balanceSnapshotDao.getLatest(workplaceId, type.persistedValue)
            ?.let { row -> runCatching { row.toDomain() }.getOrNull() }
        val eventTypeById = absenceEventDao.getByUser(userId).associate { it.localId to it.type }
        val allocations = when (latest) {
            null -> absenceAllocationDao.getForWorkplaceInRange(workplaceId, Long.MIN_VALUE, Long.MAX_VALUE)
            else -> absenceAllocationDao.getForWorkplaceAfter(workplaceId, latest.asOfDate.toEpochDay())
        }
            .mapToDomain { it.toDomain() }
            // Only this kind of leave: a sick day must not come off the vacation
            // balance.
            .filter { eventTypeById[it.absenceEventId] == type.persistedValue }

        val standardDayMinutes = workplacesRepository
            .resolvePolicy(workplaceId, Instant.now())
            ?.rules
            ?.standardDayMinutes

        return LeaveBalanceEstimator.estimate(
            workplaceId = workplaceId,
            balanceType = type,
            latestSnapshot = latest,
            allocations = allocations,
            standardDayMinutes = standardDayMinutes,
        )
    }

    override fun observeBalanceEstimates(userId: String): Flow<List<LeaveBalanceEstimate>> =
        combine(
            workplaceDao.observeAllIncludingArchived(userId),
            balanceSnapshotDao.observeSnapshots(userId),
            absenceAllocationDao.observeAllocations(userId),
            absenceEventDao.observeEvents(userId),
            // Included so this stream converts hours to days on the same basis as
            // estimateBalance. Resolving it in one path and not the other would show
            // two different balances for the same workplace.
            workplacesRepository.observePolicies(userId),
        ) { workplaces, snapshots, allocations, events, policies ->
            val snapshotsByKey = snapshots
                .mapToDomain { it.toDomain() }
                .groupBy { it.workplaceId to it.balanceType }
            val typeByEvent = events.associate { it.localId to it.type }
            val domainAllocations = allocations.mapToDomain { it.toDomain() }
            val standardDayByWorkplace = policies
                .groupBy { it.workplaceId }
                .mapValues { (_, forWorkplace) ->
                    forWorkplace.maxByOrNull { it.effectiveFrom }?.rules?.standardDayMinutes
                }

            workplaces.mapToDomain { it.toDomain() }.flatMap { workplace: Workplace ->
                AbsenceType.entries.map { type ->
                    val latest = LeaveBalanceEstimator.latestSnapshot(
                        snapshotsByKey[workplace.id to type] ?: emptyList(),
                    )
                    val relevant = domainAllocations.filter {
                        it.workplaceId == workplace.id &&
                            typeByEvent[it.absenceEventId] == type.persistedValue
                    }
                    LeaveBalanceEstimator.estimate(
                        workplaceId = workplace.id,
                        balanceType = type,
                        latestSnapshot = latest,
                        allocations = relevant,
                        standardDayMinutes = standardDayByWorkplace[workplace.id],
                    )
                }
            }
        }

    private fun syncStatusForMutation(existing: SyncStatus?): SyncStatus = when (existing) {
        null -> SyncStatus.PENDING_CREATE
        SyncStatus.PENDING_CREATE -> SyncStatus.PENDING_CREATE
        SyncStatus.PENDING_DELETE -> SyncStatus.PENDING_DELETE
        else -> SyncStatus.PENDING_UPDATE
    }

    private companion object {
        /**
         * Twelve, because the statutory average falls back to the strongest
         * complete three-month run inside the preceding year when the three months
         * before the absence are not usable.
         */
        const val EARNINGS_LOOKBACK_MONTHS = 12L
    }
}
