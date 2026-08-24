package com.elmtrackr.app.fake

import com.elmtrackr.app.data.local.entity.AbsenceAllocationEntity
import com.elmtrackr.app.data.local.entity.AbsenceEventEntity
import com.elmtrackr.app.data.local.entity.LeaveBalanceSnapshotEntity
import com.elmtrackr.app.data.local.entity.LeavePolicyEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.WorkplaceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory doubles for the five leave DAOs.
 *
 * Real behaviour where sync depends on it — the pending-rows query, the
 * optimistic `markSyncedIfUnchanged` guard, and lookups by remote id — so a test
 * of the leave sync steps exercises the same rules the SQL does. The
 * query-shaped reads the sync path never touches return the obvious thing rather
 * than pretending to filter.
 */
private val PENDING = setOf(
    SyncStatus.PENDING_CREATE,
    SyncStatus.PENDING_UPDATE,
    SyncStatus.PENDING_DELETE,
    SyncStatus.FAILED,
)

class FakeWorkplaceDao : com.elmtrackr.app.data.local.dao.WorkplaceDao {

    private val store = LinkedHashMap<String, WorkplaceEntity>()
    private val flow = MutableStateFlow<List<WorkplaceEntity>>(emptyList())

    private fun refresh() { flow.value = store.values.toList() }

    fun seed(vararg rows: WorkplaceEntity) {
        rows.forEach { store[it.localId] = it }
        refresh()
    }

    fun rows(): List<WorkplaceEntity> = store.values.toList()

    suspend fun upsertRow(row: WorkplaceEntity) { store[row.localId] = row; refresh() }

    override suspend fun upsert(workplace: WorkplaceEntity) {
        store[workplace.localId] = workplace
        refresh()
    }

    override suspend fun getByUser(userId: String): List<WorkplaceEntity> =
        store.values.filter { it.userId == userId && it.deletedAt == null }

    override suspend fun getByLocalId(localId: String): WorkplaceEntity? = store[localId]

    override suspend fun getByRemoteId(remoteId: String): WorkplaceEntity? =
        store.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun getAllForUser(userId: String): List<WorkplaceEntity> =
        store.values.filter { it.userId == userId }

    override suspend fun getPendingSyncWorkplaces(userId: String): List<WorkplaceEntity> =
        store.values.filter { it.userId == userId && it.syncStatus in PENDING }

    override suspend fun hasPendingSyncWorkplaces(userId: String): Boolean =
        store.values.any { it.userId == userId && it.syncStatus in PENDING }

    override suspend fun updateSyncState(
        localId: String,
        status: SyncStatus,
        remoteId: String?,
        syncedAt: Long?,
        error: String?,
    ) {
        store[localId]?.let {
            store[localId] = it.copy(
                syncStatus = status,
                remoteId = remoteId,
                lastSyncedAt = syncedAt,
                lastSyncError = error,
            )
            refresh()
        }
    }

    /** The optimistic guard, kept honest: a row edited since the snapshot is not marked. */
    override suspend fun markSyncedIfUnchanged(
        localId: String,
        remoteId: String?,
        syncedAt: Long?,
        expectedUpdatedAt: Long,
    ): Int {
        val row = store[localId] ?: return 0
        if (row.updatedAt != expectedUpdatedAt) return 0
        store[localId] = row.copy(
            syncStatus = SyncStatus.SYNCED,
            remoteId = remoteId,
            lastSyncedAt = syncedAt,
            lastSyncError = null,
        )
        refresh()
        return 1
    }

    override suspend fun attachRemoteId(localId: String, remoteId: String?, syncedAt: Long?) {
        store[localId]?.let {
            store[localId] = it.copy(remoteId = remoteId, lastSyncedAt = syncedAt)
            refresh()
        }
    }

    override suspend fun softDelete(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long) {
        store[localId]?.let {
            store[localId] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            refresh()
        }
    }

    override suspend fun adoptLegacyUser(userId: String) {
        store.keys.toList().forEach { key ->
            store[key]?.takeIf { it.userId == "local-user" }?.let { store[key] = it.copy(userId = userId) }
        }
        refresh()
    }

