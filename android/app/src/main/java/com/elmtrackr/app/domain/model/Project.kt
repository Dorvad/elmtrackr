package com.elmtrackr.app.domain.model

import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.ProjectFee
import java.time.Instant
import java.time.LocalDate

/**
 * How far along the *work* is. Deliberately separate from billing state: a
 * completed project can be unpaid, and a paid project can still be active.
 * Payment states never appear here.
 */
enum class ProjectWorkStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    COMPLETED,
    CANCELLED,
    ARCHIVED,
    ;

    /** Statuses that no longer expect new time to be tracked against them. */
    val isClosed: Boolean get() = this == COMPLETED || this == CANCELLED || this == ARCHIVED

    companion object {
        fun fromPersisted(raw: String?): ProjectWorkStatus {
            if (raw.isNullOrBlank()) return ACTIVE
            val normalized = raw.trim().uppercase().replace('-', '_')
            return entries.find { it.name == normalized } ?: ACTIVE
        }
    }

    fun toWire(): String = name.lowercase()
}

/**
 * A fixed-fee engagement: an agreed price, the time spent against it, and what
 * the client has paid.
 *
 * The money side lives in [fee], which guarantees base + tax == client total.
 * Billing and payments are separate entities, so a project carries no billing
 * status of its own — see ProjectBillingStatusResolver.
 */
data class Project(
    val id: String,
    val userId: String,
    val name: String,
    /** Free text today. A CRM-style clients table can arrive later via [clientId]. */
    val clientName: String? = null,
    /** Reserved for a future clients table; always null in this version. */
    val clientId: String? = null,
    val description: String? = null,
    val workStatus: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
    val fee: ProjectFee,
    /** Planned effort, in minutes, so it matches how shifts are measured. */
    val hourBudgetMinutes: Int? = null,
    /** What the user hopes to earn per hour on this project. */
    val targetHourlyRate: Money? = null,
    val startDate: LocalDate? = null,
    val deadline: LocalDate? = null,
    val completionDate: LocalDate? = null,
    val notes: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val archivedAt: Instant? = null,
    val remoteId: String? = null,
) {
    /** ISO 4217 code this project is priced in. Never converted. */
    val currencyCode: String get() = fee.currencyCode

    val isArchived: Boolean get() = archivedAt != null || workStatus == ProjectWorkStatus.ARCHIVED
}
