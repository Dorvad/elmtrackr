package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.dao.CompensationProfileDao
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.dao.TaskDao
import com.elmtrackr.app.data.local.entity.AbsenceAllocationEntity
import com.elmtrackr.app.data.local.entity.AbsenceEventEntity
import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.LeaveBalanceSnapshotEntity
import com.elmtrackr.app.data.local.entity.LeavePolicyEntity
import com.elmtrackr.app.data.local.entity.PremiumProfileEntity
import com.elmtrackr.app.data.local.entity.ProjectBillingRecordEntity
import com.elmtrackr.app.data.local.entity.ProjectEntity
import com.elmtrackr.app.data.local.entity.ProjectPaymentEntity
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.TaskEntity
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import com.elmtrackr.app.data.local.entity.WorkplaceEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object LocalBackupExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun export(
        userId: String,
        taskDao: TaskDao,
        shiftDao: ShiftDao,
        refundClaimDao: RefundClaimDao,
        settingsDao: SettingsDao,
        compensationProfileDao: CompensationProfileDao,
        premiumProfileDao: com.elmtrackr.app.data.local.dao.PremiumProfileDao,
        receiptDao: com.elmtrackr.app.data.local.dao.ReceiptDao,
        projectDao: com.elmtrackr.app.data.local.dao.ProjectDao,
        projectBillingRecordDao: com.elmtrackr.app.data.local.dao.ProjectBillingRecordDao,
        projectPaymentDao: com.elmtrackr.app.data.local.dao.ProjectPaymentDao,
        workplaceDao: com.elmtrackr.app.data.local.dao.WorkplaceDao,
        leavePolicyDao: com.elmtrackr.app.data.local.dao.LeavePolicyDao,
        absenceEventDao: com.elmtrackr.app.data.local.dao.AbsenceEventDao,
        absenceAllocationDao: com.elmtrackr.app.data.local.dao.AbsenceAllocationDao,
        leaveBalanceSnapshotDao: com.elmtrackr.app.data.local.dao.LeaveBalanceSnapshotDao,
        appVersion: String,
    ): String {
        val backup = LocalBackupDocument(
            formatVersion = BACKUP_FORMAT_VERSION,
            exportedAt = Instant.now().toString(),
            userId = userId,
            appVersion = appVersion,
            tasks = taskDao.getAllTasksForUser(userId).map { it.toBackupRow() },
            shifts = shiftDao.getAllShiftsForUser(userId).map { it.toBackupRow() },
            refundClaims = refundClaimDao.getAllClaimsForUser(userId).map { it.toBackupRow() },
            userSettings = settingsDao.getAllSettingsForUser(userId).map { it.toBackupRow() },
            compensationProfiles = compensationProfileDao.getAllProfilesForUser(userId).map { it.toBackupRow() },
            premiumProfiles = premiumProfileDao.getAllProfilesForUser(userId).map { it.toBackupRow() },
            receipts = receiptDao.getAllForUser(userId).map { it.toBackupRow() },
            projects = projectDao.getAllForUser(userId).map { it.toBackupRow() },
            projectBillingRecords = projectBillingRecordDao.getAllForUser(userId)
                .map { it.toBackupRow() },
            projectPayments = projectPaymentDao.getAllForUser(userId).map { it.toBackupRow() },
            // Archived workplaces included: a workplace is archived rather than
            // deleted, and its absences and balances still point at it. Exporting
            // only the live ones would restore leave whose job had vanished.
            workplaces = workplaceDao.getAllIncludingArchived(userId).map { it.toBackupRow() },
            leavePolicies = leavePolicyDao.getAllForUser(userId).map { it.toBackupRow() },
            absenceEvents = absenceEventDao.getAllForUser(userId).map { it.toBackupRow() },
            absenceAllocations = absenceAllocationDao.getAllForUser(userId).map { it.toBackupRow() },
            leaveBalanceSnapshots = leaveBalanceSnapshotDao.getAllForUser(userId).map { it.toBackupRow() },
        )
        return json.encodeToString(backup)
    }
}

object SyncDetailsBuilder {

    private val pendingStatuses = setOf(
        SyncStatus.PENDING_CREATE,
        SyncStatus.PENDING_UPDATE,
        SyncStatus.PENDING_DELETE,
        SyncStatus.FAILED,
    )