    override suspend fun deleteAllForUser(userId: String) {
        store.entries.removeAll { it.value.userId == userId }
        refresh()
    }

    override fun observeWorkplaces(userId: String): Flow<List<WorkplaceEntity>> =
        flow.map { list -> list.filter { it.userId == userId && !it.isArchived && it.deletedAt == null } }

    override suspend fun getAllIncludingArchived(userId: String): List<WorkplaceEntity> =
        store.values.filter { it.userId == userId && it.deletedAt == null }

    override fun observeAllIncludingArchived(userId: String): Flow<List<WorkplaceEntity>> =
        flow.map { list -> list.filter { it.userId == userId && it.deletedAt == null } }

    override suspend fun getDefaultWorkplace(userId: String): WorkplaceEntity? =
        store.values.firstOrNull { it.userId == userId && it.isDefault && it.deletedAt == null }

    override suspend fun getById(userId: String, localId: String): WorkplaceEntity? =
        store[localId]?.takeIf { it.userId == userId }

    override suspend fun countForUser(userId: String): Int =
        store.values.count { it.userId == userId && it.deletedAt == null }

    override suspend fun clearDefaultForUser(userId: String) {
        store.keys.toList().forEach { key ->
            store[key]?.takeIf { it.userId == userId }?.let { store[key] = it.copy(isDefault = false) }
        }
        refresh()
    }

    override suspend fun archive(localId: String, syncStatus: SyncStatus, updatedAt: Long) {
        store[localId]?.let {
            store[localId] = it.copy(isArchived = true, syncStatus = syncStatus, updatedAt = updatedAt)
            refresh()
        }
    }

    /** Counts rows it would have touched; the sync path does not read the number. */
    override suspend fun adoptCompensationProfiles(userId: String, workplaceLocalId: String, updatedAt: Long): Int = 0

    override suspend fun adoptShifts(userId: String, workplaceLocalId: String, updatedAt: Long): Int = 0
}

class FakeLeavePolicyDao : com.elmtrackr.app.data.local.dao.LeavePolicyDao {

    private val store = LinkedHashMap<String, LeavePolicyEntity>()
    private val flow = MutableStateFlow<List<LeavePolicyEntity>>(emptyList())

    private fun refresh() { flow.value = store.values.toList() }

    fun seed(vararg rows: LeavePolicyEntity) {
        rows.forEach { store[it.localId] = it }
        refresh()
    }

    fun rows(): List<LeavePolicyEntity> = store.values.toList()

    suspend fun upsertRow(row: LeavePolicyEntity) { store[row.localId] = row; refresh() }

    override suspend fun upsert(row: LeavePolicyEntity) {
        store[row.localId] = row
        refresh()
    }

    override suspend fun getByUser(userId: String): List<LeavePolicyEntity> =
        store.values.filter { it.userId == userId && it.deletedAt == null }

    override suspend fun getByLocalId(localId: String): LeavePolicyEntity? = store[localId]

    override suspend fun getByRemoteId(remoteId: String): LeavePolicyEntity? =
        store.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun getAllForUser(userId: String): List<LeavePolicyEntity> =
        store.values.filter { it.userId == userId }

    override suspend fun getPendingSyncPolicies(userId: String): List<LeavePolicyEntity> =
        store.values.filter { it.userId == userId && it.syncStatus in PENDING }

    override suspend fun hasPendingSyncPolicies(userId: String): Boolean =
        store.values.any { it.userId == userId && it.syncStatus in PENDING }

    override suspend fun updateSyncState(
        localId: String,
        status: SyncStatus,
        remoteId: String?,
        syncedAt: Long?,
        error: String?,
    ) {
        store[localId]?.let {
            store[localId] = it.copy(
                syncStatus = status,
                remoteId = remoteId,
                lastSyncedAt = syncedAt,
                lastSyncError = error,
            )
            refresh()
        }
    }

