package com.elmtrackr.app.domain.model

import com.elmtrackr.app.domain.money.ProjectFee
import java.time.Instant
import java.time.LocalDate

/**
 * A billing request the user recorded, or a reference to an invoice they issued
 * somewhere else.
 *
 * **ElmTrackr does not generate official invoices.** This record exists so the
 * app can track "I asked for the money on this date, it is due on that date,
 * and here is the reference I used" — nothing here is a legal document.
 *
 * A separate entity rather than columns on the project, so recurring billing,
 * partial billing and credit notes can be added later without a destructive
 * migration. The MVP UI shows one active record per project; the domain and
 * schema allow many.
 *
 * Every money field is a snapshot taken when the record was created: changing a
 * project's fee or tax rate afterwards must not restate what was already billed.
 */
data class ProjectBillingRecord(
    val id: String,
    val userId: String,
    val projectId: String,
    /** Fee snapshot: base, tax, total, mode, rate and label as billed. */
    val fee: ProjectFee,
    /** An invoice or reference number the user created elsewhere. */
    val externalReference: String? = null,
    val notes: String? = null,
    val billedOn: LocalDate,
    val dueOn: LocalDate? = null,
    /** Non-null once withdrawn. A cancelled record never becomes overdue. */
    val cancelledAt: Instant? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val remoteId: String? = null,
) {
    val isCancelled: Boolean get() = cancelledAt != null

    /** Records that count toward billing status. */
    val isActive: Boolean get() = !isCancelled

    val currencyCode: String get() = fee.currencyCode

    /** The amount the client owes for this record. */
    val billedTotal get() = fee.clientTotal
}
