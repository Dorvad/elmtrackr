package com.elmtrackr.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for Paid Projects.
 *
 * Every money field is a `String` holding a canonical decimal, matching both the
 * Room columns and the `text` columns in Postgres. Decoding them as `Double`
 * would put the one conversion the local schema exists to avoid — decimal
 * through binary floating point — back into the middle of the sync path, on the
 * numbers that say what a client owes.
 *
 * Dates that are calendar days (`billed_on`, `paid_on`, a project's deadline)
 * are epoch-day integers rather than timestamps, so they cannot shift by a day
 * through a timezone conversion on the way to the server and back.
 */
@Serializable
data class RemoteProjectRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    val description: String? = null,
    @SerialName("work_status") val workStatus: String,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("base_fee") val baseFee: String,
    @SerialName("tax_label") val taxLabel: String? = null,
    @SerialName("tax_rate_percent") val taxRatePercent: String,
    @SerialName("tax_mode") val taxMode: String,
    @SerialName("tax_amount") val taxAmount: String,
    @SerialName("client_total") val clientTotal: String,
    @SerialName("hour_budget_minutes") val hourBudgetMinutes: Int? = null,
    @SerialName("target_hourly_rate") val targetHourlyRate: String? = null,
    @SerialName("start_date") val startDate: Long? = null,
    val deadline: Long? = null,
    @SerialName("completion_date") val completionDate: Long? = null,
    val notes: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String? = null,
)

@Serializable
data class RemoteProjectInsert(
    /** Client-generated UUID so retried creates collide instead of duplicating. */
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    val description: String? = null,
    @SerialName("work_status") val workStatus: String,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("base_fee") val baseFee: String,
    @SerialName("tax_label") val taxLabel: String? = null,
    @SerialName("tax_rate_percent") val taxRatePercent: String,
    @SerialName("tax_mode") val taxMode: String,
    @SerialName("tax_amount") val taxAmount: String,
    @SerialName("client_total") val clientTotal: String,
    @SerialName("hour_budget_minutes") val hourBudgetMinutes: Int? = null,
    @SerialName("target_hourly_rate") val targetHourlyRate: String? = null,
    @SerialName("start_date") val startDate: Long? = null,
    val deadline: Long? = null,
    @SerialName("completion_date") val completionDate: Long? = null,
    val notes: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

@Serializable
data class RemoteProjectUpdate(
    val name: String,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    val description: String? = null,
    @SerialName("work_status") val workStatus: String,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("base_fee") val baseFee: String,
    @SerialName("tax_label") val taxLabel: String? = null,
    @SerialName("tax_rate_percent") val taxRatePercent: String,
    @SerialName("tax_mode") val taxMode: String,
    @SerialName("tax_amount") val taxAmount: String,
    @SerialName("client_total") val clientTotal: String,
    @SerialName("hour_budget_minutes") val hourBudgetMinutes: Int? = null,
    @SerialName("target_hourly_rate") val targetHourlyRate: String? = null,
    @SerialName("start_date") val startDate: Long? = null,
    val deadline: Long? = null,
    @SerialName("completion_date") val completionDate: Long? = null,
    val notes: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    /** Non-null makes this update a tombstone. */
    @SerialName("deleted_at") val deletedAt: String? = null,
    /** See RemoteShiftUpdate.clientUpdatedAt — this is the write's guard. */
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

@Serializable
data class RemoteProjectBillingRecordRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("base_amount") val baseAmount: String,
    @SerialName("tax_label") val taxLabel: String? = null,
    @SerialName("tax_rate_percent") val taxRatePercent: String,
    @SerialName("tax_mode") val taxMode: String,
    @SerialName("tax_amount") val taxAmount: String,
    @SerialName("total_amount") val totalAmount: String,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("external_reference") val externalReference: String? = null,
    val notes: String? = null,
    @SerialName("billed_on") val billedOn: Long,
    @SerialName("due_on") val dueOn: Long? = null,
    @SerialName("cancelled_at") val cancelledAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String? = null,
)

@Serializable
data class RemoteProjectBillingRecordInsert(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("base_amount") val baseAmount: String,
    @SerialName("tax_label") val taxLabel: String? = null,
    @SerialName("tax_rate_percent") val taxRatePercent: String,
    @SerialName("tax_mode") val taxMode: String,
    @SerialName("tax_amount") val taxAmount: String,
    @SerialName("total_amount") val totalAmount: String,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("external_reference") val externalReference: String? = null,
    val notes: String? = null,
    @SerialName("billed_on") val billedOn: Long,
    @SerialName("due_on") val dueOn: Long? = null,
    @SerialName("cancelled_at") val cancelledAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

@Serializable
data class RemoteProjectBillingRecordUpdate(
    @SerialName("base_amount") val baseAmount: String,
    @SerialName("tax_label") val taxLabel: String? = null,
    @SerialName("tax_rate_percent") val taxRatePercent: String,
    @SerialName("tax_mode") val taxMode: String,
    @SerialName("tax_amount") val taxAmount: String,
    @SerialName("total_amount") val totalAmount: String,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("external_reference") val externalReference: String? = null,
    val notes: String? = null,
    @SerialName("billed_on") val billedOn: Long,
    @SerialName("due_on") val dueOn: Long? = null,
    @SerialName("cancelled_at") val cancelledAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

@Serializable
data class RemoteProjectPaymentRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("billing_record_id") val billingRecordId: String,
    @SerialName("paid_on") val paidOn: Long,
    val amount: String,
    @SerialName("currency_code") val currencyCode: String,
    val method: String? = null,
    @SerialName("external_reference") val externalReference: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String? = null,
)

@Serializable
data class RemoteProjectPaymentInsert(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("billing_record_id") val billingRecordId: String,
    @SerialName("paid_on") val paidOn: Long,
    val amount: String,
    @SerialName("currency_code") val currencyCode: String,
    val method: String? = null,
    @SerialName("external_reference") val externalReference: String? = null,
    val notes: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

@Serializable
data class RemoteProjectPaymentUpdate(
    @SerialName("paid_on") val paidOn: Long,
    val amount: String,
    @SerialName("currency_code") val currencyCode: String,
    val method: String? = null,
    @SerialName("external_reference") val externalReference: String? = null,
    val notes: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)