    /** The optimistic guard, kept honest: a row edited since the snapshot is not marked. */
    override suspend fun markSyncedIfUnchanged(
        localId: String,
        remoteId: String?,
        syncedAt: Long?,
        expectedUpdatedAt: Long,
    ): Int {
        val row = store[localId] ?: return 0
        if (row.updatedAt != expectedUpdatedAt) return 0
        store[localId] = row.copy(
            syncStatus = SyncStatus.SYNCED,
            remoteId = remoteId,
            lastSyncedAt = syncedAt,
            lastSyncError = null,
        )
        refresh()
        return 1
    }

    override suspend fun attachRemoteId(localId: String, remoteId: String?, syncedAt: Long?) {
        store[localId]?.let {
            store[localId] = it.copy(remoteId = remoteId, lastSyncedAt = syncedAt)
            refresh()
        }
    }

    override suspend fun softDelete(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long) {
        store[localId]?.let {
            store[localId] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            refresh()
        }
    }

    override suspend fun adoptLegacyUser(userId: String) {
        store.keys.toList().forEach { key ->
            store[key]?.takeIf { it.userId == "local-user" }?.let { store[key] = it.copy(userId = userId) }
        }
        refresh()
    }

    override suspend fun deleteAllForUser(userId: String) {
        store.entries.removeAll { it.value.userId == userId }
        refresh()
    }

    override fun observePolicies(userId: String): Flow<List<LeavePolicyEntity>> =
        flow.map { list -> list.filter { it.userId == userId && it.deletedAt == null } }

    override suspend fun getForWorkplace(workplaceLocalId: String): List<LeavePolicyEntity> =
        store.values.filter { it.workplaceLocalId == workplaceLocalId && it.deletedAt == null }

    override suspend fun softDeleteForWorkplace(
        workplaceLocalId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    ) {
        store.keys.toList().forEach { key ->
            store[key]?.takeIf { it.workplaceLocalId == workplaceLocalId }?.let {
                store[key] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            }
        }
        refresh()
    }
}

class FakeAbsenceEventDao : com.elmtrackr.app.data.local.dao.AbsenceEventDao {

    private val store = LinkedHashMap<String, AbsenceEventEntity>()
    private val flow = MutableStateFlow<List<AbsenceEventEntity>>(emptyList())

    private fun refresh() { flow.value = store.values.toList() }

    fun seed(vararg rows: AbsenceEventEntity) {
        rows.forEach { store[it.localId] = it }
        refresh()
    }

    fun rows(): List<AbsenceEventEntity> = store.values.toList()

    suspend fun upsertRow(row: AbsenceEventEntity) { store[row.localId] = row; refresh() }

    override suspend fun upsert(row: AbsenceEventEntity) {
        store[row.localId] = row
        refresh()
    }

    override suspend fun getByUser(userId: String): List<AbsenceEventEntity> =
        store.values.filter { it.userId == userId && it.deletedAt == null }

    override suspend fun getByLocalId(localId: String): AbsenceEventEntity? = store[localId]

    override suspend fun getByRemoteId(remoteId: String): AbsenceEventEntity? =
        store.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun getAllForUser(userId: String): List<AbsenceEventEntity> =
        store.values.filter { it.userId == userId }

    override suspend fun getPendingSyncEvents(userId: String): List<AbsenceEventEntity> =
        store.values.filter { it.userId == userId && it.syncStatus in PENDING }

    override suspend fun hasPendingSyncEvents(userId: String): Boolean =
        store.values.any { it.userId == userId && it.syncStatus in PENDING }

    override suspend fun updateSyncState(
        localId: String,
        status: SyncStatus,
        remoteId: String?,
        syncedAt: Long?,
        error: String?,
    ) {
        store[localId]?.let {
            store[localId] = it.copy(
                syncStatus = status,
                remoteId = remoteId,
                lastSyncedAt = syncedAt,
                lastSyncError = error,
            )
            refresh()
        }
    }

