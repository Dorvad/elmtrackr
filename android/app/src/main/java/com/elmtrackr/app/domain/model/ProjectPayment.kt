package com.elmtrackr.app.domain.model

import com.elmtrackr.app.domain.money.Money
import java.time.Instant
import java.time.LocalDate

/**
 * Money received against a billing record. Several payments may reference one
 * record, which is how partial payment is represented.
 *
 * [billingRecordId] is required: a payment settles a specific billing request,
 * and the validation rules (currency match, no overpayment) are only meaningful
 * relative to one. Recording money before billing means recording the billing
 * record first.
 */
data class ProjectPayment(
    val id: String,
    val userId: String,
    val projectId: String,
    val billingRecordId: String,
    val paidOn: LocalDate,
    /** Must be positive, and in the billing record's currency. */
    val amount: Money,
    val method: String? = null,
    val externalReference: String? = null,
    val notes: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val remoteId: String? = null,
) {
    val currencyCode: String get() = amount.currencyCode
}
