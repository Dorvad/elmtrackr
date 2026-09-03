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
import com.elmtrackr.app.data.local.entity.ProjectBillingRecordEntity
import com.elmtrackr.app.data.local.entity.ProjectEntity
import com.elmtrackr.app.data.local.entity.ProjectPaymentEntity
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
import com.elmtrackr.app.domain.model.RefundDirection
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
    private val refundReceiptStorage: com.elmtrackr.app.domain.repository.RefundReceiptStorage?,
    private val receiptFileReader: com.elmtrackr.app.domain.repository.ReceiptFileReader?,
    private val remoteProjects: com.elmtrackr.app.data.remote.RemoteProjectDataSource?,
    private val remoteBillingRecords: com.elmtrackr.app.data.remote.RemoteProjectBillingRecordDataSource?,
    private val remoteProjectPayments: com.elmtrackr.app.data.remote.RemoteProjectPaymentDataSource?,
    private val workplaceDao: com.elmtrackr.app.data.local.dao.WorkplaceDao,
    private val leavePolicyDao: com.elmtrackr.app.data.local.dao.LeavePolicyDao,
    private val absenceEventDao: com.elmtrackr.app.data.local.dao.AbsenceEventDao,
    private val absenceAllocationDao: com.elmtrackr.app.data.local.dao.AbsenceAllocationDao,
    private val leaveBalanceSnapshotDao: com.elmtrackr.app.data.local.dao.LeaveBalanceSnapshotDao,
    private val remoteWorkplaces: com.elmtrackr.app.data.remote.RemoteWorkplaceDataSource?,
    private val remoteLeavePolicies: com.elmtrackr.app.data.remote.RemoteLeavePolicyDataSource?,
    private val remoteAbsenceEvents: com.elmtrackr.app.data.remote.RemoteAbsenceEventDataSource?,
    private val remoteAbsenceAllocations: com.elmtrackr.app.data.remote.RemoteAbsenceAllocationDataSource?,
    private val remoteLeaveBalances: com.elmtrackr.app.data.remote.RemoteLeaveBalanceSnapshotDataSource?,
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
    private val projectsRemote get() = checkNotNull(remoteProjects) { REMOTES_NOT_CONFIGURED }
    private val billingRemote get() = checkNotNull(remoteBillingRecords) { REMOTES_NOT_CONFIGURED }
    private val paymentsRemote get() = checkNotNull(remoteProjectPayments) { REMOTES_NOT_CONFIGURED }
    private val workplacesRemote get() = checkNotNull(remoteWorkplaces) { REMOTES_NOT_CONFIGURED }
    private val policiesRemote get() = checkNotNull(remoteLeavePolicies) { REMOTES_NOT_CONFIGURED }
    private val absenceEventsRemote get() = checkNotNull(remoteAbsenceEvents) { REMOTES_NOT_CONFIGURED }
    private val allocationsRemote get() = checkNotNull(remoteAbsenceAllocations) { REMOTES_NOT_CONFIGURED }
    private val balancesRemote get() = checkNotNull(remoteLeaveBalances) { REMOTES_NOT_CONFIGURED }

    private val idMapper = SyncIdMapper(
        shiftDao,
        compensationProfileDao,
        premiumProfileDao,
        taskDao,
        projectDao,
        projectBillingRecordDao,
        workplaceDao,
        absenceEventDao,
    )
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
            workplaceDao = workplaceDao,
            leavePolicyDao = leavePolicyDao,
            absenceEventDao = absenceEventDao,
            absenceAllocationDao = absenceAllocationDao,
            leaveBalanceSnapshotDao = leaveBalanceSnapshotDao,
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
                workplaceDao = workplaceDao,
                leavePolicyDao = leavePolicyDao,
                absenceEventDao = absenceEventDao,
                absenceAllocationDao = absenceAllocationDao,
                leaveBalanceSnapshotDao = leaveBalanceSnapshotDao,
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
            projectDao.hasPendingSyncProjects(userId) ||
            projectBillingRecordDao.hasPendingSyncRecords(userId) ||
            projectPaymentDao.hasPendingSyncPayments(userId) ||
            profileDao.hasPendingSyncProfiles(userId) ||
            workplaceDao.hasPendingSyncWorkplaces(userId) ||
            leavePolicyDao.hasPendingSyncPolicies(userId) ||
            absenceEventDao.hasPendingSyncEvents(userId) ||
            absenceAllocationDao.hasPendingSyncAllocations(userId) ||
            leaveBalanceSnapshotDao.hasPendingSyncSnapshots(userId)

    override suspend fun syncAll(userId: String): SyncResult {
        if (remoteTasks == null || remoteShifts == null || remoteRefundClaims == null ||
            remoteSettings == null || remoteCompensationProfiles == null ||
            remotePremiumProfiles == null || remoteProfiles == null ||
            remoteProjects == null || remoteBillingRecords == null ||
            remoteProjectPayments == null || remoteWorkplaces == null ||
            remoteLeavePolicies == null || remoteAbsenceEvents == null ||
            remoteAbsenceAllocations == null || remoteLeaveBalances == null
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
            runSyncStep("push projects", issues) { pushProjects(userId) }
            runSyncStep("push project billing records", issues) { pushBillingRecords(userId) }
            runSyncStep("push project payments", issues) { pushProjectPayments(userId) }
            // Workplaces before their policies, balances and allocations: every one
            // of those carries a workplace_id the server enforces as a real foreign
            // key, so the parent has to own a remote id first.
            runSyncStep("push workplaces", issues) { pushWorkplaces(userId) }
            runSyncStep("push leave policies", issues) { pushLeavePolicies(userId) }
            runSyncStep("push leave balances", issues) { pushLeaveBalances(userId) }
            // And events before allocations, for the same reason.
            runSyncStep("push absence events", issues) { pushAbsenceEvents(userId) }
            runSyncStep("push absence allocations", issues) { pushAbsenceAllocations(userId) }
            runSyncStep("push shifts", issues) { pushShifts(userId) }
            runSyncStep("upload pending receipts", issues) { uploadPendingReceipts(userId) }
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
            // Before shifts, which carry a project link.
            runSyncStep("pull projects", issues) { pullProjects(userId) }
            runSyncStep("pull project billing records", issues) { pullBillingRecords(userId) }
            runSyncStep("pull project payments", issues) { pullProjectPayments(userId) }
            // Same order on the way in, and before shifts: a pulled shift resolves
            // its workplace_id against the local workplaces table.
            runSyncStep("pull workplaces", issues) { pullWorkplaces(userId) }
            runSyncStep("pull leave policies", issues) { pullLeavePolicies(userId) }
            runSyncStep("pull leave balances", issues) { pullLeaveBalances(userId) }
            runSyncStep("pull absence events", issues) { pullAbsenceEvents(userId) }
            runSyncStep("pull absence allocations", issues) { pullAbsenceAllocations(userId) }
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
        /** False when the page budget ran out, so [seenRemoteIds] is incomplete. */
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
     * **Paging.** The cursor is a timestamp, and timestamps are not unique — a bulk
     * write, an import, a restore, or simply a busy second can leave many rows
     * sharing one `updated_at`. A cursor alone cannot say "I have applied 200 of
     * the 500 rows stamped with this millisecond", so a tie block larger than one
     * page used to be undrainable: `gte` re-fetched the same first page forever.
     * The old code detected that (the cursor stopped advancing) and gave up, which
     * is where large syncs silently skipped rows.
     *
     * [offsetWithinCursor] closes it. Combined with the total `(updated_at, id)`
     * ordering the data sources now request, it walks a tie block page by page and
     * resets as soon as the timestamp moves on. Rows are re-applied rather than
     * skipped if a run stops half way, which is safe because every [applyRow] is
     * idempotent.
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
        fetchPage: suspend (sinceIso: String?, offsetWithinCursor: Int) -> List<Row>,
        updatedAtIso: (Row) -> String,
        remoteIdOf: (Row) -> String,
        ownerOf: (Row) -> String,
        applyRow: suspend (Row) -> Boolean,
    ): PullOutcome {
        val initialCursor = syncCursorStore.lastPulledAt(userId, entity)
        val isFullSync = initialCursor == null
        var cursor = initialCursor
        var offsetWithinCursor = 0
        var holdEpoch: Long? = null
        var drainedFully = true
        val seenRemoteIds = mutableSetOf<String>()
        var pagesFetched = 0

        while (true) {
            val batch = fetchPage(syncCursorStore.sinceIso(cursor), offsetWithinCursor)
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

            offsetWithinCursor = if (cursor == previousCursor) {
                // Every row in the page carried the cursor's timestamp, so the next
                // fetch would hand back this same page. Step over what was applied.
                offsetWithinCursor + batch.size
            } else {
                // The cursor moved. The next fetch starts again at the new
                // timestamp, so skip only the rows of it already applied here.
                batch.count { isoToEpoch(updatedAtIso(it)) == cursor }
            }

            pagesFetched++
            if (pagesFetched >= MAX_PULL_PAGES) {
                // Rows written faster than they can be drained, or a server that
                // keeps returning full pages. Stop rather than sync forever; the
                // stored cursor means the next run resumes where this one stopped.
                // The server view is incomplete, so tombstoning must be skipped.
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
                if (!shouldHoldForParent(error)) markTaskFailed(task, error)
            }
        }
    }

    private suspend fun pushTaskCreate(task: TaskEntity, syncedAt: Long) {
        val remoteId = try {
            tasksRemote.insert(
                task.toRemoteInsert(
                    compensationProfileRemoteId =
                        idMapper.profileLocalToRemote(task.compensationProfileId),
                ),
            ).id
        } catch (e: Exception) {
            // The insert carries the client-generated id; a retry after a lost
            // response collides with the row it already created — adopt it.
            if (RemoteSyncErrors.isPrimaryKeyViolation(e, ENTITY_TASKS)) task.localId else throw e
        }
        markTaskSynced(task, remoteId, syncedAt)
    }

    private suspend fun pushTaskUpdate(task: TaskEntity, syncedAt: Long) {
        val remoteId = task.remoteId ?: error("Missing remoteId for task ${task.localId}")
        val update = task.toRemoteUpdate(
            compensationProfileRemoteId = idMapper.profileLocalToRemote(task.compensationProfileId),
        )
        if (tasksRemote.update(remoteId, update) == null) {
            adoptNewerRemoteTask(task, remoteId, syncedAt)
            return
        }
        markTaskSynced(task, remoteId, syncedAt)
    }

    /**
     * Resolves a rejected push: the server holds an edit newer than this one.
     *
     * See [adoptNewerRemoteShift] for why the remote copy wins and why the row is
     * left pending when it cannot be fetched.
     */
    private suspend fun adoptNewerRemoteTask(task: TaskEntity, remoteId: String, syncedAt: Long) {
        val remote = tasksRemote.findById(remoteId) ?: return
        taskDao.upsert(
            remote.toLocalEntity(
                existingLocalId = task.localId,
                compensationProfileLocalId = idMapper.profileRemoteToLocal(remote.compensationProfileId),
                preserveLocal = task,
            ),
        )
        taskDao.updateSyncState(task.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    // A row edited while its push was in flight must stay pending — flipping it
    // to SYNCED would silently drop the newer edit (it would never reach other
    // devices). The remoteId is still recorded so a pending create retries as
    // an update instead of inserting a duplicate.
    private suspend fun markTaskSynced(task: TaskEntity, remoteId: String?, syncedAt: Long) {
        val updated = taskDao.markSyncedIfUnchanged(task.localId, remoteId, syncedAt, task.updatedAt)
        if (updated == 0) taskDao.attachRemoteId(task.localId, remoteId, syncedAt)
    }

    /**
     * Publishes the delete as a tombstone rather than removing the row.
     *
     * A DELETE is invisible to every incremental pull: the other devices ask for
     * rows changed since their cursor, and a row that no longer exists is not a
     * change. Writing `deleted_at` makes the delete an ordinary update that
     * propagates like any edit — which is the whole reason a delete on one device
     * used to leave the row sitting on the other.
     */
    private suspend fun pushTaskDelete(task: TaskEntity, syncedAt: Long) {
        val remoteId = task.remoteId
        val deleteUpdate = task.toRemoteUpdate(
            compensationProfileRemoteId = idMapper.profileLocalToRemote(task.compensationProfileId),
        )
        if (remoteId != null && tasksRemote.update(remoteId, deleteUpdate) == null) {
            // See pushShiftDelete: a rejected tombstone must not be recorded as sent.
            adoptNewerRemoteTask(task, remoteId, syncedAt)
            return
        }
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
                fetchPage = { since, offset -> tasksRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE, offset) },
                updatedAtIso = { it.updatedAt },
                remoteIdOf = { it.id },
                ownerOf = { it.userId },
            ) { remote ->
                val existing = taskDao.getByRemoteId(remote.id)
                val profileLocalId =
                    idMapper.profileRemoteToLocal(remote.compensationProfileId)
                when {
                    // Nothing to delete, and materialising a hidden row would put a
                    // permanent invisible record into every reinstall.
                    existing == null && remote.deletedAt != null -> Unit
                    existing == null -> taskDao.upsert(
                        remote.toLocalEntity(compensationProfileLocalId = profileLocalId),
                    )
                    existing.syncStatus != SyncStatus.SYNCED -> Unit
                    isoToEpoch(remote.updatedAt) > existing.updatedAt ->
                        taskDao.upsert(
                            remote.toLocalEntity(
                                existingLocalId = existing.localId,
                                compensationProfileLocalId = profileLocalId,
                                // Keeps the task's scope when the profile has not
                                // been pulled yet, instead of dropping it back to
                                // the default job.
                                preserveLocal = existing,
                            ),
                        )
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
            }.onFailure { if (!shouldHoldForParent(it)) markShiftFailed(shift, it) }
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
                    workplaceRemoteId = idMapper.workplaceLocalToRemote(shift.workplaceId),
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
        val applied = shiftsRemote.update(
            remoteId,
            shift.toRemoteUpdate(
                compensationProfileRemoteId = idMapper.profileLocalToRemote(shift.compensationProfileId),
                premiumProfileRemoteId = idMapper.premiumProfileLocalToRemote(shift.premiumProfileId),
                taskRemoteId = idMapper.taskLocalToRemote(shift.taskId),
                workplaceRemoteId = idMapper.workplaceLocalToRemote(shift.workplaceId),
            ),
        )
        if (applied == null) {
            adoptNewerRemoteShift(shift, remoteId, syncedAt)
            return
        }
        markShiftSynced(shift, remoteId, syncedAt)
    }

    /**
     * Resolves a rejected push: the server holds an edit made after this one.
     *
     * This is the case the guard exists for. A device that spent a week offline
     * still has the shift as it was a week ago; pushing it unconditionally
     * reinstated that week-old state over whatever the user has done since on
     * their other device, and nothing anywhere recorded that it had happened.
     *
     * The newer edit wins, which is the same rule the pull side already applies,
     * so both directions agree and the devices converge. Taking the remote row
     * here rather than waiting for the pull matters: the remote row's `updated_at`
     * can be older than this entity's pull cursor, in which case no later pull
     * would ever fetch it and the two copies would stay different indefinitely.
     *
     * A row that cannot be fetched back is left pending rather than marked synced,
     * so the next run tries again instead of declaring a push that never landed.
     */
    private suspend fun adoptNewerRemoteShift(shift: ShiftEntity, remoteId: String, syncedAt: Long) {
        val remote = shiftsRemote.findById(remoteId) ?: return
        shiftDao.upsertShift(
            remote.toLocalEntity(
                existingLocalId = shift.localId,
                compensationProfileLocalId = idMapper.profileRemoteToLocal(remote.compensationProfileId),
                premiumProfileLocalId = idMapper.premiumProfileRemoteToLocal(remote.premiumProfileId),
                taskLocalId = idMapper.taskRemoteToLocal(remote.taskId),
                workplaceLocalId = idMapper.workplaceRemoteToLocal(remote.workplaceId),
                syncStatus = SyncStatus.SYNCED,
                preserveLocal = shift,
            ),
        )
        shiftDao.updateSyncState(shift.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    // A shift edited while its push was in flight (clock-out, note change, …)
    // must stay pending — flipping it to SYNCED would silently drop the newer
    // edit and it would never reach other devices. The remoteId is still
    // recorded so a pending create retries as an update, not a duplicate insert.
    private suspend fun markShiftSynced(shift: ShiftEntity, remoteId: String?, syncedAt: Long) {
        val updated = shiftDao.markSyncedIfUnchanged(shift.localId, remoteId, syncedAt, shift.updatedAt)
        if (updated == 0) shiftDao.attachRemoteId(shift.localId, remoteId, syncedAt)
    }

    /** Tombstone, not DELETE — see [pushTaskDelete]. */
    private suspend fun pushShiftDelete(shift: ShiftEntity, syncedAt: Long) {
        val remoteId = shift.remoteId
        if (remoteId != null) {
            val applied = shiftsRemote.update(
                remoteId,
                shift.toRemoteUpdate(
                    compensationProfileRemoteId = idMapper.profileLocalToRemote(shift.compensationProfileId),
                    premiumProfileRemoteId = idMapper.premiumProfileLocalToRemote(shift.premiumProfileId),
                    taskRemoteId = idMapper.taskLocalToRemote(shift.taskId),
                    workplaceRemoteId = idMapper.workplaceLocalToRemote(shift.workplaceId),
                ),
            )
            // A delete is an edit, and it loses to a newer one like any other.
            // Marking the row synced anyway would leave it deleted here and alive
            // everywhere else, with no later pull guaranteed to notice.
            if (applied == null) {
                adoptNewerRemoteShift(shift, remoteId, syncedAt)
                return
            }
        }
        shiftDao.updateSyncState(shift.localId, SyncStatus.SYNCED, shift.remoteId, syncedAt, null)
    }

    /**
     * Whether this failure should leave the row pending instead of marking it failed.
     *
     * A foreign-key violation means the parent has not reached the server yet — a
     * task scoped to a profile that has not synced, a shift carrying a workplace
     * that has not. The next run, once the parent has landed, succeeds unchanged,
     * so recording it as FAILED is wrong twice over: it is not a permanent
     * rejection, and FAILED rows are deliberately excluded from the immediate
     * retry path by `hasRetryablePendingWork`, which left the row stuck at
     * fifteen-minute intervals and permanently in the "unsynced changes" count.
     */
    private fun shouldHoldForParent(error: Throwable): Boolean =
        RemoteSyncErrors.isForeignKeyViolation(error)

    private suspend fun markShiftFailed(shift: ShiftEntity, error: Throwable) {
        shiftDao.updateSyncState(
            shift.localId, SyncStatus.FAILED, shift.remoteId, shift.lastSyncedAt, error.message,
        )
    }

    private suspend fun pullShifts(userId: String) {
        val outcome = pullIncremental(
            userId = userId,
            entity = ENTITY_SHIFTS,
            fetchPage = { since, offset -> shiftsRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE, offset) },
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

        enforceSingleRunningShift(userId)
    }

    /**
     * Applies the one-running-shift rule to whatever the pull left behind.
     *
     * Run here, once, rather than while applying each row: a device that clocked
     * in locally and a device that clocked in remotely produce a duplicate that
     * only exists once *both* rows are in the database, and mid-pull there is no
     * way to tell a second running shift from the first row of a batch that will
     * shortly supersede it.
     *
     * The losing rows are soft-deleted as local edits (PENDING_UPDATE), not as
     * already-synced ones, so the next push publishes the tombstone and the other
     * device drops its copy too. Resolving on one device only would leave the
     * duplicate running everywhere else.
     */
    private suspend fun enforceSingleRunningShift(userId: String) {
        val resolution = RunningShiftResolver.resolve(shiftDao.getActiveShifts(userId)) ?: return
        if (!resolution.hasDuplicates) return

        val now = Instant.now().toEpochMilli()
        val winner = resolution.winner
        val storedWinner = shiftDao.getShiftById(winner.localId)
        if (storedWinner != null && storedWinner != winner) {
            // Only when merging actually carried something across, so a resolution
            // that changes nothing does not create a write the other devices must
            // then pull back.
            shiftDao.upsertShift(
                winner.copy(
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING_UPDATE,
                ),
            )
        }
        resolution.duplicates.forEach { duplicate ->
            shiftDao.softDeleteShift(duplicate.localId, now, SyncStatus.PENDING_UPDATE, now)
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
                        workplaceLocalId = idMapper.workplaceRemoteToLocal(remote.workplaceId),
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
                            workplaceLocalId = idMapper.workplaceRemoteToLocal(remote.workplaceId),
                            syncStatus = SyncStatus.SYNCED,
                            preserveLocal = existingByStartTime,
                        ),
                    )
                }
                SyncStatus.PENDING_CREATE, SyncStatus.FAILED -> {
                    // Adopting a tombstone's id would mark this unsent shift as
                    // synced to a row the server considers deleted, and the shift
                    // the user just recorded would quietly never be published. The
                    // start time is free again — the unique index ignores
                    // tombstones — so let the create push on its own.
                    if (remote.deletedAt == null) {
                        // PENDING_UPDATE, not SYNCED.
                        //
                        // The two rows are the same shift — one clock-in recorded on
                        // two devices, or a create whose response was lost — so the
                        // remote id is right to adopt. Calling it SYNCED was not:
                        // this row still holds local values the server has never
                        // seen, typically the endTime, break and notes added after
                        // the clock-in, and nothing would ever push them. They were
                        // only overwritten if the remote copy happened to be newer;
                        // if it was older the two stayed different indefinitely,
                        // which is the one outcome the rest of this pipeline exists
                        // to prevent.
                        //
                        // Pending with a remote id retries as an update, which is
                        // what "local pending always wins" means everywhere else.
                        shiftDao.updateSyncState(
                            existingByStartTime.localId,
                            SyncStatus.PENDING_UPDATE,
                            remote.id,
                            isoToEpoch(remote.updatedAt),
                            null,
                        )
                    }
                }
                else -> Unit
            }
            return true
        }

        // A tombstone for a shift this device has never held. There is nothing to
        // delete, and inserting the row only to hide it would put a permanent
        // invisible record in every reinstall.
        if (remote.deletedAt != null) return true

        // A second open shift is materialised rather than refused. Refusing it held
        // the pull cursor on a row that would never become applicable, which
        // stalled the whole shifts pull — nothing newer than the duplicate arrived
        // until the user happened to clock out — and left the duplicate running on
        // the other device regardless. enforceSingleRunningShift collapses them
        // once the pull is complete, on every device, to the same winner.
        shiftDao.insertShift(
            remote.toLocalEntity(
                compensationProfileLocalId = idMapper.profileRemoteToLocal(remote.compensationProfileId),
                premiumProfileLocalId = idMapper.premiumProfileRemoteToLocal(remote.premiumProfileId),
                taskLocalId = idMapper.taskRemoteToLocal(remote.taskId),
                workplaceLocalId = idMapper.workplaceRemoteToLocal(remote.workplaceId),
            ),
        )
        return true
    }

    // ── Paid Projects ─────────────────────────────────────────────────────────
    //
    // Projects, what has been billed against them, and what has been paid. These
    // three tables were local-only: a project and the record of money owed for it
    // existed on one device and nowhere else, so changing phones lost them unless
    // the user had exported a backup first.
    //
    // Ordered parents before children in both directions. A billing record's
    // project_id and a payment's billing_record_id are real foreign keys on the
    // server, so pushing a child before its parent is rejected, and pulling one
    // before its parent has no local row to attach it to.

    private suspend fun pushProjects(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (project in projectDao.getPendingSyncProjects(userId)) {
            runCatching {
                when {
                    project.syncStatus == SyncStatus.SYNCED -> Unit
                    project.deletedAt != null -> pushProjectDelete(project, now)
                    project.remoteId == null -> pushProjectCreate(project, now)
                    else -> pushProjectUpdate(project, now)
                }
            }.onFailure { error ->
                projectDao.updateSyncState(
                    project.localId, SyncStatus.FAILED, project.remoteId, project.lastSyncedAt, error.message,
                )
            }
        }
    }

    private suspend fun pushProjectCreate(project: ProjectEntity, syncedAt: Long) {
        val remoteId = try {
            projectsRemote.insert(project.toRemoteInsert()).id
        } catch (e: Exception) {
            // The insert carries the client-generated id, so a retry after a lost
            // response collides with the row it already created — adopt it.
            if (RemoteSyncErrors.isPrimaryKeyViolation(e, ENTITY_PROJECTS)) project.localId else throw e
        }
        projectDao.updateSyncState(project.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    private suspend fun pushProjectUpdate(project: ProjectEntity, syncedAt: Long) {
        val remoteId = project.remoteId ?: error("Missing remoteId for project ${project.localId}")
        if (projectsRemote.update(remoteId, project.toRemoteUpdate()) == null) {
            adoptNewerRemoteProject(project, remoteId, syncedAt)
            return
        }
        projectDao.updateSyncState(project.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    /** Tombstone, not DELETE — see [pushTaskDelete]. */
    private suspend fun pushProjectDelete(project: ProjectEntity, syncedAt: Long) {
        val remoteId = project.remoteId
        if (remoteId != null && projectsRemote.update(remoteId, project.toRemoteUpdate()) == null) {
            adoptNewerRemoteProject(project, remoteId, syncedAt)
            return
        }
        projectDao.updateSyncState(project.localId, SyncStatus.SYNCED, project.remoteId, syncedAt, null)
    }

    /** See [adoptNewerRemoteShift]. */
    private suspend fun adoptNewerRemoteProject(project: ProjectEntity, remoteId: String, syncedAt: Long) {
        val remote = projectsRemote.findById(remoteId) ?: return
        projectDao.upsert(remote.toLocalEntity(existingLocalId = project.localId))
        projectDao.updateSyncState(project.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    private suspend fun pullProjects(userId: String) {
        pullIncremental(
            userId = userId,
            entity = ENTITY_PROJECTS,
            fetchPage = { since, offset -> projectsRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE, offset) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
            ownerOf = { it.userId },
        ) { remote ->
            val existing = projectDao.getByRemoteId(remote.id)
            when {
                existing == null && remote.deletedAt != null -> Unit
                existing == null -> projectDao.upsert(remote.toLocalEntity())
                existing.syncStatus != SyncStatus.SYNCED -> Unit
                isoToEpoch(remote.updatedAt) > existing.updatedAt ->
                    projectDao.upsert(remote.toLocalEntity(existingLocalId = existing.localId))
            }
            true
        }
    }

    private suspend fun pushBillingRecords(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (record in projectBillingRecordDao.getPendingSyncRecords(userId)) {
            runCatching {
                when {
                    record.syncStatus == SyncStatus.SYNCED -> Unit
                    record.deletedAt != null -> pushBillingRecordDelete(record, now)
                    record.remoteId == null -> {
                        // Parent project not pushed yet: leave the record pending
                        // rather than sending a project_id the server will reject.
                        val projectRemoteId = idMapper.projectLocalToRemote(record.projectLocalId)
                            ?: return@runCatching
                        pushBillingRecordCreate(record, projectRemoteId, now)
                    }
                    else -> pushBillingRecordUpdate(record, now)
                }
            }.onFailure { error ->
                projectBillingRecordDao.updateSyncState(
                    record.localId, SyncStatus.FAILED, record.remoteId, record.lastSyncedAt, error.message,
                )
            }
        }
    }

    private suspend fun pushBillingRecordCreate(
        record: ProjectBillingRecordEntity,
        projectRemoteId: String,
        syncedAt: Long,
    ) {
        val remoteId = try {
            billingRemote.insert(record.toRemoteInsert(projectRemoteId)).id
        } catch (e: Exception) {
            if (RemoteSyncErrors.isPrimaryKeyViolation(e, ENTITY_PROJECT_BILLING_RECORDS)) {
                record.localId
            } else {
                throw e
            }
        }
        projectBillingRecordDao.updateSyncState(record.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    private suspend fun pushBillingRecordUpdate(record: ProjectBillingRecordEntity, syncedAt: Long) {
        val remoteId = record.remoteId ?: error("Missing remoteId for billing record ${record.localId}")
        if (billingRemote.update(remoteId, record.toRemoteUpdate()) == null) {
            adoptNewerRemoteBillingRecord(record, remoteId, syncedAt)
            return
        }
        projectBillingRecordDao.updateSyncState(record.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    /** Tombstone, not DELETE — see [pushTaskDelete]. */
    private suspend fun pushBillingRecordDelete(record: ProjectBillingRecordEntity, syncedAt: Long) {
        val remoteId = record.remoteId
        if (remoteId != null && billingRemote.update(remoteId, record.toRemoteUpdate()) == null) {
            adoptNewerRemoteBillingRecord(record, remoteId, syncedAt)
            return
        }
        projectBillingRecordDao.updateSyncState(
            record.localId, SyncStatus.SYNCED, record.remoteId, syncedAt, null,
        )
    }

    /** See [adoptNewerRemoteShift]. */
    private suspend fun adoptNewerRemoteBillingRecord(
        record: ProjectBillingRecordEntity,
        remoteId: String,
        syncedAt: Long,
    ) {
        val remote = billingRemote.findById(remoteId) ?: return
        projectBillingRecordDao.upsert(
            remote.toLocalEntity(
                projectLocalId = record.projectLocalId,
                existingLocalId = record.localId,
            ),
        )
        projectBillingRecordDao.updateSyncState(record.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    private suspend fun pullBillingRecords(userId: String) {
        pullIncremental(
            userId = userId,
            entity = ENTITY_PROJECT_BILLING_RECORDS,
            fetchPage = { since, offset -> billingRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE, offset) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
            ownerOf = { it.userId },
        ) { remote ->
            // Parent project not pulled yet (e.g. the projects step failed this
            // run): hold the cursor so this row is re-fetched once it exists.
            val projectLocalId = idMapper.projectRemoteToLocal(remote.projectId)
                ?: return@pullIncremental false
            val existing = projectBillingRecordDao.getByRemoteId(remote.id)
            when {
                existing == null && remote.deletedAt != null -> Unit
                existing == null ->
                    projectBillingRecordDao.upsert(remote.toLocalEntity(projectLocalId = projectLocalId))
                existing.syncStatus != SyncStatus.SYNCED -> Unit
                isoToEpoch(remote.updatedAt) > existing.updatedAt ->
                    projectBillingRecordDao.upsert(
                        remote.toLocalEntity(
                            projectLocalId = projectLocalId,
                            existingLocalId = existing.localId,
                        ),
                    )
            }
            true
        }
    }

    private suspend fun pushProjectPayments(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (payment in projectPaymentDao.getPendingSyncPayments(userId)) {
            runCatching {
                when {
                    payment.syncStatus == SyncStatus.SYNCED -> Unit
                    payment.deletedAt != null -> pushProjectPaymentDelete(payment, now)
                    payment.remoteId == null -> {
                        val projectRemoteId = idMapper.projectLocalToRemote(payment.projectLocalId)
                            ?: return@runCatching
                        val recordRemoteId = idMapper.billingRecordLocalToRemote(payment.billingRecordLocalId)
                            ?: return@runCatching
                        pushProjectPaymentCreate(payment, projectRemoteId, recordRemoteId, now)
                    }
                    else -> pushProjectPaymentUpdate(payment, now)
                }
            }.onFailure { error ->
                projectPaymentDao.updateSyncState(
                    payment.localId, SyncStatus.FAILED, payment.remoteId, payment.lastSyncedAt, error.message,
                )
            }
        }
    }

    private suspend fun pushProjectPaymentCreate(
        payment: ProjectPaymentEntity,
        projectRemoteId: String,
        billingRecordRemoteId: String,
        syncedAt: Long,
    ) {
        val remoteId = try {
            paymentsRemote.insert(payment.toRemoteInsert(projectRemoteId, billingRecordRemoteId)).id
        } catch (e: Exception) {
            if (RemoteSyncErrors.isPrimaryKeyViolation(e, ENTITY_PROJECT_PAYMENTS)) {
                payment.localId
            } else {
                throw e
            }
        }
        projectPaymentDao.updateSyncState(payment.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    private suspend fun pushProjectPaymentUpdate(payment: ProjectPaymentEntity, syncedAt: Long) {
        val remoteId = payment.remoteId ?: error("Missing remoteId for payment ${payment.localId}")
        if (paymentsRemote.update(remoteId, payment.toRemoteUpdate()) == null) {
            adoptNewerRemoteProjectPayment(payment, remoteId, syncedAt)
            return
        }
        projectPaymentDao.updateSyncState(payment.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    /** Tombstone, not DELETE — see [pushTaskDelete]. */
    private suspend fun pushProjectPaymentDelete(payment: ProjectPaymentEntity, syncedAt: Long) {
        val remoteId = payment.remoteId
        if (remoteId != null && paymentsRemote.update(remoteId, payment.toRemoteUpdate()) == null) {
            adoptNewerRemoteProjectPayment(payment, remoteId, syncedAt)
            return
        }
        projectPaymentDao.updateSyncState(
            payment.localId, SyncStatus.SYNCED, payment.remoteId, syncedAt, null,
        )
    }

    /** See [adoptNewerRemoteShift]. */
    private suspend fun adoptNewerRemoteProjectPayment(
        payment: ProjectPaymentEntity,
        remoteId: String,
        syncedAt: Long,
    ) {
        val remote = paymentsRemote.findById(remoteId) ?: return
        projectPaymentDao.upsert(
            remote.toLocalEntity(
                projectLocalId = payment.projectLocalId,
                billingRecordLocalId = payment.billingRecordLocalId,
                existingLocalId = payment.localId,
            ),
        )
        projectPaymentDao.updateSyncState(payment.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    private suspend fun pullProjectPayments(userId: String) {
        pullIncremental(
            userId = userId,
            entity = ENTITY_PROJECT_PAYMENTS,
            fetchPage = { since, offset -> paymentsRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE, offset) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
            ownerOf = { it.userId },
        ) { remote ->
            val projectLocalId = idMapper.projectRemoteToLocal(remote.projectId)
                ?: return@pullIncremental false
            val recordLocalId = idMapper.billingRecordRemoteToLocal(remote.billingRecordId)
                ?: return@pullIncremental false
            val existing = projectPaymentDao.getByRemoteId(remote.id)
            when {
                existing == null && remote.deletedAt != null -> Unit
                existing == null ->
                    projectPaymentDao.upsert(
                        remote.toLocalEntity(
                            projectLocalId = projectLocalId,
                            billingRecordLocalId = recordLocalId,
                        ),
                    )
                existing.syncStatus != SyncStatus.SYNCED -> Unit
                isoToEpoch(remote.updatedAt) > existing.updatedAt ->
                    projectPaymentDao.upsert(
                        remote.toLocalEntity(
                            projectLocalId = projectLocalId,
                            billingRecordLocalId = recordLocalId,
                            existingLocalId = existing.localId,
                        ),
                    )
            }
            true
        }
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
        if (claimsRemote.update(remoteId, claim.toRemoteUpdate()) == null) {
            adoptNewerRemoteClaim(claim, remoteId, syncedAt)
            return
        }
        markRefundClaimSynced(claim, remoteId, syncedAt)
    }

    /** See [adoptNewerRemoteShift]. */
    private suspend fun adoptNewerRemoteClaim(
        claim: RefundClaimEntity,
        remoteId: String,
        syncedAt: Long,
    ) {
        val remote = claimsRemote.findById(remoteId) ?: return
        refundClaimDao.upsertClaim(
            remote.toLocalEntity(
                shiftLocalId = claim.shiftLocalId,
                existingLocalId = claim.localId,
            ),
        )
        refundClaimDao.updateSyncState(claim.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    // See markShiftSynced: a mid-push edit must keep the row pending.
    private suspend fun markRefundClaimSynced(claim: RefundClaimEntity, remoteId: String?, syncedAt: Long) {
        val updated = refundClaimDao.markSyncedIfUnchanged(claim.localId, remoteId, syncedAt, claim.updatedAt)
        if (updated == 0) refundClaimDao.attachRemoteId(claim.localId, remoteId, syncedAt)
    }

    /**
     * Tombstone, not DELETE — see [pushTaskDelete] — plus the claim's receipt
     * image in cloud storage.
     *
     * Deleting the object here rather than at the call site is what makes it
     * cover deleting a whole *shift*. That cascades to the shift's claims through
     * the database and never went near storage, so every receipt photo on a
     * deleted shift stayed in the bucket indefinitely: paid for, backed up, and
     * belonging to a shift the user believed they had removed.
     *
     * Ordered after the tombstone so a storage failure cannot block the delete
     * from reaching other devices, and reported as a row failure so the periodic
     * sync tries again. Both halves are idempotent, so repeating them is free.
     */
    private suspend fun pushRefundClaimDelete(claim: RefundClaimEntity, syncedAt: Long) {
        val remoteId = claim.remoteId
        if (remoteId != null && claimsRemote.update(remoteId, claim.toRemoteUpdate()) == null) {
            adoptNewerRemoteClaim(claim, remoteId, syncedAt)
            return
        }
        val receiptPath = claim.receiptPath
        if (receiptPath != null && refundReceiptStorage != null) {
            refundReceiptStorage.delete(receiptPath)
        }
        refundClaimDao.updateSyncState(claim.localId, SyncStatus.SYNCED, claim.remoteId, syncedAt, null)
    }

    /**
     * Uploads receipt photos that never made it to the cloud.
     *
     * A photo whose upload failed left the claim saved with no `receipt_path`
     * while the image stayed in local receipt storage. Nothing ever tried again,
     * so the receipt existed on exactly one device — and the user had been told
     * the claim saved, which it did.
     *
     * Runs before the claim push so a recovered path is published in the same
     * sync rather than waiting for the next one. Failures are per claim and
     * deliberately swallowed: a receipt that cannot be uploaded now is picked up
     * by the next run, and it must not fail the sync around it.
     */
    private suspend fun uploadPendingReceipts(userId: String) {
        val storage = refundReceiptStorage ?: return
        val reader = receiptFileReader ?: return

        for (claim in refundClaimDao.getClaimsAwaitingReceiptUpload(userId)) {
            val receipt = receiptDao.getByRefundClaimId(claim.localId) ?: continue
            val upload = reader.toReceiptUpload(receipt.localImageUri) ?: continue
            runCatching {
                val path = storage.upload(
                    userId,
                    claim.shiftLocalId,
                    RefundDirection.fromPersisted(claim.direction),
                    upload,
                )
                val now = Instant.now().toEpochMilli()
                // PENDING_UPDATE, not SYNCED: the new path is a local change that
                // still has to reach the server, and the push below is what does it.
                refundClaimDao.upsertClaim(
                    claim.copy(
                        receiptPath = path,
                        updatedAt = now,
                        syncStatus = SyncStatus.PENDING_UPDATE,
                    ),
                )
            }
        }
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
            fetchPage = { since, offset -> claimsRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE, offset) },
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
                existing == null && remote.deletedAt != null -> Unit
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
            compensationRemote.insert(profile.toRemoteInsert(idMapper.workplaceLocalToRemote(profile.workplaceId))).id
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
        if (compensationRemote.update(remoteId, profile.toRemoteUpdate(idMapper.workplaceLocalToRemote(profile.workplaceId))) == null) {
            adoptNewerRemoteCompensationProfile(profile, remoteId, syncedAt)
            return
        }
        markCompensationProfileSynced(profile, remoteId, syncedAt)
    }

    /** See [adoptNewerRemoteShift]. */
    private suspend fun adoptNewerRemoteCompensationProfile(
        profile: CompensationProfileEntity,
        remoteId: String,
        syncedAt: Long,
    ) {
        val remote = compensationRemote.findById(remoteId) ?: return
        compensationProfileDao.upsert(
            remote.toLocalEntity(
                existingLocalId = profile.localId,
                workplaceLocalId = idMapper.workplaceRemoteToLocal(remote.workplaceId),
                preserveLocal = profile,
            ),
        )
        compensationProfileDao.updateSyncState(
            profile.localId, SyncStatus.SYNCED, remoteId, syncedAt, null,
        )
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

    /** Tombstone, not DELETE — see [pushTaskDelete]. */
    private suspend fun pushCompensationProfileDelete(profile: CompensationProfileEntity, syncedAt: Long) {
        val remoteId = profile.remoteId
        if (remoteId != null && compensationRemote.update(remoteId, profile.toRemoteUpdate(idMapper.workplaceLocalToRemote(profile.workplaceId))) == null) {
            adoptNewerRemoteCompensationProfile(profile, remoteId, syncedAt)
            return
        }
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
            fetchPage = { since, offset -> compensationRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE, offset) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
            ownerOf = { it.userId },
        ) { remote ->
            val existing = compensationProfileDao.getByRemoteId(remote.id)
            val workplaceLocalId = idMapper.workplaceRemoteToLocal(remote.workplaceId)
            when {
                existing == null && remote.deletedAt != null -> Unit
                existing == null -> compensationProfileDao.insert(
                    remote.toLocalEntity(workplaceLocalId = workplaceLocalId),
                )
                existing.syncStatus != SyncStatus.SYNCED -> Unit
                isoToEpoch(remote.updatedAt) > existing.updatedAt ->
                    compensationProfileDao.upsert(
                        remote.toLocalEntity(
                            existingLocalId = existing.localId,
                            workplaceLocalId = workplaceLocalId,
                            // Keeps the profile's workplace when the remote link
                            // has not been pulled yet; without it a pull would
                            // strip the leave entitlement hanging off it.
                            preserveLocal = existing,
                        ),
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
        if (premiumRemote.update(remoteId, profile.toRemoteUpdate()) == null) {
            adoptNewerRemotePremiumProfile(profile, remoteId, syncedAt)
            return
        }
        markPremiumProfileSynced(profile, remoteId, syncedAt)
    }

    /** See [adoptNewerRemoteShift]. */
    private suspend fun adoptNewerRemotePremiumProfile(
        profile: PremiumProfileEntity,
        remoteId: String,
        syncedAt: Long,
    ) {
        val remote = premiumRemote.findById(remoteId) ?: return
        premiumProfileDao.upsert(remote.toLocalEntity(existingLocalId = profile.localId))
        premiumProfileDao.updateSyncState(profile.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
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

    /** Tombstone, not DELETE — see [pushTaskDelete]. */
    private suspend fun pushPremiumProfileDelete(profile: PremiumProfileEntity, syncedAt: Long) {
        val remoteId = profile.remoteId
        if (remoteId != null && premiumRemote.update(remoteId, profile.toRemoteUpdate()) == null) {
            adoptNewerRemotePremiumProfile(profile, remoteId, syncedAt)
            return
        }
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
            fetchPage = { since, offset -> premiumRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE, offset) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
            ownerOf = { it.userId },
        ) { remote ->
            val existing = premiumProfileDao.getByRemoteId(remote.id)
            when {
                existing == null && remote.deletedAt != null -> Unit
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

    // ── Workplaces and leave ──────────────────────────────────────────────────
    //
    // Five tables that had a Supabase schema and no sync steps, so every leave
    // arrangement and every reported absence was device-local: lost on reinstall,
    // absent on a second device.
    //
    // The shape below is the one the already-synced tables use, once per table: a
    // push that tombstones rather than deletes, a create that treats a primary-key
    // collision as "the previous attempt landed", an update that adopts the remote
    // row when a newer edit already exists there, and a pull that only overwrites
    // rows with no local edit pending. Parent links are resolved through idMapper
    // and a push whose parent has no remote id yet is left pending rather than sent
    // with an id the server would reject.

    private suspend fun pushWorkplaces(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (row in workplaceDao.getPendingSyncWorkplaces(userId)) {
            runCatching {
                when {
                    row.syncStatus == SyncStatus.SYNCED -> Unit
                    row.deletedAt != null -> {
                        val remoteId = row.remoteId
                        if (remoteId != null && workplacesRemote.update(remoteId, row.toRemoteUpdate()) == null) {
                            adoptNewerRemoteWorkplace(row, remoteId, now)
                        } else {
                            markWorkplaceSynced(row, row.remoteId, now)
                        }
                    }
                    row.remoteId == null -> {
                        val remoteId = try {
                            workplacesRemote.insert(row.toRemoteInsert()).id
                        } catch (e: Exception) {
                            if (RemoteSyncErrors.isPrimaryKeyViolation(e, ENTITY_WORKPLACES)) row.localId else throw e
                        }
                        markWorkplaceSynced(row, remoteId, now)
                    }
                    else -> {
                        val remoteId = row.remoteId
                        if (workplacesRemote.update(remoteId, row.toRemoteUpdate()) == null) {
                            adoptNewerRemoteWorkplace(row, remoteId, now)
                        } else {
                            markWorkplaceSynced(row, remoteId, now)
                        }
                    }
                }
            }.onFailure { error ->
                workplaceDao.updateSyncState(
                    row.localId, SyncStatus.FAILED, row.remoteId, row.lastSyncedAt, error.message,
                )
            }
        }
    }

    private suspend fun markWorkplaceSynced(
        row: com.elmtrackr.app.data.local.entity.WorkplaceEntity,
        remoteId: String?,
        syncedAt: Long,
    ) {
        if (workplaceDao.markSyncedIfUnchanged(row.localId, remoteId, syncedAt, row.updatedAt) == 0) {
            workplaceDao.attachRemoteId(row.localId, remoteId, syncedAt)
        }
    }

    private suspend fun adoptNewerRemoteWorkplace(
        row: com.elmtrackr.app.data.local.entity.WorkplaceEntity,
        remoteId: String,
        syncedAt: Long,
    ) {
        val remote = workplacesRemote.findById(remoteId) ?: return
        workplaceDao.upsert(remote.toLocalEntity(existingLocalId = row.localId))
        workplaceDao.updateSyncState(row.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    private suspend fun pullWorkplaces(userId: String) {
        pullIncremental(
            userId = userId,
            entity = ENTITY_WORKPLACES,
            fetchPage = { since, offset -> workplacesRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE, offset) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
            ownerOf = { it.userId },
        ) { remote ->
            val existing = workplaceDao.getByRemoteId(remote.id)
            when {
                existing == null && remote.deletedAt != null -> Unit
                existing == null -> workplaceDao.upsert(remote.toLocalEntity())
                existing.syncStatus != SyncStatus.SYNCED -> Unit
                isoToEpoch(remote.updatedAt) > existing.updatedAt ->
                    workplaceDao.upsert(remote.toLocalEntity(existingLocalId = existing.localId))
            }
            true
        }
        // No tombstone sweep here, unlike premium profiles. A workplace the server
        // has never heard of is one created offline and not yet pushed; sweeping on
        // absence would delete it before its first push.
    }

    private suspend fun pushLeavePolicies(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (row in leavePolicyDao.getPendingSyncPolicies(userId)) {
            runCatching {
                when {
                    row.syncStatus == SyncStatus.SYNCED -> Unit
                    row.deletedAt != null -> {
                        val remoteId = row.remoteId
                        if (remoteId != null && policiesRemote.update(remoteId, row.toRemoteUpdate()) == null) {
                            adoptNewerRemotePolicy(row, remoteId, now)
                        } else {
                            markPolicySynced(row, row.remoteId, now)
                        }
                    }
                    row.remoteId == null -> {
                        // Parent workplace not pushed yet: stay pending rather than
                        // send a workplace_id the server will reject.
                        val workplaceRemoteId = idMapper.workplaceLocalToRemote(row.workplaceLocalId)
                            ?: return@runCatching
                        val remoteId = try {
                            policiesRemote.insert(row.toRemoteInsert(workplaceRemoteId)).id
                        } catch (e: Exception) {
                            if (RemoteSyncErrors.isPrimaryKeyViolation(e, ENTITY_LEAVE_POLICIES)) row.localId else throw e
                        }
                        markPolicySynced(row, remoteId, now)
                    }
                    else -> {
                        val remoteId = row.remoteId
                        if (policiesRemote.update(remoteId, row.toRemoteUpdate()) == null) {
                            adoptNewerRemotePolicy(row, remoteId, now)
                        } else {
                            markPolicySynced(row, remoteId, now)
                        }
                    }
                }
            }.onFailure { error ->
                leavePolicyDao.updateSyncState(
                    row.localId, SyncStatus.FAILED, row.remoteId, row.lastSyncedAt, error.message,
                )
            }
        }
    }

    private suspend fun markPolicySynced(
        row: com.elmtrackr.app.data.local.entity.LeavePolicyEntity,
        remoteId: String?,
        syncedAt: Long,
    ) {
        if (leavePolicyDao.markSyncedIfUnchanged(row.localId, remoteId, syncedAt, row.updatedAt) == 0) {
            leavePolicyDao.attachRemoteId(row.localId, remoteId, syncedAt)
        }
    }

    private suspend fun adoptNewerRemotePolicy(
        row: com.elmtrackr.app.data.local.entity.LeavePolicyEntity,
        remoteId: String,
        syncedAt: Long,
    ) {
        val remote = policiesRemote.findById(remoteId) ?: return
        leavePolicyDao.upsert(
            remote.toLocalEntity(workplaceLocalId = row.workplaceLocalId, existingLocalId = row.localId),
        )
        leavePolicyDao.updateSyncState(row.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    private suspend fun pullLeavePolicies(userId: String) {
        pullIncremental(
            userId = userId,
            entity = ENTITY_LEAVE_POLICIES,
            fetchPage = { since, offset -> policiesRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE, offset) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
            ownerOf = { it.userId },
        ) { remote ->
            // Its workplace has to be local already. Returning false leaves the
            // cursor behind this page so the row is retried once it is.
            val workplaceLocalId = idMapper.workplaceRemoteToLocal(remote.workplaceId)
                ?: return@pullIncremental false
            val existing = leavePolicyDao.getByRemoteId(remote.id)
            when {
                existing == null && remote.deletedAt != null -> Unit
                existing == null -> leavePolicyDao.upsert(remote.toLocalEntity(workplaceLocalId))
                existing.syncStatus != SyncStatus.SYNCED -> Unit
                isoToEpoch(remote.updatedAt) > existing.updatedAt ->
                    leavePolicyDao.upsert(
                        remote.toLocalEntity(workplaceLocalId, existingLocalId = existing.localId),
                    )
            }
            true
        }
    }

    private suspend fun pushLeaveBalances(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (row in leaveBalanceSnapshotDao.getPendingSyncSnapshots(userId)) {
            runCatching {
                when {
                    row.syncStatus == SyncStatus.SYNCED -> Unit
                    row.deletedAt != null -> {
                        val remoteId = row.remoteId
                        if (remoteId != null && balancesRemote.update(remoteId, row.toRemoteUpdate()) == null) {
                            adoptNewerRemoteBalance(row, remoteId, now)
                        } else {
                            markBalanceSynced(row, row.remoteId, now)
                        }
                    }
                    row.remoteId == null -> {
                        val workplaceRemoteId = idMapper.workplaceLocalToRemote(row.workplaceLocalId)
                            ?: return@runCatching
                        val remoteId = try {
                            balancesRemote.insert(row.toRemoteInsert(workplaceRemoteId)).id
                        } catch (e: Exception) {
                            if (RemoteSyncErrors.isPrimaryKeyViolation(e, ENTITY_LEAVE_BALANCES)) row.localId else throw e
                        }
                        markBalanceSynced(row, remoteId, now)
                    }
                    else -> {
                        val remoteId = row.remoteId
                        if (balancesRemote.update(remoteId, row.toRemoteUpdate()) == null) {
                            adoptNewerRemoteBalance(row, remoteId, now)
                        } else {
                            markBalanceSynced(row, remoteId, now)
                        }
                    }
                }
            }.onFailure { error ->
                leaveBalanceSnapshotDao.updateSyncState(
                    row.localId, SyncStatus.FAILED, row.remoteId, row.lastSyncedAt, error.message,
                )
            }
        }
    }

    private suspend fun markBalanceSynced(
        row: com.elmtrackr.app.data.local.entity.LeaveBalanceSnapshotEntity,
        remoteId: String?,
        syncedAt: Long,
    ) {
        if (leaveBalanceSnapshotDao.markSyncedIfUnchanged(row.localId, remoteId, syncedAt, row.updatedAt) == 0) {
            leaveBalanceSnapshotDao.attachRemoteId(row.localId, remoteId, syncedAt)
        }
    }

    private suspend fun adoptNewerRemoteBalance(
        row: com.elmtrackr.app.data.local.entity.LeaveBalanceSnapshotEntity,
        remoteId: String,
        syncedAt: Long,
    ) {
        val remote = balancesRemote.findById(remoteId) ?: return
        leaveBalanceSnapshotDao.upsert(
            remote.toLocalEntity(workplaceLocalId = row.workplaceLocalId, existingLocalId = row.localId),
        )
        leaveBalanceSnapshotDao.updateSyncState(row.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    private suspend fun pullLeaveBalances(userId: String) {
        pullIncremental(
            userId = userId,
            entity = ENTITY_LEAVE_BALANCES,
            fetchPage = { since, offset -> balancesRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE, offset) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
            ownerOf = { it.userId },
        ) { remote ->
            val workplaceLocalId = idMapper.workplaceRemoteToLocal(remote.workplaceId)
                ?: return@pullIncremental false
            val existing = leaveBalanceSnapshotDao.getByRemoteId(remote.id)
            when {
                existing == null && remote.deletedAt != null -> Unit
                existing == null -> leaveBalanceSnapshotDao.upsert(remote.toLocalEntity(workplaceLocalId))
                existing.syncStatus != SyncStatus.SYNCED -> Unit
                isoToEpoch(remote.updatedAt) > existing.updatedAt ->
                    leaveBalanceSnapshotDao.upsert(
                        remote.toLocalEntity(workplaceLocalId, existingLocalId = existing.localId),
                    )
            }
            true
        }
    }

    private suspend fun pushAbsenceEvents(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (row in absenceEventDao.getPendingSyncEvents(userId)) {
            runCatching {
                when {
                    row.syncStatus == SyncStatus.SYNCED -> Unit
                    row.deletedAt != null -> {
                        val remoteId = row.remoteId
                        if (remoteId != null && absenceEventsRemote.update(remoteId, row.toRemoteUpdate()) == null) {
                            adoptNewerRemoteAbsenceEvent(row, remoteId, now)
                        } else {
                            markAbsenceEventSynced(row, row.remoteId, now)
                        }
                    }
                    row.remoteId == null -> {
                        val remoteId = try {
                            absenceEventsRemote.insert(row.toRemoteInsert()).id
                        } catch (e: Exception) {
                            if (RemoteSyncErrors.isPrimaryKeyViolation(e, ENTITY_ABSENCE_EVENTS)) row.localId else throw e
                        }
                        markAbsenceEventSynced(row, remoteId, now)
                    }
                    else -> {
                        val remoteId = row.remoteId
                        if (absenceEventsRemote.update(remoteId, row.toRemoteUpdate()) == null) {
                            adoptNewerRemoteAbsenceEvent(row, remoteId, now)
                        } else {
                            markAbsenceEventSynced(row, remoteId, now)
                        }
                    }
                }
            }.onFailure { error ->
                absenceEventDao.updateSyncState(
                    row.localId, SyncStatus.FAILED, row.remoteId, row.lastSyncedAt, error.message,
                )
            }
        }
    }

    private suspend fun markAbsenceEventSynced(
        row: com.elmtrackr.app.data.local.entity.AbsenceEventEntity,
        remoteId: String?,
        syncedAt: Long,
    ) {
        if (absenceEventDao.markSyncedIfUnchanged(row.localId, remoteId, syncedAt, row.updatedAt) == 0) {
            absenceEventDao.attachRemoteId(row.localId, remoteId, syncedAt)
        }
    }

    private suspend fun adoptNewerRemoteAbsenceEvent(
        row: com.elmtrackr.app.data.local.entity.AbsenceEventEntity,
        remoteId: String,
        syncedAt: Long,
    ) {
        val remote = absenceEventsRemote.findById(remoteId) ?: return
        absenceEventDao.upsert(remote.toLocalEntity(existingLocalId = row.localId))
        absenceEventDao.updateSyncState(row.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    private suspend fun pullAbsenceEvents(userId: String) {
        pullIncremental(
            userId = userId,
            entity = ENTITY_ABSENCE_EVENTS,
            fetchPage = { since, offset -> absenceEventsRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE, offset) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
            ownerOf = { it.userId },
        ) { remote ->
            val existing = absenceEventDao.getByRemoteId(remote.id)
            when {
                existing == null && remote.deletedAt != null -> Unit
                existing == null -> absenceEventDao.upsert(remote.toLocalEntity())
                existing.syncStatus != SyncStatus.SYNCED -> Unit
                isoToEpoch(remote.updatedAt) > existing.updatedAt ->
                    absenceEventDao.upsert(remote.toLocalEntity(existingLocalId = existing.localId))
            }
            true
        }
    }

    private suspend fun pushAbsenceAllocations(userId: String) {
        val now = Instant.now().toEpochMilli()
        for (row in absenceAllocationDao.getPendingSyncAllocations(userId)) {
            runCatching {
                when {
                    row.syncStatus == SyncStatus.SYNCED -> Unit
                    row.deletedAt != null -> {
                        val remoteId = row.remoteId
                        if (remoteId != null && allocationsRemote.update(remoteId, row.toRemoteUpdate()) == null) {
                            adoptNewerRemoteAllocation(row, remoteId, now)
                        } else {
                            markAllocationSynced(row, row.remoteId, now)
                        }
                    }
                    row.remoteId == null -> {
                        // Two parents, both real foreign keys on the server.
                        val eventRemoteId = idMapper.absenceEventLocalToRemote(row.absenceEventLocalId)
                            ?: return@runCatching
                        val workplaceRemoteId = idMapper.workplaceLocalToRemote(row.workplaceLocalId)
                            ?: return@runCatching
                        val remoteId = try {
                            allocationsRemote.insert(row.toRemoteInsert(eventRemoteId, workplaceRemoteId)).id
                        } catch (e: Exception) {
                            if (RemoteSyncErrors.isPrimaryKeyViolation(e, ENTITY_ABSENCE_ALLOCATIONS)) {
                                row.localId
                            } else {
                                throw e
                            }
                        }
                        markAllocationSynced(row, remoteId, now)
                    }
                    else -> {
                        val remoteId = row.remoteId
                        if (allocationsRemote.update(remoteId, row.toRemoteUpdate()) == null) {
                            adoptNewerRemoteAllocation(row, remoteId, now)
                        } else {
                            markAllocationSynced(row, remoteId, now)
                        }
                    }
                }
            }.onFailure { error ->
                absenceAllocationDao.updateSyncState(
                    row.localId, SyncStatus.FAILED, row.remoteId, row.lastSyncedAt, error.message,
                )
            }
        }
    }

    private suspend fun markAllocationSynced(
        row: com.elmtrackr.app.data.local.entity.AbsenceAllocationEntity,
        remoteId: String?,
        syncedAt: Long,
    ) {
        if (absenceAllocationDao.markSyncedIfUnchanged(row.localId, remoteId, syncedAt, row.updatedAt) == 0) {
            absenceAllocationDao.attachRemoteId(row.localId, remoteId, syncedAt)
        }
    }

    private suspend fun adoptNewerRemoteAllocation(
        row: com.elmtrackr.app.data.local.entity.AbsenceAllocationEntity,
        remoteId: String,
        syncedAt: Long,
    ) {
        val remote = allocationsRemote.findById(remoteId) ?: return
        absenceAllocationDao.upsert(
            remote.toLocalEntity(
                absenceEventLocalId = row.absenceEventLocalId,
                workplaceLocalId = row.workplaceLocalId,
                existingLocalId = row.localId,
            ),
        )
        absenceAllocationDao.updateSyncState(row.localId, SyncStatus.SYNCED, remoteId, syncedAt, null)
    }

    private suspend fun pullAbsenceAllocations(userId: String) {
        pullIncremental(
            userId = userId,
            entity = ENTITY_ABSENCE_ALLOCATIONS,
            fetchPage = { since, offset -> allocationsRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE, offset) },
            updatedAtIso = { it.updatedAt },
            remoteIdOf = { it.id },
            ownerOf = { it.userId },
        ) { remote ->
            val eventLocalId = idMapper.absenceEventRemoteToLocal(remote.absenceEventId)
                ?: return@pullIncremental false
            val workplaceLocalId = idMapper.workplaceRemoteToLocal(remote.workplaceId)
                ?: return@pullIncremental false
            val existing = absenceAllocationDao.getByRemoteId(remote.id)
            when {
                existing == null && remote.deletedAt != null -> Unit
                existing == null -> absenceAllocationDao.upsert(
                    remote.toLocalEntity(eventLocalId, workplaceLocalId),
                )
                existing.syncStatus != SyncStatus.SYNCED -> Unit
                isoToEpoch(remote.updatedAt) > existing.updatedAt ->
                    absenceAllocationDao.upsert(
                        remote.toLocalEntity(eventLocalId, workplaceLocalId, existingLocalId = existing.localId),
                    )
            }
            true
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
            fetchPage = { since, _ -> settingsRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE) },
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
            fetchPage = { since, _ -> profilesRemote.fetchUpdatedSince(since, PULL_PAGE_SIZE) },
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

        /**
         * Upper bound on pages per entity per run: 200k rows, far beyond any real
         * history. Only reachable if rows are written as fast as they are drained,
         * and it bounds a loop that would otherwise be unbounded.
         */
        const val MAX_PULL_PAGES = 1_000
        const val ENTITY_TASKS = "tasks"
        const val ENTITY_SHIFTS = "shifts"
        const val ENTITY_REFUND_CLAIMS = "refund_claims"
        const val ENTITY_COMPENSATION_PROFILES = "compensation_profiles"
        const val ENTITY_PREMIUM_PROFILES = "premium_profiles"
        const val ENTITY_WORKPLACES = "workplaces"
        const val ENTITY_LEAVE_POLICIES = "leave_policies"
        const val ENTITY_LEAVE_BALANCES = "leave_balance_snapshots"
        const val ENTITY_ABSENCE_EVENTS = "absence_events"
        const val ENTITY_ABSENCE_ALLOCATIONS = "absence_allocations"
        const val ENTITY_USER_SETTINGS = "user_settings"
        const val ENTITY_PROFILES = "profiles"
        const val ENTITY_PROJECTS = "projects"
        const val ENTITY_PROJECT_BILLING_RECORDS = "project_billing_records"
        const val ENTITY_PROJECT_PAYMENTS = "project_payments"
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
