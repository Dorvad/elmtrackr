package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import kotlinx.coroutines.flow.Flow

interface ProjectsRepository {

    fun observeProjects(userId: String): Flow<List<Project>>

    /** Excludes archived projects. */
    fun observeActiveProjects(userId: String): Flow<List<Project>>

    fun observeProject(projectId: String): Flow<Project?>

    suspend fun getProjects(userId: String): List<Project>

    suspend fun getProject(userId: String, projectId: String): Project?

    suspend fun hasAnyProject(userId: String): Boolean

    /** Creates or updates. A blank id is assigned a new UUID. */
    suspend fun upsertProject(project: Project): Project

    /** Reversible: sets the archived timestamp and work status. */
    suspend fun archiveProject(userId: String, projectId: String)

    suspend fun unarchiveProject(userId: String, projectId: String)

    /**
     * Soft-deletes the project and cascades to its billing records and
     * payments, so no orphaned money rows are left behind.
     */
    /**
     * Deletes a project permanently and returns the number of shifts released
     * from it. Those shifts keep their hours and become employee-paid work.
     */
    suspend fun deleteProject(userId: String, projectId: String): Int

    // ── Billing records ───────────────────────────────────────────────────────

    fun observeBillingRecords(projectId: String): Flow<List<ProjectBillingRecord>>

    /** Every billing record for the user, so a list screen can derive statuses in one pass. */
    fun observeAllBillingRecords(userId: String): Flow<List<ProjectBillingRecord>>

    suspend fun getBillingRecords(projectId: String): List<ProjectBillingRecord>

    suspend fun getBillingRecord(recordId: String): ProjectBillingRecord?

    suspend fun upsertBillingRecord(record: ProjectBillingRecord): ProjectBillingRecord

    /** Withdraws a bill. A cancelled record never counts as overdue. */
    suspend fun cancelBillingRecord(recordId: String)

    suspend fun restoreBillingRecord(recordId: String)

    /** Soft-deletes the record and its payments. */
    suspend fun deleteBillingRecord(recordId: String)

    // ── Payments ──────────────────────────────────────────────────────────────

    fun observePayments(projectId: String): Flow<List<ProjectPayment>>

    /** Every payment for the user, paired with [observeAllBillingRecords]. */
    fun observeAllPayments(userId: String): Flow<List<ProjectPayment>>

    suspend fun getPayments(projectId: String): List<ProjectPayment>

    suspend fun getPaymentsForBillingRecord(recordId: String): List<ProjectPayment>

    /**
     * Validates before writing. Throws
     * [com.elmtrackr.app.domain.projects.ProjectPaymentRejectedException] for a
     * currency mismatch, a zero or negative amount, an overpayment, or a
     * missing or cancelled billing record.
     */
    suspend fun upsertPayment(payment: ProjectPayment): ProjectPayment

    suspend fun deletePayment(paymentId: String)
}