    /** The optimistic guard, kept honest: a row edited since the snapshot is not marked. */
    override suspend fun markSyncedIfUnchanged(
        localId: String,
        remoteId: String?,
        syncedAt: Long?,
        expectedUpdatedAt: Long,
    ): Int {
        val row = store[localId] ?: return 0
        if (row.updatedAt != expectedUpdatedAt) return 0
        store[localId] = row.copy(
            syncStatus = SyncStatus.SYNCED,
            remoteId = remoteId,
            lastSyncedAt = syncedAt,
            lastSyncError = null,
        )
        refresh()
        return 1
    }

    override suspend fun attachRemoteId(localId: String, remoteId: String?, syncedAt: Long?) {
        store[localId]?.let {
            store[localId] = it.copy(remoteId = remoteId, lastSyncedAt = syncedAt)
            refresh()
        }
    }

    override suspend fun softDelete(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long) {
        store[localId]?.let {
            store[localId] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            refresh()
        }
    }

    override suspend fun adoptLegacyUser(userId: String) {
        store.keys.toList().forEach { key ->
            store[key]?.takeIf { it.userId == "local-user" }?.let { store[key] = it.copy(userId = userId) }
        }
        refresh()
    }

    override suspend fun deleteAllForUser(userId: String) {
        store.entries.removeAll { it.value.userId == userId }
        refresh()
    }

    override fun observeEvents(userId: String): Flow<List<AbsenceEventEntity>> =
        flow.map { list -> list.filter { it.userId == userId && it.deletedAt == null } }

    override suspend fun getOverlapping(
        userId: String,
        type: String,
        fromDate: Long,
        toDate: Long,
    ): List<AbsenceEventEntity> = store.values.filter {
        it.userId == userId && it.type == type && it.deletedAt == null &&
            it.startDate <= toDate && it.endDate >= fromDate
    }

    override suspend fun getById(userId: String, localId: String): AbsenceEventEntity? =
        store[localId]?.takeIf { it.userId == userId }
}

class FakeAbsenceAllocationDao : com.elmtrackr.app.data.local.dao.AbsenceAllocationDao {

    private val store = LinkedHashMap<String, AbsenceAllocationEntity>()
    private val flow = MutableStateFlow<List<AbsenceAllocationEntity>>(emptyList())

    private fun refresh() { flow.value = store.values.toList() }

    fun seed(vararg rows: AbsenceAllocationEntity) {
        rows.forEach { store[it.localId] = it }
        refresh()
    }

    fun rows(): List<AbsenceAllocationEntity> = store.values.toList()

    suspend fun upsertRow(row: AbsenceAllocationEntity) { store[row.localId] = row; refresh() }

    override suspend fun upsert(row: AbsenceAllocationEntity) {
        store[row.localId] = row
        refresh()
    }

    override suspend fun getByUser(userId: String): List<AbsenceAllocationEntity> =
        store.values.filter { it.userId == userId && it.deletedAt == null }

    override suspend fun getByLocalId(localId: String): AbsenceAllocationEntity? = store[localId]

    override suspend fun getByRemoteId(remoteId: String): AbsenceAllocationEntity? =
        store.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun getAllForUser(userId: String): List<AbsenceAllocationEntity> =
        store.values.filter { it.userId == userId }

    override suspend fun getPendingSyncAllocations(userId: String): List<AbsenceAllocationEntity> =
        store.values.filter { it.userId == userId && it.syncStatus in PENDING }

    override suspend fun hasPendingSyncAllocations(userId: String): Boolean =
        store.values.any { it.userId == userId && it.syncStatus in PENDING }

    override suspend fun updateSyncState(
        localId: String,
        status: SyncStatus,
        remoteId: String?,
        syncedAt: Long?,
        error: String?,
    ) {
        store[localId]?.let {
            store[localId] = it.copy(
                syncStatus = status,
                remoteId = remoteId,
                lastSyncedAt = syncedAt,
                lastSyncError = error,
            )
            refresh()
        }
    }

