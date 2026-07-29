package com.elmtrackr.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * A billing request or an external invoice reference the user recorded.
 * ElmTrackr does not generate official invoices.
 *
 * Its own table rather than columns on `projects`, so multiple records per
 * project — recurring billing, partial billing, credit notes — remain possible
 * without a destructive migration. Nothing here constrains a project to one.
 *
 * Every money column is a snapshot of the fee as billed, so later edits to the
 * project's price or tax rate cannot restate an existing bill.
 */
@Entity(
    tableName = "project_billing_records",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["projectLocalId"]),
        Index(value = ["userId", "syncStatus"]),
        Index(value = ["remoteId"]),
    ],
)
data class ProjectBillingRecordEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val userId: String,
    val projectLocalId: String,
    val baseAmount: BigDecimal,
    val taxLabel: String?,
    val taxRatePercent: BigDecimal,
    val taxMode: String,
    val taxAmount: BigDecimal,
    val totalAmount: BigDecimal,
    val currencyCode: String,
    /** Reference the user created elsewhere, not one ElmTrackr issued. */
    val externalReference: String?,
    /** Epoch day. */
    val billedOn: Long,
    val dueOn: Long?,
    /** Epoch millis; non-null means withdrawn. Cancelled never goes overdue. */
    val cancelledAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)
