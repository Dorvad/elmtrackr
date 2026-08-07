package com.elmtrackr.app.fake

import com.elmtrackr.app.data.local.dao.ProjectBillingRecordDao
import com.elmtrackr.app.data.local.dao.ProjectDao
import com.elmtrackr.app.data.local.dao.ProjectPaymentDao
import com.elmtrackr.app.data.local.entity.ProjectBillingRecordEntity
import com.elmtrackr.app.data.local.entity.ProjectEntity
import com.elmtrackr.app.data.local.entity.ProjectPaymentEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

private val PENDING = setOf(
    SyncStatus.PENDING_CREATE,
    SyncStatus.PENDING_UPDATE,
    SyncStatus.PENDING_DELETE,
    SyncStatus.FAILED,
)

class FakeProjectDao : ProjectDao {
    private val store = linkedMapOf<String, ProjectEntity>()
    private val flow = MutableStateFlow<List<ProjectEntity>>(emptyList())

    private fun refresh() { flow.value = store.values.toList() }

    private fun live(userId: String) = store.values.filter { it.userId == userId && it.deletedAt == null }

    override fun observeProjects(userId: String): Flow<List<ProjectEntity>> =
        flow.map { live(userId) }

    override fun observeActiveProjects(userId: String): Flow<List<ProjectEntity>> =
        flow.map { live(userId).filter { row -> row.archivedAt == null && row.workStatus != "ARCHIVED" } }

    override fun observeProject(localId: String): Flow<ProjectEntity?> =
        flow.map { store[localId]?.takeIf { row -> row.deletedAt == null } }

    override suspend fun getProjects(userId: String): List<ProjectEntity> = live(userId)

    override suspend fun getByLocalId(localId: String): ProjectEntity? = store[localId]

    override suspend fun getById(userId: String, localId: String): ProjectEntity? =
        store[localId]?.takeIf { it.userId == userId }

    override suspend fun getByRemoteId(remoteId: String): ProjectEntity? =
        store.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun hasAnyProject(userId: String): Boolean = live(userId).isNotEmpty()

    override suspend fun upsert(project: ProjectEntity) {
        store[project.localId] = project
        refresh()
    }

    override suspend fun getPendingSyncProjects(userId: String): List<ProjectEntity> =
        store.values.filter { it.userId == userId && it.syncStatus in PENDING }

    override suspend fun hasPendingSyncProjects(userId: String): Boolean =
        getPendingSyncProjects(userId).isNotEmpty()

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

    override suspend fun setArchived(
        localId: String,
        archivedAt: Long?,
        workStatus: String,
        syncStatus: SyncStatus,
        updatedAt: Long,
    ) {
        store[localId]?.let {
            store[localId] = it.copy(
                archivedAt = archivedAt,
                workStatus = workStatus,
                syncStatus = syncStatus,
                updatedAt = updatedAt,
            )
            refresh()
        }
    }

    override suspend fun softDelete(
        localId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    ) {
        store[localId]?.let {
            store[localId] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            refresh()
        }
    }

    override suspend fun adoptLegacyUser(userId: String) {
        store.replaceAll { _, row -> if (row.userId == "local-user") row.copy(userId = userId) else row }
        refresh()
    }

    override suspend fun getAllForUser(userId: String): List<ProjectEntity> =
        store.values.filter { it.userId == userId }

    override suspend fun deleteAllForUser(userId: String) {
        store.entries.removeIf { it.value.userId == userId }
        refresh()
    }
}

class FakeProjectBillingRecordDao : ProjectBillingRecordDao {
    private val store = linkedMapOf<String, ProjectBillingRecordEntity>()
    private val flow = MutableStateFlow<List<ProjectBillingRecordEntity>>(emptyList())

    private fun refresh() { flow.value = store.values.toList() }

    override fun observeForProject(projectLocalId: String): Flow<List<ProjectBillingRecordEntity>> =
        flow.map { rows ->
            rows.filter { it.projectLocalId == projectLocalId && it.deletedAt == null }
                .sortedBy { it.billedOn }
        }

    override fun observeForUser(userId: String): Flow<List<ProjectBillingRecordEntity>> =
        flow.map { rows -> rows.filter { it.userId == userId && it.deletedAt == null } }

    override suspend fun getForProject(projectLocalId: String): List<ProjectBillingRecordEntity> =
        store.values.filter { it.projectLocalId == projectLocalId && it.deletedAt == null }
            .sortedBy { it.billedOn }

    override suspend fun getByLocalId(localId: String): ProjectBillingRecordEntity? = store[localId]

