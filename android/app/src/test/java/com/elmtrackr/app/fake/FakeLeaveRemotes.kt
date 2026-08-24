package com.elmtrackr.app.fake

import com.elmtrackr.app.data.remote.RemoteAbsenceAllocationDataSource
import com.elmtrackr.app.data.remote.RemoteAbsenceAllocationInsert
import com.elmtrackr.app.data.remote.RemoteAbsenceAllocationRow
import com.elmtrackr.app.data.remote.RemoteAbsenceAllocationUpdate
import com.elmtrackr.app.data.remote.RemoteAbsenceEventDataSource
import com.elmtrackr.app.data.remote.RemoteAbsenceEventInsert
import com.elmtrackr.app.data.remote.RemoteAbsenceEventRow
import com.elmtrackr.app.data.remote.RemoteAbsenceEventUpdate
import com.elmtrackr.app.data.remote.RemoteLeaveBalanceSnapshotDataSource
import com.elmtrackr.app.data.remote.RemoteLeaveBalanceSnapshotInsert
import com.elmtrackr.app.data.remote.RemoteLeaveBalanceSnapshotRow
import com.elmtrackr.app.data.remote.RemoteLeaveBalanceSnapshotUpdate
import com.elmtrackr.app.data.remote.RemoteLeavePolicyDataSource
import com.elmtrackr.app.data.remote.RemoteLeavePolicyInsert
import com.elmtrackr.app.data.remote.RemoteLeavePolicyRow
import com.elmtrackr.app.data.remote.RemoteLeavePolicyUpdate
import com.elmtrackr.app.data.remote.RemoteWorkplaceDataSource
import com.elmtrackr.app.data.remote.RemoteWorkplaceInsert
import com.elmtrackr.app.data.remote.RemoteWorkplaceRow
import com.elmtrackr.app.data.remote.RemoteWorkplaceUpdate

/**
 * In-memory doubles for the leave tables' remotes.
 *
 * [rows] is the server. An insert appends and echoes the row back; an update
 * applies only when the caller's `clientUpdatedAt` is not older than the stored
 * one and otherwise returns null — the same write guard the PostgREST filter
 * enforces, so a test can exercise "a newer edit exists remotely" without a
 * server.
 *
 * The insert bodies are deliberately minimal: they carry the fields the sync
 * steps read back, which is the id, the owner and the timestamps.
 */

class FakeWorkplaceRemote : RemoteWorkplaceDataSource {
    val rows = mutableListOf<RemoteWorkplaceRow>()
    var insertFailure: (() -> Nothing)? = null

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteWorkplaceRow> = rows
        .sortedWith(compareBy({ it.updatedAt }, { it.id }))
        .filter { sinceIso == null || it.updatedAt >= sinceIso }
        .drop(offset)
        .take(limit)

    override suspend fun findById(remoteId: String): RemoteWorkplaceRow? = rows.firstOrNull { it.id == remoteId }

    override suspend fun insert(row: RemoteWorkplaceInsert): RemoteWorkplaceRow {
        insertFailure?.invoke()
        val stored = row.toRow()
        rows += stored
        return stored
    }

    override suspend fun update(remoteId: String, row: RemoteWorkplaceUpdate): RemoteWorkplaceRow? {
        val index = rows.indexOfFirst { it.id == remoteId }
        if (index < 0) return null
        val existing = rows[index]
        // The client_updated_at guard: an older write loses.
        val storedGuard = existing.clientUpdatedAt
        if (storedGuard != null && storedGuard > row.clientUpdatedAt) return null
        val updated = existing.merge(row)
        rows[index] = updated
        return updated
    }
}

class FakeLeavePolicyRemote : RemoteLeavePolicyDataSource {
    val rows = mutableListOf<RemoteLeavePolicyRow>()
    var insertFailure: (() -> Nothing)? = null

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteLeavePolicyRow> = rows
        .sortedWith(compareBy({ it.updatedAt }, { it.id }))
        .filter { sinceIso == null || it.updatedAt >= sinceIso }
        .drop(offset)
        .take(limit)

    override suspend fun findById(remoteId: String): RemoteLeavePolicyRow? = rows.firstOrNull { it.id == remoteId }

    override suspend fun insert(row: RemoteLeavePolicyInsert): RemoteLeavePolicyRow {
        insertFailure?.invoke()
        val stored = row.toRow()
        rows += stored
        return stored
    }

