package com.elmtrackr.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * A fixed-fee project.
 *
 * Money columns are TEXT holding canonical decimal strings (see
 * [com.elmtrackr.app.data.local.converter.Converters]) — never REAL. Date
 * columns are epoch day; timestamp columns are epoch millis.
 *
 * `taxAmount` and `clientTotal` are stored alongside `baseFee` even though they
 * are derived. They are written only by the mapper from
 * [com.elmtrackr.app.domain.money.ProjectFee], which is the single source of
 * truth for the calculation, and having them on the row lets lists sort and
 * total by client value without rebuilding every fee.
 */
@Entity(
    tableName = "projects",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "workStatus"]),
        Index(value = ["userId", "syncStatus"]),
        Index(value = ["remoteId"]),
    ],
)
data class ProjectEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val userId: String,
    val name: String,
    val clientName: String?,
    /** Reserved for a future clients table; always null in this version. */
    val clientId: String?,
    val description: String?,
    val workStatus: String,
    /** ISO 4217 code. Snapshotted per project; never converted. */
    val currencyCode: String,
    /** Fee before tax. */
    val baseFee: BigDecimal,
    val taxLabel: String?,
    /** Percentage, e.g. 18 for 18 %. */
    val taxRatePercent: BigDecimal,
    /** NONE / EXCLUSIVE / INCLUSIVE. */
    val taxMode: String,
    /** Derived from the fee; written only via ProjectFee. */
    val taxAmount: BigDecimal,
    /** Derived from the fee; what the client is charged. */
    val clientTotal: BigDecimal,
    val hourBudgetMinutes: Int?,
    val targetHourlyRate: BigDecimal?,
    /** Epoch day. */
    val startDate: Long?,
    val deadline: Long?,
    val completionDate: Long?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long?,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)
