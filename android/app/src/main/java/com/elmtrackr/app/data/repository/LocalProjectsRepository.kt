package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.dao.ProjectBillingRecordDao
import com.elmtrackr.app.data.local.dao.ProjectDao
import com.elmtrackr.app.data.local.dao.ProjectPaymentDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.TransactionRunner
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.data.local.mapper.toEntity
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.projects.PaymentValidationResult
import com.elmtrackr.app.domain.projects.ProjectPaymentRejectedException
import com.elmtrackr.app.domain.projects.ProjectPaymentValidator
import com.elmtrackr.app.domain.repository.ProjectsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed Paid Projects store, following the same offline-first shape as the
 * other repositories: soft deletes, per-row sync status, and a sync trigger
 * after every mutation.
 *
 * Payment writes go through [ProjectPaymentValidator] first — the repository is
 * the last place a bad amount can be stopped before it reaches storage, so the
 * rule lives here rather than only in a form.
 */
@Singleton
class LocalProjectsRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val billingRecordDao: ProjectBillingRecordDao,
    private val paymentDao: ProjectPaymentDao,
    private val shiftDao: ShiftDao,
    private val transactionRunner: TransactionRunner,
    private val syncTrigger: SyncTrigger,
) : ProjectsRepository {

    // ── Projects ──────────────────────────────────────────────────────────────

    override fun observeProjects(userId: String): Flow<List<Project>> =
        projectDao.observeProjects(userId).map { rows -> rows.map { it.toDomain() } }

    override fun observeActiveProjects(userId: String): Flow<List<Project>> =
        projectDao.observeActiveProjects(userId).map { rows -> rows.map { it.toDomain() } }

    override fun observeProject(projectId: String): Flow<Project?> =
        projectDao.observeProject(projectId).map { it?.toDomain() }

    override suspend fun getProjects(userId: String): List<Project> =
        projectDao.getProjects(userId).map { it.toDomain() }

    override suspend fun getProject(userId: String, projectId: String): Project? =
        projectDao.getById(userId, projectId)?.toDomain()

    override suspend fun hasAnyProject(userId: String): Boolean = projectDao.hasAnyProject(userId)

    override suspend fun upsertProject(project: Project): Project {
        val now = Instant.now()
        val id = project.id.ifBlank { UUID.randomUUID().toString() }
        val existing = projectDao.getById(project.userId, id)
            ?: project.remoteId?.let { projectDao.getByRemoteId(it) }
        val entity = project.copy(id = id).toEntity(
            syncStatus = syncStatusForMutation(existing?.syncStatus),
            remoteId = existing?.remoteId ?: project.remoteId,
        ).copy(
            createdAt = existing?.createdAt ?: now.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
        )
        projectDao.upsert(entity)
        syncTrigger.schedule()
        return entity.toDomain()
    }

    override suspend fun archiveProject(userId: String, projectId: String) {
        val existing = projectDao.getById(userId, projectId) ?: return
        val now = Instant.now().toEpochMilli()
        projectDao.setArchived(
            localId = existing.localId,
            archivedAt = now,
            workStatus = ProjectWorkStatus.ARCHIVED.name,
            syncStatus = syncStatusForMutation(existing.syncStatus),
            updatedAt = now,
        )
        syncTrigger.schedule()
    }

    override suspend fun unarchiveProject(userId: String, projectId: String) {
        val existing = projectDao.getById(userId, projectId) ?: return
        val now = Instant.now().toEpochMilli()
        projectDao.setArchived(
            localId = existing.localId,
            archivedAt = null,
            // Archiving is reversible; a restored project goes back to active
            // rather than guessing which earlier status it held.
            workStatus = ProjectWorkStatus.ACTIVE.name,
            syncStatus = syncStatusForMutation(existing.syncStatus),
            updatedAt = now,
        )
        syncTrigger.schedule()
    }

    /**
     * Deletes a project and returns how many shifts were released from it.
     *
     * Any project can be deleted, including one with tracked time. Deletion used to
     * be refused unless the project was an untouched draft, which meant a project
     * used once — a typo, a job that fell through — could never be removed, only
     * archived. That is not a defensible place to leave a user's own data.
     *
     * What survives and what does not:
     *
     * Shifts survive. They are the user's record of hours worked and have nothing
     * to do with whether the client project still exists, so they are recast as
     * ordinary employee-paid work rather than deleted. That does change their pay:
     * project time earns no wage, employee time does, so the hours start counting
     * towards wages and overtime and the caller must say so before asking.
     *
     * Billing records and payments do not survive. They describe what this client
     * was asked to pay and what they paid; without the project they describe
     * nothing.
     *
     * One transaction, because a half-deleted project is worse than either
     * outcome: shifts released but the project still listed, or the reverse.
     */
    override suspend fun deleteProject(userId: String, projectId: String): Int =
        transactionRunner.inTransaction {
            val existing = projectDao.getById(userId, projectId) ?: return@inTransaction 0
            val now = Instant.now().toEpochMilli()
            val status = SyncStatus.PENDING_DELETE
            val released = shiftDao.unlinkProject(userId, projectId, now)
            // Cascade as soft deletes: the tables carry no SQL foreign keys, so
            // leaving children behind would orphan billing and payment rows.
            paymentDao.softDeleteForProject(existing.localId, now, status, now)
            billingRecordDao.softDeleteForProject(existing.localId, now, status, now)
            projectDao.softDelete(existing.localId, now, status, now)
            released
        }.also { syncTrigger.schedule() }

    // ── Billing records ───────────────────────────────────────────────────────

    override fun observeBillingRecords(projectId: String): Flow<List<ProjectBillingRecord>> =
        billingRecordDao.observeForProject(projectId).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllBillingRecords(userId: String): Flow<List<ProjectBillingRecord>> =
        billingRecordDao.observeForUser(userId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getBillingRecords(projectId: String): List<ProjectBillingRecord> =
        billingRecordDao.getForProject(projectId).map { it.toDomain() }

    override suspend fun getBillingRecord(recordId: String): ProjectBillingRecord? =
        billingRecordDao.getByLocalId(recordId)?.takeIf { it.deletedAt == null }?.toDomain()

    override suspend fun upsertBillingRecord(record: ProjectBillingRecord): ProjectBillingRecord {
        val now = Instant.now()
        val id = record.id.ifBlank { UUID.randomUUID().toString() }
        val existing = billingRecordDao.getByLocalId(id)
            ?: record.remoteId?.let { billingRecordDao.getByRemoteId(it) }
        val entity = record.copy(id = id).toEntity(
            syncStatus = syncStatusForMutation(existing?.syncStatus),
            remoteId = existing?.remoteId ?: record.remoteId,
        ).copy(
            createdAt = existing?.createdAt ?: now.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
        )
        billingRecordDao.upsert(entity)
        syncTrigger.schedule()
        return entity.toDomain()
    }

    override suspend fun cancelBillingRecord(recordId: String) {
        val existing = billingRecordDao.getByLocalId(recordId) ?: return
        val now = Instant.now().toEpochMilli()
        billingRecordDao.setCancelled(
            localId = existing.localId,
            cancelledAt = now,
            syncStatus = syncStatusForMutation(existing.syncStatus),
            updatedAt = now,
        )
        syncTrigger.schedule()
    }

    override suspend fun restoreBillingRecord(recordId: String) {
        val existing = billingRecordDao.getByLocalId(recordId) ?: return
        val now = Instant.now().toEpochMilli()
        billingRecordDao.setCancelled(
            localId = existing.localId,
            cancelledAt = null,
            syncStatus = syncStatusForMutation(existing.syncStatus),
            updatedAt = now,
        )
        syncTrigger.schedule()
    }

    override suspend fun deleteBillingRecord(recordId: String) {
        val existing = billingRecordDao.getByLocalId(recordId) ?: return
        val now = Instant.now().toEpochMilli()
        val status = SyncStatus.PENDING_DELETE
        paymentDao.softDeleteForBillingRecord(existing.localId, now, status, now)
        billingRecordDao.softDelete(existing.localId, now, status, now)
        syncTrigger.schedule()
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    override fun observePayments(projectId: String): Flow<List<ProjectPayment>> =
        paymentDao.observeForProject(projectId).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllPayments(userId: String): Flow<List<ProjectPayment>> =
        paymentDao.observeForUser(userId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getPayments(projectId: String): List<ProjectPayment> =
        paymentDao.getForProject(projectId).map { it.toDomain() }

    override suspend fun getPaymentsForBillingRecord(recordId: String): List<ProjectPayment> =
        paymentDao.getForBillingRecord(recordId).map { it.toDomain() }

    override suspend fun upsertPayment(payment: ProjectPayment): ProjectPayment {
        val now = Instant.now()
        val id = payment.id.ifBlank { UUID.randomUUID().toString() }
        val record = billingRecordDao.getByLocalId(payment.billingRecordId)
            ?.takeIf { it.deletedAt == null }
            ?.toDomain()
        // Editing an existing payment must not count its own old amount against
        // the remaining balance, or raising a payment by a shekel would look
        // like an overpayment.
        val others = paymentDao.getForBillingRecord(payment.billingRecordId)
            .filter { it.localId != id }
            .map { it.toDomain() }

        val validation = ProjectPaymentValidator.validate(
            amount = payment.amount,
            billingRecord = record,
            existingPayments = others,
        )
        if (validation is PaymentValidationResult.Invalid) {
            throw ProjectPaymentRejectedException(validation.reason)
        }

        val existing = paymentDao.getByLocalId(id)
            ?: payment.remoteId?.let { paymentDao.getByRemoteId(it) }
        val entity = payment.copy(id = id).toEntity(
            syncStatus = syncStatusForMutation(existing?.syncStatus),
            remoteId = existing?.remoteId ?: payment.remoteId,
        ).copy(
            createdAt = existing?.createdAt ?: now.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
        )
        paymentDao.upsert(entity)
        syncTrigger.schedule()
        return entity.toDomain()
    }

    override suspend fun deletePayment(paymentId: String) {
        val existing = paymentDao.getByLocalId(paymentId) ?: return
        val now = Instant.now().toEpochMilli()
        paymentDao.softDelete(existing.localId, now, SyncStatus.PENDING_DELETE, now)
        syncTrigger.schedule()
    }

    /**
     * Keeps a row that has never reached the server in PENDING_CREATE, so an
     * edit before the first push does not turn into an update of a row that
     * does not exist remotely.
     */
    private fun syncStatusForMutation(existing: SyncStatus?): SyncStatus = when (existing) {
        null -> SyncStatus.PENDING_CREATE
        SyncStatus.PENDING_CREATE -> SyncStatus.PENDING_CREATE
        SyncStatus.PENDING_DELETE -> SyncStatus.PENDING_DELETE
        else -> SyncStatus.PENDING_UPDATE
    }
}