    override suspend fun update(remoteId: String, row: RemoteLeavePolicyUpdate): RemoteLeavePolicyRow? {
        val index = rows.indexOfFirst { it.id == remoteId }
        if (index < 0) return null
        val existing = rows[index]
        // The client_updated_at guard: an older write loses.
        val storedGuard = existing.clientUpdatedAt
        if (storedGuard != null && storedGuard > row.clientUpdatedAt) return null
        val updated = existing.merge(row)
        rows[index] = updated
        return updated
    }
}

class FakeAbsenceEventRemote : RemoteAbsenceEventDataSource {
    val rows = mutableListOf<RemoteAbsenceEventRow>()
    var insertFailure: (() -> Nothing)? = null

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteAbsenceEventRow> = rows
        .sortedWith(compareBy({ it.updatedAt }, { it.id }))
        .filter { sinceIso == null || it.updatedAt >= sinceIso }
        .drop(offset)
        .take(limit)

    override suspend fun findById(remoteId: String): RemoteAbsenceEventRow? = rows.firstOrNull { it.id == remoteId }

    override suspend fun insert(row: RemoteAbsenceEventInsert): RemoteAbsenceEventRow {
        insertFailure?.invoke()
        val stored = row.toRow()
        rows += stored
        return stored
    }

    override suspend fun update(remoteId: String, row: RemoteAbsenceEventUpdate): RemoteAbsenceEventRow? {
        val index = rows.indexOfFirst { it.id == remoteId }
        if (index < 0) return null
        val existing = rows[index]
        // The client_updated_at guard: an older write loses.
        val storedGuard = existing.clientUpdatedAt
        if (storedGuard != null && storedGuard > row.clientUpdatedAt) return null
        val updated = existing.merge(row)
        rows[index] = updated
        return updated
    }
}

class FakeAbsenceAllocationRemote : RemoteAbsenceAllocationDataSource {
    val rows = mutableListOf<RemoteAbsenceAllocationRow>()
    var insertFailure: (() -> Nothing)? = null

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteAbsenceAllocationRow> = rows
        .sortedWith(compareBy({ it.updatedAt }, { it.id }))
        .filter { sinceIso == null || it.updatedAt >= sinceIso }
        .drop(offset)
        .take(limit)

    override suspend fun findById(remoteId: String): RemoteAbsenceAllocationRow? = rows.firstOrNull { it.id == remoteId }

    override suspend fun insert(row: RemoteAbsenceAllocationInsert): RemoteAbsenceAllocationRow {
        insertFailure?.invoke()
        val stored = row.toRow()
        rows += stored
        return stored
    }

    override suspend fun update(remoteId: String, row: RemoteAbsenceAllocationUpdate): RemoteAbsenceAllocationRow? {
        val index = rows.indexOfFirst { it.id == remoteId }
        if (index < 0) return null
        val existing = rows[index]
        // The client_updated_at guard: an older write loses.
        val storedGuard = existing.clientUpdatedAt
        if (storedGuard != null && storedGuard > row.clientUpdatedAt) return null
        val updated = existing.merge(row)
        rows[index] = updated
        return updated
    }
}

class FakeLeaveBalanceSnapshotRemote : RemoteLeaveBalanceSnapshotDataSource {
    val rows = mutableListOf<RemoteLeaveBalanceSnapshotRow>()
    var insertFailure: (() -> Nothing)? = null

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteLeaveBalanceSnapshotRow> = rows
        .sortedWith(compareBy({ it.updatedAt }, { it.id }))
        .filter { sinceIso == null || it.updatedAt >= sinceIso }
        .drop(offset)
        .take(limit)

    override suspend fun findById(remoteId: String): RemoteLeaveBalanceSnapshotRow? = rows.firstOrNull { it.id == remoteId }

    override suspend fun insert(row: RemoteLeaveBalanceSnapshotInsert): RemoteLeaveBalanceSnapshotRow {
        insertFailure?.invoke()
        val stored = row.toRow()
        rows += stored
        return stored
    }

    override suspend fun update(remoteId: String, row: RemoteLeaveBalanceSnapshotUpdate): RemoteLeaveBalanceSnapshotRow? {
        val index = rows.indexOfFirst { it.id == remoteId }
        if (index < 0) return null
        val existing = rows[index]
        // The client_updated_at guard: an older write loses.
        val storedGuard = existing.clientUpdatedAt
        if (storedGuard != null && storedGuard > row.clientUpdatedAt) return null
        val updated = existing.merge(row)
        rows[index] = updated
        return updated
    }
}

