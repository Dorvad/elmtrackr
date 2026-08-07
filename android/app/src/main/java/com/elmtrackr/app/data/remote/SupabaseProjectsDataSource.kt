package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

/**
 * The three Paid Projects tables. Each mirrors SupabaseShiftsDataSource: a total
 * `(updated_at, id)` order with range paging, and updates guarded on
 * `client_updated_at` that ask for the row back so a rejected write is
 * distinguishable from an applied one.
 */
class SupabaseProjectsDataSource(
    private val client: SupabaseClient,
) : RemoteProjectDataSource {

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteProjectRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso -> filter { gte(COLUMN_UPDATED_AT, iso) } }
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            order(COLUMN_ID, Order.ASCENDING)
            range(offset.toLong(), offset.toLong() + limit - 1)
        }.decodeList<RemoteProjectRow>()

    override suspend fun findById(remoteId: String): RemoteProjectRow? =
        client.from(TABLE).select {
            filter { eq(COLUMN_ID, remoteId) }
            limit(1)
        }.decodeList<RemoteProjectRow>().firstOrNull()

    override suspend fun insert(project: RemoteProjectInsert): RemoteProjectRow =
        client.from(TABLE).insert(project) { select() }.decodeSingle<RemoteProjectRow>()

    override suspend fun update(remoteId: String, project: RemoteProjectUpdate): RemoteProjectRow? =
        client.from(TABLE).update(project) {
            select()
            filter {
                eq(COLUMN_ID, remoteId)
                lte(COLUMN_CLIENT_UPDATED_AT, project.clientUpdatedAt)
            }
        }.decodeList<RemoteProjectRow>().firstOrNull()

    private companion object {
        const val TABLE = "projects"
        const val COLUMN_ID = "id"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_CLIENT_UPDATED_AT = "client_updated_at"
    }
}

class SupabaseProjectBillingRecordsDataSource(
    private val client: SupabaseClient,
) : RemoteProjectBillingRecordDataSource {

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteProjectBillingRecordRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso -> filter { gte(COLUMN_UPDATED_AT, iso) } }
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            order(COLUMN_ID, Order.ASCENDING)
            range(offset.toLong(), offset.toLong() + limit - 1)
        }.decodeList<RemoteProjectBillingRecordRow>()

    override suspend fun findById(remoteId: String): RemoteProjectBillingRecordRow? =
        client.from(TABLE).select {
            filter { eq(COLUMN_ID, remoteId) }
            limit(1)
        }.decodeList<RemoteProjectBillingRecordRow>().firstOrNull()

    override suspend fun insert(
        record: RemoteProjectBillingRecordInsert,
    ): RemoteProjectBillingRecordRow =
        client.from(TABLE).insert(record) { select() }
            .decodeSingle<RemoteProjectBillingRecordRow>()

    override suspend fun update(
        remoteId: String,
        record: RemoteProjectBillingRecordUpdate,
    ): RemoteProjectBillingRecordRow? =
        client.from(TABLE).update(record) {
            select()
            filter {
                eq(COLUMN_ID, remoteId)
                lte(COLUMN_CLIENT_UPDATED_AT, record.clientUpdatedAt)
            }
        }.decodeList<RemoteProjectBillingRecordRow>().firstOrNull()

    private companion object {
        const val TABLE = "project_billing_records"
        const val COLUMN_ID = "id"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_CLIENT_UPDATED_AT = "client_updated_at"
    }
}

class SupabaseProjectPaymentsDataSource(
    private val client: SupabaseClient,
) : RemoteProjectPaymentDataSource {

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteProjectPaymentRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso -> filter { gte(COLUMN_UPDATED_AT, iso) } }
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            order(COLUMN_ID, Order.ASCENDING)
            range(offset.toLong(), offset.toLong() + limit - 1)
        }.decodeList<RemoteProjectPaymentRow>()

    override suspend fun findById(remoteId: String): RemoteProjectPaymentRow? =
        client.from(TABLE).select {
            filter { eq(COLUMN_ID, remoteId) }
            limit(1)
        }.decodeList<RemoteProjectPaymentRow>().firstOrNull()

    override suspend fun insert(payment: RemoteProjectPaymentInsert): RemoteProjectPaymentRow =
        client.from(TABLE).insert(payment) { select() }.decodeSingle<RemoteProjectPaymentRow>()

    override suspend fun update(
        remoteId: String,
        payment: RemoteProjectPaymentUpdate,
    ): RemoteProjectPaymentRow? =
        client.from(TABLE).update(payment) {
            select()
            filter {
                eq(COLUMN_ID, remoteId)
                lte(COLUMN_CLIENT_UPDATED_AT, payment.clientUpdatedAt)
            }
        }.decodeList<RemoteProjectPaymentRow>().firstOrNull()

    private companion object {
        const val TABLE = "project_payments"
        const val COLUMN_ID = "id"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_CLIENT_UPDATED_AT = "client_updated_at"
    }
}
