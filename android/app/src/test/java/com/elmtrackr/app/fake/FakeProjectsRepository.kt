package com.elmtrackr.app.fake

import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.projects.PaymentValidationResult
import com.elmtrackr.app.domain.projects.ProjectPaymentRejectedException
import com.elmtrackr.app.domain.projects.ProjectPaymentValidator
import com.elmtrackr.app.domain.repository.ProjectsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * In-memory ProjectsRepository. Mirrors the real implementation's behaviour where
 * tests depend on it: id assignment, cascade on delete, and payment validation.
 */
class FakeProjectsRepository : ProjectsRepository {

    private val projects = MutableStateFlow<List<Project>>(emptyList())
    private val billingRecords = MutableStateFlow<List<ProjectBillingRecord>>(emptyList())
    private val payments = MutableStateFlow<List<ProjectPayment>>(emptyList())

    private var idCounter = 0

    fun setProjects(vararg values: Project) { projects.value = values.toList() }
    fun setBillingRecords(vararg values: ProjectBillingRecord) { billingRecords.value = values.toList() }
    fun setPayments(vararg values: ProjectPayment) { payments.value = values.toList() }

    val currentProjects: List<Project> get() = projects.value
    val currentBillingRecords: List<ProjectBillingRecord> get() = billingRecords.value
    val currentPayments: List<ProjectPayment> get() = payments.value

    override fun observeProjects(userId: String): Flow<List<Project>> =
        projects.map { list -> list.filter { it.userId == userId } }

    override fun observeActiveProjects(userId: String): Flow<List<Project>> =
        projects.map { list ->
            list.filter { it.userId == userId && it.workStatus != ProjectWorkStatus.ARCHIVED }
        }

    override fun observeProject(projectId: String): Flow<Project?> =
        projects.map { list -> list.firstOrNull { it.id == projectId } }

    override suspend fun getProjects(userId: String): List<Project> =
        projects.value.filter { it.userId == userId }

    override suspend fun getProject(userId: String, projectId: String): Project? =
        projects.value.firstOrNull { it.userId == userId && it.id == projectId }

    override suspend fun hasAnyProject(userId: String): Boolean =
        projects.value.any { it.userId == userId }

    override suspend fun upsertProject(project: Project): Project {
        val id = project.id.ifBlank { "project-${++idCounter}" }
        val saved = project.copy(id = id)
        projects.value = projects.value.filterNot { it.id == id } + saved
        return saved
    }

    override suspend fun archiveProject(userId: String, projectId: String) {
        projects.value = projects.value.map {
            if (it.id == projectId) {
                it.copy(workStatus = ProjectWorkStatus.ARCHIVED, archivedAt = Instant.EPOCH)
            } else {
                it
            }
        }
    }

    override suspend fun unarchiveProject(userId: String, projectId: String) {
        projects.value = projects.value.map {
            if (it.id == projectId) it.copy(workStatus = ProjectWorkStatus.ACTIVE, archivedAt = null) else it
        }
    }

    override suspend fun deleteProject(userId: String, projectId: String) {
        payments.value = payments.value.filterNot { it.projectId == projectId }
        billingRecords.value = billingRecords.value.filterNot { it.projectId == projectId }
        projects.value = projects.value.filterNot { it.id == projectId }
    }

    override fun observeBillingRecords(projectId: String): Flow<List<ProjectBillingRecord>> =
        billingRecords.map { list -> list.filter { it.projectId == projectId } }

    override fun observeAllBillingRecords(userId: String): Flow<List<ProjectBillingRecord>> =
        billingRecords.map { list -> list.filter { it.userId == userId } }

    override suspend fun getBillingRecords(projectId: String): List<ProjectBillingRecord> =
        billingRecords.value.filter { it.projectId == projectId }

    override suspend fun getBillingRecord(recordId: String): ProjectBillingRecord? =
        billingRecords.value.firstOrNull { it.id == recordId }

    override suspend fun upsertBillingRecord(record: ProjectBillingRecord): ProjectBillingRecord {
        val id = record.id.ifBlank { "bill-${++idCounter}" }
        val saved = record.copy(id = id)
        billingRecords.value = billingRecords.value.filterNot { it.id == id } + saved
        return saved
    }

    override suspend fun cancelBillingRecord(recordId: String) {
        billingRecords.value = billingRecords.value.map {
            if (it.id == recordId) it.copy(cancelledAt = Instant.EPOCH) else it
        }
    }

    override suspend fun restoreBillingRecord(recordId: String) {
        billingRecords.value = billingRecords.value.map {
            if (it.id == recordId) it.copy(cancelledAt = null) else it
        }
    }

    override suspend fun deleteBillingRecord(recordId: String) {
        payments.value = payments.value.filterNot { it.billingRecordId == recordId }
        billingRecords.value = billingRecords.value.filterNot { it.id == recordId }
    }

    override fun observePayments(projectId: String): Flow<List<ProjectPayment>> =
        payments.map { list -> list.filter { it.projectId == projectId } }

    override fun observeAllPayments(userId: String): Flow<List<ProjectPayment>> =
        payments.map { list -> list.filter { it.userId == userId } }

    override suspend fun getPayments(projectId: String): List<ProjectPayment> =
        payments.value.filter { it.projectId == projectId }

    override suspend fun getPaymentsForBillingRecord(recordId: String): List<ProjectPayment> =
        payments.value.filter { it.billingRecordId == recordId }

    override suspend fun upsertPayment(payment: ProjectPayment): ProjectPayment {
        val id = payment.id.ifBlank { "pay-${++idCounter}" }
        val record = billingRecords.value.firstOrNull { it.id == payment.billingRecordId }
        val others = payments.value.filter { it.billingRecordId == payment.billingRecordId && it.id != id }
        val validation = ProjectPaymentValidator.validate(payment.amount, record, others)
        if (validation is PaymentValidationResult.Invalid) {
            throw ProjectPaymentRejectedException(validation.reason)
        }
        val saved = payment.copy(id = id)
        payments.value = payments.value.filterNot { it.id == id } + saved
        return saved
    }

    override suspend fun deletePayment(paymentId: String) {
        payments.value = payments.value.filterNot { it.id == paymentId }
    }
}