    /** The optimistic guard, kept honest: a row edited since the snapshot is not marked. */
    override suspend fun markSyncedIfUnchanged(
        localId: String,
        remoteId: String?,
        syncedAt: Long?,
        expectedUpdatedAt: Long,
    ): Int {
        val row = store[localId] ?: return 0
        if (row.updatedAt != expectedUpdatedAt) return 0
        store[localId] = row.copy(
            syncStatus = SyncStatus.SYNCED,
            remoteId = remoteId,
            lastSyncedAt = syncedAt,
            lastSyncError = null,
        )
        refresh()
        return 1
    }

    override suspend fun attachRemoteId(localId: String, remoteId: String?, syncedAt: Long?) {
        store[localId]?.let {
            store[localId] = it.copy(remoteId = remoteId, lastSyncedAt = syncedAt)
            refresh()
        }
    }

    override suspend fun softDelete(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long) {
        store[localId]?.let {
            store[localId] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            refresh()
        }
    }

    override suspend fun adoptLegacyUser(userId: String) {
        store.keys.toList().forEach { key ->
            store[key]?.takeIf { it.userId == "local-user" }?.let { store[key] = it.copy(userId = userId) }
        }
        refresh()
    }

    override suspend fun deleteAllForUser(userId: String) {
        store.entries.removeAll { it.value.userId == userId }
        refresh()
    }

    override fun observeAllocations(userId: String): Flow<List<AbsenceAllocationEntity>> =
        flow.map { list -> list.filter { it.userId == userId && it.deletedAt == null } }

    override fun observeInDateRange(
        userId: String,
        fromDate: Long,
        toDate: Long,
    ): Flow<List<AbsenceAllocationEntity>> = flow.map { list ->
        list.filter { it.userId == userId && it.deletedAt == null && it.affectedDate in fromDate..toDate }
    }

    override suspend fun getInDateRange(
        userId: String,
        fromDate: Long,
        toDate: Long,
    ): List<AbsenceAllocationEntity> = store.values.filter {
        it.userId == userId && it.deletedAt == null && it.affectedDate in fromDate..toDate
    }

    override suspend fun getForEvent(eventLocalId: String): List<AbsenceAllocationEntity> =
        store.values.filter { it.absenceEventLocalId == eventLocalId && it.deletedAt == null }

    override suspend fun getForWorkplaceAfter(
        workplaceLocalId: String,
        afterDate: Long,
    ): List<AbsenceAllocationEntity> = store.values.filter {
        it.workplaceLocalId == workplaceLocalId && it.deletedAt == null && it.affectedDate > afterDate
    }

    override suspend fun getForWorkplaceInRange(
        workplaceLocalId: String,
        fromDate: Long,
        toDate: Long,
    ): List<AbsenceAllocationEntity> = store.values.filter {
        it.workplaceLocalId == workplaceLocalId && it.deletedAt == null && it.affectedDate in fromDate..toDate
    }

    override suspend fun upsertAll(allocations: List<AbsenceAllocationEntity>) {
        allocations.forEach { store[it.localId] = it }
        refresh()
    }

    override suspend fun softDeleteForEvent(
        eventLocalId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    ) {
        store.keys.toList().forEach { key ->
            store[key]?.takeIf { it.absenceEventLocalId == eventLocalId }?.let {
                store[key] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            }
        }
        refresh()
    }

    override suspend fun softDeleteForWorkplace(
        workplaceLocalId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    ) {
        store.keys.toList().forEach { key ->
            store[key]?.takeIf { it.workplaceLocalId == workplaceLocalId }?.let {
                store[key] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            }
        }
        refresh()
    }
}

class FakeLeaveBalanceSnapshotDao : com.elmtrackr.app.data.local.dao.LeaveBalanceSnapshotDao {

    private val store = LinkedHashMap<String, LeaveBalanceSnapshotEntity>()
    private val flow = MutableStateFlow<List<LeaveBalanceSnapshotEntity>>(emptyList())

    private fun refresh() { flow.value = store.values.toList() }

    fun seed(vararg rows: LeaveBalanceSnapshotEntity) {
        rows.forEach { store[it.localId] = it }
        refresh()
    }

