package com.elmtrackr.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * Money received against a billing record. Many rows may share one
 * `billingRecordLocalId`, which is how partial payment is represented.
 *
 * `currencyCode` is stored per payment even though it must equal the billing
 * record's currency: the validator enforces the match on write, and storing it
 * means a payment row is self-describing and can never be summed into the wrong
 * currency by a later query.
 */
@Entity(
    tableName = "project_payments",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["projectLocalId"]),
        Index(value = ["billingRecordLocalId"]),
        Index(value = ["userId", "syncStatus"]),
        Index(value = ["remoteId"]),
    ],
)
data class ProjectPaymentEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val userId: String,
    val projectLocalId: String,
    val billingRecordLocalId: String,
    /** Epoch day. */
    val paidOn: Long,
    /** Positive; zero and negative amounts are rejected before the write. */
    val amount: BigDecimal,
    val currencyCode: String,
    val method: String?,
    val externalReference: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)
