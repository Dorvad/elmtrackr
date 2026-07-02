package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.dao.CompensationProfileDao
import com.elmtrackr.app.data.local.dao.ProfileDao
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.dao.TaskDao
import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.ProfileEntity
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.TaskEntity
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import com.elmtrackr.app.data.remote.RemoteCompensationProfileDataSource
import com.elmtrackr.app.data.remote.RemoteProfileDataSource
import com.elmtrackr.app.data.remote.RemoteRefundClaimDataSource
import com.elmtrackr.app.data.remote.RemoteShiftDataSource
import com.elmtrackr.app.data.remote.RemoteShiftRow
import com.elmtrackr.app.data.remote.RemoteSyncErrors
import com.elmtrackr.app.data.remote.RemoteTaskDataSource
import com.elmtrackr.app.data.remote.RemoteUserSettingsDataSource
import com.elmtrackr.app.data.remote.epochToIso
import com.elmtrackr.app.data.remote.isoToEpoch
import com.elmtrackr.app.data.remote.toLocalEntity
import com.elmtrackr.app.data.remote.toRemoteInsert
import com.elmtrackr.app.data.remote.toRemoteUpdate
import com.elmtrackr.app.data.remote.toRemoteUpsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val shiftDao: ShiftDao,
    private val refundClaimDao: RefundClaimDao,
    private val settingsDao: SettingsDao,
    private val compensationProfileDao: CompensationProfileDao,
    private val taskDao: TaskDao,
    private val profileDao: ProfileDao,
    private val syncCursorStore: SyncCursorStore,
    private val remoteTasks: RemoteTaskDataSource?,
    private val remoteShifts: RemoteShiftDataSource?,
    private val remoteRefundClaims: RemoteRefundClaimDataSource?,
    private val remoteSettings: RemoteUserSettingsDataSource?,
    private val remoteCompensationProfiles: RemoteCompensationProfileDataSource?,
    private val remoteProfiles: RemoteProfileDataSource?,
) : SyncRepository {

    private val idMapper = SyncIdMapper(shiftDao, compensationProfileDao, taskDao)
    private val lastSyncStatus = MutableStateFlow<String?>(null)
    // syncAll can be invoked concurrently (WorkManager, auth bootstrap, manual retry);
    // without serialization two runs can push the same PENDING_CREATE row twice.
    private val syncMutex = Mutex()
    private var tasksRemoteEnabled = true

    private data class PendingSyncSnapshot(
        val shifts: List<ShiftEntity>,
        val claims: List<RefundClaimEntity>,
        val settings: List<UserSettingsEntity>,
        val profiles: List<CompensationProfileEntity>,
        val tasks: List<TaskEntity>,
        val userProfiles: List<ProfileEntity>,
    ) {
        val pendingCount: Int
            get() = shifts.size + claims.size + settings.size + profiles.size + tasks.size +
                userProfiles.size

        val failedCount: Int
            get() = shifts.count { it.syncStatus == SyncStatus.FAILED } +
                claims.count { it.syncStatus == SyncStatus.FAILED } +
                settings.count { it.syncStatus == SyncStatus.FAILED } +
                profiles.count { it.syncStatus == SyncStatus.FAILED } +
                tasks.count { it.syncStatus == SyncStatus.FAILED } +
                userProfiles.count { it.syncStatus == SyncStatus.FAILED }
    }

    private fun observePendingSnapshot(userId: String): Flow<PendingSyncSnapshot> =
        combine(
            combine(
                shiftDao.observePendingSyncShifts(userId),
                refundClaimDao.observePendingSyncClaims(userId),
                settingsDao.observePendingSyncSettings(userId),
                compensationProfileDao.observePendingSyncProfiles(userId),
                taskDao.observePendingSyncTasks(userId),
            ) { shifts, claims, settings, profiles, tasks ->
                PendingSyncSnapshot(
                    shifts = shifts,
                    claims = claims,
                    settings = settings,
                    profiles = profiles,
                    tasks = tasks,
                    userProfiles = emptyList(),
                )
            },
            profileDao.observePendingSyncProfiles(userId),
        ) { snapshot, userProfiles ->
            snapshot.copy(userProfiles = userProfiles)
        }

    override fun observePendingCount(userId: String): Flow<Int> =
        observePendingSnapshot(userId).map { it.pendingCount }

    override fun observeSyncHealth(userId: String): Flow<SyncHealth> =
        observePendingSnapshot(userId).map { snapshot ->
            SyncHealth(
                pendingCount = snapshot.pendingCount,
                failedCount = snapshot.failedCount,
            )
        }

    override fun observeLastSyncStatus(): Flow<String?> = lastSyncStatus.asStateFlow()

    override fun observeSyncDetails(userId: String): Flow<SyncDetails> =
        combine(
            combine(
                taskDao.observeAllTasks(userId),
                shiftDao.observeShifts(userId),
                refundClaimDao.observeClaimsForUser(userId),
            ) { tasks, shifts, claims -> Triple(tasks, shifts, claims) },
            combine(
                settingsDao.observeSettings(userId),
                compensationProfileDao.observeProfiles(userId),
                lastSyncStatus,
            ) { settings, profiles, status -> Triple(settings, profiles, status) },
        ) { pending, meta ->
            SyncDetailsBuilder.build(
                tasks = pending.first,
                shifts = pending.second,
                claims = pending.third,
                settings = meta.first,
                profiles = meta.second,
                lastSyncStatus = meta.third,
            )
        }

    override suspend fun exportLocalBackup(userId: String): String =
        LocalBackupExporter.export(
            userId = userId,
            taskDao = taskDao,
            shiftDao = shiftDao,
            refundClaimDao = refundClaimDao,
            settingsDao = settingsDao,
            compensationProfileDao = compensationProfileDao,
            appVersion = com.elmtrackr.app.BuildConfig.VERSION_NAME,
        )

    override suspend fun hasPendingWork(userId: String): Boolean =
        shiftDao.hasPendingSyncShifts(userId) ||
            refundClaimDao.hasPendingSyncClaims(userId) ||
            settingsDao.hasPendingSyncSettings(userId) ||
            compensationProfileDao.hasPendingSyncProfiles(userId) ||
            taskDao.hasPendingSyncTasks(userId) ||
            profileDao.hasPendingSyncProfiles(userId)

    override suspend fun syncAll(userId: String): SyncResult {
        if (remoteTasks == null || remoteShifts == null || remoteRefundClaims == null ||
            remoteSettings == null || remoteCompensationProfiles == null || remoteProfiles == null
        ) {
            lastSyncStatus.value = "Not configured"
            return SyncResult.NotConfigured
        }

        return syncMutex.withLock {
            // Re-probe tasks sync every run so applying the missing Supabase migration
            // takes effect on the next sync instead of requiring an app restart.
            tasksRemoteEnabled = true
            runSyncPipeline(userId)
        }
    }

    private suspend fun runSyncPipeline(userId: String): SyncResult {
        return runCatching {
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()

            runSyncStep("reconcile", errors) { reconcileNeverSynced(userId) }
            runSyncStep("push tasks", errors) { pushTasks(userId, warnings) }
            runSyncStep("push compensation profiles", errors) { pushCompensationProfiles(userId) }
            runSyncStep("push shifts", errors) { pushShifts(userId) }
            runSyncStep("push refund claims", errors) { pushRefundClaims(userId) }
            runSyncStep("push user settings", errors) { pushUserSettings(userId) }
            runSyncStep("push profiles", errors) { pushProfiles(userId) }
            runSyncStep("pull profiles", errors) { pullProfiles(userId) }
            runSyncStep("pull compensation profiles", errors) { pullCompensationProfiles(userId) }
            runSyncStep("pull user settings", errors) { pullUserSettings(userId) }
            runSyncStep("pull shifts", errors) { pullShifts(userId) }
            runSyncStep("pull refund claims", errors) { pullRefundClaims(userId) }
            runSyncStep("pull tasks", errors) { pullTasks(userId, warnings) }

            when {
                errors.isNotEmpty() -> {
                    lastSyncStatus.value = "Failed: ${errors.joinToString("; ")}"
                    SyncResult.Error(errors.joinToString("; "))
                }
                warnings.isNotEmpty() -> {
                    lastSyncStatus.value = "Synced with warnings: ${warnings.joinToString("; ")}"
                    SyncResult.Success
                }
                else -> {
                    lastSyncStatus.value = "Synced ${Instant.now()}"
                    SyncResult.Success
                }
            }
        }.getOrElse { error ->
            lastSyncStatus.value = "Failed: ${error.message ?: "unknown error"}"
            SyncResult.Error(error.message ?: "Sync failed")
        }
    }

    private suspend fun runSyncStep(
        label: String,
        errors: MutableList<String>,
        block: suspend () -> Unit,
    ) {
        runCatching { block() }.onFailure { error ->
            errors += "$label: ${error.message ?: "unknown error"}"
        }
    }

    private suspend fun suspendTasksRemoteSync(userId: String, warnings: MutableList<String>) {
        if (!tasksRemoteEnabled) return
        tasksRemoteEnabled = false
        taskDao.getPendingSyncTasks(userId)
            .filter { it.syncStatus == SyncStatus.FAILED }
            .forEach { task ->
                val restoredStatus = if (task.remoteId == null) {
                    SyncStatus.PENDING_CREATE
                } else {
                    SyncStatus.PENDING_UPDATE
                }
                taskDao.updateSyncState(
                    task.localId,
                    restoredStatus,
                    task.remoteId,
                    task.lastSyncedAt,
                    null,
                )
            }
        warnings += TASKS_TABLE_MISSING_WARNING
    }

    private suspend fun reconcileNeverSynced(userId: String) {
        taskDao.markNeverSyncedPendingCreate(userId)
        shiftDao.markNeverSyncedPendingCreate(userId)
        refundClaimDao.markNeverSyncedPendingCreate(userId)
        settingsDao.markNeverSyncedPendingCreate(userId)
        compensationProfileDao.markNeverSyncedPendingCreate(userId)
        profileDao.markNeverSyncedPendingUpdate(userId)
    }

    private data class PullOutcome(
        val seenRemoteIds: Set<String>,
        val isFullSync: Boolean,
    ) {
        val pulledAnyRows: Boolean get() = seenRemoteIds.isNotEmpty()
    }

    /**
     * Fetches remote rows updated since the stored cursor, one page at a time. Each
     * page is applied via [applyRow] before the cursor advances, so a failure or
     * process death mid-pull re-fetches those rows instead of skipping them forever.
     * [applyRow] returns false when a row cannot be applied yet (e.g. its local
     * parent row is missing); the cursor is then held at that row's updated_at so a
     * later sync re-fetches it (the remote query uses gte).
     */
    private suspend fun <Row> pullIncremental(
        userId: String,
        entity: String,
        fetchPage: suspend (sinceIso: String?) -> List<Row>,
        updatedAtIso: (Row) -> String,
        remoteIdOf: (Row) -> String,
        applyRow: suspend (Row) -> Boolean,
    ): PullOutcome {
        val initialCursor = syncCursorStore.lastPulledAt(userId, entity)
        val isFullSync = initialCursor == null
        var cursor = initialCursor
        var holdEpoch: Long? = null
        val seenRemoteIds = mutableSetOf<String>()

        while (true) {
            val batch = fetchPage(syncCursorStore.sinceIso(cursor))
            if (batch.isEmpty()) {
                // Avoid repeating full-sync tombstone passes when the server returns no rows
                // (e.g. auth/RLS not ready yet). Epoch 0 makes the next pull incremental.
                // Known trade-off: once this happens, isFullSync never becomes true again for
                // this entity, so remote hard-deletes made before the next pull are never
                // tombstoned locally. Acceptable: keeping local data beats deleting it.
                if (isFullSync && cursor == null) {
                    syncCursorStore.setLastPulledAt(userId, entity, 0L)
                }
                break
            }
            var maxEpoch = cursor ?: 0L
            for (row in batch) {
                seenRemoteIds += remoteIdOf(row)
                val rowEpoch = isoToEpoch(updatedAtIso(row))
                if (!applyRow(row)) {
                    holdEpoch = minOf(holdEpoch ?: rowEpoch, rowEpoch)
                }
                maxEpoch = maxOf(maxEpoch, rowEpoch)
            }
            val previousCursor = cursor
            cursor = maxEpoch
            syncCursorStore.setLastPulledAt(userId, entity, holdEpoch?.coerceAtMost(cursor) ?: cursor)
            if (batch.size < PULL_PAGE_SIZE) break
            // A full page whose newest row does not advance the cursor means every
            // remaining fetch would return the same page (updated_at uses gte) — bail
            // out instead of looping forever.
            if (cursor == previousCursor) break
        }

        return PullOutcome(seenRemoteIds = seenRemoteIds, isFullSync = isFullSync)
    }

    // ── Tasks ───────────────────────────────────────────────────────────────

    private suspend fun pushTasks(userId: String, warnings: MutableList<String>) {
        if (!tasksRemoteEnabled) return
        val now = Instant.now().toEpochMilli()
        for (task in taskDao.getPendingSyncTasks(userId)) {
            runCatching {
                // The operation derives from row state, not the status enum, so a FAILED
                // row keeps its original intent (a failed delete must stay a delete —
                // pushing it as an update would resurrect the row remotely).
                when {
                    task.syncStatus == SyncStatus.SYNCED -> Unit
                    task.deletedAt != null -> pushTaskDelete(task, now)
                    task.remoteId == null -> pushTaskCreate(task, now)
                    else -> pushTaskUpdate(task, now)
                }
            }.onFailure { error ->
                if (RemoteSyncErrors.isMissingRemoteTable(error, ENTITY_TASKS)) {
                    suspendTasksRemoteSync(userId, warnings)
                    return
                }
                markTaskFailed(task, error)
            }
        }
    }

    private suspend fun pushTaskCreate(task: TaskEntity, syncedAt: Long) {
        val remote = remoteTasks!!.insert(task.toRemoteInsert())
        taskDao.updateSyncState(task.localId, SyncStatus.SYNCED, remote.id, syncedAt, null)
    }

    private suspend fun pushTaskUpdate(task: TaskEntity, syncedAt: Long) {
        val remoteId = task.remoteId ?: error("Missing remoteId for task ${task.localId}")
        remoteTasks!!.update(remoteId, task.toRemoteUpdate())
        taskDao.updateSyncState(task.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    private suspend fun pushTaskDelete(task: TaskEntity, syncedAt: Long) {
        task.remoteId?.let { remoteTasks!!.delete(it) }
        taskDao.updateSyncState(task.localId, SyncStatus.SYNCED, task.remoteId, syncedAt, null)
    }

    private suspend fun markTaskFailed(task: TaskEntity, error: Throwable) {
        taskDao.updateSyncState(
            task.localId, SyncStatus.FAILED, task.remoteId, task.lastSyncedAt, error.message,
        )
    }

    private suspend fun pullTasks(userId: String, warnings: MutableList<String>) {
        if (!tasksRemoteEnabled) return
        runCatching {
            val outcome = pullIncremental(
                userId = userId,
                entity = ENTITY_TASKS,
                fetchPage = { since -> remoteTasks!!.fetchUpdatedSince(since, PULL_PAGE_SIZE) },
                updatedAtIso = { it.updatedAt },
                remoteIdOf = { it.id },
            ) { remote ->
                val existing = taskDao.getByRemoteId(remote.id)
                when {
                    existing == null -> taskDao.upsert(remote.toLocalEntity())
                    existing.syncStatus != SyncStatus.SYNCED -> Unit
                    isoToEpoch(remote.updatedAt) > existing.updatedAt ->
                        taskDao.upsert(remote.toLocalEntity(existingLocalId = existing.localId))
                }
                true
            }

            if (outcome.isFullSync && outcome.pulledAnyRows) {
                val now = Instant.now().toEpochMilli()
                taskDao.getAllTasksForUser(userId)
                    .filter { it.remoteId != null && it.syncStatus == SyncStatus.SYNCED }
                    .filter { it.remoteId !in outcome.seenRemoteIds }
                    .forEach {
                        taskDao.upsert(
                            it.copy(isArchived = true, deletedAt = now, updatedAt = now, syncStatus = SyncStatus.SYNCED),
                        )
                    }
            }
        }.onFailure { error ->
            if (RemoteSyncErrors.isMissingRemoteTable(error, ENTITY_TASKS)) {
                suspendTasksRemoteSync(userId, warnings)
            } else {
                throw error
            }
        }
    }

    // ── Shifts ──────────────────────────────────────────────────────────────

    private suspend fun pushShifts(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (shift in shiftDao.getPendingSyncShifts(userId)) {
            runCatching {
                when {
                    shift.syncStatus == SyncStatus.SYNCED -> Unit
                    shift.deletedAt != null -> pushShiftDelete(shift, now)
                    shift.remoteId == null -> pushShiftCreate(shift, now)
                    else -> pushShiftUpdate(shift, now)
                }
            }.onFailure { markShiftFailed(shift, it) }
        }
    }

    private suspend fun pushShiftCreate(shift: ShiftEntity, syncedAt: Long) {
        val startTimeIso = epochToIso(shift.startTime)
        val existingRemote = remoteShifts!!.findByUserAndStartTime(shift.userId, startTimeIso)
        if (existingRemote != null) {
            shiftDao.updateSyncState(shift.localId, SyncStatus.SYNCED, existingRemote.id, syncedAt, null)
            return
        }

        runCatching {
            val remote = remoteShifts!!.insert(
                shift.toRemoteInsert(
                    compensationProfileRemoteId = idMapper.profileLocalToRemote(shift.compensationProfileId),
                    taskRemoteId = idMapper.taskLocalToRemote(shift.taskId),
                ),
            )
            shiftDao.updateSyncState(shift.localId, SyncStatus.SYNCED, remote.id, syncedAt, null)
        }.onFailure { error ->
            if (RemoteSyncErrors.isUniqueViolation(error)) {
                val linked = remoteShifts!!.findByUserAndStartTime(shift.userId, startTimeIso)
                    ?: throw error
                shiftDao.updateSyncState(shift.localId, SyncStatus.SYNCED, linked.id, syncedAt, null)
            } else {
                throw error
            }
        }
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
        val localActiveExists = shiftDao.getActiveShifts(userId).isNotEmpty()

        val outcome = pullIncremental(
            userId = userId,
            entity = ENTITY_SHIFTS,
            fetchPage = { since -> remoteShifts!!.fetchUpdatedSince(since, PULL_PAGE_SIZE) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
        ) { remote ->
            applyRemoteShift(userId, remote, localActiveExists)
        }

        if (outcome.isFullSync && outcome.pulledAnyRows) {
            val now = Instant.now().toEpochMilli()
            shiftDao.getAllShiftsForUser(userId)
                .filter { it.remoteId != null && it.syncStatus == SyncStatus.SYNCED }
                .filter { it.remoteId !in outcome.seenRemoteIds }
                .forEach { shiftDao.softDeleteShift(it.localId, now, SyncStatus.SYNCED, now) }
        }
    }

    private suspend fun applyRemoteShift(
        userId: String,
        remote: RemoteShiftRow,
        localActiveExists: Boolean,
    ): Boolean {
        val existing = shiftDao.getShiftByRemoteId(remote.id)
        if (existing != null) {
            if (existing.syncStatus != SyncStatus.SYNCED) return true
            val remoteNewer = isoToEpoch(remote.updatedAt) > existing.updatedAt
            if (remoteNewer || existing.deletedAt != null) {
                shiftDao.upsertShift(
                    remote.toLocalEntity(
                        existingLocalId = existing.localId,
                        compensationProfileLocalId = idMapper.profileRemoteToLocal(remote.compensationProfileId),
                        taskLocalId = idMapper.taskRemoteToLocal(remote.taskId),
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
            return true
        }

        val existingByStartTime = shiftDao.getShiftByStartTime(userId, isoToEpoch(remote.startTime))
        if (existingByStartTime != null) {
            when (existingByStartTime.syncStatus) {
                SyncStatus.SYNCED -> {
                    shiftDao.upsertShift(
                        remote.toLocalEntity(
                            existingLocalId = existingByStartTime.localId,
                            compensationProfileLocalId = idMapper.profileRemoteToLocal(remote.compensationProfileId),
                            taskLocalId = idMapper.taskRemoteToLocal(remote.taskId),
                            syncStatus = SyncStatus.SYNCED,
                        ),
                    )
                }
                SyncStatus.PENDING_CREATE, SyncStatus.FAILED -> {
                    shiftDao.updateSyncState(
                        existingByStartTime.localId,
                        SyncStatus.SYNCED,
                        remote.id,
                        isoToEpoch(remote.updatedAt),
                        null,
                    )
                }
                else -> Unit
            }
            return true
        }

        // Never materialize a second running shift; hold the cursor so this row is
        // pulled once the local active shift ends (or the remote one is clocked out).
        if (remote.endTime == null && localActiveExists) return false
        shiftDao.insertShift(
            remote.toLocalEntity(
                compensationProfileLocalId = idMapper.profileRemoteToLocal(remote.compensationProfileId),
                taskLocalId = idMapper.taskRemoteToLocal(remote.taskId),
            ),
        )
        return true
    }

    // ── Refund claims ─────────────────────────────────────────────────────────

    private suspend fun pushRefundClaims(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (claim in refundClaimDao.getPendingSyncClaims(userId)) {
            runCatching {
                when {
                    claim.syncStatus == SyncStatus.SYNCED -> Unit
                    claim.deletedAt != null -> pushRefundClaimDelete(claim, now)
                    claim.remoteId == null -> {
                        // Parent shift not pushed yet: leave the claim pending for a later sync.
                        val shiftRemoteId = idMapper.shiftLocalToRemote(claim.shiftLocalId)
                            ?: return@runCatching
                        pushRefundClaimCreate(claim, shiftRemoteId, now)
                    }
                    else -> pushRefundClaimUpdate(claim, now)
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
        val outcome = pullIncremental(
            userId = userId,
            entity = ENTITY_REFUND_CLAIMS,
            fetchPage = { since -> remoteRefundClaims!!.fetchUpdatedSince(since, PULL_PAGE_SIZE) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
        ) { remote ->
            // Parent shift not pulled yet (e.g. the shifts step failed this run):
            // hold the cursor so the claim is re-fetched once the shift exists.
            val shiftLocalId = idMapper.shiftRemoteToLocal(remote.shiftId)
                ?: return@pullIncremental false
            val existing = refundClaimDao.getClaimByRemoteId(remote.id)
            when {
                existing == null ->
                    refundClaimDao.insertClaim(remote.toLocalEntity(shiftLocalId = shiftLocalId))
                existing.syncStatus != SyncStatus.SYNCED -> Unit
                isoToEpoch(remote.updatedAt) > existing.updatedAt ->
                    refundClaimDao.upsertClaim(
                        remote.toLocalEntity(
                            shiftLocalId = shiftLocalId,
                            existingLocalId = existing.localId,
                        ),
                    )
            }
            true
        }

        if (outcome.isFullSync && outcome.pulledAnyRows) {
            val now = Instant.now().toEpochMilli()
            refundClaimDao.getAllClaimsForUser(userId)
                .filter { it.remoteId != null && it.syncStatus == SyncStatus.SYNCED }
                .filter { it.remoteId !in outcome.seenRemoteIds }
                .forEach { refundClaimDao.softDeleteClaim(it.localId, now, SyncStatus.SYNCED, now) }
        }
    }

    // ── Compensation profiles ─────────────────────────────────────────────────

    private suspend fun pushCompensationProfiles(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (profile in compensationProfileDao.getPendingSyncProfiles(userId)) {
            runCatching {
                when {
                    profile.syncStatus == SyncStatus.SYNCED -> Unit
                    profile.deletedAt != null -> pushCompensationProfileDelete(profile, now)
                    profile.remoteId == null -> pushCompensationProfileCreate(profile, now)
                    else -> pushCompensationProfileUpdate(profile, now)
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
        val outcome = pullIncremental(
            userId = userId,
            entity = ENTITY_COMPENSATION_PROFILES,
            fetchPage = { since -> remoteCompensationProfiles!!.fetchUpdatedSince(since, PULL_PAGE_SIZE) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
        ) { remote ->
            val existing = compensationProfileDao.getByRemoteId(remote.id)
            when {
                existing == null -> compensationProfileDao.insert(remote.toLocalEntity())
                existing.syncStatus != SyncStatus.SYNCED -> Unit
                isoToEpoch(remote.updatedAt) > existing.updatedAt ->
                    compensationProfileDao.upsert(
                        remote.toLocalEntity(existingLocalId = existing.localId),
                    )
            }
            true
        }

        if (outcome.isFullSync && outcome.pulledAnyRows) {
            val now = Instant.now().toEpochMilli()
            compensationProfileDao.getAllProfilesForUser(userId)
                .filter { it.remoteId != null && it.syncStatus == SyncStatus.SYNCED }
                .filter { it.remoteId !in outcome.seenRemoteIds }
                .forEach {
                    compensationProfileDao.upsert(
                        it.copy(isArchived = true, deletedAt = now, updatedAt = now, syncStatus = SyncStatus.SYNCED),
                    )
                }
        }
    }

    // ── User settings ─────────────────────────────────────────────────────────

    private suspend fun pushUserSettings(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (settings in settingsDao.getPendingSyncSettings(userId)) {
            runCatching {
                val profileRemoteId = idMapper.profileLocalToRemote(settings.defaultCompensationProfileId)
                when {
                    settings.syncStatus == SyncStatus.SYNCED -> Unit
                    settings.deletedAt != null -> pushUserSettingsDelete(settings, now)
                    settings.remoteId == null -> {
                        val remote = remoteSettings!!.upsert(settings.toRemoteUpsert(profileRemoteId))
                        settingsDao.updateSyncState(settings.localId, SyncStatus.SYNCED, remote.id, now, null)
                    }
                    else -> pushUserSettingsUpdate(settings, profileRemoteId, now)
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
        pullIncremental(
            userId = userId,
            entity = ENTITY_USER_SETTINGS,
            fetchPage = { since -> remoteSettings!!.fetchUpdatedSince(since, PULL_PAGE_SIZE) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
        ) { remote ->
            val profileLocalId = idMapper.profileRemoteToLocal(remote.defaultCompensationProfileId)
            val existing = settingsDao.getSettingsByRemoteId(remote.id)
                ?: settingsDao.getSettings(userId)
            when {
                existing == null ->
                    settingsDao.insertSettings(
                        remote.toLocalEntity(defaultCompensationProfileLocalId = profileLocalId),
                    )
                existing.syncStatus != SyncStatus.SYNCED -> Unit
                isoToEpoch(remote.updatedAt) > existing.updatedAt ->
                    settingsDao.upsertSettings(
                        remote.toLocalEntity(
                            existingLocalId = existing.localId,
                            defaultCompensationProfileLocalId = profileLocalId,
                        ),
                    )
            }
            true
        }
    }

    // ── User profiles (display name) ──────────────────────────────────────────

    private suspend fun pushProfiles(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (profile in profileDao.getPendingSyncProfiles(userId)) {
            runCatching {
                when (profile.syncStatus) {
                    SyncStatus.SYNCED -> Unit
                    SyncStatus.PENDING_DELETE -> Unit
                    else -> pushProfileUpdate(profile, now)
                }
            }.onFailure { markProfileFailed(profile, it) }
        }
    }

    private suspend fun pushProfileUpdate(profile: ProfileEntity, syncedAt: Long) {
        val remoteId = profile.remoteId ?: profile.userId
        val remote = remoteProfiles!!.update(remoteId, profile.toRemoteUpdate())
        profileDao.upsertProfile(
            remote.toLocalEntity(
                existingLocalId = profile.localId,
                syncStatus = SyncStatus.SYNCED,
            ).copy(lastSyncedAt = syncedAt),
        )
    }

    private suspend fun markProfileFailed(profile: ProfileEntity, error: Throwable) {
        profileDao.updateSyncState(
            profile.localId,
            SyncStatus.FAILED,
            profile.remoteId,
            profile.lastSyncedAt,
            error.message,
        )
    }

    private suspend fun pullProfiles(userId: String) {
        pullIncremental(
            userId = userId,
            entity = ENTITY_PROFILES,
            fetchPage = { since -> remoteProfiles!!.fetchUpdatedSince(since, PULL_PAGE_SIZE) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
        ) { remote ->
            val existing = profileDao.getProfile(userId)
            when {
                existing == null ->
                    profileDao.upsertProfile(remote.toLocalEntity())
                existing.syncStatus != SyncStatus.SYNCED -> Unit
                isoToEpoch(remote.updatedAt) > existing.updatedAt ->
                    profileDao.upsertProfile(
                        remote.toLocalEntity(existingLocalId = existing.localId),
                    )
            }
            true
        }
    }

    private companion object {
        const val PULL_PAGE_SIZE = 200
        const val ENTITY_TASKS = "tasks"
        const val ENTITY_SHIFTS = "shifts"
        const val ENTITY_REFUND_CLAIMS = "refund_claims"
        const val ENTITY_COMPENSATION_PROFILES = "compensation_profiles"
        const val ENTITY_USER_SETTINGS = "user_settings"
        const val ENTITY_PROFILES = "profiles"
        const val TASKS_TABLE_MISSING_WARNING =
            "Tasks sync paused because the Supabase tasks table is missing. " +
                "Apply supabase/migrations/20250628000000_tasks.sql, then sync again."
    }
}