    override suspend fun getByRemoteId(remoteId: String): ProjectBillingRecordEntity? =
        store.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun upsert(record: ProjectBillingRecordEntity) {
        store[record.localId] = record
        refresh()
    }

    override suspend fun getPendingSyncRecords(userId: String): List<ProjectBillingRecordEntity> =
        store.values.filter { it.userId == userId && it.syncStatus in PENDING }

    override suspend fun setCancelled(
        localId: String,
        cancelledAt: Long?,
        syncStatus: SyncStatus,
        updatedAt: Long,
    ) {
        store[localId]?.let {
            store[localId] = it.copy(cancelledAt = cancelledAt, syncStatus = syncStatus, updatedAt = updatedAt)
            refresh()
        }
    }

    override suspend fun softDelete(
        localId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    ) {
        store[localId]?.let {
            store[localId] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            refresh()
        }
    }

    override suspend fun softDeleteForProject(
        projectLocalId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    ) {
        store.replaceAll { _, row ->
            if (row.projectLocalId == projectLocalId && row.deletedAt == null) {
                row.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            } else {
                row
            }
        }
        refresh()
    }

    override suspend fun getAllForUser(userId: String): List<ProjectBillingRecordEntity> =
        store.values.filter { it.userId == userId }

    override suspend fun deleteAllForUser(userId: String) {
        store.entries.removeIf { it.value.userId == userId }
        refresh()
    }
    override suspend fun hasPendingSyncRecords(userId: String): Boolean =
        store.values.any { it.userId == userId && it.syncStatus in PENDING }

    override suspend fun updateSyncState(
        localId: String,
        status: com.elmtrackr.app.data.local.entity.SyncStatus,
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
        }
        refresh()
    }

}

class FakeProjectPaymentDao : ProjectPaymentDao {
    private val store = linkedMapOf<String, ProjectPaymentEntity>()
    private val flow = MutableStateFlow<List<ProjectPaymentEntity>>(emptyList())

    private fun refresh() { flow.value = store.values.toList() }

    override fun observeForProject(projectLocalId: String): Flow<List<ProjectPaymentEntity>> =
        flow.map { rows ->
            rows.filter { it.projectLocalId == projectLocalId && it.deletedAt == null }
                .sortedBy { it.paidOn }
        }

    override fun observeForUser(userId: String): Flow<List<ProjectPaymentEntity>> =
        flow.map { rows -> rows.filter { it.userId == userId && it.deletedAt == null } }

    override suspend fun getForProject(projectLocalId: String): List<ProjectPaymentEntity> =
        store.values.filter { it.projectLocalId == projectLocalId && it.deletedAt == null }
            .sortedBy { it.paidOn }

    override suspend fun getForBillingRecord(billingRecordLocalId: String): List<ProjectPaymentEntity> =
        store.values.filter { it.billingRecordLocalId == billingRecordLocalId && it.deletedAt == null }
            .sortedBy { it.paidOn }

    override suspend fun getByLocalId(localId: String): ProjectPaymentEntity? = store[localId]

    override suspend fun getByRemoteId(remoteId: String): ProjectPaymentEntity? =
        store.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun upsert(payment: ProjectPaymentEntity) {
        store[payment.localId] = payment
        refresh()
    }

    override suspend fun getPendingSyncPayments(userId: String): List<ProjectPaymentEntity> =
        store.values.filter { it.userId == userId && it.syncStatus in PENDING }

    override suspend fun softDelete(
        localId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    ) {
        store[localId]?.let {
            store[localId] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            refresh()
        }
    }

    override suspend fun softDeleteForProject(
        projectLocalId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    ) {
        store.replaceAll { _, row ->
            if (row.projectLocalId == projectLocalId && row.deletedAt == null) {
                row.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            } else {
                row
            }
        }
        refresh()
    }

    override suspend fun softDeleteForBillingRecord(
        billingRecordLocalId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    ) {
        store.replaceAll { _, row ->
            if (row.billingRecordLocalId == billingRecordLocalId && row.deletedAt == null) {
                row.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            } else {
                row
            }
        }
        refresh()
    }

    override suspend fun getAllForUser(userId: String): List<ProjectPaymentEntity> =
        store.values.filter { it.userId == userId }

    override suspend fun deleteAllForUser(userId: String) {
        store.entries.removeIf { it.value.userId == userId }
        refresh()
    }
    override suspend fun hasPendingSyncPayments(userId: String): Boolean =
        store.values.any { it.userId == userId && it.syncStatus in PENDING }

    override suspend fun updateSyncState(
        localId: String,
        status: com.elmtrackr.app.data.local.entity.SyncStatus,
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
        }
        refresh()
    }

}
