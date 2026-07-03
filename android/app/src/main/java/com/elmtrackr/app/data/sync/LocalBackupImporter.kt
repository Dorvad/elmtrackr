package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.dao.CompensationProfileDao
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.dao.TaskDao
import com.elmtrackr.app.data.local.entity.SyncStatus
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class BackupImportSummary(
    val importedTasks: Int = 0,
    val importedShifts: Int = 0,
    val importedRefundClaims: Int = 0,
    val importedCompensationProfiles: Int = 0,
    val importedSettings: Int = 0,
    val skipped: Int = 0,
) {
    val importedTotal: Int
        get() = importedTasks + importedShifts + importedRefundClaims +
            importedCompensationProfiles + importedSettings
}

class BackupImportException(message: String) : Exception(message)

/**
 * Imports a [LocalBackupDocument] produced by [LocalBackupExporter].
 *
 * Merge-missing semantics: rows whose localId already exists locally are
 * skipped, so importing never overwrites local edits and re-importing the
 * same file is a no-op. All rows are adopted to the importing user. When the
 * backup was exported by a different account, remote linkage is stripped and
 * rows are marked PENDING_CREATE so the next sync uploads fresh copies.
 * Soft-deleted rows are not imported — a backup restores data, not deletions.
 */
object LocalBackupImporter {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun import(
        rawJson: String,
        currentUserId: String,
        taskDao: TaskDao,
        shiftDao: ShiftDao,
        refundClaimDao: RefundClaimDao,
        settingsDao: SettingsDao,
        compensationProfileDao: CompensationProfileDao,
    ): BackupImportSummary {
        val document = try {
            json.decodeFromString<LocalBackupDocument>(rawJson)
        } catch (e: SerializationException) {
            throw BackupImportException("This file is not an ElmTrackr backup.")
        } catch (e: IllegalArgumentException) {
            throw BackupImportException("This file is not an ElmTrackr backup.")
        }
        if (document.formatVersion < BACKUP_FORMAT_VERSION) {
            throw BackupImportException(
                "This backup was created by an older app version and can't be imported.",
            )
        }

        val sameUser = document.userId == currentUserId
        var skipped = 0

        var importedProfiles = 0
        for (row in document.compensationProfiles) {
            if (row.deletedAt != null) continue
            if (compensationProfileDao.getByLocalId(row.localId) != null) {
                skipped++
                continue
            }
            compensationProfileDao.insert(row.toEntity(currentUserId).adopt(sameUser))
            importedProfiles++
        }

        var importedTasks = 0
        for (row in document.tasks) {
            if (row.deletedAt != null) continue
            if (taskDao.getByLocalId(row.localId) != null) {
                skipped++
                continue
            }
            taskDao.insert(row.toEntity(currentUserId).adopt(sameUser))
            importedTasks++
        }

        var importedShifts = 0
        for (row in document.shifts) {
            if (row.deletedAt != null) continue
            if (shiftDao.getShiftById(row.localId) != null) {
                skipped++
                continue
            }
            shiftDao.insertShift(row.toEntity(currentUserId).adopt(sameUser))
            importedShifts++
        }

        var importedClaims = 0
        for (row in document.refundClaims) {
            if (row.deletedAt != null) continue
            if (refundClaimDao.getClaimById(row.localId) != null) {
                skipped++
                continue
            }
            // A claim without its shift can't be displayed or synced.
            if (shiftDao.getShiftById(row.shiftLocalId) == null) {
                skipped++
                continue
            }
            refundClaimDao.insertClaim(row.toEntity(currentUserId).adopt(sameUser))
            importedClaims++
        }

        var importedSettings = 0
        // user_settings has a unique userId index; never clobber existing settings.
        if (settingsDao.getSettings(currentUserId) == null) {
            document.userSettings.firstOrNull { it.deletedAt == null }?.let { row ->
                if (settingsDao.getAllSettingsForUser(currentUserId).isEmpty()) {
                    settingsDao.insertSettings(row.toEntity(currentUserId).adopt(sameUser))
                    importedSettings++
                }
            }
        } else {
            skipped += document.userSettings.count { it.deletedAt == null }
        }

        return BackupImportSummary(
            importedTasks = importedTasks,
            importedShifts = importedShifts,
            importedRefundClaims = importedClaims,
            importedCompensationProfiles = importedProfiles,
            importedSettings = importedSettings,
            skipped = skipped,
        )
    }

    private fun com.elmtrackr.app.data.local.entity.TaskEntity.adopt(sameUser: Boolean) =
        if (sameUser) this else copy(remoteId = null, lastSyncedAt = null, syncStatus = SyncStatus.PENDING_CREATE)

    private fun com.elmtrackr.app.data.local.entity.ShiftEntity.adopt(sameUser: Boolean) =
        if (sameUser) this else copy(remoteId = null, lastSyncedAt = null, syncStatus = SyncStatus.PENDING_CREATE)

    private fun com.elmtrackr.app.data.local.entity.RefundClaimEntity.adopt(sameUser: Boolean) =
        if (sameUser) this else copy(remoteId = null, lastSyncedAt = null, syncStatus = SyncStatus.PENDING_CREATE)

    private fun com.elmtrackr.app.data.local.entity.UserSettingsEntity.adopt(sameUser: Boolean) =
        if (sameUser) this else copy(remoteId = null, lastSyncedAt = null, syncStatus = SyncStatus.PENDING_CREATE)

    private fun com.elmtrackr.app.data.local.entity.CompensationProfileEntity.adopt(sameUser: Boolean) =
        if (sameUser) this else copy(remoteId = null, lastSyncedAt = null, syncStatus = SyncStatus.PENDING_CREATE)
}
