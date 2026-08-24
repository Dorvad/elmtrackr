package com.elmtrackr.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire shapes for workplaces, leave policies, reported absences and payslip
 * balances — the five tables added by `20260811000000_workplaces_and_leave.sql`.
 *
 * They had a schema on the server and no sync steps on Android, so everything the
 * leave feature stored was device-local: a sick-pay arrangement, and every absence
 * ever reported, was lost on reinstall or a second device.
 *
 * Two conventions carried over from the tables that were already synced. Parent
 * links travel as **remote** ids (`workplace_id`, `absence_event_id`) while the
 * entities hold local ones, so the mappers translate in both directions and a push
 * waits until the parent has a remote id rather than sending one the server would
 * reject. And `client_updated_at` is the write guard: an update is filtered on it
 * so a row edited more recently elsewhere is not overwritten.
 *
 * Calendar dates are epoch-day integers, matching the tables: an absence is a date
 * and must not move by a day through a timezone conversion.
 */

// ── Workplaces ───────────────────────────────────────────────────────────────

@Serializable
data class RemoteWorkplaceRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("region_code") val regionCode: String,
    @SerialName("currency_code") val currencyCode: String,
    val timezone: String,
    @SerialName("employment_start_date") val employmentStartDate: Int? = null,
    @SerialName("is_default") val isDefault: Boolean,
    @SerialName("is_archived") val isArchived: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String? = null,
)

@Serializable
data class RemoteWorkplaceInsert(
    /** Client-generated UUID so a retried create collides instead of duplicating. */
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("region_code") val regionCode: String,
    @SerialName("currency_code") val currencyCode: String,
    val timezone: String,
    @SerialName("employment_start_date") val employmentStartDate: Int? = null,
    @SerialName("is_default") val isDefault: Boolean,
    @SerialName("is_archived") val isArchived: Boolean,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

@Serializable
data class RemoteWorkplaceUpdate(
    val name: String,
    @SerialName("region_code") val regionCode: String,
    @SerialName("currency_code") val currencyCode: String,
    val timezone: String,
    @SerialName("employment_start_date") val employmentStartDate: Int? = null,
    @SerialName("is_default") val isDefault: Boolean,
    @SerialName("is_archived") val isArchived: Boolean,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

// ── Leave policies ───────────────────────────────────────────────────────────

@Serializable
data class RemoteLeavePolicyRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("workplace_id") val workplaceId: String,
    @SerialName("region_code") val regionCode: String,
    @SerialName("rules_json") val rulesJson: JsonElement,
    @SerialName("effective_from") val effectiveFrom: String,
    @SerialName("effective_until") val effectiveUntil: String? = null,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String? = null,
)

@Serializable
data class RemoteLeavePolicyInsert(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("workplace_id") val workplaceId: String,
    @SerialName("region_code") val regionCode: String,
    @SerialName("rules_json") val rulesJson: JsonElement,
    @SerialName("effective_from") val effectiveFrom: String,
    @SerialName("effective_until") val effectiveUntil: String? = null,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

@Serializable
data class RemoteLeavePolicyUpdate(
    @SerialName("region_code") val regionCode: String,
    @SerialName("rules_json") val rulesJson: JsonElement,
    @SerialName("effective_from") val effectiveFrom: String,
    @SerialName("effective_until") val effectiveUntil: String? = null,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

// ── Absence events ───────────────────────────────────────────────────────────

@Serializable
data class RemoteAbsenceEventRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: String,
    @SerialName("start_date") val startDate: Int,
    @SerialName("end_date") val endDate: Int,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String? = null,
)

@Serializable
data class RemoteAbsenceEventInsert(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: String,
    @SerialName("start_date") val startDate: Int,
    @SerialName("end_date") val endDate: Int,
    val notes: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

@Serializable
data class RemoteAbsenceEventUpdate(
    val type: String,
    @SerialName("start_date") val startDate: Int,
    @SerialName("end_date") val endDate: Int,
    val notes: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

// ── Absence allocations ──────────────────────────────────────────────────────

@Serializable
data class RemoteAbsenceAllocationRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("absence_event_id") val absenceEventId: String,
    @SerialName("workplace_id") val workplaceId: String,
    @SerialName("affected_date") val affectedDate: Int,
    @SerialName("entitlement_units") val entitlementUnits: Double,
    val unit: String,
    @SerialName("expected_work_minutes") val expectedWorkMinutes: Int? = null,
    @SerialName("policy_snapshot_json") val policySnapshotJson: JsonElement? = null,
    @SerialName("calculation_snapshot_json") val calculationSnapshotJson: JsonElement? = null,
    @SerialName("estimated_gross_pay") val estimatedGrossPay: Double,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String? = null,
)

@Serializable
data class RemoteAbsenceAllocationInsert(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("absence_event_id") val absenceEventId: String,
    @SerialName("workplace_id") val workplaceId: String,
    @SerialName("affected_date") val affectedDate: Int,
    @SerialName("entitlement_units") val entitlementUnits: Double,
    val unit: String,
    @SerialName("expected_work_minutes") val expectedWorkMinutes: Int? = null,
    @SerialName("policy_snapshot_json") val policySnapshotJson: JsonElement? = null,
    @SerialName("calculation_snapshot_json") val calculationSnapshotJson: JsonElement? = null,
    @SerialName("estimated_gross_pay") val estimatedGrossPay: Double,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

@Serializable
data class RemoteAbsenceAllocationUpdate(
    @SerialName("affected_date") val affectedDate: Int,
    @SerialName("entitlement_units") val entitlementUnits: Double,
    val unit: String,
    @SerialName("expected_work_minutes") val expectedWorkMinutes: Int? = null,
    @SerialName("policy_snapshot_json") val policySnapshotJson: JsonElement? = null,
    @SerialName("calculation_snapshot_json") val calculationSnapshotJson: JsonElement? = null,
    @SerialName("estimated_gross_pay") val estimatedGrossPay: Double,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

// ── Payslip balance snapshots ────────────────────────────────────────────────

@Serializable
data class RemoteLeaveBalanceSnapshotRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("workplace_id") val workplaceId: String,
    @SerialName("balance_type") val balanceType: String,
    val balance: Double,
    val unit: String,
    @SerialName("as_of_date") val asOfDate: Int,
    val source: String,
    val label: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String? = null,
)

@Serializable
data class RemoteLeaveBalanceSnapshotInsert(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("workplace_id") val workplaceId: String,
    @SerialName("balance_type") val balanceType: String,
    val balance: Double,
    val unit: String,
    @SerialName("as_of_date") val asOfDate: Int,
    val source: String,
    val label: String? = null,
    val notes: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

@Serializable
data class RemoteLeaveBalanceSnapshotUpdate(
    @SerialName("balance_type") val balanceType: String,
    val balance: Double,
    val unit: String,
    @SerialName("as_of_date") val asOfDate: Int,
    val source: String,
    val label: String? = null,
    val notes: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)