private fun RemoteWorkplaceInsert.toRow() = RemoteWorkplaceRow(
    id = id, userId = userId, name = name, regionCode = regionCode, currencyCode = currencyCode,
    timezone = timezone, employmentStartDate = employmentStartDate, isDefault = isDefault,
    isArchived = isArchived, createdAt = clientUpdatedAt, updatedAt = clientUpdatedAt,
    deletedAt = null, clientUpdatedAt = clientUpdatedAt,
)

private fun RemoteWorkplaceRow.merge(update: RemoteWorkplaceUpdate) = copy(
    name = update.name, regionCode = update.regionCode, currencyCode = update.currencyCode,
    timezone = update.timezone, employmentStartDate = update.employmentStartDate,
    isDefault = update.isDefault, isArchived = update.isArchived, deletedAt = update.deletedAt,
    updatedAt = update.clientUpdatedAt, clientUpdatedAt = update.clientUpdatedAt,
)

private fun RemoteLeavePolicyInsert.toRow() = RemoteLeavePolicyRow(
    id = id, userId = userId, workplaceId = workplaceId, regionCode = regionCode,
    rulesJson = rulesJson, effectiveFrom = effectiveFrom, effectiveUntil = effectiveUntil,
    isActive = isActive, createdAt = clientUpdatedAt, updatedAt = clientUpdatedAt,
    deletedAt = null, clientUpdatedAt = clientUpdatedAt,
)

private fun RemoteLeavePolicyRow.merge(update: RemoteLeavePolicyUpdate) = copy(
    regionCode = update.regionCode, rulesJson = update.rulesJson,
    effectiveFrom = update.effectiveFrom, effectiveUntil = update.effectiveUntil,
    isActive = update.isActive, deletedAt = update.deletedAt,
    updatedAt = update.clientUpdatedAt, clientUpdatedAt = update.clientUpdatedAt,
)

private fun RemoteAbsenceEventInsert.toRow() = RemoteAbsenceEventRow(
    id = id, userId = userId, type = type, startDate = startDate, endDate = endDate,
    notes = notes, createdAt = clientUpdatedAt, updatedAt = clientUpdatedAt,
    deletedAt = null, clientUpdatedAt = clientUpdatedAt,
)

private fun RemoteAbsenceEventRow.merge(update: RemoteAbsenceEventUpdate) = copy(
    type = update.type, startDate = update.startDate, endDate = update.endDate,
    notes = update.notes, deletedAt = update.deletedAt,
    updatedAt = update.clientUpdatedAt, clientUpdatedAt = update.clientUpdatedAt,
)

private fun RemoteAbsenceAllocationInsert.toRow() = RemoteAbsenceAllocationRow(
    id = id, userId = userId, absenceEventId = absenceEventId, workplaceId = workplaceId,
    affectedDate = affectedDate, entitlementUnits = entitlementUnits, unit = unit,
    expectedWorkMinutes = expectedWorkMinutes, policySnapshotJson = policySnapshotJson,
    calculationSnapshotJson = calculationSnapshotJson, estimatedGrossPay = estimatedGrossPay,
    createdAt = clientUpdatedAt, updatedAt = clientUpdatedAt, deletedAt = null,
    clientUpdatedAt = clientUpdatedAt,
)

private fun RemoteAbsenceAllocationRow.merge(update: RemoteAbsenceAllocationUpdate) = copy(
    affectedDate = update.affectedDate, entitlementUnits = update.entitlementUnits,
    unit = update.unit, expectedWorkMinutes = update.expectedWorkMinutes,
    policySnapshotJson = update.policySnapshotJson,
    calculationSnapshotJson = update.calculationSnapshotJson,
    estimatedGrossPay = update.estimatedGrossPay, deletedAt = update.deletedAt,
    updatedAt = update.clientUpdatedAt, clientUpdatedAt = update.clientUpdatedAt,
)

private fun RemoteLeaveBalanceSnapshotInsert.toRow() = RemoteLeaveBalanceSnapshotRow(
    id = id, userId = userId, workplaceId = workplaceId, balanceType = balanceType,
    balance = balance, unit = unit, asOfDate = asOfDate, source = source, label = label,
    notes = notes, createdAt = clientUpdatedAt, updatedAt = clientUpdatedAt,
    deletedAt = null, clientUpdatedAt = clientUpdatedAt,
)

private fun RemoteLeaveBalanceSnapshotRow.merge(update: RemoteLeaveBalanceSnapshotUpdate) = copy(
    balanceType = update.balanceType, balance = update.balance, unit = update.unit,
    asOfDate = update.asOfDate, source = update.source, label = update.label,
    notes = update.notes, deletedAt = update.deletedAt,
    updatedAt = update.clientUpdatedAt, clientUpdatedAt = update.clientUpdatedAt,
)