    fun rows(): List<LeaveBalanceSnapshotEntity> = store.values.toList()

    suspend fun upsertRow(row: LeaveBalanceSnapshotEntity) { store[row.localId] = row; refresh() }

    override suspend fun upsert(row: LeaveBalanceSnapshotEntity) {
        store[row.localId] = row
        refresh()
    }

    override suspend fun getByUser(userId: String): List<LeaveBalanceSnapshotEntity> =
        store.values.filter { it.userId == userId && it.deletedAt == null }

    override suspend fun getByLocalId(localId: String): LeaveBalanceSnapshotEntity? = store[localId]

    override suspend fun getByRemoteId(remoteId: String): LeaveBalanceSnapshotEntity? =
        store.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun getAllForUser(userId: String): List<LeaveBalanceSnapshotEntity> =
        store.values.filter { it.userId == userId }

    override suspend fun getPendingSyncSnapshots(userId: String): List<LeaveBalanceSnapshotEntity> =
        store.values.filter { it.userId == userId && it.syncStatus in PENDING }

    override suspend fun hasPendingSyncSnapshots(userId: String): Boolean =
        store.values.any { it.userId == userId && it.syncStatus in PENDING }

    override suspend fun updateSyncState(
        localId: String,
        status: SyncStatus,
        remoteId: String?,
        syncedAt: Long?,
        error: String?,
    ) {
        store[localId]?.let {
            store[localId] = it.copy(
                syncStatus = status,
                remoteId = remoteId,
                lastSyncedAt = syncedAt,
                lastSyncError = error,
            )
            refresh()
        }
    }

    /** The optimistic guard, kept honest: a row edited since the snapshot is not marked. */
    override suspend fun markSyncedIfUnchanged(
        localId: String,
        remoteId: String?,
        syncedAt: Long?,
        expectedUpdatedAt: Long,
    ): Int {
        val row = store[localId] ?: return 0
        if (row.updatedAt != expectedUpdatedAt) return 0
        store[localId] = row.copy(
            syncStatus = SyncStatus.SYNCED,
            remoteId = remoteId,
            lastSyncedAt = syncedAt,
            lastSyncError = null,
        )
        refresh()
        return 1
    }

    override suspend fun attachRemoteId(localId: String, remoteId: String?, syncedAt: Long?) {
        store[localId]?.let {
            store[localId] = it.copy(remoteId = remoteId, lastSyncedAt = syncedAt)
            refresh()
        }
    }

    override suspend fun softDelete(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long) {
        store[localId]?.let {
            store[localId] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            refresh()
        }
    }

    override suspend fun adoptLegacyUser(userId: String) {
        store.keys.toList().forEach { key ->
            store[key]?.takeIf { it.userId == "local-user" }?.let { store[key] = it.copy(userId = userId) }
        }
        refresh()
    }

    override suspend fun deleteAllForUser(userId: String) {
        store.entries.removeAll { it.value.userId == userId }
        refresh()
    }

    override fun observeSnapshots(userId: String): Flow<List<LeaveBalanceSnapshotEntity>> =
        flow.map { list -> list.filter { it.userId == userId && it.deletedAt == null } }

    override suspend fun getHistory(
        workplaceLocalId: String,
        balanceType: String,
    ): List<LeaveBalanceSnapshotEntity> = store.values.filter {
        it.workplaceLocalId == workplaceLocalId && it.balanceType == balanceType && it.deletedAt == null
    }.sortedByDescending { it.asOfDate }

    override suspend fun getLatest(
        workplaceLocalId: String,
        balanceType: String,
    ): LeaveBalanceSnapshotEntity? = getHistory(workplaceLocalId, balanceType).firstOrNull()

    override suspend fun softDeleteForWorkplace(
        workplaceLocalId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    ) {
        store.keys.toList().forEach { key ->
            store[key]?.takeIf { it.workplaceLocalId == workplaceLocalId }?.let {
                store[key] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            }
        }
        refresh()
    }
}
