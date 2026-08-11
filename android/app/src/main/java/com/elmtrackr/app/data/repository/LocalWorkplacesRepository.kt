package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.TransactionRunner
import com.elmtrackr.app.data.local.dao.LeavePolicyDao
import com.elmtrackr.app.data.local.dao.WorkplaceDao
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.mapper.mapToDomain
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.domain.compensation.CompensationCurrency
import com.elmtrackr.app.domain.leave.LeavePresets
import com.elmtrackr.app.domain.model.LeavePolicy
import com.elmtrackr.app.domain.model.LeavePolicyRules
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Workplace
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.WorkplacesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalWorkplacesRepository @Inject constructor(
    private val workplaceDao: WorkplaceDao,
    private val leavePolicyDao: LeavePolicyDao,
    private val compensationProfilesRepository: CompensationProfilesRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRunner: TransactionRunner,
    private val syncTrigger: SyncTrigger,
) : WorkplacesRepository {

    // Serialises the two lazily-created singletons. Two screens opening at once
    // both find nothing and both create one otherwise, and the user ends up with
    // "Main job" twice.
    private val ensureMutex = Mutex()

    override fun observeWorkplaces(userId: String): Flow<List<Workplace>> =
        workplaceDao.observeWorkplaces(userId).map { rows -> rows.mapToDomain { it.toDomain() } }

    override fun observeAllWorkplaces(userId: String): Flow<List<Workplace>> =
        workplaceDao.observeAllIncludingArchived(userId).map { rows -> rows.mapToDomain { it.toDomain() } }

    override suspend fun getWorkplaces(userId: String): List<Workplace> =
        workplaceDao.getAllIncludingArchived(userId).mapToDomain { it.toDomain() }

    override suspend fun getWorkplace(userId: String, workplaceId: String): Workplace? =
        workplaceDao.getById(userId, workplaceId)?.toDomain()
            ?: workplaceDao.getByRemoteId(workplaceId)?.takeIf { it.userId == userId }?.toDomain()

    override suspend fun getDefaultWorkplace(userId: String): Workplace? =
        workplaceDao.getDefaultWorkplace(userId)?.toDomain()

    override suspend fun ensureDefaultWorkplace(userId: String): Workplace? = ensureMutex.withLock {
        workplaceDao.getDefaultWorkplace(userId)?.let { return it.toDomain() }
        workplaceDao.getByUser(userId).firstOrNull()?.let { return it.toDomain() }

        // Named and configured from the pay profile the user already has, so the
        // job they have been tracking against keeps its name rather than becoming
        // an anonymous "Workplace 1".
        val settings = settingsRepository.getSettings(userId)
        val profile = compensationProfilesRepository.getProfiles(userId)
            .firstOrNull { it.isDefault }
            ?: compensationProfilesRepository.getProfiles(userId).firstOrNull()
        val regionCode = profile?.regionCode ?: settings?.regionCode ?: RegionCode.IL
        val timezone = profile?.timezone ?: settings?.timezone ?: "UTC"
        val currencyCode = profile?.currencyCode
            ?: settings?.currencyCode
            ?: CompensationCurrency.fallback(regionCode, timezone)
        val now = Instant.now()

        val workplace = Workplace(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = profile?.name?.takeIf { it.isNotBlank() } ?: DEFAULT_WORKPLACE_NAME,
            regionCode = regionCode,
            currencyCode = currencyCode,
            timezone = timezone,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )

        transactionRunner.inTransaction {
            workplaceDao.clearDefaultForUser(userId)
            workplaceDao.upsert(workplace.toEntity(syncStatus = SyncStatus.PENDING_CREATE))
            // Existing shifts and profiles join the new workplace here rather than
            // in the database upgrade. As an ordinary edit it syncs like any other
            // change and is visible to the user; as a migration it would have
            // rewritten their history silently.
            workplaceDao.adoptCompensationProfiles(userId, workplace.id, now.toEpochMilli())
            workplaceDao.adoptShifts(userId, workplace.id, now.toEpochMilli())
            writePolicy(defaultPolicyFor(userId, workplace, profileDailyMinutes(profile)))
        }
        syncTrigger.schedule()
        workplace
    }

    override suspend fun upsertWorkplace(workplace: Workplace): Workplace {
        val now = Instant.now()
        val id = workplace.id.ifBlank { UUID.randomUUID().toString() }
        val existing = workplaceDao.getById(workplace.userId, id)
            ?: workplace.remoteId?.let { workplaceDao.getByRemoteId(it) }
        val entity = workplace.copy(id = id).toEntity(
            syncStatus = syncStatusForMutation(existing?.syncStatus),
            remoteId = existing?.remoteId ?: workplace.remoteId,
        ).copy(
            createdAt = existing?.createdAt ?: now.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
        )
        transactionRunner.inTransaction {
            if (workplace.isDefault) workplaceDao.clearDefaultForUser(workplace.userId)
            workplaceDao.upsert(entity)
        }
        syncTrigger.schedule()
        return entity.toDomain()
    }

    override suspend fun archiveWorkplace(userId: String, workplaceId: String) {
        val existing = workplaceDao.getById(userId, workplaceId) ?: return
        val now = Instant.now().toEpochMilli()
        // Archived, not deleted, and its policies and reported leave are left
        // alone: past months must still report what they reported.
        workplaceDao.archive(
            localId = existing.localId,
            syncStatus = syncStatusForMutation(existing.syncStatus),
            updatedAt = now,
        )
        syncTrigger.schedule()
    }

    // ── Leave policy ──────────────────────────────────────────────────────────

    override fun observePolicies(userId: String): Flow<List<LeavePolicy>> =
        leavePolicyDao.observePolicies(userId).map { rows -> rows.mapToDomain { it.toDomain() } }

    override suspend fun resolvePolicy(workplaceId: String, on: Instant): LeavePolicy? =
        leavePolicyDao.getForWorkplace(workplaceId)
            .mapToDomain { it.toDomain() }
            .firstOrNull { policy ->
                !policy.effectiveFrom.isAfter(on) &&
                    (policy.effectiveUntil?.isAfter(on) ?: true)
            }
            // A policy created after the absence being priced is better than none:
            // without it the screen can only say "no policy", which the user cannot
            // act on for a date in the past. The snapshot records what was used.
            ?: leavePolicyDao.getForWorkplace(workplaceId).mapToDomain { it.toDomain() }.firstOrNull()

    override suspend fun ensurePolicy(userId: String, workplaceId: String): LeavePolicy =
        ensureMutex.withLock {
            resolvePolicy(workplaceId, Instant.now())?.let { return it }
            val workplace = workplaceDao.getByLocalId(workplaceId)?.toDomain()
            val profile = compensationProfilesRepository.getProfiles(userId)
                .firstOrNull { it.workplaceId == workplaceId }
                ?: compensationProfilesRepository.getProfiles(userId).firstOrNull { it.isDefault }
            val policy = defaultPolicyFor(
                userId = userId,
                workplace = workplace,
                standardDayMinutes = profileDailyMinutes(profile),
                workplaceId = workplaceId,
            )
            writePolicy(policy)
            syncTrigger.schedule()
            policy
        }

    override suspend fun updatePolicyRules(
        userId: String,
        workplaceId: String,
        rules: LeavePolicyRules,
    ): LeavePolicy {
        val now = Instant.now()
        val current = resolvePolicy(workplaceId, now)
        val workplace = workplaceDao.getByLocalId(workplaceId)?.toDomain()
        val next = LeavePolicy(
            id = UUID.randomUUID().toString(),
            userId = userId,
            workplaceId = workplaceId,
            regionCode = workplace?.regionCode ?: current?.regionCode ?: RegionCode.IL,
            rules = rules,
            effectiveFrom = now,
            isActive = true,
            createdAt = now,
            updatedAt = now,
        )
        transactionRunner.inTransaction {
            // The outgoing policy is closed rather than edited, so an absence
            // reported last month keeps an explanation that matches the estimate the
            // user was shown at the time.
            current?.let { previous ->
                val closed = previous.copy(
                    effectiveUntil = now,
                    isActive = false,
                    updatedAt = now,
                )
                val existing = leavePolicyDao.getByLocalId(previous.id)
                leavePolicyDao.upsert(
                    closed.toEntity(
                        syncStatus = syncStatusForMutation(existing?.syncStatus),
                        remoteId = existing?.remoteId,
                    ).copy(createdAt = existing?.createdAt ?: now.toEpochMilli()),
                )
            }
            writePolicy(next)
        }
        syncTrigger.schedule()
        return next
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun writePolicy(policy: LeavePolicy) {
        leavePolicyDao.upsert(policy.toEntity(syncStatus = SyncStatus.PENDING_CREATE))
    }

    private fun defaultPolicyFor(
        userId: String,
        workplace: Workplace?,
        standardDayMinutes: Int?,
        workplaceId: String? = workplace?.id,
    ): LeavePolicy {
        val region = workplace?.regionCode ?: RegionCode.IL
        val now = Instant.now()
        return LeavePolicy(
            id = UUID.randomUUID().toString(),
            userId = userId,
            workplaceId = workplaceId ?: workplace?.id.orEmpty(),
            regionCode = region,
            // The preset leaves the standard day unset because guessing one would let
            // hours convert into days at an invented length. The compensation
            // profile's daily standard is not a guess, so it is used when there is
            // one.
            rules = LeavePresets.forRegion(region).copy(standardDayMinutes = standardDayMinutes),
            effectiveFrom = now,
            isActive = true,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun profileDailyMinutes(
        profile: com.elmtrackr.app.domain.model.CompensationProfile?,
    ): Int? = profile?.rules?.dailyStandardMinutes?.takeIf { it > 0 }

    private fun syncStatusForMutation(existing: SyncStatus?): SyncStatus = when (existing) {
        null -> SyncStatus.PENDING_CREATE
        SyncStatus.PENDING_CREATE -> SyncStatus.PENDING_CREATE
        SyncStatus.PENDING_DELETE -> SyncStatus.PENDING_DELETE
        else -> SyncStatus.PENDING_UPDATE
    }

    private companion object {
        /**
         * Matches the name CompensationResolver already gives a legacy profile, so
         * a user who never named anything sees one consistent label.
         */
        const val DEFAULT_WORKPLACE_NAME = "Main job"
    }
}
