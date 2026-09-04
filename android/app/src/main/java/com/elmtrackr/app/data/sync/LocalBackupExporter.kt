package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.dao.CompensationProfileDao
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.dao.TaskDao
import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.TaskEntity
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
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
    ): SyncDetails {
        val activeTasks = tasks.filter { it.deletedAt == null }
        val activeShifts = shifts.filter { it.deletedAt == null }
        val activeClaims = claims.filter { it.deletedAt == null }
        val activeProfiles = profiles.filter { it.deletedAt == null }
        val settingsList = settings?.let { listOf(it) }.orEmpty().filter { it.deletedAt == null }

        val pendingByType = listOf(
            summaryFor(SyncEntityType.TASKS, activeTasks) { it.syncStatus },
            summaryFor(SyncEntityType.SHIFTS, activeShifts) { it.syncStatus },
            summaryFor(SyncEntityType.REFUND_CLAIMS, activeClaims) { it.syncStatus },
            summaryFor(SyncEntityType.USER_SETTINGS, settingsList) { it.syncStatus },
            summaryFor(SyncEntityType.COMPENSATION_PROFILES, activeProfiles) { it.syncStatus },
        )

        val failedRows = buildList {
            activeTasks.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(it.toFailedRow(SyncEntityType.TASKS, it.name))
            }
            activeShifts.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(it.toFailedRow(SyncEntityType.SHIFTS, "Shift ${formatShiftLabel(it.startTime)}"))
            }
            activeClaims.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(it.toFailedRow(SyncEntityType.REFUND_CLAIMS, "${it.direction} refund"))
            }
            settingsList.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(it.toFailedRow(SyncEntityType.USER_SETTINGS, "User settings"))
            }
            activeProfiles.filter { it.syncStatus == SyncStatus.FAILED }.forEach {
                add(it.toFailedRow(SyncEntityType.COMPENSATION_PROFILES, it.name))
            }
        }

        val lastSuccessfulSyncAt = sequenceOf(
            activeTasks.mapNotNull { it.lastSyncedAt },
            activeShifts.mapNotNull { it.lastSyncedAt },
            activeClaims.mapNotNull { it.lastSyncedAt },
            settingsList.mapNotNull { it.lastSyncedAt },
            activeProfiles.mapNotNull { it.lastSyncedAt },
        ).flatten().maxOrNull()?.let(Instant::ofEpochMilli)
            ?: parseSuccessfulSyncInstant(lastSyncStatus)

        return SyncDetails(
            lastSuccessfulSyncAt = lastSuccessfulSyncAt,
            lastSyncStatus = lastSyncStatus,
            pendingByType = pendingByType,
            failedRows = failedRows,
            totalPending = pendingByType.sumOf { it.pendingCount },
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

    private fun TaskEntity.toFailedRow(type: SyncEntityType, label: String) = SyncFailedRow(
        entityType = type,
        localId = localId,
        label = label,
        syncStatus = syncStatus,
        errorMessage = lastSyncError,
    )

    private fun ShiftEntity.toFailedRow(type: SyncEntityType, label: String) = SyncFailedRow(
        entityType = type,
        localId = localId,
        label = label,
        syncStatus = syncStatus,
        errorMessage = lastSyncError,
    )

    private fun RefundClaimEntity.toFailedRow(type: SyncEntityType, label: String) = SyncFailedRow(
        entityType = type,
        localId = localId,
        label = label,
        syncStatus = syncStatus,
        errorMessage = lastSyncError,
    )

    private fun UserSettingsEntity.toFailedRow(type: SyncEntityType, label: String) = SyncFailedRow(
        entityType = type,
        localId = localId,
        label = label,
        syncStatus = syncStatus,
        errorMessage = lastSyncError,
    )

    private fun CompensationProfileEntity.toFailedRow(type: SyncEntityType, label: String) = SyncFailedRow(
        entityType = type,
        localId = localId,
        label = label,
        syncStatus = syncStatus,
        errorMessage = lastSyncError,
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
