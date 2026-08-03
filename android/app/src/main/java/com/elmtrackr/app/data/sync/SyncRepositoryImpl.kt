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
import com.elmtrackr.app.data.local.dao.PremiumProfileDao
import com.elmtrackr.app.data.local.entity.PremiumProfileEntity
import com.elmtrackr.app.data.local.TransactionRunner
import com.elmtrackr.app.data.remote.RemoteCompensationProfileDataSource
import com.elmtrackr.app.data.remote.RemotePremiumProfileDataSource
import com.elmtrackr.app.data.remote.toLocalEntity
import com.elmtrackr.app.data.remote.toRemoteInsert
import com.elmtrackr.app.data.remote.toRemoteUpdate
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
import com.elmtrackr.app.monitoring.CrashReporting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val premiumProfileDao: PremiumProfileDao,
    private val receiptDao: com.elmtrackr.app.data.local.dao.ReceiptDao,
    private val projectDao: com.elmtrackr.app.data.local.dao.ProjectDao,
    private val projectBillingRecordDao: com.elmtrackr.app.data.local.dao.ProjectBillingRecordDao,
    private val projectPaymentDao: com.elmtrackr.app.data.local.dao.ProjectPaymentDao,
    private val taskDao: TaskDao,
    private val profileDao: ProfileDao,
    private val transactionRunner: TransactionRunner,
    private val syncCursorStore: SyncCursorStore,
    private val remoteTasks: RemoteTaskDataSource?,
    private val remoteShifts: RemoteShiftDataSource?,
    private val remoteRefundClaims: RemoteRefundClaimDataSource?,
    private val remoteSettings: RemoteUserSettingsDataSource?,
    private val remoteCompensationProfiles: RemoteCompensationProfileDataSource?,
    private val remotePremiumProfiles: RemotePremiumProfileDataSource?,
    private val remoteProfiles: RemoteProfileDataSource?,
) : SyncRepository {

    // The push/pull methods below are only reachable through syncAll(), which
    // returns NotConfigured before the pipeline runs unless every remote data
    // source exists. These accessors encode that invariant once instead of
    // scattering raw !! assertions through the pipeline.
    private val tasksRemote get() = checkNotNull(remoteTasks) { REMOTES_NOT_CONFIGURED }
    private val shiftsRemote get() = checkNotNull(remoteShifts) { REMOTES_NOT_CONFIGURED }
    private val claimsRemote get() = checkNotNull(remoteRefundClaims) { REMOTES_NOT_CONFIGURED }
    private val settingsRemote get() = checkNotNull(remoteSettings) { REMOTES_NOT_CONFIGURED }
    private val compensationRemote get() = checkNotNull(remoteCompensationProfiles) { REMOTES_NOT_CONFIGURED }
    private val premiumRemote get() = checkNotNull(remotePremiumProfiles) { REMOTES_NOT_CONFIGURED }
    private val profilesRemote get() = checkNotNull(remoteProfiles) { REMOTES_NOT_CONFIGURED }

    private val idMapper = SyncIdMapper(shiftDao, compensationProfileDao, premiumProfileDao, taskDao)
    private val lastSyncStatus = MutableStateFlow<String?>(null)
    // syncAll can be invoked concurrently (WorkManager, auth bootstrap, manual retry);
    // without serialization two runs can push the same PENDING_CREATE row twice.
    private val syncMutex = Mutex()
    private var tasksRemoteEnabled = true

    /**
     * Rows the server returned that belong to a different user, counted per run.
     *
     * Should always be zero. It is reported rather than only guarded against
     * because a non-zero count means RLS is not doing its job or the app and the
     * session disagree about who is signed in — neither is something to discover
     * from a user's bug report. Guarded by [syncMutex] like the rest of the
     * pipeline state.
     */
    private var foreignRowsThisRun = 0

    private data class PendingSyncSnapshot(
        val shifts: List<ShiftEntity>,
        val claims: List<RefundClaimEntity>,
        val settings: List<UserSettingsEntity>,
        val profiles: List<CompensationProfileEntity>,
        val premiumProfiles: List<PremiumProfileEntity>,
        val tasks: List<TaskEntity>,
        val userProfiles: List<ProfileEntity>,
    ) {
        val pendingCount: Int
            get() = shifts.size + claims.size + settings.size + profiles.size + premiumProfiles.size +
                tasks.size +
                userProfiles.size

        val failedCount: Int
            get() = shifts.count { it.syncStatus == SyncStatus.FAILED } +
                claims.count { it.syncStatus == SyncStatus.FAILED } +
                settings.count { it.syncStatus == SyncStatus.FAILED } +
                profiles.count { it.syncStatus == SyncStatus.FAILED } +
                premiumProfiles.count { it.syncStatus == SyncStatus.FAILED } +
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
                combine(
                    premiumProfileDao.observePendingSyncProfiles(userId),
                    taskDao.observePendingSyncTasks(userId),
                ) { premiumProfiles, tasks -> premiumProfiles to tasks },
            ) { shifts, claims, settings, profiles, premiumAndTasks ->
                val (premiumProfiles, tasks) = premiumAndTasks
                PendingSyncSnapshot(
                    shifts = shifts,
                    claims = claims,
                    settings = settings,
                    profiles = profiles,
                    premiumProfiles = premiumProfiles,
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

    // Both backup paths declare their own dispatcher rather than trusting the
    // caller: the ViewModel invokes them from viewModelScope (Main.immediate),
    // and only the *file* read was wrapped there. Serialising the entire user
    // database into one pretty-printed string, and parsing it back, were both
    // running on the UI thread.
    override suspend fun exportLocalBackup(userId: String): String = withContext(Dispatchers.IO) {
        LocalBackupExporter.export(
            userId = userId,
            taskDao = taskDao,
            shiftDao = shiftDao,
            refundClaimDao = refundClaimDao,
            settingsDao = settingsDao,
            compensationProfileDao = compensationProfileDao,
            premiumProfileDao = premiumProfileDao,
            receiptDao = receiptDao,
            projectDao = projectDao,
            projectBillingRecordDao = projectBillingRecordDao,
            projectPaymentDao = projectPaymentDao,
            appVersion = com.elmtrackr.app.BuildConfig.VERSION_NAME,
        )
    }

    /**
     * One transaction for the whole import.
     *
     * The importer writes eleven tables in sequence with cross-table references —
     * shifts point at tasks and compensation profiles, billing records point at
     * projects. Without a transaction, a failure part-way through (a malformed
     * row late in the file, a constraint violation, the process being killed)
     * left the database holding half a backup: shifts with dangling profile ids
     * and a summary the user never saw. LocalUserDataCleaner and
     * LegacyDataAdopter already work this way; this was the outlier.
     */
    override suspend fun importLocalBackup(userId: String, json: String): BackupImportSummary = withContext(Dispatchers.IO) {
        transactionRunner.inTransaction {
            LocalBackupImporter.import(
                rawJson = json,
                currentUserId = userId,
                taskDao = taskDao,
                shiftDao = shiftDao,
                refundClaimDao = refundClaimDao,
                settingsDao = settingsDao,
                compensationProfileDao = compensationProfileDao,
                premiumProfileDao = premiumProfileDao,
                receiptDao = receiptDao,
                projectDao = projectDao,
                projectBillingRecordDao = projectBillingRecordDao,
                projectPaymentDao = projectPaymentDao,
            )
        }
    }

    /**
     * Reads the pending set once, reusing the same suspend getters the push
     * phase uses.
     */
    private suspend fun pendingSnapshot(userId: String): PendingSyncSnapshot =
        PendingSyncSnapshot(
            shifts = shiftDao.getPendingSyncShifts(userId),
            claims = refundClaimDao.getPendingSyncClaims(userId),
            settings = settingsDao.getPendingSyncSettings(userId),
            profiles = compensationProfileDao.getPendingSyncProfiles(userId),
            premiumProfiles = premiumProfileDao.getPendingSyncProfiles(userId),
            tasks = taskDao.getPendingSyncTasks(userId),
            userProfiles = profileDao.getPendingSyncProfiles(userId),
        )

    /**
     * Whether a follow-up sync could plausibly make progress.
     *
     * [hasPendingWork] counts `FAILED` too, which is correct for a "you have
     * unsynced changes" badge and wrong as a trigger to sync again: a row that
     * can never succeed — a duplicate refund claim, a server-side constraint,
     * a missing table — kept `hasPendingWork` true forever, and SyncWorker
     * responded by enqueueing another immediate run. That is an unbounded loop
     * of network calls and Room writes, and MAX_RETRY_ATTEMPTS does not bound it
     * because every follow-up is a fresh request with runAttemptCount == 0.
     *
     * Only rows that are pending for a reason other than a recorded failure
     * justify an immediate retry. `FAILED` rows are picked up by the 15-minute
     * periodic sync instead, which is the right cadence for something that needs
     * a server-side or user-side change first.
     */
    override suspend fun hasRetryablePendingWork(userId: String): Boolean {
        val snapshot = pendingSnapshot(userId)
        return snapshot.pendingCount > snapshot.failedCount
    }

    override suspend fun hasPendingWork(userId: String): Boolean =
        shiftDao.hasPendingSyncShifts(userId) ||
            refundClaimDao.hasPendingSyncClaims(userId) ||
            settingsDao.hasPendingSyncSettings(userId) ||
            compensationProfileDao.hasPendingSyncProfiles(userId) ||
            premiumProfileDao.hasPendingSyncProfiles(userId) ||
            taskDao.hasPendingSyncTasks(userId) ||
            // Paid Projects tables are local-only until the Supabase contract
            // carries them, so their pending rows are deliberately not counted
            // here — reporting unsyncable work would show a permanent
            // "N changes waiting" badge the user can never clear.
            profileDao.hasPendingSyncProfiles(userId)

    override suspend fun syncAll(userId: String): SyncResult {
        if (remoteTasks == null || remoteShifts == null || remoteRefundClaims == null ||
            remoteSettings == null || remoteCompensationProfiles == null ||
            remotePremiumProfiles == null || remoteProfiles == null
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

    private class PipelineIssues {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var authExpired = false
            private set

        fun recordFailure(label: String, error: Throwable) {
            errors += "$label: ${error.message ?: "unknown error"}"
            if (RemoteSyncErrors.isAuthExpired(error)) authExpired = true
        }
    }

    private suspend fun runSyncPipeline(userId: String): SyncResult {
        return runCatching {
            val issues = PipelineIssues()
            val warnings = issues.warnings
            foreignRowsThisRun = 0

            runSyncStep("reconcile", issues) { reconcileNeverSynced(userId) }
            runSyncStep("push tasks", issues) { pushTasks(userId, warnings) }
            runSyncStep("push compensation profiles", issues) { pushCompensationProfiles(userId) }
            runSyncStep("push premium profiles", issues) { pushPremiumProfiles(userId) }
            runSyncStep("push shifts", issues) { pushShifts(userId) }
            runSyncStep("push refund claims", issues) { pushRefundClaims(userId) }
            runSyncStep("push user settings", issues) { pushUserSettings(userId) }
            runSyncStep("push profiles", issues) { pushProfiles(userId) }
            runSyncStep("pull profiles", issues) { pullProfiles(userId) }
            runSyncStep("pull compensation profiles", issues) { pullCompensationProfiles(userId) }
            runSyncStep("pull premium profiles", issues) { pullPremiumProfiles(userId) }
            runSyncStep("pull user settings", issues) { pullUserSettings(userId) }
            // Tasks must land before shifts: applyRemoteShift resolves each
            // shift's task_id against the local tasks table.
            runSyncStep("pull tasks", issues) { pullTasks(userId, warnings) }
            runSyncStep("pull shifts", issues) { pullShifts(userId) }
            runSyncStep("pull refund claims", issues) { pullRefundClaims(userId) }

            reportForeignRows()

            when {
                issues.authExpired -> {
                    lastSyncStatus.value = AUTH_EXPIRED_STATUS
                    SyncResult.AuthExpired
                }
                issues.errors.isNotEmpty() -> {
                    lastSyncStatus.value = "Failed: ${issues.errors.joinToString("; ")}"
                    SyncResult.Error(issues.errors.joinToString("; "))
                }
                warnings.isNotEmpty() -> {
                    // A machine marker, not a sentence. The old value began
                    // with "Synced ", so SyncStatusText matched it as a
                    // timestamp, failed to parse and rendered the raw English.
                    // Keep this in step with SyncStatusText's prefixes.
                    lastSyncStatus.value = "SyncedWarn: ${warnings.joinToString("; ")}"
                    SyncResult.Success
                }
                else -> {
                    // Per-row push failures are recorded on the row, not on the
                    // step, so they never reach `issues` — which meant a sync
                    // where every pending row failed still reported a clean
                    // "Synced <time>". Check the rows themselves before saying so.
                    val failed = pendingSnapshot(userId).failedCount
                    lastSyncStatus.value = if (failed > 0) {
                        "$UNSENT_STATUS_PREFIX $failed"
                    } else {
                        "Synced ${Instant.now()}"
                    }
                    SyncResult.Success
                }
            }
        }.getOrElse { error ->
            if (RemoteSyncErrors.isAuthExpired(error)) {
                lastSyncStatus.value = AUTH_EXPIRED_STATUS
                SyncResult.AuthExpired
            } else {
                lastSyncStatus.value = "Failed: ${error.message ?: "unknown error"}"
                SyncResult.Error(error.message ?: "Sync failed")
            }
        }
    }

    private suspend fun runSyncStep(
        label: String,
        issues: PipelineIssues,
        block: suspend () -> Unit,
    ) {
        runCatching { block() }.onFailure { error ->
            issues.recordFailure(label, error)
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

    /**
     * Surfaces an ownership mismatch to crash reporting without surfacing it to
     * the user: nothing about it is actionable from the app, and the local data
     * is intact because the rows were skipped. Deliberately not a warning on the
     * sync status for that reason.
     */
    private fun reportForeignRows() {
        val count = foreignRowsThisRun
        if (count == 0) return
        CrashReporting.report(
            IllegalStateException(
                "Sync pull received $count row(s) belonging to another user; " +
                    "rows were skipped. Check RLS policies and the signed-in session.",
            ),
        )
    }

    private data class PullOutcome(
        val seenRemoteIds: Set<String>,
        val isFullSync: Boolean,
        /** False when pagination bailed out early, so [seenRemoteIds] is incomplete. */
        val drainedFully: Boolean = true,
    ) {
        val pulledAnyRows: Boolean get() = seenRemoteIds.isNotEmpty()

        /** Tombstoning against a partial server view would delete valid local rows. */
        val safeToTombstone: Boolean get() = isFullSync && pulledAnyRows && drainedFully
    }

    /**
     * Fetches remote rows updated since the stored cursor, one page at a time. Each
     * page is applied via [applyRow] before the cursor advances, so a failure or
     * process death mid-pull re-fetches those rows instead of skipping them forever.
     * [applyRow] returns false when a row cannot be applied yet (e.g. its local
     * parent row is missing); the cursor is then held at that row's updated_at so a
     * later sync re-fetches it (the remote query uses gte).
     *
     * [ownerOf] is a second layer of defence behind RLS. None of the remote
     * queries filter by user — they rely entirely on the policies to scope the
     * result set — and [SyncWorker] resolves "who am I" from a stored preference
     * while PostgREST resolves it from the session JWT, so the two can disagree
     * (a sign-out that loses the preference write, an account switch mid-sync).
     * A row that does not belong to [userId] is skipped rather than written into
     * this user's Room database.
     */
    private suspend fun <Row> pullIncremental(
        userId: String,
        entity: String,
        fetchPage: suspend (sinceIso: String?) -> List<Row>,
        updatedAtIso: (Row) -> String,
        remoteIdOf: (Row) -> String,
        ownerOf: (Row) -> String,
        applyRow: suspend (Row) -> Boolean,
    ): PullOutcome {
        val initialCursor = syncCursorStore.lastPulledAt(userId, entity)
        val isFullSync = initialCursor == null
        var cursor = initialCursor
        var holdEpoch: Long? = null
        var drainedFully = true
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
                // The cursor still advances past a foreign row and its id still
                // counts as seen: holding the cursor would stall the pull on a row
                // that will never be applicable, and excluding it from
                // seenRemoteIds would let the tombstone pass delete a local row
                // that happened to share the id. Keeping local data beats
                // deleting it, here as elsewhere in this pipeline.
                if (ownerOf(row) != userId) {
                    foreignRowsThisRun++
                    maxEpoch = maxOf(maxEpoch, rowEpoch)
                    continue
                }
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
            // out instead of looping forever. The server view is incomplete at
            // this point, so downstream tombstoning must be skipped.
            if (cursor == previousCursor) {
                drainedFully = false
                break
            }
        }

        return PullOutcome(seenRemoteIds = seenRemoteIds, isFullSync = isFullSync, drainedFully = drainedFully)
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
        val remoteId = try {
            tasksRemote.insert(task.toRemoteInsert()).id
        } catch (e: Exception) {
            // The insert carries the client-generated id; a retry after a lost
            // response collides with the row it already created — adopt it.
            if (RemoteSyncErrors.isPrimaryKeyViolation(e, ENTITY_TASKS)) task.localId else throw e
        }
        markTaskSynced(task, remoteId, syncedAt)
    }

    private suspend fun pushTaskUpdate(task: TaskEntity, syncedAt: Long) {
        val remoteId = task.remoteId ?: error("Missing remoteId for task ${task.localId}")
        tasksRemote.update(remoteId, task.toRemoteUpdate())
        markTaskSynced(task, remoteId, syncedAt)
    }

    // A row edited while its push was in flight must stay pending — flipping it
    // to SYNCED would silently drop the newer edit (it would never reach other
    // devices). The remoteId is still recorded so a pending create retries as
    // an update instead of inserting a duplicate.
    private suspend fun markTaskSynced(task: TaskEntity, remoteId: String?, syncedAt: Long) {
        val updated = taskDao.markSyncedIfUnchanged(task.localId, remoteId, syncedAt, task.updatedAt)
        if (updated == 0) taskDao.attachRemoteId(task.localId, remoteId, syncedAt)
    }

    private suspend fun pushTaskDelete(task: TaskEntity, syncedAt: Long) {
        task.remoteId?.let { tasksRemote.delete(it) }
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
                fetchPage = { since -> tasksRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE) },
                updatedAtIso = { it.updatedAt },
                remoteIdOf = { it.id },
                ownerOf = { it.userId },
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

            if (outcome.safeToTombstone) {
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
        val existingRemote = shiftsRemote.findByUserAndStartTime(shift.userId, startTimeIso)
        if (existingRemote != null) {
            markShiftSynced(shift, existingRemote.id, syncedAt)
            return
        }

        runCatching {
            val remote = shiftsRemote.insert(
                shift.toRemoteInsert(
                    compensationProfileRemoteId = idMapper.profileLocalToRemote(shift.compensationProfileId),
                    premiumProfileRemoteId = idMapper.premiumProfileLocalToRemote(shift.premiumProfileId),
                    taskRemoteId = idMapper.taskLocalToRemote(shift.taskId),
                ),
            )
            markShiftSynced(shift, remote.id, syncedAt)
        }.onFailure { error ->
            if (RemoteSyncErrors.isUniqueViolation(error)) {
                val linked = shiftsRemote.findByUserAndStartTime(shift.userId, startTimeIso)
                    ?: throw error
                markShiftSynced(shift, linked.id, syncedAt)
            } else {
                throw error
            }
        }
    }

    private suspend fun pushShiftUpdate(shift: ShiftEntity, syncedAt: Long) {
        val remoteId = shift.remoteId ?: error("Missing remoteId for shift ${shift.localId}")
        shiftsRemote.update(
            remoteId,
            shift.toRemoteUpdate(
                compensationProfileRemoteId = idMapper.profileLocalToRemote(shift.compensationProfileId),
                premiumProfileRemoteId = idMapper.premiumProfileLocalToRemote(shift.premiumProfileId),
                taskRemoteId = idMapper.taskLocalToRemote(shift.taskId),
            ),
        )
        markShiftSynced(shift, remoteId, syncedAt)
    }

    // A shift edited while its push was in flight (clock-out, note change, …)
    // must stay pending — flipping it to SYNCED would silently drop the newer
    // edit and it would never reach other devices. The remoteId is still
    // recorded so a pending create retries as an update, not a duplicate insert.
    private suspend fun markShiftSynced(shift: ShiftEntity, remoteId: String?, syncedAt: Long) {
        val updated = shiftDao.markSyncedIfUnchanged(shift.localId, remoteId, syncedAt, shift.updatedAt)
        if (updated == 0) shiftDao.attachRemoteId(shift.localId, remoteId, syncedAt)
    }

    private suspend fun pushShiftDelete(shift: ShiftEntity, syncedAt: Long) {
        shift.remoteId?.let { shiftsRemote.delete(it) }
        shiftDao.updateSyncState(shift.localId, SyncStatus.SYNCED, shift.remoteId, syncedAt, null)
    }

    private suspend fun markShiftFailed(shift: ShiftEntity, error: Throwable) {
        shiftDao.updateSyncState(
            shift.localId, SyncStatus.FAILED, shift.remoteId, shift.lastSyncedAt, error.message,
        )
    }

    private suspend fun pullShifts(userId: String) {
        val outcome = pullIncremental(
            userId = userId,
            entity = ENTITY_SHIFTS,
            fetchPage = { since -> shiftsRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
            ownerOf = { it.userId },
        ) { remote ->
            applyRemoteShift(userId, remote)
        }

        if (outcome.safeToTombstone) {
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
    ): Boolean {
        val existing = shiftDao.getShiftByRemoteId(remote.id)
        if (existing != null) {
            if (existing.syncStatus != SyncStatus.SYNCED) return true
            val remoteNewer = isoToEpoch(remote.updatedAt) > existing.updatedAt
            // Heal links dropped by the old pull order, which materialised
            // shifts before their tasks existed locally.
            if (!remoteNewer && existing.deletedAt == null && existing.taskId == null && remote.taskId != null) {
                idMapper.taskRemoteToLocal(remote.taskId)?.let { taskLocalId ->
                    shiftDao.upsertShift(existing.copy(taskId = taskLocalId))
                }
            }
            if (remoteNewer || existing.deletedAt != null) {
                shiftDao.upsertShift(
                    remote.toLocalEntity(
                        existingLocalId = existing.localId,
                        compensationProfileLocalId = idMapper.profileRemoteToLocal(remote.compensationProfileId),
                        premiumProfileLocalId = idMapper.premiumProfileRemoteToLocal(remote.premiumProfileId),
                        taskLocalId = idMapper.taskRemoteToLocal(remote.taskId),
                        syncStatus = SyncStatus.SYNCED,
                        // The project link and compensation source live only
                        // locally; a pull must not erase them.
                        preserveLocal = existing,
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
                            premiumProfileLocalId = idMapper.premiumProfileRemoteToLocal(remote.premiumProfileId),
                            taskLocalId = idMapper.taskRemoteToLocal(remote.taskId),
                            syncStatus = SyncStatus.SYNCED,
                            preserveLocal = existingByStartTime,
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
        //
        // Read here rather than once before the pull. A snapshot taken up front is
        // false for every row in the batch, so two open remote shifts — two
        // devices that both clocked in — were both materialised and the user ended
        // up with two running shifts. Only reached for open-ended rows that matched
        // nothing locally, so the extra query is rare.
        if (remote.endTime == null && shiftDao.getActiveShifts(userId).isNotEmpty()) return false
        shiftDao.insertShift(
            remote.toLocalEntity(
                compensationProfileLocalId = idMapper.profileRemoteToLocal(remote.compensationProfileId),
                premiumProfileLocalId = idMapper.premiumProfileRemoteToLocal(remote.premiumProfileId),
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
        val remoteId = try {
            claimsRemote.insert(claim.toRemoteInsert(shiftRemoteId)).id
        } catch (e: Exception) {
            // Primary-key only: a shift may hold several rides per direction, and
            // adopting the local id on any 23505 silently dropped the second one.
            if (RemoteSyncErrors.isPrimaryKeyViolation(e, ENTITY_REFUND_CLAIMS)) claim.localId else throw e
        }
        markRefundClaimSynced(claim, remoteId, syncedAt)
    }

    private suspend fun pushRefundClaimUpdate(claim: RefundClaimEntity, syncedAt: Long) {
        val remoteId = claim.remoteId ?: error("Missing remoteId for claim ${claim.localId}")
        claimsRemote.update(remoteId, claim.toRemoteUpdate())
        markRefundClaimSynced(claim, remoteId, syncedAt)
    }

    // See markShiftSynced: a mid-push edit must keep the row pending.
    private suspend fun markRefundClaimSynced(claim: RefundClaimEntity, remoteId: String?, syncedAt: Long) {
        val updated = refundClaimDao.markSyncedIfUnchanged(claim.localId, remoteId, syncedAt, claim.updatedAt)
        if (updated == 0) refundClaimDao.attachRemoteId(claim.localId, remoteId, syncedAt)
    }

    private suspend fun pushRefundClaimDelete(claim: RefundClaimEntity, syncedAt: Long) {
        claim.remoteId?.let { claimsRemote.delete(it) }
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
            fetchPage = { since -> claimsRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
            ownerOf = { it.userId },
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

        if (outcome.safeToTombstone) {
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
        val remoteId = try {
            compensationRemote.insert(profile.toRemoteInsert()).id
        } catch (e: Exception) {
            // The insert carries the client-generated id, so a retry after a lost
            // response collides with the row it already created — adopt that row
            // instead of inserting the profile again.
            if (RemoteSyncErrors.isPrimaryKeyViolation(e, ENTITY_COMPENSATION_PROFILES)) {
                profile.localId
            } else {
                throw e
            }
        }
        markCompensationProfileSynced(profile, remoteId, syncedAt)
    }

    private suspend fun pushCompensationProfileUpdate(profile: CompensationProfileEntity, syncedAt: Long) {
        val remoteId = profile.remoteId ?: error("Missing remoteId for profile ${profile.localId}")
        compensationRemote.update(remoteId, profile.toRemoteUpdate())
        markCompensationProfileSynced(profile, remoteId, syncedAt)
    }

    // See markShiftSynced: a mid-push edit must keep the row pending.
    private suspend fun markCompensationProfileSynced(
        profile: CompensationProfileEntity,
        remoteId: String?,
        syncedAt: Long,
    ) {
        val updated = compensationProfileDao.markSyncedIfUnchanged(
            profile.localId, remoteId, syncedAt, profile.updatedAt,
        )
        if (updated == 0) compensationProfileDao.attachRemoteId(profile.localId, remoteId, syncedAt)
    }

    private suspend fun pushCompensationProfileDelete(profile: CompensationProfileEntity, syncedAt: Long) {
        profile.remoteId?.let { compensationRemote.delete(it) }
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
            fetchPage = { since -> compensationRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
            ownerOf = { it.userId },
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

        if (outcome.safeToTombstone) {
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

    // ── Premium profiles ────────────────────────────────────────────────────

    private suspend fun pushPremiumProfiles(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (profile in premiumProfileDao.getPendingSyncProfiles(userId)) {
            runCatching {
                when {
                    profile.syncStatus == SyncStatus.SYNCED -> Unit
                    profile.deletedAt != null -> pushPremiumProfileDelete(profile, now)
                    profile.remoteId == null -> pushPremiumProfileCreate(profile, now)
                    else -> pushPremiumProfileUpdate(profile, now)
                }
            }.onFailure { markPremiumProfileFailed(profile, it) }
        }
    }

    private suspend fun pushPremiumProfileCreate(profile: PremiumProfileEntity, syncedAt: Long) {
        val remoteId = try {
            premiumRemote.insert(profile.toRemoteInsert()).id
        } catch (e: Exception) {
            if (RemoteSyncErrors.isPrimaryKeyViolation(e, ENTITY_PREMIUM_PROFILES)) {
                profile.localId
            } else {
                throw e
            }
        }
        markPremiumProfileSynced(profile, remoteId, syncedAt)
    }

    private suspend fun pushPremiumProfileUpdate(profile: PremiumProfileEntity, syncedAt: Long) {
        val remoteId = profile.remoteId ?: error("Missing remoteId for premium profile ${profile.localId}")
        premiumRemote.update(remoteId, profile.toRemoteUpdate())
        markPremiumProfileSynced(profile, remoteId, syncedAt)
    }

    // See markShiftSynced: a mid-push edit must keep the row pending.
    private suspend fun markPremiumProfileSynced(
        profile: PremiumProfileEntity,
        remoteId: String?,
        syncedAt: Long,
    ) {
        val updated = premiumProfileDao.markSyncedIfUnchanged(
            profile.localId, remoteId, syncedAt, profile.updatedAt,
        )
        if (updated == 0) premiumProfileDao.attachRemoteId(profile.localId, remoteId, syncedAt)
    }

    private suspend fun pushPremiumProfileDelete(profile: PremiumProfileEntity, syncedAt: Long) {
        profile.remoteId?.let { premiumRemote.delete(it) }
        premiumProfileDao.updateSyncState(
            profile.localId, SyncStatus.SYNCED, profile.remoteId, syncedAt, null,
        )
    }

    private suspend fun markPremiumProfileFailed(profile: PremiumProfileEntity, error: Throwable) {
        premiumProfileDao.updateSyncState(
            profile.localId, SyncStatus.FAILED, profile.remoteId, profile.lastSyncedAt, error.message,
        )
    }

    private suspend fun pullPremiumProfiles(userId: String) {
        val outcome = pullIncremental(
            userId = userId,
            entity = ENTITY_PREMIUM_PROFILES,
            fetchPage = { since -> premiumRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
            ownerOf = { it.userId },
        ) { remote ->
            val existing = premiumProfileDao.getByRemoteId(remote.id)
            when {
                existing == null -> premiumProfileDao.insert(remote.toLocalEntity())
                existing.syncStatus != SyncStatus.SYNCED -> Unit
                isoToEpoch(remote.updatedAt) > existing.updatedAt ->
                    premiumProfileDao.upsert(
                        remote.toLocalEntity(existingLocalId = existing.localId),
                    )
            }
            true
        }

        if (outcome.safeToTombstone) {
            val now = Instant.now().toEpochMilli()
            premiumProfileDao.getAllProfilesForUser(userId)
                .filter { it.remoteId != null && it.syncStatus == SyncStatus.SYNCED }
                .filter { it.remoteId !in outcome.seenRemoteIds }
                .forEach {
                    premiumProfileDao.upsert(
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
                        val remote = settingsRemote.upsert(settings.toRemoteUpsert(profileRemoteId))
                        markUserSettingsSynced(settings, remote.id, now)
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
        settingsRemote.update(remoteId, settings.toRemoteUpdate(profileRemoteId))
        markUserSettingsSynced(settings, remoteId, syncedAt)
    }

    // See markShiftSynced: a mid-push edit must keep the row pending.
    private suspend fun markUserSettingsSynced(
        settings: UserSettingsEntity,
        remoteId: String?,
        syncedAt: Long,
    ) {
        val updated = settingsDao.markSyncedIfUnchanged(
            settings.localId, remoteId, syncedAt, settings.updatedAt,
        )
        if (updated == 0) settingsDao.attachRemoteId(settings.localId, remoteId, syncedAt)
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
            fetchPage = { since -> settingsRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
            ownerOf = { it.userId },
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
                            // Paid Projects defaults are local-only until the
                            // Supabase contract carries them; keep them.
                            preserveLocal = existing,
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
        val remote = profilesRemote.update(remoteId, profile.toRemoteUpdate())
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
            fetchPage = { since -> profilesRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
            // profiles.id IS the auth uid — there is no separate user_id column.
            ownerOf = { it.id },
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
        const val ENTITY_PREMIUM_PROFILES = "premium_profiles"
        const val ENTITY_USER_SETTINGS = "user_settings"
        const val ENTITY_PROFILES = "profiles"
        const val TASKS_TABLE_MISSING_WARNING =
            "Tasks sync paused because the Supabase tasks table is missing. " +
                "Apply supabase/migrations/20250628000000_tasks.sql, then sync again."
        const val AUTH_EXPIRED_STATUS =
            "Session expired — sign in again to resume syncing. Your data is safe on this device."

        /**
         * Machine marker followed by a count, not display text — do not
         * translate. SyncStatusText turns it into a localized sentence; the
         * count is passed rather than formatted here so Hebrew users do not get
         * an English status line. Keep in step with SyncStatusText.UNSENT_PREFIX.
         */
        const val UNSENT_STATUS_PREFIX = "SyncedUnsent:"
        const val REMOTES_NOT_CONFIGURED =
            "Sync pipeline invoked without configured remote data sources; syncAll must gate on configuration first."
    }
}