    fun build(
        tasks: List<TaskEntity>,
        shifts: List<ShiftEntity>,
        claims: List<RefundClaimEntity>,
        settings: UserSettingsEntity?,
        profiles: List<CompensationProfileEntity>,
        lastSyncStatus: String?,
        workplaces: List<WorkplaceEntity> = emptyList(),
        leavePolicies: List<LeavePolicyEntity> = emptyList(),
        absenceEvents: List<AbsenceEventEntity> = emptyList(),
        absenceAllocations: List<AbsenceAllocationEntity> = emptyList(),
        leaveBalances: List<LeaveBalanceSnapshotEntity> = emptyList(),
        premiumProfiles: List<PremiumProfileEntity> = emptyList(),
        projects: List<ProjectEntity> = emptyList(),
        billingRecords: List<ProjectBillingRecordEntity> = emptyList(),
        projectPayments: List<ProjectPaymentEntity> = emptyList(),
    ): SyncDetails {
        val activeTasks = tasks.filter { it.deletedAt == null }
        val activeShifts = shifts.filter { it.deletedAt == null }
        val activeClaims = claims.filter { it.deletedAt == null }
        val activeProfiles = profiles.filter { it.deletedAt == null }
        val settingsList = settings?.let { listOf(it) }.orEmpty().filter { it.deletedAt == null }
        // Workplaces and leave rows that are soft-deleted still need to sync their
        // tombstones, so pending/failed counts include them. Synced counts stay
        // about live rows only — a tombstone is not "on the device" anymore.
        val liveWorkplaces = workplaces.filter { it.deletedAt == null }
        val livePolicies = leavePolicies.filter { it.deletedAt == null }
        val liveEvents = absenceEvents.filter { it.deletedAt == null }
        val liveAllocations = absenceAllocations.filter { it.deletedAt == null }
        val liveBalances = leaveBalances.filter { it.deletedAt == null }
        val livePremium = premiumProfiles.filter { it.deletedAt == null }
        val liveProjects = projects.filter { it.deletedAt == null }
        val liveBilling = billingRecords.filter { it.deletedAt == null }
        val livePayments = projectPayments.filter { it.deletedAt == null }

        val pendingByType = listOf(
            summaryFor(SyncEntityType.TASKS, activeTasks) { it.syncStatus },
            summaryFor(SyncEntityType.SHIFTS, activeShifts) { it.syncStatus },
            summaryFor(SyncEntityType.REFUND_CLAIMS, activeClaims) { it.syncStatus },
            summaryFor(SyncEntityType.USER_SETTINGS, settingsList) { it.syncStatus },
            summaryFor(SyncEntityType.COMPENSATION_PROFILES, activeProfiles) { it.syncStatus },
            summaryFor(SyncEntityType.WORKPLACES, workplaces) { it.syncStatus },
            summaryFor(SyncEntityType.LEAVE_POLICIES, leavePolicies) { it.syncStatus },
            summaryFor(SyncEntityType.ABSENCE_EVENTS, absenceEvents) { it.syncStatus },
            summaryFor(SyncEntityType.ABSENCE_ALLOCATIONS, absenceAllocations) { it.syncStatus },
            summaryFor(SyncEntityType.LEAVE_BALANCES, leaveBalances) { it.syncStatus },
            summaryFor(SyncEntityType.PREMIUM_PROFILES, livePremium) { it.syncStatus },
            summaryFor(SyncEntityType.PROJECTS, liveProjects) { it.syncStatus },
            summaryFor(SyncEntityType.PROJECT_BILLING, liveBilling) { it.syncStatus },
            summaryFor(SyncEntityType.PROJECT_PAYMENTS, livePayments) { it.syncStatus },
        ).map { row ->
            // Synced counts for work-profile tables should reflect live rows.
            when (row.entityType) {
                SyncEntityType.WORKPLACES -> row.copy(syncedCount = liveWorkplaces.count { it.syncStatus == SyncStatus.SYNCED })
                SyncEntityType.LEAVE_POLICIES -> row.copy(syncedCount = livePolicies.count { it.syncStatus == SyncStatus.SYNCED })
                SyncEntityType.ABSENCE_EVENTS -> row.copy(syncedCount = liveEvents.count { it.syncStatus == SyncStatus.SYNCED })
                SyncEntityType.ABSENCE_ALLOCATIONS -> row.copy(syncedCount = liveAllocations.count { it.syncStatus == SyncStatus.SYNCED })
                SyncEntityType.LEAVE_BALANCES -> row.copy(syncedCount = liveBalances.count { it.syncStatus == SyncStatus.SYNCED })
                else -> row
            }
        }

        val failedRows = buildList {
            activeTasks.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(failedRow(SyncEntityType.TASKS, it.localId, it.name, it.syncStatus, it.lastSyncError))
            }
            activeShifts.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(failedRow(SyncEntityType.SHIFTS, it.localId, "Shift ${formatShiftLabel(it.startTime)}", it.syncStatus, it.lastSyncError))
            }
            activeClaims.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(failedRow(SyncEntityType.REFUND_CLAIMS, it.localId, "${it.direction} refund", it.syncStatus, it.lastSyncError))
            }
            settingsList.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(failedRow(SyncEntityType.USER_SETTINGS, it.localId, "User settings", it.syncStatus, it.lastSyncError))
            }
            activeProfiles.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(failedRow(SyncEntityType.COMPENSATION_PROFILES, it.localId, it.name, it.syncStatus, it.lastSyncError))
            }
            workplaces.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(failedRow(SyncEntityType.WORKPLACES, it.localId, it.name, it.syncStatus, it.lastSyncError))
            }
            leavePolicies.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(failedRow(SyncEntityType.LEAVE_POLICIES, it.localId, "Leave policy", it.syncStatus, it.lastSyncError))
            }
            absenceEvents.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(failedRow(SyncEntityType.ABSENCE_EVENTS, it.localId, it.type, it.syncStatus, it.lastSyncError))
            }
            absenceAllocations.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(failedRow(SyncEntityType.ABSENCE_ALLOCATIONS, it.localId, "Absence day", it.syncStatus, it.lastSyncError))
            }
            leaveBalances.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(failedRow(SyncEntityType.LEAVE_BALANCES, it.localId, "Leave balance", it.syncStatus, it.lastSyncError))
            }
            livePremium.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(failedRow(SyncEntityType.PREMIUM_PROFILES, it.localId, it.name, it.syncStatus, it.lastSyncError))
            }
            liveProjects.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(failedRow(SyncEntityType.PROJECTS, it.localId, it.name, it.syncStatus, it.lastSyncError))
            }
            liveBilling.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(failedRow(SyncEntityType.PROJECT_BILLING, it.localId, "Billing record", it.syncStatus, it.lastSyncError))
            }
            livePayments.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(failedRow(SyncEntityType.PROJECT_PAYMENTS, it.localId, "Payment", it.syncStatus, it.lastSyncError))
            }
        }

        val lastSuccessfulSyncAt = sequenceOf(
            activeTasks.mapNotNull { it.lastSyncedAt },
            activeShifts.mapNotNull { it.lastSyncedAt },
            activeClaims.mapNotNull { it.lastSyncedAt },
            settingsList.mapNotNull { it.lastSyncedAt },
            activeProfiles.mapNotNull { it.lastSyncedAt },
            liveWorkplaces.mapNotNull { it.lastSyncedAt },
            livePolicies.mapNotNull { it.lastSyncedAt },
            liveEvents.mapNotNull { it.lastSyncedAt },
            liveAllocations.mapNotNull { it.lastSyncedAt },
            liveBalances.mapNotNull { it.lastSyncedAt },
            livePremium.mapNotNull { it.lastSyncedAt },
            liveProjects.mapNotNull { it.lastSyncedAt },
            liveBilling.mapNotNull { it.lastSyncedAt },
            livePayments.mapNotNull { it.lastSyncedAt },
        ).flatten().maxOrNull()?.let(Instant::ofEpochMilli)
            ?: parseSuccessfulSyncInstant(lastSyncStatus)

        // Hide entity types the user has never used — a Sync Details screen
        // full of empty "Leave balances / Project payments" rows is noise.
        val visiblePendingByType = pendingByType.filter { it.pendingCount > 0 || it.syncedCount > 0 }

        return SyncDetails(
            lastSuccessfulSyncAt = lastSuccessfulSyncAt,
            lastSyncStatus = lastSyncStatus,
            pendingByType = visiblePendingByType,
            failedRows = failedRows,
            totalPending = visiblePendingByType.sumOf { it.pendingCount },
            totalFailed = failedRows.size,
        )
    }

    private fun <T> summaryFor(
        type: SyncEntityType,
        rows: List<T>,
        statusOf: (T) -> SyncStatus,
    ): SyncPendingByType {
        val pending = rows.count { statusOf(it) in pendingStatuses }
        val synced = rows.count { statusOf(it) == SyncStatus.SYNCED }
        return SyncPendingByType(type, pending, synced)
    }

    private fun failedRow(
        type: SyncEntityType,
        localId: String,
        label: String,
        syncStatus: SyncStatus,
        errorMessage: String?,
    ) = SyncFailedRow(
        entityType = type,
        localId = localId,
        label = label,
        syncStatus = syncStatus,
        errorMessage = errorMessage,
    )

    private fun formatShiftLabel(startMillis: Long): String =
        Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))

    private fun parseSuccessfulSyncInstant(status: String?): Instant? {
        if (status.isNullOrBlank()) return null
        if (!status.startsWith("Synced") || status.contains("warnings", ignoreCase = true)) return null
        if (status.startsWith("Failed", ignoreCase = true)) return null
        val raw = status.removePrefix("Synced").trim()
        return runCatching { Instant.parse(raw) }.getOrNull()
    }
}
